package com.bandwidth.webrtc;

import com.bandwidth.webrtc.signaling.NullSessionException;
import com.bandwidth.webrtc.signaling.Signaling;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.bandwidth.webrtc.signaling.websockets.WebSocketProvider;

import org.junit.Test;

import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SignalingClientTest {
    @Test
    public void shouldOfferSdp() {
        WebSocketProvider mockedWebSocketProvider = mock(WebSocketProvider.class);
        SignalingDelegate mockedSignalingDelegate = mock(SignalingDelegate.class);

        Signaling signaling = new SignalingClient(mockedWebSocketProvider, mockedSignalingDelegate);
        signaling.offerSdp("123-456", "offer-sdp");

        // Pattern matching for an sdp offer, required due to each request having a unique id.
        String pattern = "^\\{\"id\":\"[\\w\\d-]+\",\"jsonrpc\":\"2.0\",\"method\":\"offerSdp\",\"params\":\\{\"endpointId\":\"123-456\",\"sdpOffer\":\"offer-sdp\"\\}\\}$";

        verify(mockedWebSocketProvider, times(1)).sendMessage(matches(pattern));
    }

    @Test
    public void shouldRequestToPublish() {
        WebSocketProvider mockedWebSocketProvider = mock(WebSocketProvider.class);
        SignalingDelegate mockedSignalingDelegate = mock(SignalingDelegate.class);

        Signaling signaling = new SignalingClient(mockedWebSocketProvider, mockedSignalingDelegate);
        signaling.requestToPublish(true, true, "signaling-alias");

        // Pattern matching for an sdp offer, required due to each request having a unique id.
        String pattern = "^\\{\"id\":\"[\\w\\d-]+\",\"jsonrpc\":\"2.0\",\"method\":\"requestToPublish\",\"params\":\\{\"mediaTypes\":\\[\"AUDIO\",\"VIDEO\"\\],\"alias\":\"signaling-alias\"\\}\\}$";

        verify(mockedWebSocketProvider, times(1)).sendMessage(matches(pattern));
    }

    @Test
    public void shouldSetMediaPreferences() {
        WebSocketProvider mockedWebSocketProvider = mock(WebSocketProvider.class);
        SignalingDelegate mockedSignalingDelegate = mock(SignalingDelegate.class);

        Signaling signaling = new SignalingClient(mockedWebSocketProvider, mockedSignalingDelegate);
        signaling.setMediaPreferences();

        // Pattern matching for an sdp offer, required due to each request having a unique id.
        String pattern = "^\\{\"id\":\"[\\w\\d-]+\",\"jsonrpc\":\"2.0\",\"method\":\"setMediaPreferences\",\"params\":\\{\"protocol\":\"WEB_RTC\",\"aggregationType\":\"NONE\",\"sendRecv\":false\\}\\}$";

        verify(mockedWebSocketProvider, times(1)).sendMessage(matches(pattern));
    }
}
