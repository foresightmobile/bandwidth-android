package com.bandwidth.webrtc.signaling

import com.bandwidth.webrtc.signaling.listeners.OnConnectListener
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener
import com.bandwidth.webrtc.signaling.rpc.QueueRequest
import com.bandwidth.webrtc.signaling.rpc.transit.*
import com.bandwidth.webrtc.signaling.rpc.transit.base.Notification
import com.bandwidth.webrtc.signaling.rpc.transit.base.Request
import com.bandwidth.webrtc.signaling.rpc.transit.base.Response
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider
import com.bandwidth.webrtc.signaling.websockets.listeners.OnCloseListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnErrorListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnMessageListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnOpenListener
import com.bandwidth.webrtc.types.PublishMetadata
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class SignalingClient(
    private val webSocketProvider: WebSocketProvider,
    delegate: SignalingDelegate
) : Signaling {
    private val delegate: SignalingDelegate
    private val deviceUniqueId = UUID.randomUUID().toString()
    private val pendingQueueRequests: MutableMap<String?, QueueRequest> = HashMap()
    private var hasSetMediaPreferences = false
    private var onConnectListener: OnConnectListener? = null
    private var onDisconnectListener: OnDisconnectListener? = null
    fun setOnConnectListener(onConnectListener: OnConnectListener?) {
        this.onConnectListener = onConnectListener
    }

    override fun setOnConnectListener(listener: (Signaling?) -> Unit) {}

    override fun setOnDisconnectListener(onDisconnectListener: OnDisconnectListener?) {
        this.onDisconnectListener = onDisconnectListener
    }

    @Throws(ConnectionException::class)
    override fun connect(deviceToken: String?) {
        connect("wss://device.webrtc.bandwidth.com", deviceToken)
    }

    @Throws(ConnectionException::class)
    override fun connect(webSocketUrl: String?, deviceToken: String?) {
        val url = String.format(
            "%s/v3/?token=%s&client=android&sdkVersion=0.1.0-alpha.2&uniqueId=%s",
            webSocketUrl,
            deviceToken,
            deviceUniqueId
        )
        webSocketProvider.open(url)
    }

    override fun disconnect() {
        val params = LeaveParams()
        val notification = Notification("2.0", "leave", params)
        sendNotification(notification)
        hasSetMediaPreferences = false
        webSocketProvider.close()
    }

    override fun offerSdp(
        sdp: String,
        publishMetadata: PublishMetadata,
        observer: Signaling.Observer
    ) {
        val params = OfferSdpParams(sdp, publishMetadata)
        val request = Request(UUID.randomUUID().toString(), "2.0", "offerSdp", params)
        sendRequest(request, observer)
    }

    override fun answerSdp(sdp: String, observer: Signaling.Observer) {
        val params = AnswerSdpParams(sdp)
        val request = Request(UUID.randomUUID().toString(), "2.0", "answerSdp", params)
        sendRequest(request, observer)
    }

    private fun setMediaPreferences(protocol: String, observer: Signaling.Observer) {
        val params = SetMediaPreferencesParams(protocol)
        val request = Request(UUID.randomUUID().toString(), "2.0", "setMediaPreferences", params)
        sendRequest(request, observer)
    }

    private fun sendRequest(
        request: Request<*>,
        observer: Signaling.Observer,
        timeout: Long = 5000L
    ) {
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                pendingQueueRequests.remove(request.id)
                cancel()
            }
        }, timeout)

        // Keep a reference to our request as we wait for a response.
        pendingQueueRequests[request.id] =
            QueueRequest(request.method, observer, timer)
        val json = Gson().toJson(request)
        println("↑ $json")

        // Send our request to the moon (or signaling server).
        webSocketProvider.sendMessage(json)
    }

    private fun sendNotification(notification: Notification<*>) {
        val json = Gson().toJson(notification)
        println("↑ $json")

        // Send our notification to the moon (or signaling server).
        webSocketProvider.sendMessage(json)
    }

    private fun handleResponse(message: String, id: String?) {
        val pendingQueueRequest = pendingQueueRequests[id]
        pendingQueueRequest!!.timer.cancel()
        pendingQueueRequests.remove(id)
        when (pendingQueueRequest.method) {
            "setMediaPreferences" -> {
                val setMediaPreferencesResultType =
                    object : TypeToken<Response<SetMediaPreferencesResult?>?>() {}.type
                val setMediaPreferencesResponse =
                    Gson().fromJson<Response<SetMediaPreferencesResult>>(
                        message,
                        setMediaPreferencesResultType
                    )
                pendingQueueRequest.observer.onSetMediaPreferences(
                    this,
                    setMediaPreferencesResponse.result
                )
            }
            "offerSdp" -> {
                val offerSdpResultType = object : TypeToken<Response<OfferSdpResult?>?>() {}.type
                val offerSdpResponse =
                    Gson().fromJson<Response<OfferSdpResult>>(message, offerSdpResultType)
                pendingQueueRequest.observer.onOfferSdp(this, offerSdpResponse.result)
            }
            "answerSdp" -> {
                val answerSdpResultType = object : TypeToken<Response<AnswerSdpResult?>?>() {}.type
                val answerSdpResponse =
                    Gson().fromJson<Response<AnswerSdpResult>>(message, answerSdpResultType)
                pendingQueueRequest.observer.onAnswerSdp(this, answerSdpResponse.result)
            }
        }
    }

    private fun handleNotification(message: String, notification: Notification<*>) {
        if (notification.method == "sdpOffer") {
            val sdpOfferNotificationType =
                object : TypeToken<Notification<SdpOfferParams?>?>() {}.type
            val sdpOfferNotification =
                Gson().fromJson<Notification<SdpOfferParams>>(message, sdpOfferNotificationType)
            delegate.onSdpOffer(this, sdpOfferNotification.params)
        }
    }

    init {
        webSocketProvider.setOnOpenListener(object : OnOpenListener {
            override fun onOpen(webSocketProvider: WebSocketProvider?) {
                if (!hasSetMediaPreferences) {
                    // Set media preferences once the WebSocket connection has been opened.
                    setMediaPreferences("WEBRTC", object : Signaling.Adapter() {
                        override fun onSetMediaPreferences(
                            signaling: Signaling?,
                            result: SetMediaPreferencesResult?
                        ) {
                            super.onSetMediaPreferences(signaling, result)
                            hasSetMediaPreferences = true
                            if (onConnectListener != null) {
                                onConnectListener!!.onConnect(this@SignalingClient)
                            }
                        }
                    })
                } else {
                    if (onConnectListener != null) {
                        onConnectListener!!.onConnect(this@SignalingClient)
                    }
                }
            }
        })
        webSocketProvider.setOnCloseListener(object : OnCloseListener {
            override fun onClose(webSocketProvider: WebSocketProvider?) {
                if (onDisconnectListener != null) {
                    onDisconnectListener!!.onDisconnect(this@SignalingClient)
                }
            }
        })
        webSocketProvider.setOnMessageListener(object : OnMessageListener {
            override fun onMessage(webSocketProvider: WebSocketProvider?, message: String?) {
                // Determine if we're receiving a response or notification.
                val response = Gson().fromJson(message, Response::class.java)
                val notification = Gson().fromJson(message, Notification::class.java)
                if (response != null && response.id != null) {
                    if (message != null) {
                        handleResponse(message, response.id)
                    }
                } else notification?.let {
                    if (message != null) {
                        handleNotification(message, it)
                    }
                }
                println("↓ $message")
            }
        })
        webSocketProvider.setOnErrorListener(object : OnErrorListener {
            override fun onError(webSocketProvider: WebSocketProvider?, throwable: Throwable?) {

            }
        })
        this.delegate = delegate
    }
}