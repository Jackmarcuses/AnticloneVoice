package com.jackmarcus.anti_clonevoice.ui.call

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val remoteUserName by viewModel.remoteUserName.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val audioLevel by viewModel.remoteAudioLevel.collectAsState()
    val duration by viewModel.callDuration.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val identityMatchScore by viewModel.identityMatchScore.collectAsState()
    val acousticAuthScore by viewModel.acousticAuthScore.collectAsState()
    val behavioralRhythmScore by viewModel.behavioralRhythmScore.collectAsState()
    val detectionMessage by viewModel.detectionMessage.collectAsState()
    val threatLevel by viewModel.threatLevel.collectAsState()
    val isEnrolling by viewModel.isEnrolling.collectAsState()
    val vaultContacts by viewModel.vaultContacts.collectAsState()
    val targetCheckId by viewModel.targetCheckId.collectAsState()
    val securityChallenge by viewModel.securityChallenge.collectAsState()
    val isHardKillActive by viewModel.isHardKillActive.collectAsState()

    var showVaultDialog by remember { mutableStateOf(false) }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    LaunchedEffect(callState) {
        if (callState == CallState.ENDED || callState == CallState.FAILED) {
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F171E)) // Premium Dark Slate
    ) {
        when (callState) {
            CallState.RINGING -> {
                IncomingCallContent(
                    callerName = remoteUserName ?: "Unknown Caller",
                    onAccept = { viewModel.acceptCall() },
                    onDecline = { viewModel.rejectCall() }
                )
            }
            CallState.DIALING -> {
                OutgoingCallContent(
                    calleeName = remoteUserName ?: "Unknown Contact",
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onEndCall = { viewModel.endCall() }
                )
            }
            CallState.CONNECTING -> {
                ConnectingCallContent(
                    remoteName = remoteUserName ?: "Unknown Contact",
                    onEndCall = { viewModel.endCall() }
                )
            }
            CallState.CONNECTED -> {
                ActiveCallContent(
                    remoteUserName = remoteUserName,
                    durationText = formatDuration(duration),
                    riskScore = riskScore,
                    identityMatchScore = identityMatchScore,
                    acousticAuthScore = acousticAuthScore,
                    behavioralRhythmScore = behavioralRhythmScore,
                    detectionMessage = detectionMessage,
                    threatLevel = threatLevel,
                    isEnrolling = isEnrolling,
                    targetCheckId = targetCheckId,
                    securityChallenge = securityChallenge,
                    audioLevel = audioLevel,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    getDisplayName = { id -> viewModel.getDisplayName(id) },
                    onEnroll = { viewModel.enrollVoice() },
                    onCrossCheckClick = {
                        viewModel.fetchVault()
                        showVaultDialog = true
                    },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onEndCall = { viewModel.endCall() }
                )
            }
            else -> {}
        }

        if (showVaultDialog) {
            AlertDialog(
                onDismissRequest = { showVaultDialog = false },
                title = { Text("Vault Cross-Verification") },
                text = {
                    Column {
                        Text("Compare this voice DNA against a saved identity.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (vaultContacts.isEmpty()) {
                            Text("No fingerprints in vault.", color = Color.Gray)
                        } else {
                            vaultContacts.forEach { item ->
                                TextButton(
                                    onClick = {
                                        viewModel.performCrossCheck(item.id)
                                        showVaultDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(item.name, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        
                        if (targetCheckId != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.performCrossCheck(null)
                                    showVaultDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset to Caller Identity", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVaultDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        if (isHardKillActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.95f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.GppBad, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.White)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("SECURITY KILL-SWITCH", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Identity Mismatch Detected. Terminating.", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun IncomingCallContent(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "INCOMING VOICE CALL",
                color = Color(0xFF25D366),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = callerName,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Anti-Clone Voice Protected",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Pulsing Call Avatar Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Surface(
                modifier = Modifier
                    .size(130.dp * pulseScale)
                    .clip(CircleShape),
                color = Color(0xFF25D366).copy(alpha = 0.15f),
                border = BorderStroke(2.dp, Color(0xFF25D366).copy(alpha = 0.6f))
            ) {}
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                color = Color(0xFF1B2733)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Ringing Action Buttons (Decline / Accept)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEA0038))
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }

            // Accept Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onAccept,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366))
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Accept Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Answer", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun OutgoingCallContent(
    calleeName: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dial_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OUTGOING VOICE CALL",
                color = Color(0xFF25D366),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = calleeName,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Calling...",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            Surface(
                modifier = Modifier
                    .size(120.dp * pulseScale)
                    .clip(CircleShape),
                color = Color(0xFF2196F3).copy(alpha = 0.15f),
                border = BorderStroke(2.dp, Color(0xFF2196F3).copy(alpha = 0.6f))
            ) {}
            Surface(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape),
                color = Color(0xFF1B2733)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = Color(0xFF1B2733)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    backgroundColor = if (isMuted) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = Color.White,
                    onClick = onToggleMute
                )

                CallControlButton(
                    icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    backgroundColor = if (isSpeakerOn) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = Color.White,
                    onClick = onToggleSpeaker
                )

                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    backgroundColor = Color(0xFFEA0038),
                    contentColor = Color.White,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
private fun ConnectingCallContent(
    remoteName: String,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ESTABLISHING ENCRYPTED SESSION",
                color = Color(0xFF25D366),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = remoteName,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Connecting...",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        CircularProgressIndicator(
            color = Color(0xFF25D366),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = Color(0xFF1B2733)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    backgroundColor = Color(0xFFEA0038),
                    contentColor = Color.White,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
private fun ActiveCallContent(
    remoteUserName: String?,
    durationText: String,
    riskScore: Float,
    identityMatchScore: Float,
    acousticAuthScore: Float,
    behavioralRhythmScore: Float,
    detectionMessage: String,
    threatLevel: String,
    isEnrolling: Boolean,
    targetCheckId: String?,
    securityChallenge: String?,
    audioLevel: Float,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    getDisplayName: (String?) -> String,
    onEnroll: () -> Unit,
    onCrossCheckClick: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "VOICE INTEGRITY ACTIVE",
                color = Color(0xFF25D366),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = remoteUserName ?: "Unknown Caller",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        
        Text(
            text = durationText,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        SecurityShieldDashboard(
            score = riskScore, 
            identityMatchScore = identityMatchScore,
            acousticAuthScore = acousticAuthScore,
            behavioralRhythmScore = behavioralRhythmScore,
            message = detectionMessage, 
            level = threatLevel,
            isEnrolling = isEnrolling,
            targetCheckName = if (targetCheckId != null) getDisplayName(targetCheckId) else null,
            onEnroll = onEnroll,
            onCrossCheckClick = onCrossCheckClick
        )
        
        if (securityChallenge != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ChallengeCard(challenge = securityChallenge)
        }

        Spacer(modifier = Modifier.height(16.dp))
        VoiceVisualizer(level = audioLevel)

        Spacer(modifier = Modifier.weight(1f))

        // Controls Row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = Color(0xFF1B2733) 
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    backgroundColor = if (isMuted) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = Color.White,
                    onClick = onToggleMute
                )

                CallControlButton(
                    icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    backgroundColor = if (isSpeakerOn) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    contentColor = Color.White,
                    onClick = onToggleSpeaker
                )

                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    backgroundColor = Color(0xFFEA0038), 
                    contentColor = Color.White,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
fun SecurityShieldDashboard(
    score: Float, 
    identityMatchScore: Float,
    acousticAuthScore: Float,
    behavioralRhythmScore: Float,
    message: String, 
    level: String,
    isEnrolling: Boolean,
    targetCheckName: String?,
    onEnroll: () -> Unit,
    onCrossCheckClick: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when(level) {
            "CRITICAL" -> Color(0xFFEA0038)
            "SUSPICIOUS" -> Color(0xFFFFC107)
            else -> Color(0xFF25D366)
        }, label = "color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Surface(
                modifier = Modifier.size(80.dp * pulseScale).clip(CircleShape),
                color = statusColor.copy(alpha = 0.1f),
                border = BorderStroke(2.dp, statusColor)
            ) {}
            Icon(
                imageVector = if (level == "GENUINE") Icons.Default.Shield else Icons.Default.GppMaybe,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = statusColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        
        if (targetCheckName != null) {
            Text(text = "Verifying against: $targetCheckName", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        SecurityMetricRow("ACOUSTIC ANOMALY (SYNTHETIC)", acousticAuthScore, statusColor)
        SecurityMetricRow("IDENTITY MISMATCH (VOICE DNA)", identityMatchScore, statusColor)
        SecurityMetricRow("BEHAVIORAL ANOMALY (RHYTHM)", behavioralRhythmScore, statusColor)

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onEnroll,
                modifier = Modifier.weight(1f),
                enabled = !isEnrolling,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isEnrolling) "Saving..." else "Enroll DNA", fontSize = 12.sp)
            }

            Button(
                onClick = onCrossCheckClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cross-Check", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SecurityMetricRow(label: String, value: Float, color: Color) {
    val clampedValue = value.coerceIn(0f, 100f)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
            Text("${clampedValue.toInt()}%", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (clampedValue / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun ChallengeCard(challenge: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QuestionMark, contentDescription = null, tint = Color(0xFFFF9800))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("IDENTITY CHALLENGE RECOMMENDED", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF9800))
                Text("Ask: \"$challenge\"", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun VoiceVisualizer(level: Float) {
    val displayLevel = (level * 100f).coerceIn(10f, 150f)
    Row(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(15) { index ->
            val barHeight = displayLevel * (1f - (Math.abs(index - 7) / 10f))
            Box(modifier = Modifier.padding(horizontal = 2.dp).width(3.dp).height(barHeight.dp).clip(CircleShape).background(Color(0xFF25D366).copy(alpha = 0.7f)))
        }
    }
}

@Composable
fun CallControlButton(icon: ImageVector, backgroundColor: Color, contentColor: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp).clip(CircleShape).background(backgroundColor)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = contentColor)
    }
}
