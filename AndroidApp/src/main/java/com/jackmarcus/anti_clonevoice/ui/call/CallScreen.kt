package com.jackmarcus.anti_clonevoice.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val remoteUserId by viewModel.remoteUserId.collectAsState()

    if (callState == CallState.ENDED) {
        onCallEnded()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Call with $remoteUserId",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = callState.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (callState == CallState.CONNECTED) Color.Green else Color.Gray
        )

        Spacer(modifier = Modifier.height(64.dp))

        Row {
            if (callState == CallState.RINGING) {
                FloatingActionButton(
                    onClick = { viewModel.acceptCall() },
                    containerColor = Color.Green,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Accept Call")
                }
                Spacer(modifier = Modifier.width(32.dp))
            }

            FloatingActionButton(
                onClick = { viewModel.endCall() },
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call")
            }
        }
    }
}
