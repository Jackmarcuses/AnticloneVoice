package com.jackmarcus.anti_clonevoice.data.remote

import com.jackmarcus.anti_clonevoice.data.remote.models.*
import retrofit2.Response
import retrofit2.http.*

interface AuthService {
    @POST("api/v1/auth/send-otp")
    suspend fun sendOtp(@Query("email") email: String): Response<MessageResponse>

    @POST("api/v1/signup")
    suspend fun signup(
        @Body request: SignupRequest,
        @Query("otp") otp: String
    ): Response<AuthResponse>

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/firebase")
    suspend fun firebaseAuth(@Body request: FirebaseAuthRequest): Response<AuthResponse>

    @GET("api/v1/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @PATCH("api/v1/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<MessageResponse>
}
