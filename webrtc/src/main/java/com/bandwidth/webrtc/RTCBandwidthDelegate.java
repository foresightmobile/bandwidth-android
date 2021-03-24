package com.bandwidth.webrtc;

import org.webrtc.RtpReceiver;

import java.util.List;

public interface RTCBandwidthDelegate {
    void onStreamAvailable(RTCBandwidth bandwidth, String endpointId, String participantId, String alias, List<String> mediaTypes, RtpReceiver rtpReceiver);
    void onStreamUnavailable(RTCBandwidth bandwidth, String endpointId);
}
