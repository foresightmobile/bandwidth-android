package com.bandwidth.webrtc.signaling.listeners

import com.bandwidth.webrtc.signaling.Signaling

interface OnDisconnectListener {
    fun onDisconnect(signaling: Signaling?)
}