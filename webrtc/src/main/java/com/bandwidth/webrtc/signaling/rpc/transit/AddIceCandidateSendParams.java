package com.bandwidth.webrtc.signaling.rpc.transit;

public class AddIceCandidateSendParams {
    private String endpointId;
    private String candidate;
    private Integer sdpMLineIndex;
    private String sdpMid;

    public AddIceCandidateSendParams(String endpointId, String candidate, Integer sdpMLineIndex, String sdpMid) {
        this.endpointId = endpointId;
        this.candidate = candidate;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdpMid = sdpMid;
    }
}
