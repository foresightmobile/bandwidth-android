package com.bandwidth.webrtc.signaling.rpc.transit;

public class OfferSdpParams {
    private String endpointId;
    private String sdpOffer;

    public OfferSdpParams(String endpointId, String sdpOffer) {
        this.endpointId = endpointId;
        this.sdpOffer = sdpOffer;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public String getSdpOffer() {
        return sdpOffer;
    }
}