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
    private val TAG = "WebRtcClient"
    interface WebRtcObserver {
        fun onIceCandidate(candidate: IceCandidate)
        fun onLocalSdpCreated(sessionDescription: SessionDescription)
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    }

    private val rootEglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        // TODO: Replace with your actual TURN server credentials
        // PeerConnection.IceServer.builder("turn:your.turn.server:3478")
        //     .setUsername("user")
        //     .setPassword("password")
        //     .createIceServer()
    )
    private var peerConnection: PeerConnection? = null

    private var statsTimer: Timer? = null

    init {
        initPeerConnectionFactory(context)
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "New ICE Candidate: ${candidate.sdpMid} ${candidate.sdp}")
                observer.onIceCandidate(candidate)
            }

            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE Connection State: $state")
                state?.let { observer.onConnectionStateChange(it) }
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE Gathering State: $state")
            }
            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "OnAddStream: ${stream.id}")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling State: $state")
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation Needed")
            }
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(TAG, "OnAddTrack: ${receiver?.id()}")
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        })
    }

    fun restartIce() {
        Log.i(TAG, "Restarting ICE...")
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

    private fun startStatsMonitoring() {
        statsTimer = Timer()
        statsTimer?.schedule(object : TimerTask() {
            override fun run() {
                peerConnection?.getStats { report ->
                    Log.d(TAG, "Stats Report Available")
                    // Basic logging of RTT if found
                    report.statsMap.values.find { it.type == "candidate-pair" }?.let { stats ->
                        Log.d(TAG, "Stats - Candidate Pair: ${stats.id}")
                    }
                }
            }
        }, 0, 10000)
    }

    fun startCall() {
        peerConnection = createPeerConnection()
        startStatsMonitoring()
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        mediaStream.addTrack(audioTrack)
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

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun onRemoteSessionDescription(sdp: SessionDescription) {
        if (peerConnection == null) {
            peerConnection = createPeerConnection()
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

    fun answerCall() {
        if (peerConnection == null) {
            peerConnection = createPeerConnection()
        }
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        mediaStream.addTrack(audioTrack)
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

    fun close() {
        statsTimer?.cancel()
        statsTimer = null
        peerConnection?.close()
        peerConnection = null
    }
}
