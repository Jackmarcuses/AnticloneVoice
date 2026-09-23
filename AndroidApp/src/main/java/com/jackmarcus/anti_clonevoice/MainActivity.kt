package com.jackmarcus.anti_clonevoice

import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager
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
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.NetworkClient
import com.jackmarcus.anti_clonevoice.data.remote.PresenceManager
import com.jackmarcus.anti_clonevoice.data.repository.AuthRepository
import com.jackmarcus.anti_clonevoice.data.repository.ChatRepository
import com.jackmarcus.anti_clonevoice.data.repository.ContactsRepository
import com.jackmarcus.anti_clonevoice.data.repository.TranscriptRepository
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
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

import android.provider.Settings
import android.net.Uri
import android.content.Intent

import android.util.Log

object CallViewModelHolder {
    private var instance: CallViewModel? = null

    fun getInstance(
        context: Context,
        signalingClient: SignalingClient,
        secureStorage: SecureStorage,
        contactsRepository: ContactsRepository,
        transcriptRepository: TranscriptRepository
    ): CallViewModel {
        if (instance == null) {
            instance = CallViewModel(
                context.applicationContext,
                signalingClient,
                secureStorage,
                contactsRepository,
                transcriptRepository
            )
        }
        return instance!!
    }
}

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var callViewModel: CallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lockscreen and turn screen on for incoming calls (like WhatsApp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Add Global Crash Handler to help debug POCO crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRITICAL_CRASH", "Crash in thread ${thread.name}: ${throwable.message}")
            throwable.printStackTrace()
            // You can also Toast here if you want to see it on screen
        }

        enableEdgeToEdge()

        // Check for "Display over other apps" permission (Required for Xiaomi/POCO bg calls)
        if (!Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Please enable 'Display over other apps' for Anti-Clone Voice", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open overlay settings: ${e.message}")
            }
        }

        // Manual Dependency Injection with Error Handling
        val secureStorage = SecureStorage(applicationContext)
        NetworkClient.init(secureStorage)
        
        // Initialize Config from storage
        Config.isProduction = secureStorage.isProductionMode()
        Config.localIp = secureStorage.getLocalIp()
        
        val authRepository = AuthRepository(secureStorage)
        val authViewModel = AuthViewModel(authRepository)
        
        // Create a singular shared OkHttpClient configuration that implements keep-alives and timeouts
        val sharedClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS) // Active ping keeps pipes open on Render free instances
            .build()
            
        val contactsRepository = ContactsRepository(secureStorage)
        val presenceManager = PresenceManager(secureStorage, sharedClient)
        val contactsViewModel = ContactsViewModel(contactsRepository, presenceManager)
        
        val signalingClient = SignalingClient(sharedClient)
        val transcriptRepository = TranscriptRepository()
        callViewModel = CallViewModelHolder.getInstance(
            applicationContext,
            signalingClient,
            secureStorage,
            contactsRepository,
            transcriptRepository
        )
        
        val chatRepository = ChatRepository(secureStorage, sharedClient)
        val chatViewModel = ChatViewModel(chatRepository, secureStorage)

        handleIntent(intent, callViewModel)

        setContent {
            AnticloneVoiceTheme {
                MainApp(authViewModel, contactsViewModel, callViewModel, chatViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::callViewModel.isInitialized) {
            handleIntent(intent, callViewModel)
        }
    }

    private fun handleIntent(intent: Intent?, viewModel: CallViewModel) {
        when (intent?.action) {
            "ANSWER_CALL" -> {
                Log.i(TAG, "Answer call action triggered from notification")
                viewModel.acceptCall()
            }
            "DECLINE_CALL" -> {
                Log.i(TAG, "Decline call action triggered from notification")
                viewModel.rejectCall()
            }
            "END_CALL" -> {
                Log.i(TAG, "End call action triggered from notification")
                viewModel.endCall()
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
        if (callState == CallState.RINGING || callState == CallState.DIALING || callState == CallState.CONNECTING) {
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
        } else if (authViewModel.isLoggedIn()) {
            // Ensure signaling is connected whenever user is logged in
            callViewModel.connectSignaling()
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
