package com.bandwidth.webrtc.signaling.websockets

import com.bandwidth.webrtc.signaling.websockets.listeners.OnOpenListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnMessageListener
import com.bandwidth.webrtc.signaling.ConnectionException
import com.bandwidth.webrtc.signaling.websockets.listeners.OnCloseListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnErrorListener

interface WebSocketProvider {
    fun setOnOpenListener(onOpenListener: OnOpenListener)
    fun setOnCloseListener(onCloseListener: OnCloseListener)
    fun setOnMessageListener(onMessageListener: OnMessageListener)
    fun setOnErrorListener(onErrorListener: OnErrorListener)

    @Throws(ConnectionException::class)
    fun open(uri: String?)
    fun close()
    fun sendMessage(message: String?)
}