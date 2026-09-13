package com.jackmarcus.anti_clonevoice.data.repository

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.AuthService
import com.jackmarcus.anti_clonevoice.data.remote.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val authService: AuthService,
    private val secureStorage: SecureStorage
) {
    private val firebaseAuth = FirebaseAuth.getInstance()

    suspend fun signup(request: SignupRequest, otp: String): Result<AuthResponse> {
        return try {
            val response = authService.signup(request, otp)
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                secureStorage.saveToken(authResponse.token)
                secureStorage.saveUserId(authResponse.userId)
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Signup failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOtp(email: String): Result<String> {
        return try {
            val response = authService.sendOtp(email)
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "OTP sent")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to send OTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> {
        return try {
            val response = authService.login(request)
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                secureStorage.saveToken(authResponse.token)
                secureStorage.saveUserId(authResponse.userId)
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(): Result<ProfileResponse> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = authService.getProfile("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to fetch profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(username: String?, avatarUrl: String?): Result<String> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = authService.updateProfile("Bearer $token", UpdateProfileRequest(username, avatarUrl))
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Profile updated")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        secureStorage.clearToken()
    }

    fun isLoggedIn(): Boolean {
        return secureStorage.getToken() != null
    }

    suspend fun firebaseAuthBackend(idToken: String): Result<AuthResponse> {
        return try {
            val response = authService.firebaseAuth(FirebaseAuthRequest(idToken))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                secureStorage.saveToken(authResponse.token)
                secureStorage.saveUserId(authResponse.userId)
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Firebase authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentFirebaseUser(): FirebaseUser? = firebaseAuth.currentUser

    suspend fun signInWithPhone(credential: PhoneAuthCredential): Result<AuthResponse> {
        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val idToken = authResult.user?.getIdToken(true)?.await()?.token
            if (idToken != null) {
                firebaseAuthBackend(idToken)
            } else {
                Result.failure(Exception("Failed to get Firebase ID token"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
