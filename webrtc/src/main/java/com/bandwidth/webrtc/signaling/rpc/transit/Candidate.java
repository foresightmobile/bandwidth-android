package com.bandwidth.webrtc.signaling.rpc.transit;

public class Candidate {
    private String candidate;
    private Integer sdpMLineIndex;
    private String sdpMid;

    public String getCandidate() {
        return candidate;
    }

    public Integer getSdpMLineIndex() {
        return sdpMLineIndex;
    }

    public String getSdpMid() {
        return sdpMid;
    }
}
