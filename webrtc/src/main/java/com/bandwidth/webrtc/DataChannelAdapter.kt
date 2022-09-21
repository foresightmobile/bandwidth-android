package com.bandwidth.webrtc

import org.webrtc.DataChannel

open class DataChannelAdapter : DataChannel.Observer {
    override fun onBufferedAmountChange(l: Long) {}
    override fun onStateChange() {}
    override fun onMessage(buffer: DataChannel.Buffer) {}
}