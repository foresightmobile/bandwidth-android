package com.bandwidth.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(s: String) {}
    override fun onSetFailure(s: String) {}
}