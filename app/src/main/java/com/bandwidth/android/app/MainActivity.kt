package com.bandwidth.android.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bandwidth.android.BuildConfig
import com.bandwidth.android.R
import com.bandwidth.webrtc.RTCBandwidth
import com.bandwidth.webrtc.RTCBandwidthClient
import com.bandwidth.webrtc.listeners.OnConnectListener
import com.bandwidth.webrtc.listeners.OnPublishListener
import com.bandwidth.webrtc.listeners.OnStreamAvailableListener
import com.bandwidth.webrtc.listeners.OnStreamUnavailableListener
import com.bandwidth.webrtc.signaling.ConnectionException
import org.webrtc.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var bandwidth: RTCBandwidth? = null
    var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var offeredVideoTracks = HashMap<String, VideoTrack>()
    private var eglBase: EglBase? = null
    private var isConnected = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            100
        )
        localRenderer = findViewById(R.id.localSurfaceViewRenderer)
        remoteRenderer = findViewById(R.id.remoteSurfaceViewRenderer)
        eglBase = EglBase.create()

        // Create local video renderer.
        localRenderer?.init(eglBase?.getEglBaseContext(), null)
        localRenderer?.setMirror(true)
        localRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

        // Create remote video renderer.
        remoteRenderer?.init(eglBase?.getEglBaseContext(), null)
        remoteRenderer?.setMirror(false)
        remoteRenderer?.setEnableHardwareScaler(false)
        bandwidth = RTCBandwidthClient(applicationContext, eglBase?.getEglBaseContext())
        bandwidth?.setOnStreamAvailableListener(object : OnStreamAvailableListener {
            override fun onStreamAvailable(
                streamId: String?,
                mediaTypes: List<String?>?,
                audioTracks: List<AudioTrack?>?,
                videoTracks: List<VideoTrack?>?,
                alias: String?
            ) {
                if (videoTracks.isNullOrEmpty().not()) {
                    if (!offeredVideoTracks.containsKey(streamId)) {
                        offeredVideoTracks[streamId] = videoTracks?.get(0)
                    }
                    if (remoteVideoTrack == null) {
                        runOnUiThread {
                            remoteVideoTrack = videoTracks?.get(0)
                            remoteVideoTrack!!.setEnabled(true)
                            remoteVideoTrack!!.addSink(remoteRenderer)
                        }
                    }
                }
            }
        })
        bandwidth?.setOnStreamUnavailableListener(object : OnStreamUnavailableListener {
            override fun onStreamUnavailable(streamId: String?) {
                offeredVideoTracks.remove(streamId)
                if (offeredVideoTracks.isEmpty()) {
                    println("on Stream NOT available - no more tracks")
                    remoteRenderer?.clearImage()
                    remoteVideoTrack = null
                } else {
                    val temp = offeredVideoTracks.entries.iterator().next().value
                    println("on Stream NOT available - video track replacement - $temp")
                    runOnUiThread {
                        remoteVideoTrack = temp
                        remoteVideoTrack!!.setEnabled(true)
                        remoteVideoTrack!!.addSink(remoteRenderer)
                    }
                }
            }
        })
        val button = findViewById<Button>(R.id.connectButton)
        button.setOnClickListener { view: View? ->
            if (isConnected) {
                disconnect()
                button.text = "Connect"
            } else {
                connect()
                button.text = "Disconnect"
            }
        }
    }

    private fun connect() {
        offeredVideoTracks = HashMap()
        Thread {
            try {
                val deviceToken: String? =
                    Conference?.instance?.requestDeviceToken(BuildConfig.CONFERENCE_SERVER_PATH)
                bandwidth!!.connect(BuildConfig.WEBRTC_SERVER_PATH, deviceToken,
                    object : OnConnectListener {
                        override fun onConnect() {
                            isConnected = true
                            bandwidth!!.publish(
                                "hello-world-android",
                                object : OnPublishListener {
                                    override fun onPublish(
                                        streamId: String?,
                                        mediaTypes: List<String?>?,
                                        audioSource: AudioSource?,
                                        audioTrack: AudioTrack?,
                                        videoSource: VideoSource?,
                                        videoTrack: VideoTrack?
                                    ) {
                                        runOnUiThread {
                                            if (videoSource != null) {
                                                if (videoTrack != null) {
                                                    publish(
                                                        videoSource,
                                                        videoTrack
                                                    )
                                                }
                                            }
                                        }
                                    }
                                })
                        }
                    })
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: ConnectionException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun disconnect() {
        isConnected = false
        try {
            videoCapturer!!.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        localRenderer!!.clearImage()
        remoteVideoTrack = null
        bandwidth!!.disconnect()
        remoteRenderer!!.clearImage()
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val cameraEnumerator = createCameraEnumerator()
        val deviceNames = cameraEnumerator.deviceNames
        for (deviceName in deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun createCameraEnumerator(): CameraEnumerator {
        return if (Camera2Enumerator.isSupported(applicationContext)) {
            Camera2Enumerator(applicationContext)
        } else Camera1Enumerator(false)
    }

    private fun publish(videoSource: VideoSource, videoTrack: VideoTrack) {
        localVideoTrack = videoTrack
        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        videoCapturer = createVideoCapturer()
        videoCapturer!!.initialize(
            surfaceTextureHelper,
            applicationContext,
            videoSource.capturerObserver
        )
        videoCapturer!!.startCapture(640, 480, 20)
        println("video stream attributes$videoTrack")
        localVideoTrack!!.setEnabled(true)
        localVideoTrack!!.addSink(localRenderer)
    }
}

private operator fun <K, V> HashMap<K, V>.set(streamId: K?, value: V?) {

}
