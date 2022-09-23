package com.bandwidth.webrtc.signaling.rpc.transit.base

class Notification<T>(val jsonrpc: String, val method: String, val params: T)