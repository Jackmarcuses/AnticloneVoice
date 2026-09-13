package com.jackmarcus.anti_clonevoice.webrtc

import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.remote.models.SignalingMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*

class SignalingClient(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    
    private val _signalingMessages = MutableSharedFlow<SignalingMessage>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signalingMessages: SharedFlow<SignalingMessage> = _signalingMessages.asSharedFlow()

    fun connect(userId: String) {
        val request = Request.Builder()
            .url("${Config.WS_URL}/api/v1/call/signal/$userId")
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = Json.decodeFromString<SignalingMessage>(text)
                    _signalingMessages.tryEmit(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Handle close
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    fun sendMessage(message: SignalingMessage) {
        val text = Json.encodeToString(message)
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "Call ended")
        webSocket = null
    }
}
