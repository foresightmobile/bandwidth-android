package com.bandwidth.webrtc.signaling.rpc.transit;

public class AddIceCandidateParams {
    private String endpointId;
    private Candidate candidate;

    public String getEndpointId() {
        return endpointId;
    }

    public Candidate getCandidate() {
        return candidate;
    }
}
