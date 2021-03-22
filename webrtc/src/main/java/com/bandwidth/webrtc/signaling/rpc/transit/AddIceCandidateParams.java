package com.bandwidth.webrtc.signaling.rpc.transit;

public class AddIceCandidateParams {
    private String endpointId;
    private Candidate candidate;

    private class Candidate {
        private String candidate;
        private Integer sdpMLineIndex;
        private String sdpMid;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public Candidate getCandidate() {
        return candidate;
    }
}
