package com.bandwidth.webrtc.signaling.websockets.listeners

import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider

interface OnMessageListener {
    fun onMessage(webSocketProvider: WebSocketProvider?, message: String?)
}