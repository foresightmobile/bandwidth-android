package com.bandwidth.webrtc.signaling.rpc.transit;

import java.util.List;

public class RequestToPublishParams {
    List<String> mediaTypes;
    String alias;

    public RequestToPublishParams(List<String> mediaTypes, String alias) {
        this.mediaTypes = mediaTypes;
        this.alias = alias;
    }
}
