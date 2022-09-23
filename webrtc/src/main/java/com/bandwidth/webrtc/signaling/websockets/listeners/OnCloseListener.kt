package com.bandwidth.webrtc.signaling.websockets.listeners

import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider

interface OnCloseListener {
    fun onClose(webSocketProvider: WebSocketProvider?)
}