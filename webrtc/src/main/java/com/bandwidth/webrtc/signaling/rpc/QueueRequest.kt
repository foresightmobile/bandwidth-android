package com.bandwidth.webrtc.signaling.rpc

import com.bandwidth.webrtc.signaling.Signaling
import java.util.*

class QueueRequest(val method: String?, val observer: Signaling.Observer, val timer: Timer)