package com.jackmarcus.anti_clonevoice.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.SignupRequest
import com.jackmarcus.anti_clonevoice.ui.common.BackendSettingsDialog
import kotlinx.coroutines.launch

@Composable
fun SignupScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isOtpSent by remember { mutableStateOf(false) }
    var showBackendSettings by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }

    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.OtpSent) {
            isOtpSent = true
        }
    }
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)

    if (showBackendSettings) {
        BackendSettingsDialog(
            secureStorage = secureStorage,
            onDismiss = { showBackendSettings = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Signup", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showBackendSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Backend Settings")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (!isOtpSent) {
            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 6 chars)") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "All fields are required"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        errorMessage = "Invalid email format"
                        return@Button
                    }
                    errorMessage = null
                    viewModel.sendOtp(email)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthViewModel.AuthState.Loading
            ) {
                Text("Send Verification OTP")
            }
        } else {
            Text(text = "Verify your email: $email", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            TextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                label = { Text("Enter OTP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (otpCode.isBlank()) {
                        errorMessage = "Enter OTP"
                    } else {
                        viewModel.signup(SignupRequest(username, password, email), otpCode)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthViewModel.AuthState.Loading
            ) {
                Text("Verify & Create Account")
            }
            
            TextButton(onClick = { isOtpSent = false }) {
                Text("Back to Edit")
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("68309335150-3cmp2r9j3li5689hv1vtv6keb6mfvbh4.apps.googleusercontent.com") // User must replace this
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    try {
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        (credential as? GoogleIdTokenCredential)?.let {
                            viewModel.authenticateWithFirebase(it.idToken)
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Google Sign-in Error: ${e.message}")
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            Text("Sign up with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (authState) {
            is AuthViewModel.AuthState.Loading -> CircularProgressIndicator()
            is AuthViewModel.AuthState.Authenticated, 
            is AuthViewModel.AuthState.Success -> LaunchedEffect(Unit) { onSignupSuccess() }
            is AuthViewModel.AuthState.Error -> Text(
                text = (authState as AuthViewModel.AuthState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
            else -> {}
        }
    }
}
