package com.bandwidth.webrtc.listeners;

import org.webrtc.RtpSender;

import java.util.List;

public interface OnPublishListener {
    void onPublish(List<String> mediaTypes, RtpSender audioSender, RtpSender videoSender);
}
