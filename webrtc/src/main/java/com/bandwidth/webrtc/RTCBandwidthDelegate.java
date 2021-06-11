package com.bandwidth.webrtc;

import com.bandwidth.webrtc.types.RTCStream;

import org.webrtc.RtpReceiver;

import java.util.List;

public interface RTCBandwidthDelegate {
    void onStreamAvailable(RTCBandwidth bandwidth, RTCStream stream);
    void onStreamUnavailable(RTCBandwidth bandwidth, String streamId);
}
