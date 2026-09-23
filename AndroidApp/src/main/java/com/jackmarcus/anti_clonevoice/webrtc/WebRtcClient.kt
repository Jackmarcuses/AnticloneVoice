package com.jackmarcus.anti_clonevoice.webrtc

import android.content.Context
import android.util.Log
import com.jackmarcus.anti_clonevoice.data.repository.TranscriptRepository
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class WebRtcClient(
    private val context: Context,
    private val observer: WebRtcObserver,
    private val transcriptRepository: TranscriptRepository
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
        fun onRemoteAudioLevel(level: Double)
        fun onRemoteDescriptionSet()
        fun onDetectionResult(
            riskScore: Float,
            message: String,
            level: String,
            recommendChallenge: Boolean = false,
            identityMatch: Float = 100f,
            acousticAuth: Float = 100f,
            behavioralMatch: Float = 100f
        )
        fun onTranscriptUpdated(transcript: String)
        fun onLanguageDetected(language: String)
    }

    private val rootEglBase: EglBase by lazy { EglBase.create() }
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        if (singletonFactory == null) {
            singletonFactory = buildPeerConnectionFactory()
        }
        singletonFactory!!
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        
        // OpenRelay Free Community TURN Servers
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )
    
    private var peerConnection: PeerConnection? = null
    private var statsTimer: Timer? = null
    private var localAudioTrack: AudioTrack? = null
    private var statsLogCounter = 0
    private var isClosed = false

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
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Ensure we use all transport types
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        
        return try {
            peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(TAG, "New ICE Candidate: ${candidate.sdpMid} | ${candidate.sdp}")
                    observer.onIceCandidate(candidate)
                }

                override fun onDataChannel(p0: DataChannel?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.i(TAG, "ICE State: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                         Log.d(TAG, "WebRTC Transport Connected")
                    }
                    state?.let { observer.onConnectionStateChange(it) }
                }
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    Log.i(TAG, "onAddTrack: ${receiver?.track()?.kind()}")
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        track.setVolume(1.0)
                        track.addSink(remoteAudioSink)
                        Log.i(TAG, "Remote Audio Track Enabled, Volume set, and AudioTrackSink attached")
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
        
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        }
        
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)
        // Use addTrack instead of addStream for better Unified Plan support
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                observer.onLocalSdpCreated(sdp)
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Local description set successfully (Offer)")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Offer creation failed: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Set local description failed: $p0")
            }
        }, MediaConstraints())
    }

    private fun startStatsMonitoring() {
        statsTimer = Timer()
        statsTimer?.schedule(object : TimerTask() {
            override fun run() {
                try {
                    val pc = peerConnection ?: return
                    statsLogCounter++
                    
                    pc.getStats { report ->
                        var packetsLost = 0L
                        var rtt = 0.0
                        var localCandidateType = "unknown"
                        var remoteCandidateType = "unknown"
                        var srtpCipher = "unknown"
                        var dtlsCipher = "unknown"
                        
                        report.statsMap.values.forEach { stats ->
                            if (stats.type == "inbound-rtp") {
                                val level = stats.members["audioLevel"] as? Double ?: 0.0
                                observer.onRemoteAudioLevel(level)
                                
                                val lost = stats.members["packetsLost"]?.toString()?.toLongOrNull() ?: 0L
                                if (lost > 0) packetsLost = lost
                            }
                            
                            if (stats.type == "candidate-pair") {
                                val state = stats.members["state"]?.toString()
                                if (state == "succeeded" || state == "in-progress") {
                                    rtt = stats.members["currentRoundTripTime"]?.toString()?.toDoubleOrNull() ?: 0.0
                                }
                            }
                            
                            if (stats.type == "local-candidate") {
                                localCandidateType = stats.members["candidateType"]?.toString() ?: "unknown"
                            }
                            if (stats.type == "remote-candidate") {
                                remoteCandidateType = stats.members["candidateType"]?.toString() ?: "unknown"
                            }
                            
                            // Transport statistics contain encryption metadata (DTLS + SRTP)
                            if (stats.type == "transport") {
                                srtpCipher = stats.members["srtpCipher"]?.toString() ?: "unknown"
                                dtlsCipher = stats.members["dtlsCipher"]?.toString() ?: "unknown"
                            }
                        }
                        
                        // Log stats every 2 seconds (10 * 200ms)
                        if (statsLogCounter % 10 == 0) {
                            Log.i(TAG, "Performance Stats -> RTT: ${String.format(Locale.US, "%.3f", rtt)}s, Packets Lost: $packetsLost, Local Candidate: $localCandidateType, Remote Candidate: $remoteCandidateType")
                            Log.i(TAG, "Security Encryption -> WebRTC DTLS-SRTP Active: true, DTLS Cipher: $dtlsCipher, SRTP Cipher: $srtpCipher")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stats error: ${e.message}")
                }
            }
        }, 0, 200)
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
                Log.d(TAG, "Remote description set successfully")
                observer.onRemoteDescriptionSet()
                if (sdp.type == SessionDescription.Type.OFFER) {
                    answerCall()
                }
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Failed to create remote description: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Failed to set remote description: $p0")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (peerConnection == null) {
            Log.w(TAG, "Cannot add ICE candidate: peerConnection is null")
            return
        }
        peerConnection?.addIceCandidate(candidate)
    }

    fun answerCall() {
        if (peerConnection == null) {
            peerConnection = createPeerConnection()
        }
        
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)
        // Use addTrack instead of addStream
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                observer.onLocalSdpCreated(sdp)
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Local description set successfully (Answer)")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Answer creation failed: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Set local description failed: $p0")
            }
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
        if (isClosed) return
        isClosed = true
        try {
            statsTimer?.cancel()
            statsTimer = null
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null
            peerConnection?.dispose()
            peerConnection = null
            detectionEngine.close()
            Log.i(TAG, "WebRtcClient closed safely")
        } catch (e: Exception) {
            Log.e(TAG, "Error during close: ${e.message}")
        }
    }

    fun setMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun updateTranscript(text: String) {
        detectionEngine.updateTranscript(text)
    }

    private val detectionEngine = DetectionEngine(context, observer, transcriptRepository)
    private val pipelineProcessor = AudioPipelineProcessor(detectionEngine, observer)

    fun setRemoteVoiceProfile(embedding: FloatArray?, rate: Float, variance: Float, userId: String, userName: String = "Unknown Caller", myUserId: String) {
        detectionEngine.setStoredProfile(embedding, rate, variance)
        detectionEngine.setRemoteUserId(userId, userName)
        detectionEngine.setMyUserId(myUserId)
    }

    fun setCheckAgainstUserId(userId: String?, targetEmbedding: FloatArray? = null) {
        detectionEngine.setCheckAgainstUserId(userId, targetEmbedding)
    }

    fun resetDetectionEngine() {
        detectionEngine.resetEngineState()
    }

    fun enrollVoice() {
        detectionEngine.enrollVoice()
    }

    fun setCheckAgainstUserId(userId: String?) {
        detectionEngine.setCheckAgainstUserId(userId)
    }

    private val remoteAudioSink = object : AudioTrackSink {
        override fun onData(
            audioData: ByteBuffer?,
            bitsPerSample: Int,
            sampleRate: Int,
            numberOfChannels: Int,
            numberOfFrames: Int,
            absoluteCaptureTimestampMs: Long
        ) {
            if (isClosed) return
            if (audioData != null) {
                // Pass digital audio to the detection pipeline for features
                pipelineProcessor.processIncomingBuffer(audioData, sampleRate, numberOfChannels)
                
                // ALSO pass it to the transcription engine for text (without using mic)
                detectionEngine.processDigitalAudioForTranscription(audioData, sampleRate, numberOfChannels)
            }
        }
    }
}

