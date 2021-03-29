package com.bandwidth.webrtc.listeners;

import org.webrtc.AudioSource;
import org.webrtc.RtpSender;
import org.webrtc.VideoSource;

import java.util.List;

public interface OnPublishListener {
    void onPublish(List<String> mediaTypes, RtpSender audioSender, AudioSource audioSource, RtpSender videoSender, VideoSource videoSource);
}
