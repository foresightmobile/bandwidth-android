package com.bandwidth.webrtc;

import org.webrtc.RtpReceiver;

import java.util.List;

public interface RTCBandwidthDelegate {
    void onStreamAvailable(String endpointId, String participantId, String alias, List<String> mediaTypes, RtpReceiver rtpReceiver);
    void onStreamUnavailable(String endpointId);
}
