package com.bandwidth.webrtc.signaling.rpc.transit

import com.bandwidth.webrtc.types.PublishMetadata

class OfferSdpParams(private val sdpOffer: String, private val mediaMetadata: PublishMetadata)