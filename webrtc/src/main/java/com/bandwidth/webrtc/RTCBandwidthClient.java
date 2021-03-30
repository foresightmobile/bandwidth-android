package com.bandwidth.webrtc;

import android.content.Context;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnNegotiateSdpListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.NullSessionException;
import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;
import com.bandwidth.webrtc.signaling.websockets.NeoVisionariesWebSocket;
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider;

import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RTCBandwidthClient implements RTCBandwidth, SignalingDelegate {
    private final RTCBandwidthDelegate delegate;
    private final Signaling signaling;

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection.RTCConfiguration configuration;

    private final Map<String, PeerConnection> localPeerConnections;
    private final Map<String, PeerConnection> remotePeerConnections;

    private OnConnectListener onConnectListener;
    private OnPublishListener onPublishListener;
    private OnNegotiateSdpListener onNegotiateSdpListener;

    public RTCBandwidthClient(Context context, EglBase.Context eglContext, RTCBandwidthDelegate delegate) {
            this(context, eglContext, delegate, new NeoVisionariesWebSocket());
    }

    public RTCBandwidthClient(Context context, EglBase.Context eglContext, RTCBandwidthDelegate delegate, WebSocketProvider webSocketProvider) {
        this.delegate = delegate;

        signaling = new SignalingClient(webSocketProvider, this);

//        EglBase eglBase = EglBase.create();

        VideoEncoderFactory videoEncoderFactory = new DefaultVideoEncoderFactory(eglContext, true, true);
        VideoDecoderFactory videoDecoderFactory = new DefaultVideoDecoderFactory(eglContext);

        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory();

        configuration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

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

    private void setOnNegotiateSdpListener(OnNegotiateSdpListener listener) {
        onNegotiateSdpListener = listener;
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
    public void publish(Boolean audio, Boolean video, String alias) throws NullSessionException {
        signaling.setOnRequestToPublishListener((signaling, result) -> {
            PeerConnection localPeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnection.Observer() {
                @Override
                public void onSignalingChange(PeerConnection.SignalingState signalingState) {

                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

                }

                @Override
                public void onIceConnectionReceivingChange(boolean b) {

                }

                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

                }

                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {

                }

                @Override
                public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

                }

                @Override
                public void onAddStream(MediaStream mediaStream) {

                }

                @Override
                public void onRemoveStream(MediaStream mediaStream) {

                }

                @Override
                public void onDataChannel(DataChannel dataChannel) {

                }

                @Override
                public void onRenegotiationNeeded() {

                }

                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                    delegate.onStreamAvailable(RTCBandwidthClient.this, result.getEndpointId(), result.getParticipantId(), alias, result.getMediaTypes(), rtpReceiver);
                }
            });

            String streamId = UUID.randomUUID().toString();

            AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
            VideoSource videoSource = peerConnectionFactory.createVideoSource(false);

            RtpSender audioSender = audio ? localPeerConnection.addTrack(peerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), audioSource), Arrays.asList(streamId)) : null;
            RtpSender videoSender = video ? localPeerConnection.addTrack(peerConnectionFactory.createVideoTrack(UUID.randomUUID().toString(), videoSource), Arrays.asList(streamId)) : null;

            localPeerConnections.put(result.getEndpointId(), localPeerConnection);

            setOnNegotiateSdpListener(() -> {
                onPublishListener.onPublish(result.getMediaTypes(), audioSender, audioSource, videoSender, videoSource);
            });

            negotiateSdp(result.getEndpointId(), result.getDirection(), result.getMediaTypes(), localPeerConnection);
        });

        signaling.setOnSetMediaPreferencesListener(signaling -> {
            signaling.requestToPublish(audio, video, alias);
        });

        signaling.setMediaPreferences();
    }

    private void negotiateSdp(String endpointId, String direction, List<String> mediaTypes, PeerConnection peerConnection) {
        MediaConstraints mediaConstraints = new MediaConstraints();

        if (direction.contains("recv")) {
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(mediaTypes.contains("AUDIO"))));
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(mediaTypes.contains("VIDEO"))));
        }

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                signaling.setOnOfferSdpListener((signaling, result) -> {
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {

                        }

                        @Override
                        public void onSetSuccess() {
                            SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER, result.getSdpAnswer());
                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {

                                }

                                @Override
                                public void onSetSuccess() {
                                    onNegotiateSdpListener.onNegotiateSdp();
                                }

                                @Override
                                public void onCreateFailure(String s) {

                                }

                                @Override
                                public void onSetFailure(String s) {

                                }
                            }, sdp);
                        }

                        @Override
                        public void onCreateFailure(String s) {

                        }

                        @Override
                        public void onSetFailure(String s) {

                        }
                    }, sessionDescription);
                });

                signaling.offerSdp(endpointId, sessionDescription.description);
            }

            @Override
            public void onSetSuccess() {

            }

            @Override
            public void onCreateFailure(String s) {

            }

            @Override
            public void onSetFailure(String s) {

            }
        }, mediaConstraints);
    }

    @Override
    public void onAddIceCandidate(Signaling signaling, AddIceCandidateParams params) {
        PeerConnection remotePeerConnection = remotePeerConnections.get(params.getEndpointId());
        if (remotePeerConnection != null) {
            IceCandidate candidate = new IceCandidate(params.getCandidate().getCandidate(), params.getCandidate().getSdpMLineIndex(), params.getCandidate().getSdpMid());
            remotePeerConnection.addIceCandidate(candidate);
        }

        PeerConnection localPeerConnection = localPeerConnections.get(params.getEndpointId());
        if (localPeerConnection != null) {
            IceCandidate candidate = new IceCandidate(params.getCandidate().getCandidate(), params.getCandidate().getSdpMLineIndex(), params.getCandidate().getSdpMid());
            localPeerConnection.addIceCandidate(candidate);
        }
    }

    @Override
    public void onEndpointRemoved(Signaling signaling, EndpointRemovedParams params) {
        delegate.onStreamUnavailable(RTCBandwidthClient.this, params.getEndpointId());
    }

    @Override
    public void onSdpNeeded(Signaling signaling, SdpNeededParams params) {
        PeerConnection remotePeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                signaling.sendIceCandidate(params.getEndpointId(), iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onRenegotiationNeeded() {

            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                delegate.onStreamAvailable(RTCBandwidthClient.this, params.getEndpointId(), params.getParticipantId(), params.getAlias(), params.getMediaTypes(), rtpReceiver);
            }
        });

        remotePeerConnections.put(params.getEndpointId(), remotePeerConnection);

        negotiateSdp(params.getEndpointId(), params.getDirection(), params.getMediaTypes(), remotePeerConnection);
    }
}
