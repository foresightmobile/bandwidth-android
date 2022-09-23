package com.bandwidth.webrtc

import org.webrtc.MediaStream
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.PeerConnectionState
import org.webrtc.RtpReceiver
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.IceCandidate

open class PeerConnectionAdapter : PeerConnection.Observer {
    override fun onSignalingChange(signalingState: SignalingState) {}
    override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {}
    override fun onIceConnectionReceivingChange(b: Boolean) {}
    override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {}
    override fun onIceCandidate(iceCandidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {}
    override fun onAddStream(mediaStream: MediaStream) {}
    override fun onRemoveStream(mediaStream: MediaStream) {}
    override fun onDataChannel(dataChannel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
    override fun onConnectionChange(newState: PeerConnectionState) {}
}