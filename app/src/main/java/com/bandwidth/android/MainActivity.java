package com.bandwidth.android;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bandwidth.android.app.Conference;
import com.bandwidth.webrtc.RTCBandwidth;
import com.bandwidth.webrtc.RTCBandwidthClient;
import com.bandwidth.webrtc.signaling.ConnectionException;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    private RTCBandwidth bandwidth;

    VideoCapturer videoCapturer;

    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private HashMap<String, VideoTrack> offeredVideoTracks = new HashMap<String, VideoTrack>();

    private EglBase eglBase;

    private Boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO}, 100);

        localRenderer = findViewById(R.id.localSurfaceViewRenderer);
        remoteRenderer = findViewById(R.id.remoteSurfaceViewRenderer);

        eglBase = EglBase.create();

        // Create local video renderer.
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(true);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        // Create remote video renderer.
        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setMirror(false);
        remoteRenderer.setEnableHardwareScaler(false);

        bandwidth = new RTCBandwidthClient(getApplicationContext(), eglBase.getEglBaseContext());

        bandwidth.setOnStreamAvailableListener((streamId, mediaTypes, audioTracks, videoTracks, alias) -> {
            if (!videoTracks.isEmpty()) {
                if (!offeredVideoTracks.containsKey(streamId)) {
                    offeredVideoTracks.put(streamId,videoTracks.get(0));
                }
                if (remoteVideoTrack == null) {
                    runOnUiThread(() -> {
                        remoteVideoTrack = videoTracks.get(0);
                        remoteVideoTrack.setEnabled(true);
                        remoteVideoTrack.addSink(remoteRenderer);
                    });
                };
            };
        });

        bandwidth.setOnStreamUnavailableListener(streamId -> {
            offeredVideoTracks.remove(streamId);
            if (offeredVideoTracks.isEmpty()) {
                System.out.println("on Stream NOT available - no more tracks");
                remoteRenderer.clearImage();
                remoteVideoTrack = null;
            } else {
                VideoTrack temp = offeredVideoTracks.entrySet().iterator().next().getValue();
                System.out.println("on Stream NOT available - video track replacement - " +  temp);
                runOnUiThread(() -> {
                    remoteVideoTrack = temp;
                    remoteVideoTrack.setEnabled(true);
                    remoteVideoTrack.addSink(remoteRenderer);
                });
            };
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
        offeredVideoTracks = new HashMap<String, VideoTrack>();
        new Thread((() -> {
            try {
                String deviceToken = Conference.getInstance().requestDeviceToken(BuildConfig.CONFERENCE_SERVER_PATH);
                bandwidth.connect(BuildConfig.WEBRTC_SERVER_PATH, deviceToken, () -> {
                    isConnected = true;

                    bandwidth.publish("hello-world-android", (streamId, mediaTypes, audioSource, audioTrack, videoSource, videoTrack) -> {
                        runOnUiThread(() -> publish(videoSource, videoTrack));
                    });
                });
            } catch (IOException | ConnectionException e) {
                e.printStackTrace();
            }
        })).start();
    }

    private void disconnect() {
        isConnected = false;

        try {
            videoCapturer.stopCapture();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        localRenderer.clearImage();
        remoteVideoTrack = null;

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

    private void publish(VideoSource videoSource, VideoTrack videoTrack) {
        localVideoTrack = videoTrack;

        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());

        videoCapturer = createVideoCapturer();
        videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(640, 480, 20);

        System.out.println("video stream attributes" + videoTrack);

        localVideoTrack.setEnabled(true);
        localVideoTrack.addSink(localRenderer);
    }

}