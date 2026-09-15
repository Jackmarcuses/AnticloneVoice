package com.jackmarcus.anti_clonevoice.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.ui.auth.AuthViewModel
import com.jackmarcus.anti_clonevoice.ui.common.BackendSettingsDialog

@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    
    var isEditing by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newAvatarUrl by remember { mutableStateOf("") }
    var showBackendSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.getProfile()
    }

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
            Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showBackendSettings = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Backend Settings"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (profileState) {
            is AuthViewModel.ProfileState.Loading -> CircularProgressIndicator()
            is AuthViewModel.ProfileState.Success -> {
                val profile = (profileState as AuthViewModel.ProfileState.Success).profile
                
                if (isEditing) {
                    TextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newAvatarUrl,
                        onValueChange = { newAvatarUrl = it },
                        label = { Text("Avatar URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = {
                            viewModel.updateProfile(newUsername, newAvatarUrl)
                            isEditing = false
                        }) {
                            Text("Save")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { isEditing = false }) {
                            Text("Cancel")
                        }
                    }
                } else {
                    Text(text = "Username: ${profile.username}")
                    Text(text = "Email: ${profile.email}")
                    if (profile.avatarUrl.isNotEmpty()) {
                        Text(text = "Avatar: ${profile.avatarUrl}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = {
                        newUsername = profile.username
                        newAvatarUrl = profile.avatarUrl
                        isEditing = true
                    }) {
                        Text("Edit Profile")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(onClick = onNavigateToContacts) {
                        Text("Go to Contacts")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(onClick = {
                        viewModel.logout()
                        onLogout()
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Logout")
                    }
                }
            }
            is AuthViewModel.ProfileState.Error -> {
                Text(
                    text = (profileState as AuthViewModel.ProfileState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.getProfile() }) {
                    Text("Retry")
                }
            }
            else -> {}
        }
    }
}
