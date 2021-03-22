package com.bandwidth.webrtc.signaling.rpc.transit;

public class SetMediaPreferencesParams {
    String protocol;
    String aggregationType;
    Boolean sendRecv;

    public SetMediaPreferencesParams(String protocol, String aggregationType, Boolean sendRecv) {
        this.protocol = protocol;
        this.aggregationType = aggregationType;
        this.sendRecv = sendRecv;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getAggregationType() {
        return aggregationType;
    }

    public Boolean getSendRecv() {
        return sendRecv;
    }
}
