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
import com.jackmarcus.anti_clonevoice.data.repository.ChatRepository
import com.jackmarcus.anti_clonevoice.data.repository.ContactsRepository
import com.jackmarcus.anti_clonevoice.ui.auth.AuthViewModel
import com.jackmarcus.anti_clonevoice.ui.auth.LoginScreen
import com.jackmarcus.anti_clonevoice.ui.auth.SignupScreen
import com.jackmarcus.anti_clonevoice.ui.call.CallScreen
import com.jackmarcus.anti_clonevoice.ui.call.CallState
import com.jackmarcus.anti_clonevoice.ui.call.CallViewModel
import com.jackmarcus.anti_clonevoice.ui.chat.ChatScreen
import com.jackmarcus.anti_clonevoice.ui.chat.ChatViewModel
import com.jackmarcus.anti_clonevoice.ui.contacts.ContactsScreen
import com.jackmarcus.anti_clonevoice.ui.contacts.ContactsViewModel
import com.jackmarcus.anti_clonevoice.ui.profile.ProfileScreen
import com.jackmarcus.anti_clonevoice.ui.theme.AnticloneVoiceTheme
import com.jackmarcus.anti_clonevoice.webrtc.SignalingClient
import okhttp3.OkHttpClient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

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
        
        val chatRepository = ChatRepository(NetworkClient.chatService, secureStorage, okHttpClient)
        val chatViewModel = ChatViewModel(chatRepository, secureStorage)

        setContent {
            AnticloneVoiceTheme {
                MainApp(authViewModel, contactsViewModel, callViewModel, chatViewModel)
            }
        }
    }
}

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    contactsViewModel: ContactsViewModel,
    callViewModel: CallViewModel,
    chatViewModel: ChatViewModel
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (authState !is AuthViewModel.AuthState.Idle && authViewModel.isLoggedIn()) {
                NavigationBar {
                    val items = listOf(
                        Triple("contacts", "Contacts", Icons.Default.Person),
                        Triple("chats_list", "Chats", Icons.Default.Email),
                        Triple("profile", "Profile", Icons.Default.AccountCircle)
                    )
                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
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
                    onNavigateToProfile = { navController.navigate("profile") },
                    onCallContact = { userId ->
                        callViewModel.startCall(userId)
                        navController.navigate("call")
                    },
                    onChatContact = { userId, username ->
                        navController.navigate("chat/$userId/$username")
                    }
                )
            }
            composable("chats_list") {
                // Reusing ContactsScreen as a way to start chats for now
                ContactsScreen(
                    viewModel = contactsViewModel,
                    onNavigateToProfile = { navController.navigate("profile") },
                    onCallContact = { userId ->
                        callViewModel.startCall(userId)
                        navController.navigate("call")
                    },
                    onChatContact = { userId, username ->
                        navController.navigate("chat/$userId/$username")
                    }
                )
            }
            composable("chat/{contactId}/{contactName}") { backStackEntry ->
                val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
                val contactName = backStackEntry.arguments?.getString("contactName") ?: ""
                ChatScreen(
                    viewModel = chatViewModel,
                    contactId = contactId,
                    contactName = contactName,
                    onBack = { navController.popBackStack() }
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
