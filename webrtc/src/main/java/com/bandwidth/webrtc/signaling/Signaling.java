package com.bandwidth.webrtc.signaling;

import com.bandwidth.webrtc.signaling.listeners.OnConnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnOfferSdpListener;
import com.bandwidth.webrtc.signaling.listeners.OnRequestToPublishListener;
import com.bandwidth.webrtc.signaling.listeners.OnSetMediaPreferencesListener;

import java.io.IOException;
import java.net.URI;

public interface Signaling {
    void setOnConnectListener(OnConnectListener listener);
    void setOnDisconnectListener(OnDisconnectListener listener);
    void setOnOfferSdpListener(OnOfferSdpListener listener);
    void setOnRequestToPublishListener(OnRequestToPublishListener listener);
    void setOnSetMediaPreferencesListener(OnSetMediaPreferencesListener listener);
}
