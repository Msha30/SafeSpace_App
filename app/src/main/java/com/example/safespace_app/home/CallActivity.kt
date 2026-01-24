package com.example.safespace_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*

class CallActivity : AppCompatActivity() {

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var hangupBtn: Button

    private val db = FirebaseFirestore.getInstance()
    private var callId: String? = null
    private var submissionId: String? = null

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBase = EglBase.create()
    private var videoCapturer: CameraVideoCapturer? = null

    private var callListener: ListenerRegistration? = null
    private var offerCandidatesListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "CallActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )

        setContentView(R.layout.activity_call)

        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)
        hangupBtn = findViewById(R.id.hangupBtn)

        callId = intent.getStringExtra("CALL_ID")
        submissionId = intent.getStringExtra("SUBMISSION_ID")

        Log.d(TAG, "CallActivity started with callId: $callId")

        // Initialize views first
        initializeViews()

        hangupBtn.setOnClickListener {
            endCall()
            finish()
        }

        if (checkPermissions()) {
            initializePeerConnection()
            listenForCall()
        } else {
            requestPermissions()
        }
    }

    private fun initializeViews() {
        // Remote view (fullscreen background)
        remoteView.init(eglBase.eglBaseContext, null)
        remoteView.setMirror(false)
        remoteView.setZOrderMediaOverlay(false)  // Bottom layer
        remoteView.setEnableHardwareScaler(true)

        // Local view (small overlay on top)
        localView.init(eglBase.eglBaseContext, null)
        localView.setMirror(true)  // Mirror for selfie effect
        localView.setZOrderMediaOverlay(true)  // CRITICAL: Must be true to show on top!
        localView.setEnableHardwareScaler(true)

        Log.d(TAG, "Views initialized")
    }

    private fun checkPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED &&
                audio == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            100
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializePeerConnection()
            listenForCall()
        } else {
            Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializePeerConnection() {
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null && callId != null) {
                        Log.d(TAG, "New ICE candidate: ${candidate.sdp}")

                        val candidateData = hashMapOf(
                            "candidate" to hashMapOf(
                                "candidate" to candidate.sdp,
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex
                            ),
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("calls")
                            .document(callId!!)
                            .collection("answerCandidates")
                            .add(candidateData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Answer candidate added successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to add answer candidate", e)
                            }
                    }
                }

                override fun onAddStream(stream: MediaStream?) {
                    Log.d(TAG, "onAddStream called (shouldn't happen with Unified Plan)")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.d(TAG, "onTrack called")
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        Log.d(TAG, "Remote video track added")
                        runOnUiThread {
                            track.addSink(remoteView)
                        }
                    }
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection State: $newState")
                    runOnUiThread {
                        when (newState) {
                            PeerConnection.IceConnectionState.CONNECTED -> {
                                Toast.makeText(this@CallActivity, "Connected!", Toast.LENGTH_SHORT).show()
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED -> {
                                Toast.makeText(this@CallActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                            }
                            PeerConnection.IceConnectionState.FAILED -> {
                                Toast.makeText(this@CallActivity, "Connection failed", Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling State: $newState")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE Gathering State: $newState")
                }
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "onAddTrack called")
                }
            }
        )

        // Setup local media AFTER peer connection is created
        setupLocalMedia()
    }

    private fun setupLocalMedia() {
        // Get Local Audio
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO", audioSource)

        // Get Local Video
        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "CaptureThread",
            eglBase.eglBaseContext
        )

        val enumerator = Camera2Enumerator(this)
        val cameraName = enumerator.deviceNames.firstOrNull {
            enumerator.isFrontFacing(it)
        } ?: enumerator.deviceNames[0]

        videoCapturer = enumerator.createCapturer(cameraName, null)

        videoCapturer?.initialize(
            surfaceTextureHelper,
            this,
            videoSource.capturerObserver
        )

        localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO", videoSource)

        // IMPORTANT: Add sink to local view BEFORE starting capture
        localVideoTrack?.addSink(localView)
        Log.d(TAG, "Local video sink added to localView")

        // NOW start capture
        videoCapturer?.startCapture(640, 480, 30)
        Log.d(TAG, "Video capture started")

        // Add tracks to peer connection
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("LOCAL_STREAM")) }
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("LOCAL_STREAM")) }

        Log.d(TAG, "Local tracks added to peer connection")
    }

    private fun listenForCall() {
        if (callId == null) {
            Log.e(TAG, "Cannot listen for call: callId is null")
            return
        }

        val callRef = db.collection("calls").document(callId!!)

        // Listen for Offer Candidates from web
        offerCandidatesListener = callRef.collection("offerCandidates")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to offer candidates", e)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data

                        val candidateData = data["candidate"] as? Map<*, *>

                        if (candidateData != null) {
                            val sdpMid = candidateData["sdpMid"] as? String
                            val sdpMLineIndex = when (val idx = candidateData["sdpMLineIndex"]) {
                                is Long -> idx.toInt()
                                is Int -> idx
                                else -> null
                            }
                            val sdp = candidateData["candidate"] as? String

                            if (sdpMid != null && sdpMLineIndex != null && sdp != null) {
                                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                peerConnection?.addIceCandidate(iceCandidate)
                                Log.d(TAG, "Added remote offer candidate")
                            }
                        }
                    }
                }
            }

        // Listen for Offer (SDP)
        callListener = callRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Error listening to call document", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                val offer = data?.get("offer") as? Map<*, *>

                if (offer != null && peerConnection?.remoteDescription == null) {
                    val sdp = offer["sdp"] as? String ?: ""
                    val typeString = offer["type"] as? String ?: "offer"

                    Log.d(TAG, "Received offer, creating answer...")

                    val sdpType = if (typeString == "offer") {
                        SessionDescription.Type.OFFER
                    } else {
                        SessionDescription.Type.ANSWER
                    }

                    val remoteDesc = SessionDescription(sdpType, sdp)

                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set successfully")
                            createAnswer(callRef)
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, remoteDesc)
                }
            }
        }
    }

    private fun createAnswer(callRef: com.google.firebase.firestore.DocumentReference) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set, updating Firestore with answer")

                            val answerMap = hashMapOf(
                                "type" to "answer",
                                "sdp" to desc.description
                            )

                            callRef.update("answer", answerMap)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Answer uploaded to Firestore")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to upload answer", e)
                                }
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Failed to create answer: $p0")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Failed to create answer: $p0")
            }
        }, constraints)
    }

    private fun endCall() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()

        localVideoTrack?.removeSink(localView)
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()

        peerConnection?.close()
        peerConnection = null

        callListener?.remove()
        offerCandidatesListener?.remove()

        if (callId != null) {
            db.collection("calls").document(callId!!)
                .update("status", "ended")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        endCall()
        localView.release()
        remoteView.release()
        eglBase.release()
    }
}