package com.jackmarcus.anti_clonevoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    // Trigger recomposition if auth state changes
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    
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
