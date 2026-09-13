package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.remote.models.*
import retrofit2.Response
import retrofit2.http.*

interface ContactsService {
    @GET("api/v1/contacts")
    suspend fun getContacts(@Header("Authorization") token: String): Response<List<ContactResponse>>

    @POST("api/v1/contacts")
    suspend fun addContact(
        @Header("Authorization") token: String,
        @Body request: AddContactRequest
    ): Response<MessageResponse>

    @DELETE("api/v1/contacts/{contactId}")
    suspend fun deleteContact(
        @Header("Authorization") token: String,
        @Path("contactId") contactId: String
    ): Response<MessageResponse>

    @GET("api/v1/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("query") query: String
    ): Response<List<ContactResponse>>
}
