package com.bandwidth.webrtc.signaling

import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult
import com.bandwidth.webrtc.types.PublishMetadata
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener
import com.bandwidth.webrtc.signaling.rpc.transit.AnswerSdpResult
import com.bandwidth.webrtc.signaling.rpc.transit.SetMediaPreferencesResult

interface Signaling {
    fun setOnConnectListener(listener: (Signaling?) -> Unit)
    fun setOnDisconnectListener(listener: OnDisconnectListener?)

    @Throws(ConnectionException::class)
    fun connect(deviceToken: String?)

    @Throws(ConnectionException::class)
    fun connect(webSocketUrl: String?, deviceToken: String?)
    fun disconnect()
    fun offerSdp(sdp: String, publishMetadata: PublishMetadata, observer: Observer)
    fun answerSdp(sdp: String, observer: Observer)
    interface Observer {
        fun onOfferSdp(signaling: Signaling?, result: OfferSdpResult?)
        fun onAnswerSdp(signaling: Signaling?, result: AnswerSdpResult?)
        fun onSetMediaPreferences(signaling: Signaling?, result: SetMediaPreferencesResult?)
    }

    open class Adapter : Observer {
        override fun onOfferSdp(signaling: Signaling?, result: OfferSdpResult?) {}
        override fun onAnswerSdp(signaling: Signaling?, result: AnswerSdpResult?) {}
        override fun onSetMediaPreferences(
            signaling: Signaling?,
            result: SetMediaPreferencesResult?
        ) {
        }
    }
}