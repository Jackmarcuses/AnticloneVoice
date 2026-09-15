package com.jackmarcus.anti_clonevoice.data.remote

import android.util.Log
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.PresenceUpdate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class PresenceManager(
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val TAG = "PresenceManager"
    
    private val _presenceUpdates = MutableSharedFlow<PresenceUpdate>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val presenceUpdates: SharedFlow<PresenceUpdate> = _presenceUpdates.asSharedFlow()

    fun connect() {
        val userId = secureStorage.getUserId() ?: return
        val url = "${Config.WS_URL}/presence/$userId"
        Log.d(TAG, "Connecting to presence WS: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Presence WebSocket Opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d(TAG, "Presence Update Received: $text")
                    val update = Json.decodeFromString<PresenceUpdate>(text)
                    _presenceUpdates.tryEmit(update)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing presence: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Presence WebSocket Closed: $reason")
                reconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Presence WebSocket Failure: ${t.message}")
                reconnect()
            }
        })
    }

    private fun reconnect() {
        Log.d(TAG, "Scheduling Presence Reconnect...")
        // Reconnect after 3 seconds
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                connect()
            }
        }, 3000)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}
