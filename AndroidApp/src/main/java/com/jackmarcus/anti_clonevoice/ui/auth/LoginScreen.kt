package com.jackmarcus.anti_clonevoice.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.LoginRequest
import com.jackmarcus.anti_clonevoice.ui.common.BackendSettingsDialog
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToSignup: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showBackendSettings by remember { mutableStateOf(false) }
    
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    val activity = context as? ComponentActivity
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
            Text(text = "Login", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showBackendSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Backend Settings")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.login(LoginRequest(username, password)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            Text("Login")
        }
        
        TextButton(onClick = onNavigateToSignup) {
            Text("Don't have an account? Signup")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Button(
            onClick = {
                scope.launch {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("YOUR_SERVER_CLIENT_ID") // User must replace this
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    try {
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        if (credential is GoogleIdTokenCredential) {
                            viewModel.authenticateWithFirebase(credential.idToken)
                        }
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Sign in with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        if (authState is AuthViewModel.AuthState.OtpSent) {
            val verificationId = (authState as AuthViewModel.AuthState.OtpSent).verificationId
            TextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                label = { Text("Enter OTP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                    viewModel.signInWithPhone(credential)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify OTP")
            }
        } else {
            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number (+1...)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (activity != null) {
                        val options = PhoneAuthOptions.newBuilder()
                            .setPhoneNumber(phoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    viewModel.signInWithPhone(credential)
                                }

                                override fun onVerificationFailed(e: FirebaseException) {
                                    // Handle error
                                }

                                override fun onCodeSent(
                                    verificationId: String,
                                    token: PhoneAuthProvider.ForceResendingToken
                                ) {
                                    viewModel.onOtpSent(verificationId)
                                }
                            })
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Send OTP")
            }
        }
        
        when (authState) {
            is AuthViewModel.AuthState.Loading -> CircularProgressIndicator()
            is AuthViewModel.AuthState.Authenticated -> LaunchedEffect(Unit) { onLoginSuccess() }
            is AuthViewModel.AuthState.Error -> Text(
                text = (authState as AuthViewModel.AuthState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
            else -> {}
        }
    }
}
