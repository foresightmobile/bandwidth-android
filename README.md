# Bandwidth Android SDK

The Bandwidth Android SDK makes it quick and easy to build an excellent audio and video experience in your Android app. We provide tools to unlock the power of Bandwidth's audio and video networks.

## Installation

### Requirements

* Android

## Getting Started

```java
class WebRTCService implements RTCBandwidthDelegate {
    RTCBandwidth bandwidth;

    public WebRTCService(Context context) throws URISyntaxException {
        bandwidth = new RTCBandwidthClient(context, this);

        bandwidth.setOnConnectListener(() -> {
            // Start requesting to publish audio and video once connected.
            bandwidth.publish(true, true, "stream-alias");
        });

        bandwidth.setOnPublishListener((mediaTypes, audioSender, videoSender) -> {
            // Work with the local audio and video senders.
        });

        URI uri = new URI("wss://device.webrtc.bandwidth.com/v2?token=<token>&uniqueId=<unique-id>");
        bandwidth.connect(uri);
    }

    @Override
    public void onStreamAvailable(RTCBandwidth bandwidth, String endpointId, String participantId, String alias, List<String> mediaTypes, RtpReceiver rtpReceiver) {
        System.out.println("onStreamAvailable");
    }

    @Override
    public void onStreamUnavailable(RTCBandwidth bandwidth, String endpointId) {
        System.out.println("onStreamUnavailable");
    }
}
```
