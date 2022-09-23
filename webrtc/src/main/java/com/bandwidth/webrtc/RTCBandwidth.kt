package com.bandwidth.webrtc

import com.bandwidth.webrtc.listeners.*
import com.bandwidth.webrtc.signaling.ConnectionException

interface RTCBandwidth {
    @Throws(ConnectionException::class)
    fun connect(deviceToken: String?, onConnectListener: OnConnectListener)

    @Throws(ConnectionException::class)
    fun connect(webSocketUrl: String?, deviceToken: String?, onConnectListener: OnConnectListener)
    fun disconnect()
    fun publish(alias: String, onPublishListener: OnPublishListener?)
    fun unpublish(streamIds: List<String>, onUnpublishListener: OnUnpublishListener)
    fun setOnStreamAvailableListener(onStreamAvailableListener: OnStreamAvailableListener?)
    fun setOnStreamUnavailableListener(onStreamUnavailableListener: OnStreamUnavailableListener?)
}