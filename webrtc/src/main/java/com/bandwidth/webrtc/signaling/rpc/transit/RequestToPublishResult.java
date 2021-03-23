package com.bandwidth.webrtc.signaling.rpc.transit;

import java.util.List;

public class RequestToPublishResult {
    private String endpointId;
    private String participantId;
    private List<String> mediaTypes;
    private String direction;

    public String getEndpointId() {
        return endpointId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public String getDirection() {
        return direction;
    }
}
