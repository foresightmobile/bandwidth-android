package com.bandwidth.webrtc.signaling;

import com.bandwidth.webrtc.signaling.listeners.OnConnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult;
import com.bandwidth.webrtc.signaling.rpc.transit.RequestToPublishResult;

import java.net.URI;

public interface Signaling {
    void setOnConnectListener(OnConnectListener listener);
    void setOnDisconnectListener(OnDisconnectListener listener);

    void connect(URI uri) throws ConnectionException;
    void disconnect();
    void sendIceCandidate(String endpointId, String sdp, Integer sdpMLineIndex, String sdpMid);

    void offerSdp(String endpointId, String sdp, Observer observer);
    void requestToPublish(Boolean audio, Boolean video, String alias, Observer observer);
    void setMediaPreferences(Observer observer);

    interface Observer {
        void onOfferSdp(Signaling signaling, OfferSdpResult result);
        void onRequestToPublish(Signaling signaling, RequestToPublishResult result);
        void onSetMediaPreferences(Signaling signaling);
    }

    class Adapter implements Observer {
        @Override
        public void onOfferSdp(Signaling signaling, OfferSdpResult result) {

        }

        @Override
        public void onRequestToPublish(Signaling signaling, RequestToPublishResult result) {

        }

        @Override
        public void onSetMediaPreferences(Signaling signaling) {

        }
    }
}
