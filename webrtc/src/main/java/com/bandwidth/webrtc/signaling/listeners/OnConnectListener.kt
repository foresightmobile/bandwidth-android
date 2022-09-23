package com.bandwidth.webrtc.signaling.listeners

import com.bandwidth.webrtc.signaling.Signaling

interface OnConnectListener {
    fun onConnect(signaling: Signaling?)
}