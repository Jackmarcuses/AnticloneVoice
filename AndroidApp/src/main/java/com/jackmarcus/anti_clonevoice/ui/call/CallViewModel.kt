package com.jackmarcus.anti_clonevoice.ui.call

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.SignalingMessage
import com.jackmarcus.anti_clonevoice.data.repository.ContactsRepository
import com.jackmarcus.anti_clonevoice.data.repository.TranscriptRepository
import com.jackmarcus.anti_clonevoice.webrtc.CallService
import com.jackmarcus.anti_clonevoice.webrtc.SignalingClient
import com.jackmarcus.anti_clonevoice.webrtc.WebRtcClient
import com.jackmarcus.anti_clonevoice.webrtc.CloudInferenceClient
import com.jackmarcus.anti_clonevoice.webrtc.VaultItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

enum class CallState {
    IDLE, DIALING, RINGING, CONNECTING, CONNECTED, ENDED, FAILED
}

class CallViewModel(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val secureStorage: SecureStorage,
    private val contactsRepository: ContactsRepository,
    private val transcriptRepository: TranscriptRepository
) : ViewModel(), WebRtcClient.WebRtcObserver {

    private val TAG = "CallViewModel"
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _remoteUserId = MutableStateFlow<String?>(null)
    val remoteUserId: StateFlow<String?> = _remoteUserId.asStateFlow()

    private val _remoteUserName = MutableStateFlow<String?>("Unknown")
    val remoteUserName: StateFlow<String?> = _remoteUserName.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel = _remoteAudioLevel.asStateFlow()

    private val _riskScore = MutableStateFlow(0f)
    val riskScore = _riskScore.asStateFlow()

    private val _identityMatchScore = MutableStateFlow(0f)
    val identityMatchScore = _identityMatchScore.asStateFlow()

    private val _acousticAuthScore = MutableStateFlow(0f)
    val acousticAuthScore = _acousticAuthScore.asStateFlow()

    private val _behavioralRhythmScore = MutableStateFlow(0f)
    val behavioralRhythmScore = _behavioralRhythmScore.asStateFlow()

    private val _detectionMessage = MutableStateFlow("Verifying voice identity...")
    val detectionMessage = _detectionMessage.asStateFlow()

    private val _threatLevel = MutableStateFlow("GENUINE")
    val threatLevel = _threatLevel.asStateFlow()

    private val _liveTranscript = MutableStateFlow("TRANSCRIPTS DISABLED")
    val liveTranscript = _liveTranscript.asStateFlow()

    private val _securityChallenge = MutableStateFlow<String?>(null)
    val securityChallenge = _securityChallenge.asStateFlow()

    private val _isHardKillActive = MutableStateFlow(false)
    val isHardKillActive = _isHardKillActive.asStateFlow()

    private val _isEnrolling = MutableStateFlow(false)
    val isEnrolling = _isEnrolling.asStateFlow()

    private val _vaultContacts = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultContacts = _vaultContacts.asStateFlow()

    private val _targetCheckId = MutableStateFlow<String?>(null)
    val targetCheckId = _targetCheckId.asStateFlow()

    private val _contactMap = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _detectedLanguage = MutableStateFlow("Pure Integrity Mode")
    val detectedLanguage = _detectedLanguage.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration = _callDuration.asStateFlow()

    private val callAudioManager = CallAudioManager(context)
    private var timerJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var webRtcClient: WebRtcClient? = null
    private val cloudClient = CloudInferenceClient("")
    private val myUserId: String get() = secureStorage.getUserId() ?: ""
    private var isCaller = false
    private var retryCount = 0
    private val MAX_RETRIES = 2
    
    private var criticalRiskCounter = 0
    private val HARD_KILL_THRESHOLD = 20 // Approx 10 seconds (20 * 500ms)
    
    private var challengeLatchJob: Job? = null
    
    private val challenges = listOf(
        "What was the last thing we ate together?",
        "Who else was with us yesterday?",
        "What color shirt am I wearing right now?",
        "Where did we go on our last vacation?",
        "What is the name of our favorite restaurant?"
    )

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    init {
        viewModelScope.launch {
            signalingClient.signalingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
        viewModelScope.launch {
            contactsRepository.getContacts().onSuccess { contacts ->
                _contactMap.value = contacts.associate { it.userId to it.username }
            }
        }
        connectSignaling()
    }

    fun getDisplayName(userId: String?): String {
        if (userId == null) return "Unknown"
        return _contactMap.value[userId] ?: (if (userId.length > 8) userId.take(8) + "..." else userId)
    }

    fun connectSignaling() {
        val userId = secureStorage.getUserId() ?: return
        signalingClient.connect(userId)
    }

    private fun resetCallState() {
        stopCallService()
        callAudioManager.stopAll()
        stopTimer()
        _targetCheckId.value = null
        _riskScore.value = 0f
        _identityMatchScore.value = 0f
        _acousticAuthScore.value = 0f
        _behavioralRhythmScore.value = 0f
        _threatLevel.value = "GENUINE"
        _detectionMessage.value = "Verifying voice identity..."
        _isHardKillActive.value = false
        criticalRiskCounter = 0
        isCaller = false
        _remoteUserId.value = null
        isRemoteDescriptionSet = false
        pendingCandidates.clear()
        webRtcClient?.resetDetectionEngine()
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        val currentUserId = myUserId
        if (message.senderId == currentUserId || message.senderId.isEmpty()) return

        when (message.type) {
            "offer" -> {
                if (_callState.value == CallState.IDLE || 
                    _callState.value == CallState.RINGING || 
                    _callState.value == CallState.DIALING ||
                    _callState.value == CallState.CONNECTING ||
                    _callState.value == CallState.CONNECTED) {
                    
                    _remoteUserId.value = message.senderId
                    _remoteUserName.value = getDisplayName(message.senderId)
                    if (_callState.value != CallState.CONNECTED) {
                        _callState.value = CallState.CONNECTING
                        callAudioManager.stopAll()
                    }
                    
                    if (webRtcClient == null) {
                        webRtcClient = WebRtcClient(context, this, transcriptRepository)
                    }
                    fetchRemoteProfile(message.senderId)
                    webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.OFFER, message.data))
                }
            }
            "answer" -> {
                if (_callState.value == CallState.CONNECTING || _callState.value == CallState.DIALING || _callState.value == CallState.CONNECTED) {
                    if (_callState.value != CallState.CONNECTED) {
                        callAudioManager.stopAll()
                        _callState.value = CallState.CONNECTING
                    }
                    val remoteId = _remoteUserId.value
                    if (remoteId != null) fetchRemoteProfile(remoteId)
                    webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.ANSWER, message.data))
                }
            }
            "candidate" -> {
                try {
                    val candidateData = message.data?.split("|") ?: return
                    if (candidateData.size >= 3) {
                        val sdp = candidateData.drop(2).joinToString("|")
                        val candidate = IceCandidate(candidateData[0], candidateData[1].toInt(), sdp)
                        if (isRemoteDescriptionSet && webRtcClient != null) {
                            webRtcClient?.addIceCandidate(candidate)
                        } else {
                            pendingCandidates.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error candidate: ${e.message}")
                }
            }
            "call_request" -> {
                 val currentState = _callState.value
                 val isAlreadyInCall = currentState != CallState.IDLE && 
                                     currentState != CallState.ENDED && 
                                     currentState != CallState.FAILED
                                     
                 if (isCaller || isAlreadyInCall) return
                 
                 resetCallState()
                 _remoteUserId.value = message.senderId
                 val callerName = getDisplayName(message.senderId)
                 _remoteUserName.value = callerName
                 _callState.value = CallState.RINGING
                 callAudioManager.startRinging()
                 startCallService(CallService.ACTION_START_INCOMING, callerName)
            }
            "call_response" -> {
                if (message.data == "accepted") {
                    callAudioManager.stopAll()
                    _callState.value = CallState.CONNECTING 
                    val name = _remoteUserName.value ?: getDisplayName(_remoteUserId.value)
                    startCallService(CallService.ACTION_START_ONGOING, name)
                    if (webRtcClient == null) {
                        webRtcClient = WebRtcClient(context, this, transcriptRepository)
                    }
                    _remoteUserId.value?.let { fetchRemoteProfile(it) }
                    webRtcClient?.startCall()
                } else {
                    resetCallState()
                    _callState.value = CallState.ENDED
                }
            }
            "end_call" -> {
                resetCallState()
                webRtcClient?.close()
                webRtcClient = null
                _callState.value = CallState.ENDED
            }
            "ice_restart" -> {
                webRtcClient?.restartIce()
            }
        }
    }

    fun startCall(receiverId: String) {
        resetCallState()
        isCaller = true
        _remoteUserId.value = receiverId
        val calleeName = getDisplayName(receiverId)
        _remoteUserName.value = calleeName
        _callState.value = CallState.DIALING
        callAudioManager.startDialing()
        startCallService(CallService.ACTION_START_OUTGOING, calleeName)
        signalingClient.sendMessage(SignalingMessage("call_request", myUserId, receiverId))
        webRtcClient = WebRtcClient(context, this, transcriptRepository)
        fetchRemoteProfile(receiverId)
        startConnectionTimeout()
    }

    private fun fetchRemoteProfile(userId: String) {
        viewModelScope.launch {
            val contacts = contactsRepository.getContacts().getOrNull()
            val contact = contacts?.find { it.userId == userId }
            val displayName = contact?.username ?: getDisplayName(userId)
            _remoteUserName.value = displayName
            
            if (contact != null && contact.voiceEmbedding != null) {
                val embedding = try {
                    contact.voiceEmbedding.split(",").map { it.toFloat() }.toFloatArray()
                } catch (e: Exception) { null }
                
                webRtcClient?.setRemoteVoiceProfile(embedding, contact.baselineSpeechRate, contact.pitchVariance, userId, displayName, myUserId)
                Log.i(TAG, "Loaded voice profile for $userId ($displayName)")
            } else {
                webRtcClient?.setRemoteVoiceProfile(null, 0f, 0f, userId, displayName, myUserId)
            }
        }
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(20000) 
            if (_callState.value == CallState.CONNECTING || _callState.value == CallState.DIALING) {
                resetCallState()
                _callState.value = CallState.FAILED
            }
        }
    }

    fun acceptCall() {
        isCaller = false
        callAudioManager.stopAll()
        val receiverId = _remoteUserId.value ?: return
        val name = _remoteUserName.value ?: getDisplayName(receiverId)
        _callState.value = CallState.CONNECTING 
        startCallService(CallService.ACTION_START_ONGOING, name)
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "accepted"))
        startConnectionTimeout()
    }

    fun rejectCall() {
        val receiverId = _remoteUserId.value ?: return
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "rejected"))
        resetCallState()
        _callState.value = CallState.ENDED
    }

    fun endCall() {
        val receiverId = _remoteUserId.value
        if (receiverId != null) {
            signalingClient.sendMessage(SignalingMessage("end_call", myUserId, receiverId))
        }
        webRtcClient?.close()
        webRtcClient = null
        resetCallState()
        _callState.value = CallState.ENDED
    }

    private fun startTimer() {
        stopTimer()
        _callDuration.value = 0
        timerJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(1000)
                    _callDuration.value += 1
                }
            } catch (e: Exception) {}
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        challengeLatchJob?.cancel()
        challengeLatchJob = null
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        webRtcClient?.setMute(muted)
    }

    fun toggleSpeaker() {
        val speakerOn = !_isSpeakerOn.value
        _isSpeakerOn.value = speakerOn
        callAudioManager.setSpeakerphoneOn(speakerOn)
    }

    fun enrollVoice() {
        if (_isEnrolling.value) return
        
        // Refresh name right before enrollment to prevent "Unknown Caller" bug
        val userId = _remoteUserId.value ?: return
        val currentName = getDisplayName(userId)
        _remoteUserName.value = currentName
        
        _isEnrolling.value = true
        webRtcClient?.enrollVoice()
        viewModelScope.launch {
            delay(3000) // Visual feedback for 3 seconds
            _isEnrolling.value = false
        }
    }

    fun fetchVault() {
        viewModelScope.launch {
            val list = cloudClient.getVaultList(myUserId)
            _vaultContacts.value = list
        }
    }

    fun performCrossCheck(targetUserId: String?) {
        _targetCheckId.value = targetUserId
        
        // Reset scores immediately to clean defaults
        _riskScore.value = 0f
        _identityMatchScore.value = 0f
        _acousticAuthScore.value = 0f
        _behavioralRhythmScore.value = 0f
        _threatLevel.value = "GENUINE"
        
        if (targetUserId != null) {
            val item = _vaultContacts.value.find { it.id == targetUserId }
            val name = item?.name ?: getDisplayName(targetUserId)
            _detectionMessage.value = "Cross-checking against $name..."
            
            val targetEmbedding = item?.embedding?.let { str ->
                try {
                    str.split(",").map { it.toFloat() }.toFloatArray()
                } catch (e: Exception) { null }
            }
            webRtcClient?.setCheckAgainstUserId(targetUserId, targetEmbedding)
        } else {
            _detectionMessage.value = "Verifying voice identity..."
            val remoteId = _remoteUserId.value
            webRtcClient?.setCheckAgainstUserId(null, null)
            if (remoteId != null) {
                fetchRemoteProfile(remoteId)
            }
        }
    }

    fun simulateHindiScam() {
        // Feature disabled in Pure Integrity Mode
    }

    private fun startCallService(action: String, name: String) {
        try {
            val intent = Intent(context, CallService::class.java).apply {
                this.action = action
                putExtra(CallService.EXTRA_CALLER_NAME, name)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call service ($action): ${e.message}")
        }
    }

    private fun stopCallService() {
        try {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_STOP
            }
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call service: ${e.message}")
        }
    }

    override fun onCleared() {
        resetCallState()
        webRtcClient?.close()
        webRtcClient = null
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        val receiverId = _remoteUserId.value ?: return
        val data = "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}"
        signalingClient.sendMessage(SignalingMessage("candidate", myUserId, receiverId, data))
    }

    override fun onLocalSdpCreated(sessionDescription: SessionDescription) {
        val receiverId = _remoteUserId.value ?: return
        val type = sessionDescription.type.canonicalForm()
        signalingClient.sendMessage(SignalingMessage(type, myUserId, receiverId, sessionDescription.description))
    }

    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        viewModelScope.launch {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    connectionTimeoutJob?.cancel()
                    callAudioManager.stopAll()
                    callAudioManager.setCommunicationMode()
                    _callState.value = CallState.CONNECTED
                    val name = _remoteUserName.value ?: getDisplayName(_remoteUserId.value)
                    startCallService(CallService.ACTION_START_ONGOING, name)
                    startTimer()
                    retryCount = 0
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        signalingClient.sendMessage(SignalingMessage("ice_restart", myUserId, _remoteUserId.value ?: ""))
                        webRtcClient?.restartIce()
                    } else {
                        stopTimer()
                        resetCallState()
                        _callState.value = CallState.FAILED
                    }
                }
                else -> {}
            }
        }
    }

    override fun onRemoteAudioLevel(level: Double) {
        _remoteAudioLevel.value = level.toFloat()
    }

    override fun onRemoteDescriptionSet() {
        isRemoteDescriptionSet = true
        pendingCandidates.forEach { candidate ->
            webRtcClient?.addIceCandidate(candidate)
        }
        pendingCandidates.clear()
    }

    override fun onDetectionResult(
        riskScore: Float, 
        message: String, 
        level: String, 
        recommendChallenge: Boolean,
        identityMatch: Float,
        acousticAuth: Float,
        behavioralMatch: Float
    ) {
        _riskScore.value = riskScore.coerceIn(0f, 100f)
        _detectionMessage.value = message
        _threatLevel.value = level
        _identityMatchScore.value = identityMatch.coerceIn(0f, 100f)
        _acousticAuthScore.value = acousticAuth.coerceIn(0f, 100f)
        _behavioralRhythmScore.value = behavioralMatch.coerceIn(0f, 100f)
        
        // 1. Proof of Life Logic with 10s Latching
        if (recommendChallenge && challengeLatchJob == null) {
            _securityChallenge.value = challenges.random()
            // Latch the challenge for 10 seconds
            challengeLatchJob = viewModelScope.launch {
                delay(10000)
                _securityChallenge.value = null
                challengeLatchJob = null
            }
        }
        
        // 2. Hard Kill Logic
        if (riskScore >= 95f) {
            criticalRiskCounter++
            if (criticalRiskCounter >= HARD_KILL_THRESHOLD && !_isHardKillActive.value) {
                _isHardKillActive.value = true
                Log.e(TAG, "HARD KILL TRIGGERED: Sustained critical risk detected.")
                viewModelScope.launch {
                    delay(3000) // Show alert for 3 seconds before hanging up
                    endCall()
                }
            }
        } else {
            criticalRiskCounter = 0
        }
    }

    override fun onTranscriptUpdated(transcript: String) {}
    override fun onLanguageDetected(language: String) {}
}
