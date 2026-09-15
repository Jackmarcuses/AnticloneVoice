package com.jackmarcus.anti_clonevoice.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jackmarcus.anti_clonevoice.data.Config
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage

@Composable
fun BackendSettingsDialog(
    secureStorage: SecureStorage,
    onDismiss: () -> Unit
) {
    var isProduction by remember { mutableStateOf(Config.isProduction) }
    var localIp by remember { mutableStateOf(Config.localIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isProduction, onCheckedChange = { isProduction = it })
                    Text("Production (Render.com)")
                }
                if (!isProduction) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = localIp,
                        onValueChange = { localIp = it },
                        label = { Text("Local IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Config.isProduction = isProduction
                Config.localIp = localIp
                secureStorage.saveProductionMode(isProduction)
                secureStorage.saveLocalIp(localIp)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
