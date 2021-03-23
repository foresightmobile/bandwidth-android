package com.bandwidth.webrtc.signaling.listeners;

import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult;

public interface OnOfferSdpListener {
    void onOfferSdp(Signaling signaling, OfferSdpResult result);
}
