package com.bandwidth.webrtc.listeners

import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult

interface OnOfferPublishSdpListener {
    fun onOfferPublishSdp(result: OfferSdpResult?)
}