class AudioPipelineProcessor(private val detectionEngine: DetectionEngine, private val observer: WebRtcClient.WebRtcObserver) {
    private val TAG = "AudioPipelineProcessor"
    private val targetSampleRate = 16000
    private val windowSize = 48000 // 3 seconds buffer at 16kHz
    private val stepSize = 8000   // Process every 500ms
    private val pcmBuffer = ShortArray(windowSize)
    private var bufferIndex = 0

    fun processIncomingBuffer(audioData: ByteBuffer, nativeSampleRate: Int, numChannels: Int) {
        // Safe readout of shorts from ByteBuffer
        val shortLength = audioData.remaining() / 2
        if (shortLength <= 0) return
        
        val tempShorts = ShortArray(shortLength)
        audioData.mark()
        audioData.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(tempShorts)
        audioData.reset()

        // Convert multi-channel to mono by averaging channels
        val monoShorts = if (numChannels > 1) {
            val monoLength = shortLength / numChannels
            val mono = ShortArray(monoLength)
            for (i in 0 until monoLength) {
                var sum = 0
                for (c in 0 until numChannels) {
                    sum += tempShorts[i * numChannels + c]
                }
                mono[i] = (sum / numChannels).toShort()
            }
            mono
        } else {
            tempShorts
        }

        // Resample mono data to 16kHz if necessary
        val resampledShorts = if (nativeSampleRate != targetSampleRate) {
            resample(monoShorts, nativeSampleRate, targetSampleRate)
        } else {
            monoShorts
        }

        // Buffer with sliding window logic
        for (sample in resampledShorts) {
            if (bufferIndex < windowSize) {
                pcmBuffer[bufferIndex++] = sample
            }
            
            if (bufferIndex == windowSize) {
                // Run full fusion pipeline on the 3s window
                val result = detectionEngine.processAudioWindow(pcmBuffer.clone(), targetSampleRate)
                
                // Notify UI via observer
                observer.onDetectionResult(
                    riskScore = result.riskScore,
                    message = result.message,
                    level = result.threatLevel,
                    recommendChallenge = result.recommendChallenge,
                    identityMatch = result.identityMismatch,
                    acousticAuth = result.acousticAnomaly,
                    behavioralMatch = result.behavioralAnomaly
                )

                // Slide window: Keep the last (windowSize - stepSize) and move it to the front
                val remainingSamples = windowSize - stepSize
                System.arraycopy(pcmBuffer, stepSize, pcmBuffer, 0, remainingSamples)
                bufferIndex = remainingSamples
            }
        }
    }

    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val newLength = (input.size / ratio).toInt()
        val output = ShortArray(newLength)
        for (i in 0 until newLength) {
            val sourceIndex = (i * ratio).toInt()
            if (sourceIndex < input.size) {
                output[i] = input[sourceIndex]
            }
        }
        return output
    }
}
