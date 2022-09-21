package com.bandwidth.webrtc

import android.content.Context
import com.bandwidth.webrtc.listeners.*
import com.bandwidth.webrtc.signaling.ConnectionException
import com.bandwidth.webrtc.signaling.Signaling
import com.bandwidth.webrtc.signaling.SignalingClient
import com.bandwidth.webrtc.signaling.SignalingDelegate
import com.bandwidth.webrtc.signaling.rpc.transit.AnswerSdpResult
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult
import com.bandwidth.webrtc.signaling.rpc.transit.SdpOfferParams
import com.bandwidth.webrtc.signaling.websockets.NeoVisionariesWebSocket
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider
import com.bandwidth.webrtc.types.*
import org.webrtc.*
import org.webrtc.PeerConnection.PeerConnectionState
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory.InitializationOptions
import org.webrtc.RtpTransceiver.RtpTransceiverInit
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

class RTCBandwidthClient @JvmOverloads constructor(
    appContext: Context?,
    eglContext: EglBase.Context?,
    webSocketProvider: WebSocketProvider? = NeoVisionariesWebSocket()
) : RTCBandwidth, SignalingDelegate {
    private val signaling: Signaling
    private val peerConnectionFactory: PeerConnectionFactory
    private val configuration: RTCConfiguration

    // One peer for all published (outgoing) streams, one for all subscribed (incoming) streams;
    private var publishingPeerConnection: PeerConnection? = null
    private var subscribingPeerConnection: PeerConnection? = null

    // Standard data channels used for platform diagnostics and health checks.
    private var publishHeartbeatDataChannel: DataChannel? = null
    private var publishDiagnosticsDataChannel: DataChannel? = null
    private val publishedDataChannels: MutableMap<String, DataChannel> = HashMap()
    private var subscribeHeartbeatDataChannel: DataChannel? = null
    private var subscribeDiagnosticsDataChannel: DataChannel? = null
    private val subscribedDataChannels: MutableMap<String, DataChannel> = HashMap()

    // Published (outgoing) streams keyed by media stream id (msid).
    private val publishedStreams: MutableMap<String, PublishedStream?> = HashMap()

    // Subscribed (incoming) streams keyed by media stream id (msid).
    private var subscribedStreams: Map<String?, StreamMetadata>? = HashMap()

    // Keep track of our available streams. Prevents duplicate stream available / unavailable events.
    private val availableMediaStreams: MutableMap<String, MediaStream> = HashMap()
    private var onStreamAvailableListener: OnStreamAvailableListener? = null
    private var onStreamUnavailableListener: OnStreamUnavailableListener? = null
    @Throws(ConnectionException::class)
    override fun connect(deviceToken: String?, onConnectListener: OnConnectListener) {
        signaling.setOnConnectListener { signaling: Signaling? ->
            onConnectListener.onConnect()
            null
        }
        signaling.connect(deviceToken)
    }

    @Throws(ConnectionException::class)
    override fun connect(
        webSocketUrl: String?,
        deviceToken: String?,
        onConnectListener: OnConnectListener
    ) {
        signaling.setOnConnectListener { signaling: Signaling? ->
            onConnectListener.onConnect()
            null
        }
        signaling.connect(webSocketUrl, deviceToken)
    }

    override fun disconnect() {
        signaling.disconnect()
        cleanupPublishedStreams(publishedStreams)
        if (publishingPeerConnection != null) {
            publishingPeerConnection!!.close()
            publishingPeerConnection = null
        }
        if (subscribingPeerConnection != null) {
            subscribingPeerConnection!!.close()
            subscribingPeerConnection = null
        }
        availableMediaStreams.clear()
    }

    override fun publish(alias: String, onPublishListener: OnPublishListener?) {
        setupPublishingPeerConnection(object : OnSetupPublishingPeerConnectionListener {
            override fun setupPublishingPeerConnection() {
                val mediaStream =
                    peerConnectionFactory.createLocalMediaStream(UUID.randomUUID().toString())
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                val audioTrack =
                    peerConnectionFactory.createAudioTrack(
                        UUID.randomUUID().toString(),
                        audioSource
                    )
                mediaStream.addTrack(audioTrack)
                val videoSource = peerConnectionFactory.createVideoSource(false)
                val videoTrack =
                    peerConnectionFactory.createVideoTrack(
                        UUID.randomUUID().toString(),
                        videoSource
                    )
                mediaStream.addTrack(videoTrack)
                addStreamToPublishingPeerConnection(mediaStream)
                val publishMetadata = StreamPublishMetadata(alias)
                publishedStreams[mediaStream.id] = PublishedStream(mediaStream, publishMetadata)
                offerPublishSdp(false, object : OnOfferPublishSdpListener {
                    override fun onOfferPublishSdp(result: OfferSdpResult?) {
                        val metadata = result?.streamMetadata!![mediaStream.id]
                        val mediaTypes: List<String?>? =
                            if (metadata != null) metadata.mediaTypes else listOf("APPLICATION")
                        onPublishListener?.onPublish(
                            mediaStream.id,
                            mediaTypes,
                            audioSource,
                            audioTrack,
                            videoSource,
                            videoTrack
                        )
                    }
                })
            }
        })
    }

    override fun unpublish(streamIds: List<String>, onUnpublishListener: OnUnpublishListener) {
        val filteredPublishedStreams: MutableMap<String, PublishedStream?> = HashMap()
        for (streamId in streamIds) {
            val publishedStream = publishedStreams[streamId]
            filteredPublishedStreams[streamId] = publishedStream
        }
        cleanupPublishedStreams(filteredPublishedStreams)
        offerPublishSdp(
            false,
            object : OnOfferPublishSdpListener {
                override fun onOfferPublishSdp(result: OfferSdpResult?) {
                    onUnpublishListener.onUnpublish()
                }
            })
    }

    override fun setOnStreamAvailableListener(onStreamAvailableListener: OnStreamAvailableListener?) {
        this.onStreamAvailableListener = onStreamAvailableListener
    }

    override fun setOnStreamUnavailableListener(onStreamUnavailableListener: OnStreamUnavailableListener?) {
        this.onStreamUnavailableListener = onStreamUnavailableListener
    }

    private fun addHeartbeatDataChannel(peerConnection: PeerConnection): DataChannel {
        val init = DataChannel.Init()
        init.id = 0
        init.negotiated = true
        init.protocol = "udp"
        return peerConnection.createDataChannel("__heartbeat__", init)
    }

    private fun addDiagnosticsDataChannel(peerConnection: PeerConnection): DataChannel {
        val init = DataChannel.Init()
        init.id = 1
        init.negotiated = true
        init.protocol = "udp"
        val dataChannel = peerConnection.createDataChannel("__diagnostics__", init)
        dataChannel.registerObserver(object : DataChannelAdapter() {
            override fun onMessage(buffer: DataChannel.Buffer) {
                super.onMessage(buffer)
                System.out.printf(
                    "Diagnostics Received: %s%n",
                    StandardCharsets.UTF_8.decode(buffer.data).toString()
                )
            }
        })
        return dataChannel
    }

    private fun setupPublishingPeerConnection(onSetupPublishingPeerConnectionListener: OnSetupPublishingPeerConnectionListener) {
        if (publishingPeerConnection == null) {
            publishingPeerConnection = peerConnectionFactory.createPeerConnection(
                configuration,
                object : PeerConnectionAdapter() {
                    override fun onConnectionChange(newState: PeerConnectionState) {
                        super.onConnectionChange(newState)
                        if (newState == PeerConnectionState.FAILED) {
                            offerPublishSdp(
                                true,
                                object : OnOfferPublishSdpListener {
                                    override fun onOfferPublishSdp(result: OfferSdpResult?) {

                                    }
                                })
                        }
                    }
                })
            if (publishingPeerConnection != null) {
                val heartbeatDataChannel = addHeartbeatDataChannel(
                    publishingPeerConnection!!
                )
                publishedDataChannels[heartbeatDataChannel.label()] = heartbeatDataChannel
                publishHeartbeatDataChannel = heartbeatDataChannel
                val diagnosticsDataChannel = addDiagnosticsDataChannel(
                    publishingPeerConnection!!
                )
                publishedDataChannels[diagnosticsDataChannel.label()] = diagnosticsDataChannel
                publishDiagnosticsDataChannel = diagnosticsDataChannel
                offerPublishSdp(false, object : OnOfferPublishSdpListener {
                    override fun onOfferPublishSdp(result: OfferSdpResult?) {
                        // (Re)publish any existing media streams.
                        for ((_, value) in publishedStreams) {
                            addStreamToPublishingPeerConnection(value!!.mediaStream)
                            offerPublishSdp(
                                false,
                                object : OnOfferPublishSdpListener {
                                    override fun onOfferPublishSdp(result: OfferSdpResult?) {
                                        onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection()
                                    }
                                })
                        }
                        onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection()
                    }
                })
            }
        } else {
            onSetupPublishingPeerConnectionListener.setupPublishingPeerConnection()
        }
    }

    private fun setupSubscribingPeerConnection() {
        if (subscribingPeerConnection == null) {
            subscribingPeerConnection = peerConnectionFactory.createPeerConnection(
                configuration,
                object : PeerConnectionAdapter() {
                    override fun onAddTrack(
                        rtpReceiver: RtpReceiver,
                        mediaStreams: Array<MediaStream>
                    ) {
                        super.onAddTrack(rtpReceiver, mediaStreams)
                        for (mediaStream in mediaStreams) {
                            if (!availableMediaStreams.containsKey(mediaStream.id)) {
                                availableMediaStreams[mediaStream.id] = mediaStream
                                val subscribedStream = subscribedStreams!![mediaStream.id]
                                if (subscribedStream != null) {
                                    if (onStreamAvailableListener != null) {
                                        onStreamAvailableListener!!.onStreamAvailable(
                                            mediaStream.id,
                                            subscribedStream.mediaTypes,
                                            mediaStream.audioTracks,
                                            mediaStream.videoTracks,
                                            subscribedStream.alias
                                        )
                                    }
                                }
                            }
                        }
                    }

                    override fun onRemoveStream(mediaStream: MediaStream) {
                        super.onRemoveStream(mediaStream)

                        // TODO: 6/10/2021 - https://chromium.googlesource.com/external/webrtc/+/ffbfba979f9d48176c7ed5dcc60b6a8076303b71
                        // Swap onRemoveStream for onRemoveTrack once the above change becomes available in a release.
                        availableMediaStreams.remove(mediaStream.id)
                        if (onStreamUnavailableListener != null) {
                            onStreamUnavailableListener!!.onStreamUnavailable(mediaStream.id)
                        }
                    }
                })
            if (subscribingPeerConnection != null) {
                val heartbeatDataChannel = addHeartbeatDataChannel(
                    subscribingPeerConnection!!
                )
                subscribedDataChannels[heartbeatDataChannel.label()] = heartbeatDataChannel
                subscribeHeartbeatDataChannel = heartbeatDataChannel
                val diagnosticsDataChannel = addDiagnosticsDataChannel(
                    subscribingPeerConnection!!
                )
                subscribedDataChannels[diagnosticsDataChannel.label()] = diagnosticsDataChannel
                subscribeDiagnosticsDataChannel = diagnosticsDataChannel
            }
        }
    }

    private fun offerPublishSdp(
        restartIce: Boolean,
        onOfferPublishSdpListener: OnOfferPublishSdpListener
    ) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio",
                false.toString()
            )
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                false.toString()
            )
        )
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "IceRestart",
                restartIce.toString()
            )
        )
        publishingPeerConnection!!.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                val mediaStreams: MutableMap<String, StreamPublishMetadata?> = HashMap()
                for ((key, value) in publishedStreams) {
                    mediaStreams[key] = value!!.metadata
                }
                val dataChannels: MutableMap<String, DataChannelPublishMetadata> = HashMap()
                for ((_, value) in publishedDataChannels) {
                    dataChannels[value.label()] =
                        DataChannelPublishMetadata(value.label(), value.id())
                }
                val publishMetadata = PublishMetadata(mediaStreams, dataChannels)
                signaling.offerSdp(
                    sessionDescription.description,
                    publishMetadata,
                    object : Signaling.Adapter() {
                        override fun onOfferSdp(signaling: Signaling?, result: OfferSdpResult?) {
                            super.onOfferSdp(signaling, result)
                            publishingPeerConnection!!.setLocalDescription(object : SdpAdapter() {
                                override fun onSetSuccess() {
                                    val sessionDescription = SessionDescription(
                                        SessionDescription.Type.ANSWER,
                                        result!!.sdpAnswer
                                    )
                                    publishingPeerConnection!!.setRemoteDescription(object :
                                        SdpAdapter() {
                                        override fun onSetSuccess() {
                                            super.onSetSuccess()
                                            onOfferPublishSdpListener.onOfferPublishSdp(result)
                                        }
                                    }, sessionDescription)
                                }
                            }, sessionDescription)
                        }
                    })
            }
        }, mediaConstraints)
    }

    private fun addStreamToPublishingPeerConnection(mediaStream: MediaStream) {
        val tracks: MutableList<MediaStreamTrack> = ArrayList()
        tracks.addAll(mediaStream.audioTracks)
        tracks.addAll(mediaStream.videoTracks)
        for (track in tracks) {
            publishingPeerConnection!!.addTransceiver(
                track,
                RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_ONLY,
                    listOf(mediaStream.id)
                )
            )
        }
    }

    private fun cleanupPublishedStreams(publishedStreams: MutableMap<String, PublishedStream?>) {
        val iterator: MutableIterator<Map.Entry<String, PublishedStream?>> =
            publishedStreams.entries.iterator()
        while (iterator.hasNext()) {
            val (_, value) = iterator.next()
            val publishedStreamTracks: MutableList<MediaStreamTrack> = ArrayList()
            publishedStreamTracks.addAll(value!!.mediaStream.audioTracks)
            publishedStreamTracks.addAll(value.mediaStream.videoTracks)
            for (publishedStreamTrack in publishedStreamTracks) {
                for (transceiver in publishingPeerConnection!!.transceivers) {
                    val track = transceiver.sender.track()
                    if (track != null) {
                        if (publishedStreamTrack.id() == track.id()) {
                            publishingPeerConnection!!.removeTrack(transceiver.sender)
                            transceiver.stop()
                        }
                    }
                }
                publishedStreamTrack.setEnabled(false)
            }
            iterator.remove()
        }
    }

    private fun setSdpMediaSetup(
        sdp: String?,
        considerDirection: Boolean,
        template: String
    ): String? {
        var mungedSdp = sdp
        val mediaMatches: MutableList<String?> = ArrayList()
        val matcher = sdp?.let { Pattern.compile("m=.*?(?=m=|$)", Pattern.MULTILINE).matcher(it) }
        if (matcher != null) {
            while (matcher.find()) {
                mediaMatches.add(matcher.group())
            }
        }
        mediaMatches.reverse()
        for (mediaMatch in mediaMatches) {
            if (!considerDirection || !mediaMatch!!.matches("""(sendrecv|recvonly|sendonly|inactive)""".toRegex())) {
                mungedSdp = mungedSdp!!.replaceFirst("a=setup:(?:active)".toRegex(), template)
            }
        }
        return mungedSdp
    }

    private fun handleSubscribeSdpOffer(
        params: SdpOfferParams?,
        onHandleSubscribeSdpOfferListener: OnHandleSubscribeSdpOfferListener
    ) {
        subscribedStreams = params!!.streamMetadata
        setupSubscribingPeerConnection()
        val mungedSdp = setSdpMediaSetup(params.sdpOffer, true, "a=setup:actpass")
        val mungedSessionDescription = SessionDescription(SessionDescription.Type.OFFER, mungedSdp)
        subscribingPeerConnection!!.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                subscribingPeerConnection!!.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        super.onCreateSuccess(sessionDescription)
                        val mungedSdp = setSdpMediaSetup(
                            sessionDescription.description,
                            false,
                            "a=setup:passive"
                        )
                        val mungedSessionDescription =
                            SessionDescription(sessionDescription.type, mungedSdp)
                        subscribingPeerConnection!!.setLocalDescription(object : SdpAdapter() {
                            override fun onSetSuccess() {
                                super.onSetSuccess()
                                signaling.answerSdp(
                                    mungedSessionDescription.description,
                                    object : Signaling.Adapter() {
                                        override fun onAnswerSdp(
                                            signaling: Signaling?,
                                            result: AnswerSdpResult?
                                        ) {
                                            super.onAnswerSdp(signaling, result)
                                            onHandleSubscribeSdpOfferListener.onHandleSubscribeSdpOffer()
                                        }
                                    })
                            }
                        }, mungedSessionDescription)
                    }
                }, MediaConstraints())
            }
        }, mungedSessionDescription)
    }

    override fun onSdpOffer(signaling: Signaling?, params: SdpOfferParams?) {
        handleSubscribeSdpOffer(params, object : OnHandleSubscribeSdpOfferListener {
            override fun onHandleSubscribeSdpOffer() {

            }
        })
    }

    init {
        signaling = SignalingClient(webSocketProvider!!, this)
        val videoEncoderFactory: VideoEncoderFactory =
            DefaultVideoEncoderFactory(eglContext, true, true)
        val videoDecoderFactory: VideoDecoderFactory = DefaultVideoDecoderFactory(eglContext)
        val initializationOptions = InitializationOptions.builder(appContext)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
        configuration = RTCConfiguration(emptyList())
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        configuration.iceServers = ArrayList()
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL
        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        configuration.enableDtlsSrtp = true
    }
}