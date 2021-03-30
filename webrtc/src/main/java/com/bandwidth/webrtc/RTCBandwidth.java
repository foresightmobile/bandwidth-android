package com.bandwidth.webrtc;

import com.bandwidth.webrtc.listeners.OnConnectListener;
import com.bandwidth.webrtc.listeners.OnPublishListener;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.NullSessionException;
import com.bandwidth.webrtc.signaling.websockets.WebSocketException;

import java.io.IOException;
import java.net.URI;

public interface RTCBandwidth {
    void setOnConnectListener(OnConnectListener listener);
    void setOnPublishListener(OnPublishListener listener);

    void connect(URI uri) throws ConnectionException;
    void publish(Boolean audio, Boolean video, String alias) throws NullSessionException;
    void unpublish(String endpointId);
}
