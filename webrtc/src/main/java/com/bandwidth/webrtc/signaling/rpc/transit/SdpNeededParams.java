package com.bandwidth.webrtc.signaling.rpc.transit;

import java.util.List;

public class SdpNeededParams {
    private List<String> mediaTypes;
    private String alias;
    private String direction;
    private String endpointId;
    private String participantId;

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public String getAlias() {
        return alias;
    }

    public String getDirection() {
        return direction;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public String getParticipantId() {
        return participantId;
    }
}
