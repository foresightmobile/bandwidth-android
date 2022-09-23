package com.bandwidth.webrtc.listeners

import org.webrtc.*

interface OnStreamAvailableListener {
    fun onStreamAvailable(
        streamId: String?,
        mediaTypes: List<String?>?,
        audioTracks: List<AudioTrack?>?,
        videoTracks: List<VideoTrack?>?,
        alias: String?
    )
}