package com.bandwidth.webrtc.types

class PublishMetadata(
    var mediaStreams: Map<String, StreamPublishMetadata?>,
    var dataChannels: Map<String, DataChannelPublishMetadata>
)