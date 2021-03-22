package com.bandwidth.webrtc.signaling;

import com.bandwidth.webrtc.signaling.listeners.OnConnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnOfferSdpListener;
import com.bandwidth.webrtc.signaling.listeners.OnRequestToPublishListener;
import com.bandwidth.webrtc.signaling.listeners.OnSetMediaPreferencesListener;

public class SignalingClient implements Signaling {

    @Override
    public void setOnConnectListener(OnConnectListener listener) {
        
    }

    @Override
    public void setOnDisconnectListener(OnDisconnectListener listener) {

    }

    @Override
    public void setOnOfferSdpListener(OnOfferSdpListener listener) {

    }

    @Override
    public void setOnRequestToPublishListener(OnRequestToPublishListener listener) {

    }

    @Override
    public void setOnSetMediaPreferencesListener(OnSetMediaPreferencesListener listener) {

    }
}
