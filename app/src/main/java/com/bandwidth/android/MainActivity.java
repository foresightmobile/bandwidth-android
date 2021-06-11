package com.bandwidth.android;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.bandwidth.android.app.Conference;
import com.bandwidth.webrtc.RTCBandwidth;
import com.bandwidth.webrtc.RTCBandwidthClient;
import com.bandwidth.webrtc.signaling.ConnectionException;
import com.bandwidth.webrtc.types.RTCStream;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    private SurfaceTextureHelper surfaceTextureHelper;

    private RTCBandwidth bandwidth;

    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;

    private EglBase eglBase;

    private Boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 100);

        localRenderer = findViewById(R.id.localSurfaceViewRenderer);
        remoteRenderer = findViewById(R.id.remoteSurfaceViewRenderer);

        eglBase = EglBase.create();

        // Create local video renderer.
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        // Create remote video renderer.
        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setMirror(false);
        remoteRenderer.setEnableHardwareScaler(false);

        bandwidth = new RTCBandwidthClient(getApplicationContext(), eglBase.getEglBaseContext());

        bandwidth.setOnStreamAvailableListener((streamId, mediaTypes, audioTracks, videoTracks, alias) -> {
            runOnUiThread(() -> {
                remoteVideoTrack = videoTracks.get(0);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(remoteRenderer);
            });
        });

        bandwidth.setOnStreamUnavailableListener(streamId -> {
            runOnUiThread(this::streamUnavailable);
        });

        final Button button = findViewById(R.id.connectButton);
        button.setOnClickListener(view -> {
            if (isConnected) {
                disconnect();
                button.setText("Connect");
            } else {
                connect();
                button.setText("Disconnect");
            }
        });
    }

    private void connect() {
        new Thread((() -> {
            try {
                URI uri = createURI();

                bandwidth.connect(uri, () -> {
                    isConnected = true;

                    bandwidth.publish("hello-world", (streamId, mediaTypes, audioSource, audioTrack, videoSource, videoTrack) -> {
                        runOnUiThread(() -> {
                            localVideoTrack = videoTrack;

                            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());

                            VideoCapturer videoCapturer = createVideoCapturer();
                            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
                            videoCapturer.startCapture(640, 480, 30);

                            localVideoTrack.setEnabled(true);
                            localVideoTrack.addSink(localRenderer);
                        });
                    });
                });
            } catch (IOException | ConnectionException | URISyntaxException e) {
                e.printStackTrace();
            }
        })).start();
    }

    private void disconnect() {
        isConnected = false;
        bandwidth.disconnect();

        remoteRenderer.clearImage();
    }

    private VideoCapturer createVideoCapturer() {
        CameraEnumerator cameraEnumerator = createCameraEnumerator();

        String[] deviceNames = cameraEnumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null);
            }
        }

        return null;
    }

    private CameraEnumerator createCameraEnumerator() {
        if (Camera2Enumerator.isSupported(getApplicationContext())) {
            return new Camera2Enumerator(getApplicationContext());
        }

        return new Camera1Enumerator(false);
    }

    private URI createURI() throws URISyntaxException, IOException {
        final String webRtcServerPath = BuildConfig.WEBRTC_SERVER_PATH;
        final String conferenceServerPath = BuildConfig.CONFERENCE_SERVER_PATH;

        String deviceToken = Conference.getInstance().requestDeviceToken(conferenceServerPath);
        String uniqueId = UUID.randomUUID().toString();
        String sdkVersion = "android-alpha";

        String path = String.format("%s?token=%s&sdkVersion=%s&uniqueId=%s", webRtcServerPath, deviceToken, sdkVersion, uniqueId);
        return new URI(path);
    }

    private void streamUnavailable() {
        remoteRenderer.clearImage();
    }
}