package com.bandwidth.webrtc.signaling;

import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;

public interface SignalingDelegate {
    void onAddIceCandidate(Signaling signaling, AddIceCandidateParams params);
    void onEndpointRemoved(Signaling signaling, EndpointRemovedParams params);
    void onSdpNeeded(Signaling signaling, SdpNeededParams params);
}
