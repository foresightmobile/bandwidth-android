package com.bandwidth.webrtc.signaling.rpc.transit;

import java.util.List;

public class OfferSdpResult {
    private String sdpAnswer;
    private List<Candidate> candidates;

    public String getSdpAnswer() {
        return sdpAnswer;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }
}
