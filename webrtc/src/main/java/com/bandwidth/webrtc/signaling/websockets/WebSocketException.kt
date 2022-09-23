package com.bandwidth.webrtc.signaling.websockets

import java.lang.Exception

class WebSocketException(message: String?, cause: Throwable?) : Exception(message, cause)