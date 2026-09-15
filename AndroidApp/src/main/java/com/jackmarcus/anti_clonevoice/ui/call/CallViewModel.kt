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
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel = _remoteAudioLevel.asStateFlow()

    private val _riskScore = MutableStateFlow(0f)
    val riskScore = _riskScore.asStateFlow()

    private val _detectionMessage = MutableStateFlow("Analyzing voice...")
    val detectionMessage = _detectionMessage.asStateFlow()

    private val _threatLevel = MutableStateFlow("GENUINE")
    val threatLevel = _threatLevel.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript = _liveTranscript.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("detecting...")
    val detectedLanguage = _detectedLanguage.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration = _callDuration.asStateFlow()

    private val callAudioManager = CallAudioManager(context)
    private var timerJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var webRtcClient: WebRtcClient? = null
    private val myUserId: String get() = secureStorage.getUserId() ?: ""
    private var isCaller = false
    private var retryCount = 0
    private val MAX_RETRIES = 2
    
    // Pending candidates that arrived before the peer connection was ready
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    init {
        viewModelScope.launch {
            signalingClient.signalingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
        connectSignaling()
    }

    fun connectSignaling() {
        val userId = secureStorage.getUserId() ?: return
        signalingClient.connect(userId)
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        val currentUserId = myUserId
        Log.i(TAG, "New Signaling Message: ${message.type} from ${message.senderId} (Me: $currentUserId)")
        
        // Safety check: Don't process our own messages if they somehow loop back
        if (message.senderId == currentUserId || message.senderId.isEmpty()) {
            Log.d(TAG, "Dropping self-loop or invalid message")
            return
        }

        when (message.type) {
            "offer" -> {
                Log.d(TAG, "Handling 'offer'")
                if (_callState.value == CallState.IDLE || 
                    _callState.value == CallState.RINGING || 
                    _callState.value == CallState.DIALING ||
                    _callState.value == CallState.CONNECTING ||
                    _callState.value == CallState.CONNECTED) {
                    
                    _remoteUserId.value = message.senderId
                    if (_callState.value != CallState.CONNECTED) {
                        _callState.value = CallState.CONNECTING
                        callAudioManager.stopAll()
                    } else {
                        Log.i(TAG, "Receiving renegotiation/ICE restart offer while CONNECTED")
                    }
                    
                    if (webRtcClient == null) {
                        webRtcClient = WebRtcClient(context, this, transcriptRepository)
                    }
                    fetchRemoteProfile(message.senderId)
                    webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.OFFER, message.data))
                }
            }
            "answer" -> {
                Log.d(TAG, "Handling 'answer'")
                if (_callState.value == CallState.CONNECTING || _callState.value == CallState.DIALING || _callState.value == CallState.CONNECTED) {
                    if (_callState.value != CallState.CONNECTED) {
                        callAudioManager.stopAll()
                        _callState.value = CallState.CONNECTING
                    } else {
                        Log.i(TAG, "Receiving ICE restart answer while CONNECTED")
                    }
                    val remoteId = _remoteUserId.value
                    if (remoteId != null) fetchRemoteProfile(remoteId)
                    webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.ANSWER, message.data))
                }
            }
            "candidate" -> {
                try {
                    Log.d(TAG, "Handling 'candidate' from ${message.senderId}")
                    val candidateData = message.data?.split("|") ?: return
                    if (candidateData.size >= 3) {
                        val sdp = candidateData.drop(2).joinToString("|")
                        val candidate = IceCandidate(candidateData[0], candidateData[1].toInt(), sdp)
                        if (isRemoteDescriptionSet && webRtcClient != null) {
                            webRtcClient?.addIceCandidate(candidate)
                        } else {
                            Log.d(TAG, "Buffering candidate from ${message.senderId} (Remote description not yet set)")
                            pendingCandidates.add(candidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling candidate reception: ${e.message}")
                }
            }
            "call_request" -> {
                 // Loopback Fix: Strictly ignore call_request if we are already in an active call session
                 val currentState = _callState.value
                 val isAlreadyInCall = currentState != CallState.IDLE && 
                                     currentState != CallState.ENDED && 
                                     currentState != CallState.FAILED
                                     
                 if (isCaller || isAlreadyInCall) {
                     Log.d(TAG, "Ignoring 'call_request' as we are busy (State: $currentState, isCaller: $isCaller)")
                     return
                 }
                 
                 Log.i(TAG, "Received 'call_request' from ${message.senderId}")
                 isRemoteDescriptionSet = false
                 pendingCandidates.clear()
                 _remoteUserId.value = message.senderId
                 _callState.value = CallState.RINGING
                 callAudioManager.startRinging()
                 startCallService()
            }
            "call_response" -> {
                if (message.data == "accepted") {
                    Log.d(TAG, "Call accepted by remote")
                    callAudioManager.stopAll()
                    _callState.value = CallState.CONNECTING // Transition from "Calling..." to "Connecting..."
                    if (webRtcClient == null) {
                        webRtcClient = WebRtcClient(context, this, transcriptRepository)
                    }
                    _remoteUserId.value?.let { fetchRemoteProfile(it) }
                    webRtcClient?.startCall()
                } else {
                    Log.d(TAG, "Call rejected by remote")
                    stopCallService()
                    callAudioManager.stopAll()
                    _callState.value = CallState.ENDED
                    isCaller = false
                    _remoteUserId.value = null
                }
            }
            "end_call" -> {
                Log.d(TAG, "Call ended by remote")
                stopCallService()
                callAudioManager.stopAll()
                webRtcClient?.close()
                _callState.value = CallState.ENDED
                isCaller = false
                _remoteUserId.value = null
            }
            "ice_restart" -> {
                Log.i(TAG, "Remote requested ICE restart")
                webRtcClient?.restartIce()
            }
        }
    }

    fun startCall(receiverId: String) {
        isCaller = true
        isRemoteDescriptionSet = false
        pendingCandidates.clear()
        _remoteUserId.value = receiverId
        _callState.value = CallState.DIALING
        callAudioManager.startDialing()
        startCallService()
        signalingClient.sendMessage(SignalingMessage("call_request", myUserId, receiverId))
        webRtcClient = WebRtcClient(context, this, transcriptRepository)
        fetchRemoteProfile(receiverId)
        startConnectionTimeout()
    }

    private fun fetchRemoteProfile(userId: String) {
        viewModelScope.launch {
            val contacts = contactsRepository.getContacts().getOrNull()
            val contact = contacts?.find { it.userId == userId }
            if (contact != null && contact.voiceEmbedding != null) {
                val embedding = try {
                    contact.voiceEmbedding.split(",").map { it.toFloat() }.toFloatArray()
                } catch (e: Exception) { null }
                
                webRtcClient?.setRemoteVoiceProfile(embedding, contact.baselineSpeechRate, contact.pitchVariance)
                Log.i(TAG, "Loaded voice biometric profile for $userId")
            } else {
                Log.w(TAG, "No voice biometric profile found for $userId")
                // Reset to default (no profile)
                webRtcClient?.setRemoteVoiceProfile(null, 0f, 0f)
            }
        }
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(20000) // 20 seconds timeout
            if (_callState.value == CallState.CONNECTING || _callState.value == CallState.DIALING) {
                Log.e(TAG, "Connection timed out after 20s")
                _callState.value = CallState.FAILED
            }
        }
    }

    fun acceptCall() {
        isCaller = false
        callAudioManager.stopAll()
        val receiverId = _remoteUserId.value ?: return
        Log.i(TAG, "Accepting call from $receiverId")
        _callState.value = CallState.CONNECTING // Show "Connecting..." UI
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "accepted"))
        startConnectionTimeout()
    }

    fun rejectCall() {
        val receiverId = _remoteUserId.value ?: return
        stopCallService()
        callAudioManager.stopAll()
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "rejected"))
        _callState.value = CallState.ENDED
        isCaller = false
        _remoteUserId.value = null
        isRemoteDescriptionSet = false
        pendingCandidates.clear()
    }

    fun endCall() {
        val receiverId = _remoteUserId.value ?: return
        stopCallService()
        callAudioManager.stopAll()
        stopTimer()
        signalingClient.sendMessage(SignalingMessage("end_call", myUserId, receiverId))
        webRtcClient?.close()
        _callState.value = CallState.ENDED
        isCaller = false
        _remoteUserId.value = null
        isRemoteDescriptionSet = false
        pendingCandidates.clear()
    }

    private fun startTimer() {
        Log.d(TAG, "Starting Call Timer")
        stopTimer()
        _callDuration.value = 0
        timerJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(1000)
                    _callDuration.value += 1
                    Log.d(TAG, "Call Duration: ${_callDuration.value}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Timer Job cancelled/error: ${e.message}")
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
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

    /**
     * Simulates a Hindi scam call for demonstration of LLM and keyword detection.
     */
    fun simulateHindiScam() {
        viewModelScope.launch {
            val scamTranscript = "Namaste, aapka account khatre mein hai. Jaldi se bank transfer kijiye warna police jail bhej degi. OTP bataye turant."
            webRtcClient?.updateTranscript(scamTranscript)
        }
    }

    private fun startCallService() {
        val intent = Intent(context, CallService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopCallService() {
        val intent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_STOP
        }
        context.stopService(intent)
    }

    override fun onCleared() {
        callAudioManager.stopAll()
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
            Log.d(TAG, "ICE Connection State Change: $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.i(TAG, "Call Connected!")
                    connectionTimeoutJob?.cancel()
                    callAudioManager.stopAll()
                    callAudioManager.setCommunicationMode()
                    _callState.value = CallState.CONNECTED
                    startTimer()
                    retryCount = 0
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(TAG, "ICE Connection Failed")
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.i(TAG, "Attempting ICE Restart due to connection failure (Retry $retryCount/$MAX_RETRIES)")
                        signalingClient.sendMessage(SignalingMessage("ice_restart", myUserId, _remoteUserId.value ?: ""))
                        webRtcClient?.restartIce()
                    } else {
                        stopTimer()
                        connectionTimeoutJob?.cancel()
                        _callState.value = CallState.FAILED
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "ICE Connection Disconnected")
                    delay(3000)
                    if ((_callState.value == CallState.CONNECTED || _callState.value == CallState.CONNECTING) && 
                        retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.w(TAG, "Still disconnected after 3s, initiating ICE restart (Retry $retryCount/$MAX_RETRIES)...")
                        signalingClient.sendMessage(SignalingMessage("ice_restart", myUserId, _remoteUserId.value ?: ""))
                        webRtcClient?.restartIce()
                    }
                }
                else -> {}
            }
        }
    }

    override fun onRemoteAudioLevel(level: Double) {
        // Convert normalized 0.0-1.0 level to a float for Compose animation
        _remoteAudioLevel.value = level.toFloat()
    }

    override fun onRemoteDescriptionSet() {
        Log.d(TAG, "Remote description set, flushing ${pendingCandidates.size} candidates")
        isRemoteDescriptionSet = true
        pendingCandidates.forEach { candidate ->
            webRtcClient?.addIceCandidate(candidate)
        }
        pendingCandidates.clear()
    }

    override fun onDetectionResult(riskScore: Float, message: String, level: String) {
        _riskScore.value = riskScore
        _detectionMessage.value = message
        _threatLevel.value = level
    }

    override fun onTranscriptUpdated(transcript: String) {
        _liveTranscript.value = transcript
    }

    override fun onLanguageDetected(language: String) {
        _detectedLanguage.value = language
    }
}
