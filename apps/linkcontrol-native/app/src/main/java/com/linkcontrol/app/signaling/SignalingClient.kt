package com.linkcontrol.app.signaling

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin WebSocket client for the signaling/pairing server. It carries ONLY
 * control-plane messages (register, pairing, offer/answer/ICE relay). Screen
 * frames and input never pass through here — those go peer-to-peer over WebRTC.
 */
class SignalingClient(
    private val url: String,
    private val listener: Events,
) {
    interface Events {
        fun onOpen()
        fun onMessage(obj: JSONObject)
        fun onClosed(reason: String)
        fun onFailure(t: Throwable)
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)   // keep NAT mappings + detect drops
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    @Volatile var isOpen = false; private set

    fun connect() {
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isOpen = true; Log.i(TAG, "open"); listener.onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.onSuccess(listener::onMessage)
                    .onFailure { Log.w(TAG, "bad message: $text") }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isOpen = false; Log.i(TAG, "closed $code $reason"); listener.onClosed(reason)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isOpen = false; Log.w(TAG, "failure", t); listener.onFailure(t)
            }
        })
    }

    fun send(obj: JSONObject): Boolean = ws?.send(obj.toString()) ?: false

    fun send(type: String, build: JSONObject.() -> Unit = {}): Boolean =
        send(JSONObject().apply { put("type", type); build() })

    fun close() {
        isOpen = false
        ws?.close(1000, "client_close")
        ws = null
    }

    companion object { private const val TAG = "SignalingClient" }
}
