package com.bandwidth.webrtc.integration;

import com.bandwidth.webrtc.integration.utils.TestSignalingDelegate;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.signaling.SignalingClient;
import com.bandwidth.webrtc.signaling.SignalingDelegate;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class SignalingClientTest {

    private class ParticipantsResponse {
        private String deviceToken;

        public String getDeviceToken() {
            return deviceToken;
        }
    }

    private String getDeviceToken() throws IOException {
        String path = System.getenv("BANDWIDTH_URL_WEBRTC_TOKEN");
        URL url = new URL(path);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder stringBuilder = new StringBuilder();

        String output;
        while ((output = bufferedReader.readLine()) != null) {
            stringBuilder.append(output);
        }
        output = stringBuilder.toString();

        ParticipantsResponse response = new Gson().fromJson(output, ParticipantsResponse.class);
        return response.getDeviceToken();
    }

    CountDownLatch lock = new CountDownLatch(1);

    @Test
    public void shouldConnect() throws IOException, URISyntaxException, InterruptedException, ConnectionException {
        String deviceToken = getDeviceToken();
        String uniqueId = UUID.randomUUID().toString();

        String basePath = System.getenv("BANDWIDTH_URL_WEBRTC");
        String combinedPath = String.format("%s?token=%s&uniqueId=%s", basePath, deviceToken, uniqueId);
        URI uri = new URI(combinedPath);

        SignalingDelegate delegate = new TestSignalingDelegate();
        SignalingClient client = new SignalingClient(delegate);

        client.setOnConnectListener(signaling -> {
            lock.countDown();
        });

        client.connect(uri);

        lock.await(5000, TimeUnit.MILLISECONDS);
    }
}
