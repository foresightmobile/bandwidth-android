package com.bandwidth.webrtc;

import android.content.Context;
import android.util.Log;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnNegotiateSdpListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;
import com.bandwidth.webrtc.signaling.websockets.NeoVisionariesWebSocket;
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
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
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RTCBandwidthClient implements RTCBandwidth, SignalingDelegate {
    private static final String TAG = "PCRTCClient";

    private final Context appContext;

    private final RTCBandwidthDelegate delegate;
    private final Signaling signaling;

    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection.RTCConfiguration configuration;

    private final Map<String, PeerConnection> localPeerConnections;
    private final Map<String, PeerConnection> remotePeerConnections;

    private OnConnectListener onConnectListener;
    private OnPublishListener onPublishListener;
    private OnNegotiateSdpListener onNegotiateSdpListener;

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

        final AudioDeviceModule adm = createJavaAudioDevice();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory();

        configuration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        configuration.enableDtlsSrtp = true;

        localPeerConnections = new HashMap<>();
        remotePeerConnections = new HashMap<>();

        adm.release();
    }


    AudioDeviceModule createJavaAudioDevice() {
        // Enable/disable OpenSL ES playback.
//        if (!peerConnectionParameters.useOpenSLES) {
//            Log.w(TAG, "External OpenSLES ADM not implemented yet.");
//             TODO(magjed): Add support for external OpenSLES ADM.
//        }
        // Set audio record error callbacks.
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
//                reportError(errorMessage);
            }
            @Override
            public void onWebRtcAudioRecordStartError(
                    JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
//                reportError(errorMessage);
            }
            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
//                reportError(errorMessage);
            }
        };
        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
//                reportError(errorMessage);
            }
            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
//                reportError(errorMessage);
            }
            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
//                reportError(errorMessage);
            }
        };
        // Set audio record state callbacks.
        JavaAudioDeviceModule.AudioRecordStateCallback audioRecordStateCallback = new JavaAudioDeviceModule.AudioRecordStateCallback() {
            @Override
            public void onWebRtcAudioRecordStart() {
                Log.i(TAG, "Audio recording starts");
            }
            @Override
            public void onWebRtcAudioRecordStop() {
                Log.i(TAG, "Audio recording stops");
            }
        };
        // Set audio track state callbacks.
        JavaAudioDeviceModule.AudioTrackStateCallback audioTrackStateCallback = new JavaAudioDeviceModule.AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                Log.i(TAG, "Audio playout starts");
            }
            @Override
            public void onWebRtcAudioTrackStop() {
                Log.i(TAG, "Audio playout stops");
            }
        };
        return JavaAudioDeviceModule.builder(appContext)
//                .setSamplesReadyCallback(saveRecordedAudioToFile)
//                .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
//                .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .setAudioRecordStateCallback(audioRecordStateCallback)
                .setAudioTrackStateCallback(audioTrackStateCallback)
                .createAudioDeviceModule();
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
    public void publish(Boolean audio, Boolean video, String alias) {
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), audioSource);

        VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(UUID.randomUUID().toString(), videoSource);

        PeerConnection localPeerConnection = peerConnectionFactory.createPeerConnection(configuration, new PeerConnectionAdapter());

        signaling.setOnRequestToPublishListener((signaling, result) -> {
            String streamId = UUID.randomUUID().toString();

            RtpSender audioSender = audio ? localPeerConnection.addTrack(audioTrack, Arrays.asList(streamId)) : null;
            RtpSender videoSender = video ? localPeerConnection.addTrack(videoTrack, Arrays.asList(streamId)) : null;

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

        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(direction.contains("recv") && mediaTypes.contains("AUDIO"))));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(direction.contains("recv") && mediaTypes.contains("VIDEO"))));

        peerConnection.createOffer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);

                signaling.setOnOfferSdpListener((signaling, result) -> {
                    peerConnection.setLocalDescription(new SdpAdapter() {
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();

                            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getSdpAnswer());
                            peerConnection.setRemoteDescription(new SdpAdapter() {
                                @Override
                                public void onSetSuccess() {
                                    super.onSetSuccess();

                                    onNegotiateSdpListener.onNegotiateSdp();
                                }
                            }, sessionDescription);
                        }
                    }, sessionDescription);
                });

                signaling.offerSdp(endpointId, sessionDescription.description);
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
