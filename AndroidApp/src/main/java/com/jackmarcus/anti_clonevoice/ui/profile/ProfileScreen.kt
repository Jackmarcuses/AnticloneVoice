package com.jackmarcus.anti_clonevoice.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jackmarcus.anti_clonevoice.ui.auth.AuthViewModel

@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    
    var isEditing by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newAvatarUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.getProfile()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
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
                    }) {
                        Text("Logout")
                    }
                }
            }
            is AuthViewModel.ProfileState.Error -> {
                Text(
                    text = (profileState as AuthViewModel.ProfileState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { viewModel.getProfile() }) {
                    Text("Retry")
                }
            }
            else -> {}
        }
    }
}
