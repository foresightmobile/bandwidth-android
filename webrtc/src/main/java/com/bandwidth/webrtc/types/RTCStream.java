package com.bandwidth.webrtc.types;

import org.webrtc.AudioSource;
import org.webrtc.MediaStream;
import org.webrtc.VideoSource;

import java.util.List;

public class RTCStream {
    private final List<String> mediaTypes;
    private final MediaStream mediaStream;
    private final AudioSource audioSource;
    private final VideoSource videoSource;
    private final String alias;
    private final String participantId;

    public RTCStream(List<String> mediaTypes, MediaStream mediaStream, AudioSource audioSource, VideoSource videoSource, String alias, String participantId) {
        this.mediaTypes = mediaTypes;
        this.mediaStream = mediaStream;
        this.audioSource = audioSource;
        this.videoSource = videoSource;
        this.alias = alias;
        this.participantId = participantId;
    }

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

    public AudioSource getAudioSource() {
        return audioSource;
    }

    public VideoSource getVideoSource() {
        return videoSource;
    }

    public String getAlias() {
        return alias;
    }

    public String getParticipantId() {
        return participantId;
    }
}
