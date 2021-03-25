package com.bandwidth.webrtc.signaling.websockets;

import com.bandwidth.webrtc.signaling.websockets.listeners.OnCloseListener;
import com.bandwidth.webrtc.signaling.websockets.listeners.OnErrorListener;
import com.bandwidth.webrtc.signaling.websockets.listeners.OnMessageListener;
import com.bandwidth.webrtc.signaling.websockets.listeners.OnOpenListener;

public interface WebSocketProvider {
    void setOnOpenListener(OnOpenListener onOpenListener);
    void setOnCloseListener(OnCloseListener onCloseListener);
    void setOnMessageListener(OnMessageListener onMessageListener);
    void setOnErrorListener(OnErrorListener onErrorListener);

    void open() throws WebSocketException;
    void close();
    void sendMessage(String message);
}
