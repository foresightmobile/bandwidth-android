package com.bandwidth.webrtc.signaling.websockets.listeners

import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider

interface OnErrorListener {
    fun onError(webSocketProvider: WebSocketProvider?, throwable: Throwable?)
}