package com.bandwidth.webrtc.signaling.rpc.transit;

public class AddIceCandidateParams {
    private String endpointId;
    private Candidate candidate;

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

    public String getEndpointId() {
        return endpointId;
    }

    public Candidate getCandidate() {
        return candidate;
    }
}
