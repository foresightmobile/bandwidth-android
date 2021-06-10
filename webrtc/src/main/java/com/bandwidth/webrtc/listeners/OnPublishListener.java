package com.bandwidth.webrtc.listeners;

import com.bandwidth.webrtc.types.RTCStream;

public interface OnPublishListener {
    void onPublish(RTCStream stream);
}
