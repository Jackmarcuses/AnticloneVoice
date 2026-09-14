package com.jackmarcus.anti_clonevoice.webrtc

import android.util.Log
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.remote.models.SignalingMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.Timer
import java.util.TimerTask

class SignalingClient(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val TAG = "SignalingClient"
    
    private val _signalingMessages = MutableSharedFlow<SignalingMessage>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signalingMessages: SharedFlow<SignalingMessage> = _signalingMessages.asSharedFlow()

    fun connect(userId: String) {
        val url = "${Config.WS_URL}/api/v1/call/signal/$userId"
        Log.d(TAG, "Connecting to signaling WS: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Signaling WebSocket Opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d(TAG, "Signaling Message Received: $text")
                    val message = Json.decodeFromString<SignalingMessage>(text)
                    _signalingMessages.tryEmit(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing signaling message: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Signaling WebSocket Closed: $reason")
                // Aggressive reconnect after 2 seconds
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        connect(userId)
                    }
                }, 2000)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket Failure: ${t.message}")
                // Aggressive reconnect after 2 seconds
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        connect(userId)
                    }
                }, 2000)
            }
        })
    }

    fun sendMessage(message: SignalingMessage) {
        val text = Json.encodeToString(message)
        Log.d(TAG, "Sending Signaling Message: $text")
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "Call ended")
        webSocket = null
    }
}
