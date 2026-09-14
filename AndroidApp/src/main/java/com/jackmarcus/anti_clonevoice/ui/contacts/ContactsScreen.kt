package com.jackmarcus.anti_clonevoice.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackmarcus.anti_clonevoice.data.remote.models.ContactResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onNavigateToProfile: () -> Unit,
    onCallContact: (String) -> Unit,
    onChatContact: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var contactIdToAdd by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Anti-Clone Voice", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Contact")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            if (uiState.isLoading && uiState.contacts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.contacts) { contact ->
                        WhatsAppContactItem(
                            contact = contact,
                            onClick = { onChatContact(contact.userId, contact.username) },
                            onCall = { onCallContact(contact.userId) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            thickness = 0.5.dp,
                            color = Color.LightGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Contact") },
                text = {
                    Column {
                        TextField(
                            value = contactIdToAdd,
                            onValueChange = { 
                                contactIdToAdd = it
                                viewModel.searchUsers(it)
                            },
                            placeholder = { Text("Search by username") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(uiState.searchResults) { result ->
                                ListItem(
                                    headlineContent = { Text(result.username) },
                                    trailingContent = {
                                        Button(onClick = {
                                            viewModel.addContact(result.userId)
                                            showAddDialog = false
                                            contactIdToAdd = ""
                                        }) {
                                            Text("Add")
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { 
                        showAddDialog = false
                        contactIdToAdd = ""
                        viewModel.searchUsers("")
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun WhatsAppContactItem(
    contact: ContactResponse,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(30.dp))
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF25D366)) // WhatsApp Green
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (contact.isOnline) "Online" else "Last seen recently",
                style = MaterialTheme.typography.bodySmall,
                color = if (contact.isOnline) Color(0xFF25D366) else Color.Gray
            )
        }

        IconButton(onClick = onCall) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call",
                tint = Color(0xFF25D366),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
