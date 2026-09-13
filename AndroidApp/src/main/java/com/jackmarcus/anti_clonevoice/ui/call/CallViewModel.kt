package com.jackmarcus.anti_clonevoice.ui.call

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.models.SignalingMessage
import com.jackmarcus.anti_clonevoice.webrtc.CallService
import com.jackmarcus.anti_clonevoice.webrtc.SignalingClient
import com.jackmarcus.anti_clonevoice.webrtc.WebRtcClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

enum class CallState {
    IDLE, DIALING, RINGING, CONNECTED, ENDED, FAILED
}

class CallViewModel(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val secureStorage: SecureStorage
) : ViewModel(), WebRtcClient.WebRtcObserver {

    private val TAG = "CallViewModel"
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _remoteUserId = MutableStateFlow<String?>(null)
    val remoteUserId: StateFlow<String?> = _remoteUserId.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val callAudioManager = CallAudioManager(context)
    private var webRtcClient: WebRtcClient? = null
    private val myUserId = secureStorage.getUserId() ?: ""
    private var isCaller = false
    private var retryCount = 0
    private val MAX_RETRIES = 2

    init {
        viewModelScope.launch {
            signalingClient.signalingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
        connectSignaling()
    }

    private fun connectSignaling() {
        val userId = secureStorage.getUserId() ?: return
        signalingClient.connect(userId)
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        when (message.type) {
            "offer" -> {
                if (_callState.value == CallState.IDLE || _callState.value == CallState.RINGING) {
                    _remoteUserId.value = message.senderId
                    _callState.value = CallState.RINGING
                    callAudioManager.startRinging()
                    startCallService()
                    webRtcClient = WebRtcClient(context, this)
                    webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.OFFER, message.data))
                }
            }
            "answer" -> {
                callAudioManager.stopAll()
                webRtcClient?.onRemoteSessionDescription(SessionDescription(SessionDescription.Type.ANSWER, message.data))
                _callState.value = CallState.CONNECTED
            }
            "candidate" -> {
                val candidateData = message.data?.split("|") ?: return
                if (candidateData.size >= 3) {
                    val candidate = IceCandidate(candidateData[0], candidateData[1].toInt(), candidateData[2])
                    webRtcClient?.addIceCandidate(candidate)
                }
            }
            "call_request" -> {
                 _remoteUserId.value = message.senderId
                 _callState.value = CallState.RINGING
                 callAudioManager.startRinging()
                 startCallService()
            }
            "call_response" -> {
                if (message.data == "accepted") {
                    callAudioManager.stopAll()
                    _callState.value = CallState.DIALING
                    webRtcClient?.startCall()
                } else {
                    stopCallService()
                    callAudioManager.stopAll()
                    _callState.value = CallState.ENDED
                }
            }
            "end_call" -> {
                stopCallService()
                callAudioManager.stopAll()
                webRtcClient?.close()
                _callState.value = CallState.ENDED
            }
            "ice_restart" -> {
                Log.i(TAG, "Remote requested ICE restart")
                webRtcClient?.restartIce()
            }
        }
    }

    fun startCall(receiverId: String) {
        isCaller = true
        _remoteUserId.value = receiverId
        _callState.value = CallState.DIALING
        callAudioManager.startDialing()
        startCallService()
        signalingClient.sendMessage(SignalingMessage("call_request", myUserId, receiverId))
        webRtcClient = WebRtcClient(context, this)
    }

    fun acceptCall() {
        isCaller = false
        callAudioManager.stopAll()
        val receiverId = _remoteUserId.value ?: return
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "accepted"))
        _callState.value = CallState.DIALING
    }

    fun rejectCall() {
        val receiverId = _remoteUserId.value ?: return
        stopCallService()
        callAudioManager.stopAll()
        signalingClient.sendMessage(SignalingMessage("call_response", myUserId, receiverId, "rejected"))
        _callState.value = CallState.ENDED
    }

    fun endCall() {
        val receiverId = _remoteUserId.value ?: return
        stopCallService()
        callAudioManager.stopAll()
        signalingClient.sendMessage(SignalingMessage("end_call", myUserId, receiverId))
        webRtcClient?.close()
        _callState.value = CallState.ENDED
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        webRtcClient?.setMute(muted)
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
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    callAudioManager.stopAll()
                    _callState.value = CallState.CONNECTED
                    retryCount = 0
                }
                PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.DISCONNECTED -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.w(TAG, "Connection lost. Retry attempt $retryCount")
                        val receiverId = _remoteUserId.value ?: return@launch
                        if (isCaller) {
                            signalingClient.sendMessage(SignalingMessage("ice_restart", myUserId, receiverId))
                            webRtcClient?.restartIce()
                        }
                    } else {
                        _callState.value = CallState.FAILED
                    }
                }
                else -> {}
            }
        }
    }
}
