package com.bandwidth.webrtc.signaling.websockets.listeners

import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider

interface OnOpenListener {
    fun onOpen(webSocketProvider: WebSocketProvider?)
}