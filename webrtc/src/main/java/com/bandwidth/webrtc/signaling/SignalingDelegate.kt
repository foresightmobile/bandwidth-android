package com.bandwidth.webrtc.signaling

import com.bandwidth.webrtc.signaling.rpc.transit.SdpOfferParams

interface SignalingDelegate {
    fun onSdpOffer(signaling: Signaling?, params: SdpOfferParams?)
}