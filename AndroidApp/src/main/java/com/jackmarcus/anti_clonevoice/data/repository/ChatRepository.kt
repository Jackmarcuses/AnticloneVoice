package com.jackmarcus.anti_clonevoice.data.repository

import android.util.Log
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.ChatService
import com.jackmarcus.anti_clonevoice.data.remote.NetworkClient
import com.jackmarcus.anti_clonevoice.data.remote.models.ChatMessagePayload
import com.jackmarcus.anti_clonevoice.data.remote.models.Message
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*

class ChatRepository(
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient,
    private val injectedChatService: ChatService? = null
) {
    private val chatService get() = injectedChatService ?: NetworkClient.chatService
    private var webSocket: WebSocket? = null
    private val TAG = "ChatRepository"

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    suspend fun getMessages(contactId: String): List<Message> {
        val token = secureStorage.getToken() ?: return emptyList()
        return try {
            chatService.getMessages("Bearer $token", contactId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages: ${e.message}")
            emptyList()
        }
    }

    fun connect(userId: String) {
        val url = "${Config.WS_URL}/api/v1/chat/$userId"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val payload = Json.decodeFromString<ChatMessagePayload>(text)
                    if (payload.type == "chat_message") {
                        _messages.tryEmit(payload.message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing chat message: ${e.message}")
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Chat WebSocket failure: ${t.message}")
            }
        })
    }

    fun sendMessage(receiverId: String, content: String) {
        val senderId = secureStorage.getUserId() ?: return
        val message = Message(
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        val payload = ChatMessagePayload(message = message)
        val text = Json.encodeToString(payload)
        webSocket?.send(text)
        
        // Also emit locally for immediate UI update
        _messages.tryEmit(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "Closed")
        webSocket = null
    }
}
