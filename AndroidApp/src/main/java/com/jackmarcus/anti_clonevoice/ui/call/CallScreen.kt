package com.jackmarcus.anti_clonevoice.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    val audioLevel by viewModel.remoteAudioLevel.collectAsState()

    // Handle call termination
    LaunchedEffect(callState) {
        if (callState == CallState.ENDED || callState == CallState.FAILED) {
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF075E54)) // WhatsApp Dark Green
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Anti-Clone Voice",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = remoteUserId ?: "Contact",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when(callState) {
                    CallState.DIALING -> "Calling..."
                    CallState.RINGING -> "Incoming Call..."
                    CallState.CONNECTED -> "00:00"
                    CallState.FAILED -> "Call Failed"
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp
            )

            if (callState == CallState.CONNECTED) {
                Spacer(modifier = Modifier.height(64.dp))
                VoiceVisualizer(level = audioLevel)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                color = Color(0xFF121B22) // WhatsApp Background Dark
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        backgroundColor = if (isMuted) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                        contentColor = Color.White,
                        onClick = { viewModel.toggleMute() }
                    )

                    if (callState == CallState.RINGING) {
                        CallControlButton(
                            icon = Icons.Default.Call,
                            backgroundColor = Color(0xFF25D366), // WhatsApp Light Green
                            contentColor = Color.White,
                            onClick = { viewModel.acceptCall() }
                        )
                    }

                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        backgroundColor = Color(0xFFEA0038), // WhatsApp Red
                        contentColor = Color.White,
                        onClick = { viewModel.endCall() }
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceVisualizer(level: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_anim")
    val animScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Current level combined with minor animation pulse. Level is typically 0.0 to 1.0
    val displayLevel = (level * 150f).coerceIn(10f, 250f) * animScale

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Draw 7 bars that dance according to the level
        repeat(7) { index ->
            val factor = when(index) {
                0, 6 -> 0.4f
                1, 5 -> 0.7f
                2, 4 -> 0.9f
                else -> 1.1f
            }
            val barHeight = displayLevel * factor
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(6.dp)
                    .height(barHeight.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366)) // WhatsApp Green
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
