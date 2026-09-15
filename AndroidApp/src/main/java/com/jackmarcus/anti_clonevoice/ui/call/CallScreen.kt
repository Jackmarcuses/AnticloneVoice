package com.jackmarcus.anti_clonevoice.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val remoteUserId by viewModel.remoteUserId.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val audioLevel by viewModel.remoteAudioLevel.collectAsState()
    val duration by viewModel.callDuration.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val detectionMessage by viewModel.detectionMessage.collectAsState()
    val threatLevel by viewModel.threatLevel.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val detectedLanguage by viewModel.detectedLanguage.collectAsState()

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

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
                    CallState.CONNECTING -> "Connecting..."
                    CallState.CONNECTED -> formatDuration(duration)
                    CallState.FAILED -> "Call Failed"
                    CallState.ENDED -> "Call Ended"
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp
            )

            if (callState == CallState.CONNECTED) {
                Spacer(modifier = Modifier.height(32.dp))
                RiskDashboard(score = riskScore, message = detectionMessage, level = threatLevel)
                
                if (liveTranscript.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TranscriptView(transcript = liveTranscript, language = detectedLanguage)
                } else {
                    // Show a small test button if no transcript is present
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.simulateHindiScam() }) {
                        Text("Test Hindi Scam Detection", color = Color.White.copy(alpha = 0.3f))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
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

                    CallControlButton(
                        icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        backgroundColor = if (isSpeakerOn) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                        contentColor = Color.White,
                        onClick = { viewModel.toggleSpeaker() }
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
fun RiskDashboard(score: Float, message: String, level: String) {
    val color = when(level) {
        "CRITICAL" -> Color(0xFFEA0038)
        "SUSPICIOUS" -> Color(0xFFFFC107)
        else -> Color(0xFF25D366)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Trust Risk Score: ${String.format(Locale.US, "%.1f", score)}%",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun TranscriptView(transcript: String, language: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LIVE TRANSCRIPT",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "LANG: ${language.uppercase()}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\"$transcript\"",
            color = Color.White,
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic
        )
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
