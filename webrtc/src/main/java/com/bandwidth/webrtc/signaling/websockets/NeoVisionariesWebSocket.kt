package com.bandwidth.webrtc.signaling.websockets

import com.bandwidth.webrtc.signaling.websockets.listeners.OnOpenListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnMessageListener
import com.bandwidth.webrtc.signaling.ConnectionException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFrame
import com.bandwidth.webrtc.signaling.websockets.listeners.OnCloseListener
import com.bandwidth.webrtc.signaling.websockets.listeners.OnErrorListener
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketException
import java.io.IOException
import java.lang.Exception

class NeoVisionariesWebSocket : WebSocketProvider {
    private var webSocket: WebSocket? = null
    private var onOpenListener: OnOpenListener? = null
    private var onCloseListener: OnCloseListener? = null
    private var onMessageListener: OnMessageListener? = null
    private var onErrorListener: OnErrorListener? = null
    override fun setOnOpenListener(onOpenListener: OnOpenListener) {
        this.onOpenListener = onOpenListener
    }

    override fun setOnCloseListener(onCloseListener: OnCloseListener) {
        this.onCloseListener = onCloseListener
    }

    override fun setOnMessageListener(onMessageListener: OnMessageListener) {
        this.onMessageListener = onMessageListener
    }

    override fun setOnErrorListener(onErrorListener: OnErrorListener) {
        this.onErrorListener = onErrorListener
    }

    @Throws(ConnectionException::class)
    override fun open(uri: String?) {
        try {
            webSocket = WebSocketFactory().createSocket(uri)
            webSocket?.addListener(object : WebSocketAdapter() {
                @Throws(Exception::class)
                override fun onConnected(websocket: WebSocket, headers: Map<String, List<String>>) {
                    super.onConnected(websocket, headers)
                    if (onOpenListener != null) {
                        onOpenListener!!.onOpen(this@NeoVisionariesWebSocket)
                    }
                }

                @Throws(Exception::class)
                override fun onDisconnected(
                    websocket: WebSocket,
                    serverCloseFrame: WebSocketFrame,
                    clientCloseFrame: WebSocketFrame,
                    closedByServer: Boolean
                ) {
                    super.onDisconnected(
                        websocket,
                        serverCloseFrame,
                        clientCloseFrame,
                        closedByServer
                    )
                    if (onCloseListener != null) {
                        onCloseListener!!.onClose(this@NeoVisionariesWebSocket)
                    }
                }

                @Throws(Exception::class)
                override fun onTextMessage(websocket: WebSocket, message: String) {
                    super.onTextMessage(websocket, message)
                    if (onMessageListener != null) {
                        onMessageListener!!.onMessage(this@NeoVisionariesWebSocket, message)
                    }
                }

                @Throws(Exception::class)
                override fun onError(websocket: WebSocket, cause: WebSocketException) {
                    super.onError(websocket, cause)
                    if (onErrorListener != null) {
                        onErrorListener!!.onError(this@NeoVisionariesWebSocket, cause)
                    }
                }
            })
            webSocket?.connect()
        } catch (e: IOException) {
            throw ConnectionException("Could not connect to signaling server.", e)
        } catch (e: WebSocketException) {
            throw ConnectionException("Could not connect to signaling server.", e)
        }
    }

    override fun close() {
        webSocket!!.sendClose()
    }

    override fun sendMessage(message: String?) {
        webSocket!!.sendText(message)
    }
}