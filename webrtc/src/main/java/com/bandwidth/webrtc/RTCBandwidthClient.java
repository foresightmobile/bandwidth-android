package com.bandwidth.webrtc;

import android.content.Context;
import android.se.omapi.Session;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnHandleSubscribeSdpOfferListener;
import com.bandwidth.webrtc.listeners.OnOfferPublishSdpListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.listeners.OnSetupPublishingPeerConnectionListener;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.rpc.transit.AnswerSdpResult;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpOfferParams;
import com.bandwidth.webrtc.signaling.websockets.NeoVisionariesWebSocket;
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider;
import com.bandwidth.webrtc.types.DataChannelPublishMetadata;
import com.bandwidth.webrtc.types.PublishMetadata;
import com.bandwidth.webrtc.types.PublishedStream;
import com.bandwidth.webrtc.types.RTCStream;
import com.bandwidth.webrtc.types.StreamMetadata;
import com.bandwidth.webrtc.types.StreamPublishMetadata;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RTCBandwidthClient implements RTCBandwidth, SignalingDelegate {
    private final Context appContext;

    private final RTCBandwidthDelegate delegate;
    private final Signaling signaling;

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection.RTCConfiguration configuration;

    // One peer for all published (outgoing) streams, one for all subscribed (incoming) streams;
    private PeerConnection publishingPeerConnection;
    private PeerConnection subscribingPeerConnection;

    // Standard data channels used for platform diagnostics and health checks.
    private DataChannel publishHeartbeatDataChannel;
    private DataChannel publishDiagnosticsDataChannel;
    private final Map<String, DataChannel> publishedDataChannels = new HashMap<>();
    private DataChannel subscribeHeartbeatDataChannel;
    private DataChannel subscribeDiagnosticsDataChannel;
    private final Map<String, DataChannel> subscribedDataChannels = new HashMap<>();

    // Published (outgoing) streams keyed by media stream id (msid).
    private final Map<String, PublishedStream> publishedStreams = new HashMap<>();
    // Subscribed (incoming) streams keyed by media stream id (msid).
    private Map<String, StreamMetadata> subscribedStreams= new HashMap<>();

    // Keep track of our available streams. Prevents duplicate stream available / unavailable events.
    private Map<String, MediaStream> availableMediaStreams;

    public RTCBandwidthClient(Context appContext, EglBase.Context eglContext, RTCBandwidthDelegate delegate) {
        this(appContext, eglContext, delegate, new NeoVisionariesWebSocket());
    }

    public RTCBandwidthClient(Context appContext, EglBase.Context eglContext, RTCBandwidthDelegate delegate, WebSocketProvider webSocketProvider) {
        this.appContext = appContext;
        this.delegate = delegate;

        signaling = new SignalingClient(webSocketProvider, this);

        VideoEncoderFactory videoEncoderFactory = new DefaultVideoEncoderFactory(eglContext, true, true);
        VideoDecoderFactory videoDecoderFactory = new DefaultVideoDecoderFactory(eglContext);

        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory();

        configuration = new PeerConnection.RTCConfiguration(Collections.emptyList());
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        configuration.iceServers = new ArrayList<>();
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.enableDtlsSrtp = true;

    }

    @Override
    public void connect(URI uri, OnConnectListener onConnectListener) throws ConnectionException {
        signaling.setOnConnectListener(signaling -> {
            onConnectListener.onConnect();
        });

        signaling.connect(uri);
    }

    @Override
    public void disconnect() {
        signaling.disconnect();
        cleanupPublishedStreams(publishedStreams);
        if (publishingPeerConnection != null) {
            publishingPeerConnection.close();
        }
        if (subscribingPeerConnection != null) {
            subscribingPeerConnection.close();
        }
        publishingPeerConnection = null;
        subscribingPeerConnection = null;
    }

    @Override
    public void publish(String alias, OnPublishListener onPublishListener) {
        setupPublishingPeerConnection(() -> {
            MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream(UUID.randomUUID().toString());

            AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
            AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), audioSource);
            mediaStream.addTrack(audioTrack);

            VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
            VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(UUID.randomUUID().toString(), videoSource);
            mediaStream.addTrack(videoTrack);

            addStreamToPublishingPeerConnection(mediaStream);

            StreamPublishMetadata publishMetadata = new StreamPublishMetadata(alias);
            publishedStreams.put(mediaStream.getId(), new PublishedStream(mediaStream, publishMetadata));

            offerPublishSdp(false, result -> {
                StreamMetadata metadata = result.getStreamMetadata().get(mediaStream.getId());
                List<String> mediaTypes = metadata != null ? metadata.getMediaTypes() : Collections.singletonList("APPLICATION");

                RTCStream stream = new RTCStream(mediaTypes, mediaStream, audioSource, videoSource, alias, null);
                onPublishListener.onPublish(stream);
            });
        });
    }

    private DataChannel addHeartbeatDataChannel(PeerConnection peerConnection) {
        DataChannel.Init init = new DataChannel.Init();
        init.id = 0;
        init.negotiated = true;
        init.protocol = "udp";

        return peerConnection.createDataChannel("__heartbeat__", init);
    }

    private DataChannel addDiagnosticsDataChannel(PeerConnection peerConnection) {
        DataChannel.Init init = new DataChannel.Init();
        init.id = 1;
        init.negotiated = true;
        init.protocol = "udp";

        DataChannel dataChannel = peerConnection.createDataChannel("__diagnostics__", init);
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {

            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                System.out.printf("Diagnostics Received: %s%n", StandardCharsets.UTF_8.decode(buffer.data).toString());
            }
        });
        return dataChannel;
    }

    private void setupPublishingPeerConnection(OnSetupPublishingPeerConnectionListener onSetupPublishingPeerConnectionListener) {
        if (publishingPeerConnection == null) {
            publishingPeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnectionAdapter() {
            });

            if (publishingPeerConnection != null) {
                DataChannel heartbeatDataChannel = addHeartbeatDataChannel(publishingPeerConnection);
                publishedDataChannels.put(heartbeatDataChannel.label(), heartbeatDataChannel);
                publishHeartbeatDataChannel = heartbeatDataChannel;

                DataChannel diagnosticsDataChannel = addDiagnosticsDataChannel(publishingPeerConnection);
                publishedDataChannels.put(diagnosticsDataChannel.label(), diagnosticsDataChannel);
                publishDiagnosticsDataChannel = diagnosticsDataChannel;

                offerPublishSdp(false, result -> {
                    // (Re)publish any existing media streams.
                    for (Map.Entry<String, PublishedStream> publishedStream : publishedStreams.entrySet()) {
                        addStreamToPublishingPeerConnection(publishedStream.getValue().getMediaStream());

                        offerPublishSdp(false, republishResult -> {
                            onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection();
                        });
                    }

                    onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection();
                });
            }
        } else {
            onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection();
        }
    }

    private void setupSubscribingPeerConnection() {
        if (subscribingPeerConnection == null) {
            subscribingPeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnectionAdapter() {
                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                    super.onAddTrack(rtpReceiver, mediaStreams);

                    for (MediaStream mediaStream : mediaStreams) {
                        StreamMetadata subscribedStream = subscribedStreams.get(mediaStream.getId());

                        RTCStream stream = new RTCStream(subscribedStream.getMediaTypes(), mediaStream, null, null, subscribedStream.getAlias(), subscribedStream.getParticipantId());

                        delegate.onStreamAvailable(RTCBandwidthClient.this, stream);
                    }
                }
            });

            if (subscribingPeerConnection != null) {
                DataChannel heartbeatDataChannel = addHeartbeatDataChannel(subscribingPeerConnection);
                subscribedDataChannels.put(heartbeatDataChannel.label(), heartbeatDataChannel);
                subscribeHeartbeatDataChannel = heartbeatDataChannel;

                DataChannel diagnosticsDataChannel = addDiagnosticsDataChannel(subscribingPeerConnection);
                subscribedDataChannels.put(diagnosticsDataChannel.label(), diagnosticsDataChannel);
                subscribeDiagnosticsDataChannel = diagnosticsDataChannel;
            }
        }
    }

    private void offerPublishSdp(Boolean restartIce, OnOfferPublishSdpListener onOfferPublishSdpListener) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(false)));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(false)));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", String.valueOf(restartIce)));

        publishingPeerConnection.createOffer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);

                Map<String, StreamPublishMetadata> mediaStreams = new HashMap<>();
                for (Map.Entry<String, PublishedStream> publishedStream : publishedStreams.entrySet()) {
                    mediaStreams.put(publishedStream.getKey(), publishedStream.getValue().getMetadata());
                }

                Map<String, DataChannelPublishMetadata> dataChannels = new HashMap<>();
                for (Map.Entry<String, DataChannel> dataChannel : publishedDataChannels.entrySet()) {
                    dataChannels.put(dataChannel.getValue().label(), new DataChannelPublishMetadata(dataChannel.getValue().label(), dataChannel.getValue().id()));
                }

                PublishMetadata publishMetadata = new PublishMetadata(mediaStreams, dataChannels);

                signaling.offerSdp(sessionDescription.description, publishMetadata, new Signaling.Adapter() {
                    @Override
                    public void onOfferSdp(Signaling signaling, OfferSdpResult result) {
                        super.onOfferSdp(signaling, result);

                        publishingPeerConnection.setLocalDescription(new SdpAdapter() {
                            @Override
                            public void onSetSuccess() {
                                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getSdpAnswer());
                                publishingPeerConnection.setRemoteDescription(new SdpAdapter() {
                                    @Override
                                    public void onSetSuccess() {
                                        super.onSetSuccess();

                                        onOfferPublishSdpListener.onOfferPublishSdp(result);
                                    }
                                }, sessionDescription);
                            }
                        }, sessionDescription);
                    }
                });
            }
        }, mediaConstraints);
    }

    private void addStreamToPublishingPeerConnection(MediaStream mediaStream) {
        List<MediaStreamTrack> tracks = new ArrayList<>();
        tracks.addAll(mediaStream.audioTracks);
        tracks.addAll(mediaStream.videoTracks);

        for (MediaStreamTrack track : tracks) {
            publishingPeerConnection.addTransceiver(track, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, Collections.singletonList(mediaStream.getId())));
        }
    }

    private void cleanupPublishedStreams(Map<String, PublishedStream> publishedStreams) {
        // TODO: Fill with logic for unpublishing, published streams.
    }

    private String setSdpMediaSetup(String sdp, Boolean considerDirection, String template) {
        String mungedSdp = sdp;

        List<String> mediaMatches = new ArrayList<>();
        Matcher matcher = Pattern.compile("m=.*?(?=m=|$)", Pattern.MULTILINE).matcher(sdp);

        while (matcher.find()) {
            mediaMatches.add(matcher.group());
        }

        Collections.reverse(mediaMatches);

        for (String mediaMatch : mediaMatches) {
            if (!considerDirection || !mediaMatch.matches("a=(?:sendrecv|recvonly|sendonly|inactive)")) {
                mungedSdp = mungedSdp.replaceFirst("a=setup:(?:active)", template);
            }
        }

        return mungedSdp;
    }

    private void handleSubscribeSdpOffer(SdpOfferParams params, OnHandleSubscribeSdpOfferListener onHandleSubscribeSdpOfferListener) {
        subscribedStreams = params.getStreamMetadata();

        setupSubscribingPeerConnection();

        String mungedSdp = setSdpMediaSetup(params.getSdpOffer(), true, "a=setup:actpass");
        SessionDescription mungedSessionDescription = new SessionDescription(SessionDescription.Type.OFFER, mungedSdp);

        subscribingPeerConnection.setRemoteDescription(new SdpAdapter() {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();

                subscribingPeerConnection.createAnswer(new SdpAdapter() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);

                        String mungedSdp = setSdpMediaSetup(sessionDescription.description, false, "a=setup:passive");
                        SessionDescription mungedSessionDescription = new SessionDescription(sessionDescription.type, mungedSdp);

                        subscribingPeerConnection.setLocalDescription(new SdpAdapter() {
                            @Override
                            public void onSetSuccess() {
                                super.onSetSuccess();

                                signaling.answerSdp(mungedSessionDescription.description, new Signaling.Adapter() {
                                    @Override
                                    public void onAnswerSdp(Signaling signaling, AnswerSdpResult result) {
                                        super.onAnswerSdp(signaling, result);

                                        onHandleSubscribeSdpOfferListener.onHandleSubscribeSdpOffer();
                                    }
                                });

                            }

                            @Override
                            public void onSetFailure(String s) {
                                super.onSetFailure(s);
                            }
                        }, mungedSessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        super.onCreateFailure(s);
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onSetFailure(String s) {
                super.onSetFailure(s);
            }
        }, mungedSessionDescription);
    }

    @Override
    public void onSdpOffer(Signaling signaling, SdpOfferParams params) {
        handleSubscribeSdpOffer(params, () -> {

        });
    }
}
