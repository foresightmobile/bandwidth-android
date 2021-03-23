package com.bandwidth.webrtc.signaling.listeners;

import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.rpc.transit.RequestToPublishResult;

public interface OnRequestToPublishListener {
    void onRequestToPublish(Signaling signaling, RequestToPublishResult result);
}
