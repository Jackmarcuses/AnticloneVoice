package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.PresenceUpdate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import io.mockk.slot

class PresenceManagerTest {
    private val secureStorage = mockk<SecureStorage>()
    private val okHttpClient = mockk<OkHttpClient>()
    private val presenceManager = PresenceManager(secureStorage, okHttpClient)

    @Test
    fun `connect establishes websocket and emits updates`() = runBlocking {
        val userId = "user123"
        every { secureStorage.getUserId() } returns userId
        val webSocket = mockk<WebSocket>()
        val listenerSlot = slot<WebSocketListener>()
        
        every { okHttpClient.newWebSocket(any(), capture(listenerSlot)) } returns webSocket
        
        presenceManager.connect()
        
        val update = PresenceUpdate("contact1", "online")
        val json = Json.encodeToString(update)
        
        // Simulate receiving a message
        listenerSlot.captured.onMessage(webSocket, json)
        
        val receivedUpdate = presenceManager.presenceUpdates.first()
        assertEquals(update, receivedUpdate)
    }
}
