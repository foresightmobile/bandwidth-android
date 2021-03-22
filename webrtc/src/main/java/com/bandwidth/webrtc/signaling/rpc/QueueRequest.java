package com.bandwidth.webrtc.signaling.rpc;

import java.util.Timer;

public class QueueRequest {
    private String method;
    private Timer timer;

    public QueueRequest(String method, Timer timer) {
        this.method = method;
        this.timer = timer;
    }

    public String getMethod() {
        return method;
    }

    public Timer getTimer() {
        return timer;
    }
}
