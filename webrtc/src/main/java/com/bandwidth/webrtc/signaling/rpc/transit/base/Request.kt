package com.bandwidth.webrtc.signaling.rpc.transit.base

class Request<T>(val id: String, val jsonrpc: String, val method: String, val params: T)