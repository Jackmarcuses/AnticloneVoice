package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var secureStorage: SecureStorage? = null

    fun init(storage: SecureStorage) {
        this.secureStorage = storage
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            // Only add token if it exists AND we aren't trying to log in/sign up
            val path = chain.request().url.encodedPath
            val token = secureStorage?.getToken()
            if (!token.isNullOrEmpty() && !path.contains("login") && !path.contains("signup")) {
                requestBuilder.removeHeader("Authorization")
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private var currentRetrofit: Retrofit? = null
    private var currentBaseUrl: String? = null

    val retrofit: Retrofit
        get() {
            val url = Config.BASE_URL
            if (currentRetrofit == null || currentBaseUrl != url) {
                currentBaseUrl = url
                currentRetrofit = Retrofit.Builder()
                    .baseUrl(url)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                
                // Clear cached services
                _authService = null
                _contactsService = null
                _chatService = null
            }
            return currentRetrofit!!
        }

    private var _authService: AuthService? = null
    val authService: AuthService get() {
        if (_authService == null) _authService = retrofit.create(AuthService::class.java)
        return _authService!!
    }

    private var _contactsService: ContactsService? = null
    val contactsService: ContactsService get() {
        if (_contactsService == null) _contactsService = retrofit.create(ContactsService::class.java)
        return _contactsService!!
    }

    private var _chatService: ChatService? = null
    val chatService: ChatService get() {
        if (_chatService == null) _chatService = retrofit.create(ChatService::class.java)
        return _chatService!!
    }

    private var _transcriptService: TranscriptService? = null
    val transcriptService: TranscriptService get() {
        if (_transcriptService == null) _transcriptService = retrofit.create(TranscriptService::class.java)
        return _transcriptService!!
    }
}
