package com.jackmarcus.anti_clonevoice

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.NetworkClient
import com.jackmarcus.anti_clonevoice.data.remote.PresenceManager
import com.jackmarcus.anti_clonevoice.data.repository.AuthRepository
import com.jackmarcus.anti_clonevoice.data.repository.ContactsRepository
import com.jackmarcus.anti_clonevoice.ui.auth.AuthViewModel
import com.jackmarcus.anti_clonevoice.ui.auth.LoginScreen
import com.jackmarcus.anti_clonevoice.ui.auth.SignupScreen
import com.jackmarcus.anti_clonevoice.ui.call.CallScreen
import com.jackmarcus.anti_clonevoice.ui.call.CallState
import com.jackmarcus.anti_clonevoice.ui.call.CallViewModel
import com.jackmarcus.anti_clonevoice.ui.contacts.ContactsScreen
import com.jackmarcus.anti_clonevoice.ui.contacts.ContactsViewModel
import com.jackmarcus.anti_clonevoice.ui.profile.ProfileScreen
import com.jackmarcus.anti_clonevoice.ui.theme.AnticloneVoiceTheme
import com.jackmarcus.anti_clonevoice.webrtc.SignalingClient
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Manual Dependency Injection for now
        val secureStorage = SecureStorage(applicationContext)
        val authRepository = AuthRepository(NetworkClient.authService, secureStorage)
        val authViewModel = AuthViewModel(authRepository)
        
        val contactsRepository = ContactsRepository(NetworkClient.contactsService, secureStorage)
        val presenceManager = PresenceManager(secureStorage, OkHttpClient())
        val contactsViewModel = ContactsViewModel(contactsRepository, presenceManager)
        
        val okHttpClient = OkHttpClient()
        val signalingClient = SignalingClient(okHttpClient)
        val callViewModel = CallViewModel(applicationContext, signalingClient, secureStorage)

        setContent {
            AnticloneVoiceTheme {
                MainApp(authViewModel, contactsViewModel, callViewModel)
            }
        }
    }
}

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    contactsViewModel: ContactsViewModel,
    callViewModel: CallViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Permission Handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(context, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Trigger recomposition if auth state changes
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val callState by callViewModel.callState.collectAsStateWithLifecycle()
    
    // Auto-navigate to call screen for incoming calls or when dialing
    LaunchedEffect(callState) {
        if (callState == CallState.RINGING || callState == CallState.DIALING) {
            // Check if we are already on the call screen to avoid duplicate navigation
            if (navController.currentBackStackEntry?.destination?.route != "call") {
                navController.navigate("call")
            }
        }
    }
    
    // Only navigate to login if we were previously logged in and now we are not.
    // startDestination handles the initial launch correctly.
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.Idle && !authViewModel.isLoggedIn()) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != "login" && currentRoute != "signup") {
                navController.navigate("login") {
                    popUpTo(0)
                }
            }
        }
    }
    
    val startDestination = remember {
        if (authViewModel.isLoggedIn()) "profile" else "login"
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToSignup = { navController.navigate("signup") },
                    onLoginSuccess = { 
                        navController.navigate("profile") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("signup") {
                SignupScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = { navController.navigate("login") },
                    onSignupSuccess = {
                        navController.navigate("profile") {
                            popUpTo("signup") { inclusive = true }
                        }
                    }
                )
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = authViewModel,
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo("profile") { inclusive = true }
                        }
                    },
                    onNavigateToContacts = { navController.navigate("contacts") }
                )
            }
            composable("contacts") {
                ContactsScreen(
                    viewModel = contactsViewModel,
                    onNavigateToProfile = { navController.popBackStack() },
                    onCallContact = { userId ->
                        callViewModel.startCall(userId)
                        navController.navigate("call")
                    }
                )
            }
            composable("call") {
                CallScreen(
                    viewModel = callViewModel,
                    onCallEnded = { navController.popBackStack() }
                )
            }
        }
    }
}
