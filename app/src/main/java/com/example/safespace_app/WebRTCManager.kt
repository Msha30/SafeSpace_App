package com.example.safespace_app

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import org.webrtc.*
import org.json.JSONObject
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ChildEventListener

class WebRTCManager(
    private val context: Context,
    private val sessionId: String,
    private val isInitiator: Boolean,
    private val eglBaseContext: EglBase.Context,  // ✅ Pass from CallActivity
    private val onLocalStreamReady: (MediaStream) -> Unit,
    private val onRemoteStreamAdded: (MediaStream) -> Unit,
    private val onCallEnded: () -> Unit
) {
    private val TAG = "WebRTCManager"

    private var peerConnection: PeerConnection? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var isCaptureStarted = false

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localStream: MediaStream? = null

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
    private val signalingRef = rtdb.getReference("call_signals/$sessionId")

    companion object {
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // Free TURN server for testing (use your own in production!)
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )
    }

    fun initialize(isVideoCall: Boolean) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Initializing WebRTC")
        Log.d(TAG, "Session ID: $sessionId")
        Log.d(TAG, "Is Initiator: $isInitiator")
        Log.d(TAG, "Is Video Call: $isVideoCall")
        Log.d(TAG, "========================================")

        // Initialize PeerConnectionFactory with the SAME EglBase context
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory created")

        // Create audio track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("audio", audioSource)
        localAudioTrack?.setEnabled(true)

        Log.d(TAG, "Audio track created")

        // Create video track if needed
        if (isVideoCall) {
            videoCapturer = createCameraCapturer()
            videoCapturer?.let { capturer ->
                surfaceTextureHelper = SurfaceTextureHelper.create(
                    "CaptureThread",
                    eglBaseContext
                )

                val videoSource = peerConnectionFactory!!.createVideoSource(capturer.isScreencast)

                capturer.initialize(
                    surfaceTextureHelper,
                    context,
                    videoSource.capturerObserver
                )

                // Start capture with reasonable resolution
                capturer.startCapture(640, 480, 30)
                isCaptureStarted = true

                localVideoTrack = peerConnectionFactory!!.createVideoTrack("video", videoSource)
                localVideoTrack?.setEnabled(true)

                Log.d(TAG, "Video track created and capture started")
            }
        }

        // Create peer connection BEFORE adding tracks
        createPeerConnection()

        // Add tracks to peer connection
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("stream"))
            Log.d(TAG, "Audio track added to peer connection")
        }

        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("stream"))
            Log.d(TAG, "Video track added to peer connection")
        }

        // Create local stream for callback
        localStream = peerConnectionFactory!!.createLocalMediaStream("local_stream")
        localAudioTrack?.let { localStream?.addTrack(it) }
        localVideoTrack?.let { localStream?.addTrack(it) }

        // Notify that local stream is ready
        onLocalStreamReady(localStream!!)
        Log.d(TAG, "Local stream ready callback invoked")

        // Setup signaling
        setupSignalingListeners()

        if (isInitiator) {
            Log.d(TAG, "Creating offer as initiator")
            createOffer()

            // Add timeout to detect if answer never comes
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (peerConnection?.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    Log.e(TAG, "⚠️ TIMEOUT: No answer received after 10 seconds!")
                    Log.e(TAG, "Check if the other peer is actually joining the call")
                }
            }, 10000)
        } else {
            Log.d(TAG, "Waiting for offer as receiver")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)

        // Try front camera first
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                enumerator.createCapturer(deviceName, null)?.let {
                    Log.d(TAG, "Using front camera: $deviceName")
                    return it
                }
            }
        }

        // Fallback to back camera
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                enumerator.createCapturer(deviceName, null)?.let {
                    Log.d(TAG, "Using back camera: $deviceName")
                    return it
                }
            }
        }

        Log.e(TAG, "No camera found")
        return null
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory!!.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling state: $newState")
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE connection state: $newState")
                    if (newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                        newState == PeerConnection.IceConnectionState.FAILED ||
                        newState == PeerConnection.IceConnectionState.CLOSED
                    ) {
                        onCallEnded()
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "ICE receiving: $receiving")
                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "ICE gathering state: $newState")
                }

                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(TAG, "New ICE candidate: ${candidate.sdp}")
                    sendIceCandidate(candidate)
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }

                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    val track = receiver.track()
                    Log.d(TAG, "🎥 Track added!")
                    Log.d(TAG, "  - Track kind: ${track?.kind()}")
                    Log.d(TAG, "  - Track ID: ${track?.id()}")
                    Log.d(TAG, "  - Track enabled: ${track?.enabled()}")
                    Log.d(TAG, "  - Streams count: ${streams.size}")

                    if (streams.isNotEmpty()) {
                        val stream = streams[0]
                        Log.d(TAG, "  - Stream ID: ${stream.id}")
                        Log.d(TAG, "  - Video tracks: ${stream.videoTracks.size}")
                        Log.d(TAG, "  - Audio tracks: ${stream.audioTracks.size}")
                        onRemoteStreamAdded(stream)
                    } else {
                        Log.w(TAG, "  - No streams in onAddTrack!")
                    }
                }
            }
        )

        Log.d(TAG, "PeerConnection created")
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "Offer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set successfully")
                        sendOffer(sdp)
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "setLocalDescription failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "createOffer failed: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun setupSignalingListeners() {
        Log.d(TAG, "Setting up signaling listeners for path: call_signals/$sessionId")

        if (!isInitiator) {
            Log.d(TAG, "Listening for OFFER at: call_signals/$sessionId/offer")
            signalingRef.child("offer").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Offer snapshot changed, exists: ${snapshot.exists()}")
                    val offerJson = snapshot.getValue(String::class.java)
                    if (!offerJson.isNullOrBlank()) {
                        Log.d(TAG, "✅ Offer received")
                        handleOffer(offerJson)
                    } else {
                        Log.d(TAG, "Offer is null or blank")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "offer listener cancelled: ${error.message}")
                }
            })
        } else {
            Log.d(TAG, "Listening for ANSWER at: call_signals/$sessionId/answer")
            signalingRef.child("answer").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Answer snapshot changed, exists: ${snapshot.exists()}")
                    val answerJson = snapshot.getValue(String::class.java)
                    if (!answerJson.isNullOrBlank()) {
                        Log.d(TAG, "✅ Answer received")
                        handleAnswer(answerJson)
                    } else {
                        Log.d(TAG, "Answer is null or blank, waiting...")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "answer listener cancelled: ${error.message}")
                }
            })
        }

        signalingRef.child("ice_candidates")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prevName: String?) {
                    val candidateJson = snapshot.getValue(String::class.java)
                    if (!candidateJson.isNullOrBlank()) {
                        Log.d(TAG, "ICE candidate received")
                        handleIceCandidate(candidateJson)
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, prevName: String?) {}
                override fun onChildMoved(snapshot: DataSnapshot, prevName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "ICE candidate listener cancelled: ${error.message}")
                }
            })
    }

    private fun sendOffer(sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        signalingRef.child("offer").setValue(json.toString())
            .addOnSuccessListener { Log.d(TAG, "Offer sent") }
            .addOnFailureListener { Log.e(TAG, "Failed to send offer", it) }
    }

    private fun handleOffer(json: String) {
        try {
            val obj = JSONObject(json)
            val sdp = SessionDescription(
                SessionDescription.Type.OFFER,
                obj.getString("sdp")
            )

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description set, creating answer")
                    createAnswer()
                }
                override fun onSetFailure(error: String) {
                    Log.e(TAG, "setRemoteDescription failed: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle offer", e)
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "Answer created")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set for answer")
                        sendAnswer(sdp)
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "setLocalDescription for answer failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "createAnswer failed: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun sendAnswer(sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        signalingRef.child("answer").setValue(json.toString())
            .addOnSuccessListener { Log.d(TAG, "Answer sent") }
            .addOnFailureListener { Log.e(TAG, "Failed to send answer", it) }
    }

    private fun handleAnswer(json: String) {
        try {
            val obj = JSONObject(json)
            val sdp = SessionDescription(
                SessionDescription.Type.ANSWER,
                obj.getString("sdp")
            )

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully")
                }
                override fun onSetFailure(error: String) {
                    Log.e(TAG, "setRemoteDescription for answer failed: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle answer", e)
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        signalingRef.child("ice_candidates").push().setValue(json.toString())
    }

    private fun handleIceCandidate(json: String) {
        try {
            val obj = JSONObject(json)
            val candidate = IceCandidate(
                obj.getString("sdpMid"),
                obj.getInt("sdpMLineIndex"),
                obj.getString("candidate")
            )
            peerConnection?.addIceCandidate(candidate)
            Log.d(TAG, "ICE candidate added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle ICE candidate", e)
        }
    }

    fun toggleAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Audio ${if (enabled) "enabled" else "disabled"}")
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        Log.d(TAG, "Video ${if (enabled) "enabled" else "disabled"}")
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
        Log.d(TAG, "Camera switched")
    }

    fun endCall() {
        Log.d(TAG, "Ending call and cleaning up")

        // DON'T remove signaling immediately - let it stay for late joiners
        // Only clear our own ICE candidates to avoid accumulation
        try {
            signalingRef.child("ice_candidates").removeValue()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing ICE candidates", e)
        }

        // Close peer connection FIRST (this also cleans up tracks internally)
        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing peer connection", e)
        }

        // Stop camera capture
        try {
            videoCapturer?.let {
                if (isCaptureStarted) {
                    it.stopCapture()
                }
                it.dispose()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping capture", e)
        }
        videoCapturer = null
        isCaptureStarted = false

        // Dispose surface texture helper
        try {
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing surface texture helper", e)
        }
        surfaceTextureHelper = null

        // DON'T dispose tracks individually - they're managed by PeerConnection
        // Just null them out
        localAudioTrack = null
        localVideoTrack = null

        // DON'T dispose the local stream - it's already cleaned up
        localStream = null

        // Dispose factory last
        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing factory", e)
        }
        peerConnectionFactory = null

        Log.d(TAG, "Cleanup done")
    }
}