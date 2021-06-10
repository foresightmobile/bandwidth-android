package com.bandwidth.webrtc;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.signaling.ConnectionException;

import java.net.URI;

public interface RTCBandwidth {
    void connect(URI uri, OnConnectListener onConnectListener) throws ConnectionException;
    void disconnect();
    void publish(String alias, OnPublishListener onPublishListener);
}
