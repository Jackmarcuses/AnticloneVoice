package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*

class WebRtcClient(
    private val context: Context,
    private val observer: WebRtcObserver
) {
    companion object {
        private var isInitialized = false
        private var singletonFactory: PeerConnectionFactory? = null

        fun initializeFactory(context: Context) {
            if (isInitialized) return
            try {
                val options = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                isInitialized = true
                Log.i("WebRtcClient", "PeerConnectionFactory initialized")
            } catch (e: Exception) {
                Log.e("WebRtcClient", "Failed to initialize Factory: ${e.message}")
            }
        }
    }

    private val TAG = "WebRtcClient"
    interface WebRtcObserver {
        fun onIceCandidate(candidate: IceCandidate)
        fun onLocalSdpCreated(sessionDescription: SessionDescription)
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    }

    private val rootEglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        if (singletonFactory == null) {
            singletonFactory = buildPeerConnectionFactory()
        }
        singletonFactory!!
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    
    private var peerConnection: PeerConnection? = null
    private var statsTimer: Timer? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        try {
            initializeFactory(context)
            Log.i(TAG, "WebRtcClient instance created")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed in init: ${e.message}")
        }
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        val audioDeviceModule = try {
            JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
        } catch (e: Exception) {
            Log.e(TAG, "AudioDeviceModule creation failed: ${e.message}")
            JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        }

        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        return try {
            peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    observer.onIceCandidate(candidate)
                }

                override fun onDataChannel(p0: DataChannel?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.i(TAG, "ICE State: $state")
                    state?.let { observer.onConnectionStateChange(it) }
                }
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        track.setVolume(1.0)
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Create PeerConnection failed: ${e.message}")
            null
        }
    }

    fun startCall() {
        peerConnection = createPeerConnection()
        startStatsMonitoring()
        
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.let { mediaStream.addTrack(it) }
        peerConnection?.addStream(mediaStream)

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                observer.onLocalSdpCreated(sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun startStatsMonitoring() {
        statsTimer = Timer()
        statsTimer?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    peerConnection?.getStats { report ->
                        Log.d(TAG, "Stats - RTT check")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stats error: ${e.message}")
                }
            }
        }, 0, 10000)
    }

    fun onRemoteSessionDescription(sdp: SessionDescription) {
        Log.i(TAG, "Setting remote description: ${sdp.type}")
        if (peerConnection == null) {
            Log.d(TAG, "Creating PeerConnection for remote SDP")
            peerConnection = createPeerConnection()
        }
        
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is still NULL, cannot set remote description")
            return
        }

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                if (sdp.type == SessionDescription.Type.OFFER) {
                    answerCall()
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun answerCall() {
        if (peerConnection == null) {
            peerConnection = createPeerConnection()
        }
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.let { mediaStream.addTrack(it) }
        peerConnection?.addStream(mediaStream)

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                observer.onLocalSdpCreated(sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun restartIce() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                observer.onLocalSdpCreated(sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        })
    }

    fun close() {
        try {
            statsTimer?.cancel()
            statsTimer = null
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null
            peerConnection?.close()
            peerConnection = null
            Log.i(TAG, "WebRtcClient closed safely")
        } catch (e: Exception) {
            Log.e(TAG, "Error during close: ${e.message}")
        }
    }

    fun setMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }
}
