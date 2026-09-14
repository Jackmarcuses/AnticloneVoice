package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.remote.models.Message
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface ChatService {
    @GET("api/v1/messages/{contactId}")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("contactId") contactId: String
    ): List<Message>
}
