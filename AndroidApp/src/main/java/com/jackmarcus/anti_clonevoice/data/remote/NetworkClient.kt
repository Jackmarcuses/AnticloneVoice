package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.Config
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

object NetworkClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Config.BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authService: AuthService = retrofit.create(AuthService::class.java)
    val contactsService: ContactsService = retrofit.create(ContactsService::class.java)
}
