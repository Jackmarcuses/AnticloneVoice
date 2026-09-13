package com.jackmarcus.anti_clonevoice.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val remoteUserId by viewModel.remoteUserId.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    // Handle call termination
    if (callState == CallState.ENDED || callState == CallState.FAILED) {
        LaunchedEffect(Unit) {
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C1C1C), Color(0xFF0A0A0A))
                )
            )
    ) {
        // Top Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.LightGray
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = remoteUserId ?: "Unknown",
                color = Color.White,
                fontSize = 28.sp,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when(callState) {
                    CallState.DIALING -> "Calling..."
                    CallState.RINGING -> "Incoming Call..."
                    CallState.CONNECTED -> "00:00" // Time logic can be added
                    CallState.FAILED -> "Call Failed"
                    else -> ""
                },
                color = if (callState == CallState.CONNECTED) Color.Green else Color.Gray,
                fontSize = 18.sp
            )
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                backgroundColor = if (isMuted) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                contentColor = Color.White,
                onClick = { viewModel.toggleMute() }
            )

            if (callState == CallState.RINGING) {
                CallControlButton(
                    icon = Icons.Default.Call,
                    backgroundColor = Color.Green,
                    contentColor = Color.White,
                    onClick = { viewModel.acceptCall() }
                )
            }

            CallControlButton(
                icon = Icons.Default.CallEnd,
                backgroundColor = Color.Red,
                contentColor = Color.White,
                onClick = { viewModel.endCall() }
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = contentColor
        )
    }
}
