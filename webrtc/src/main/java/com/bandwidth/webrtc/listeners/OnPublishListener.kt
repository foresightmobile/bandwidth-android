package com.bandwidth.webrtc.listeners

import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

interface OnPublishListener {
    fun onPublish(
        streamId: String?,
        mediaTypes: List<String?>?,
        audioSource: AudioSource?,
        audioTrack: AudioTrack?,
        videoSource: VideoSource?,
        videoTrack: VideoTrack?
    )
}