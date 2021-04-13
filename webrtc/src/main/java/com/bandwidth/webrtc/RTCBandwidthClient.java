package com.bandwidth.webrtc;

import android.content.Context;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnNegotiateSdpListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult;
import com.bandwidth.webrtc.signaling.rpc.transit.RequestToPublishResult;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;
import com.bandwidth.webrtc.signaling.websockets.NeoVisionariesWebSocket;
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RTCBandwidthClient implements RTCBandwidth, SignalingDelegate {
    private final Context appContext;

    private final RTCBandwidthDelegate delegate;
    private final Signaling signaling;

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection.RTCConfiguration configuration;

    private final Map<String, PeerConnection> localPeerConnections;
    private final Map<String, PeerConnection> remotePeerConnections;

    private OnConnectListener onConnectListener;
    private OnPublishListener onPublishListener;

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
        configuration.enableDtlsSrtp = true;

        localPeerConnections = new HashMap<>();
        remotePeerConnections = new HashMap<>();
    }

    @Override
    public void setOnConnectListener(OnConnectListener listener) {
        onConnectListener = listener;
    }

    @Override
    public void setOnPublishListener(OnPublishListener listener) {
        onPublishListener = listener;
    }

    @Override
    public void connect(URI uri) throws ConnectionException {
        signaling.setOnConnectListener(signaling -> {
            if (onConnectListener != null) {
                onConnectListener.onConnect();
            }
        });

        signaling.connect(uri);
    }

    @Override
    public void publish(Boolean audio, Boolean video, String alias) {
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), audioSource);

        VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(UUID.randomUUID().toString(), videoSource);

        PeerConnection localPeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnectionAdapter());
        if (localPeerConnection != null) {
            String streamId = UUID.randomUUID().toString();

            if (audio) {
                localPeerConnection.addTrack(audioTrack, Collections.singletonList(streamId));
            }

            if (video) {
                localPeerConnection.addTrack(videoTrack, Collections.singletonList(streamId));
            }
        }

        signaling.setMediaPreferences(new Signaling.Adapter() {
            @Override
            public void onSetMediaPreferences(Signaling signaling) {
                super.onSetMediaPreferences(signaling);

                signaling.requestToPublish(audio, video, alias, new Signaling.Adapter() {
                    @Override
                    public void onRequestToPublish(Signaling signaling, RequestToPublishResult result) {
                        super.onRequestToPublish(signaling, result);

                        localPeerConnections.put(result.getEndpointId(), localPeerConnection);

                        negotiateSdp(result.getEndpointId(), result.getDirection(), result.getMediaTypes(), localPeerConnection, () -> {
                            System.out.println("onNegotiateSdpListener...");
                            onPublishListener.onPublish(result.getMediaTypes(), audioSource, audioTrack, videoSource, videoTrack);
                        });
                    }
                });
            }
        });
    }

    private void negotiateSdp(String endpointId, String direction, List<String> mediaTypes, PeerConnection peerConnection, OnNegotiateSdpListener onNegotiateSdpListener) {
        MediaConstraints mediaConstraints = new MediaConstraints();

        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(direction.contains("recv") && mediaTypes.contains("AUDIO"))));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(direction.contains("recv") && mediaTypes.contains("VIDEO"))));

        peerConnection.createOffer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);

                signaling.offerSdp(endpointId, sessionDescription.description, new Signaling.Adapter() {
                    @Override
                    public void onOfferSdp(Signaling signaling, OfferSdpResult result) {
                        super.onOfferSdp(signaling, result);

                        peerConnection.setLocalDescription(new SdpAdapter() {
                            @Override
                            public void onSetSuccess() {
                                super.onSetSuccess();

                                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getSdpAnswer());
                                peerConnection.setRemoteDescription(new SdpAdapter() {
                                    @Override
                                    public void onSetSuccess() {
                                        onNegotiateSdpListener.onNegotiateSdp();
                                    }
                                }, sessionDescription);
                            }
                        }, sessionDescription);
                    }
                });
            }
        }, mediaConstraints);
    }

    @Override
    public void onAddIceCandidate(Signaling signaling, AddIceCandidateParams params) {
        PeerConnection remotePeerConnection = remotePeerConnections.get(params.getEndpointId());
        if (remotePeerConnection != null) {
            IceCandidate candidate = new IceCandidate(params.getCandidate().getSdpMid(), params.getCandidate().getSdpMLineIndex(), params.getCandidate().getCandidate());
            remotePeerConnection.addIceCandidate(candidate);
        }

        PeerConnection localPeerConnection = localPeerConnections.get(params.getEndpointId());
        if (localPeerConnection != null) {
            IceCandidate candidate = new IceCandidate(params.getCandidate().getSdpMid(), params.getCandidate().getSdpMLineIndex(), params.getCandidate().getCandidate());
            localPeerConnection.addIceCandidate(candidate);
        }
    }

    @Override
    public void onEndpointRemoved(Signaling signaling, EndpointRemovedParams params) {
        delegate.onStreamUnavailable(RTCBandwidthClient.this, params.getEndpointId());
    }

    @Override
    public void onSdpNeeded(Signaling signaling, SdpNeededParams params) {
        PeerConnection remotePeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnectionAdapter() {
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                signaling.sendIceCandidate(params.getEndpointId(), iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid);
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                delegate.onStreamAvailable(RTCBandwidthClient.this, params.getEndpointId(), params.getParticipantId(), params.getAlias(), params.getMediaTypes(), rtpReceiver);
            }
        });

        if (remotePeerConnection != null) {
            remotePeerConnections.put(params.getEndpointId(), remotePeerConnection);

            negotiateSdp(params.getEndpointId(), params.getDirection(), params.getMediaTypes(), remotePeerConnection, () -> {

            });
        }
    }
}
