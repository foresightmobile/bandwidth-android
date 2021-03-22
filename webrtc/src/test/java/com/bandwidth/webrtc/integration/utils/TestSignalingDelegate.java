package com.bandwidth.webrtc.integration.utils;

import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;

public class TestSignalingDelegate implements SignalingDelegate {
    @Override
    public void onAddIceCandidate(Signaling signaling, AddIceCandidateParams params) {

    }

    @Override
    public void onEndpointRemoved(Signaling signaling, EndpointRemovedParams params) {

    }

    @Override
    public void onSdpNeeded(Signaling signaling, SdpNeededParams params) {

    }
}
