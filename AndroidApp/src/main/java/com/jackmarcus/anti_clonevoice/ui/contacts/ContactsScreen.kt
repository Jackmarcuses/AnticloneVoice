package com.jackmarcus.anti_clonevoice.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jackmarcus.anti_clonevoice.data.remote.models.ContactResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onNavigateToProfile: () -> Unit,
    onCallContact: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var contactIdToAdd by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Contact")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onDelete = { viewModel.deleteContact(contact.userId) },
                            onCall = { onCallContact(contact.userId) }
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
fun ContactItem(
    contact: ContactResponse,
    onDelete: () -> Unit,
    onCall: () -> Unit
) {
    ListItem(
        headlineContent = { Text(contact.username) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 4.dp),
                ) {
                   // Status dot
                   Surface(
                       shape = MaterialTheme.shapes.small,
                       color = if (contact.isOnline) Color.Green else Color.Gray,
                       modifier = Modifier.fillMaxSize()
                   ) {}
                }
                Text(if (contact.isOnline) "Online" else "Offline")
            }
        },
        leadingContent = {
            Icon(Icons.Default.Person, contentDescription = null)
        },
        trailingContent = {
            Row {
                if (contact.isOnline) {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.Green)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    )
}
