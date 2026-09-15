package com.jackmarcus.anti_clonevoice.ui.auth

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackmarcus.anti_clonevoice.data.remote.models.LoginRequest
import com.jackmarcus.anti_clonevoice.data.remote.models.SignupRequest
import com.jackmarcus.anti_clonevoice.data.remote.models.ProfileResponse
import com.jackmarcus.anti_clonevoice.data.repository.AuthRepository
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState = _profileState.asStateFlow()

    fun signup(request: SignupRequest, otp: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.signup(request, otp)
            _authState.value = if (result.isSuccess) {
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Signup failed")
            }
        }
    }

    fun sendOtp(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.sendOtp(email)
            if (result.isSuccess) {
                _authState.value = AuthState.OtpSent(result.getOrNull() ?: "")
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Failed to send OTP")
            }
        }
    }

    fun login(request: LoginRequest) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(request)
            _authState.value = if (result.isSuccess) {
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            val result = repository.getProfile()
            _profileState.value = if (result.isSuccess) {
                ProfileState.Success(result.getOrNull()!!)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to fetch profile"
                if (error.contains("User not found", ignoreCase = true) || 
                    error.contains("404", ignoreCase = true) ||
                    error.contains("Token is not valid", ignoreCase = true) ||
                    error.contains("401", ignoreCase = true)) {
                    logout()
                }
                ProfileState.Error(error)
            }
        }
    }

    fun updateProfile(username: String?, avatarUrl: String?) {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            val result = repository.updateProfile(username, avatarUrl)
            if (result.isSuccess) {
                getProfile()
            } else {
                _profileState.value = ProfileState.Error(result.exceptionOrNull()?.message ?: "Update failed")
            }
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Idle
    }

    fun authenticateWithFirebase(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.firebaseAuthBackend(idToken)
            _authState.value = if (result.isSuccess) {
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Authentication failed")
            }
        }
    }

    fun signInWithPhone(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.signInWithPhone(credential)
            _authState.value = if (result.isSuccess) {
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Phone sign-in failed")
            }
        }
    }

    fun onOtpSent(verificationId: String) {
        _authState.value = AuthState.OtpSent(verificationId)
    }

    fun isLoggedIn(): Boolean = repository.isLoggedIn()

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Authenticated : AuthState()
        data class OtpSent(val verificationId: String) : AuthState()
        data class Success(val message: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    sealed class ProfileState {
        object Idle : ProfileState()
        object Loading : ProfileState()
        data class Success(val profile: ProfileResponse) : ProfileState()
        data class Error(val message: String) : ProfileState()
    }
}
