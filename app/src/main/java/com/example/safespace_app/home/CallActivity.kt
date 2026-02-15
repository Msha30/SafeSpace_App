package com.example.safespace_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
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
    private lateinit var muteBtn: Button
    private lateinit var cameraBtn: Button
    private lateinit var statusText: TextView
    private lateinit var callTypeText: TextView

    private val db = FirebaseFirestore.getInstance()
    private var callId: String? = null
    private var submissionId: String? = null
    private var callType: String? = null // "video", "audio", or "face-to-face"

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBase = EglBase.create()
    private var videoCapturer: CameraVideoCapturer? = null

    private var callListener: ListenerRegistration? = null
    private var offerCandidatesListener: ListenerRegistration? = null

    private var isCallEnded = false // Prevents double cleanup

    // Track mute and camera states
    private var isMuted = false
    private var isCameraOff = false

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
        muteBtn = findViewById(R.id.muteBtn)
        cameraBtn = findViewById(R.id.cameraBtn)
        statusText = findViewById(R.id.statusText)
        callTypeText = findViewById(R.id.callTypeText)

        callId = intent.getStringExtra("CALL_ID")
        submissionId = intent.getStringExtra("SUBMISSION_ID")

        Log.d(TAG, "CallActivity started with callId: $callId")

        // Setup button click listeners
        hangupBtn.setOnClickListener {
            endCall()
            finish()
        }

        muteBtn.setOnClickListener {
            toggleMute()
        }

        cameraBtn.setOnClickListener {
            toggleCamera()
        }

        // Get call type from Firestore
        callId?.let { id ->
            db.collection("calls").document(id).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        callType = doc.getString("type")
                        Log.d(TAG, "Call type: $callType")
                        setupCallUI()
                    }
                }
        }

        if (checkPermissions()) {
            // Permissions granted, setup will happen after getting call type
        } else {
            requestPermissions()
        }
    }

    private fun setupCallUI() {
        when (callType) {
            "face-to-face" -> {
                // Hide video views and control buttons, show info
                localView.visibility = View.GONE
                remoteView.visibility = View.GONE
                muteBtn.visibility = View.GONE
                cameraBtn.visibility = View.GONE
                callTypeText.text = "Face-to-Face Session"
                statusText.text = "Session in Progress"
                hangupBtn.text = "End Session"
            }
            "audio" -> {
                // Hide video views and camera button, show mute button
                localView.visibility = View.GONE
                remoteView.visibility = View.GONE
                cameraBtn.visibility = View.GONE
                muteBtn.visibility = View.VISIBLE
                callTypeText.text = "Voice Call"
                statusText.text = "Connecting..."

                // Initialize audio-only connection
                if (checkPermissions()) {
                    initializePeerConnection()
                    listenForCall()
                }
            }
            "video", null -> {
                // Show video views and all controls
                localView.visibility = View.VISIBLE
                remoteView.visibility = View.VISIBLE
                muteBtn.visibility = View.VISIBLE
                cameraBtn.visibility = View.VISIBLE
                callTypeText.text = "Video Call"
                statusText.text = "Connecting..."

                // Initialize video connection
                initializeViews()
                if (checkPermissions()) {
                    initializePeerConnection()
                    listenForCall()
                }
            }
        }
    }

    private fun toggleMute() {
        localAudioTrack?.let { audioTrack ->
            isMuted = !isMuted
            audioTrack.setEnabled(!isMuted)

            runOnUiThread {
                if (isMuted) {
                    muteBtn.text = "🔇 Unmute"
                    muteBtn.setBackgroundColor(getColor(android.R.color.holo_red_light))
                    Toast.makeText(this, "Microphone muted", Toast.LENGTH_SHORT).show()
                } else {
                    muteBtn.text = "🔊 Mute"
                    muteBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                    Toast.makeText(this, "Microphone unmuted", Toast.LENGTH_SHORT).show()
                }
            }

            Log.d(TAG, "Audio ${if (isMuted) "muted" else "unmuted"}")
        }
    }

    private fun toggleCamera() {
        if (callType != "video") return

        localVideoTrack?.let { videoTrack ->
            isCameraOff = !isCameraOff
            videoTrack.setEnabled(!isCameraOff)

            runOnUiThread {
                if (isCameraOff) {
                    cameraBtn.text = "📹 Turn On"
                    cameraBtn.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                    localView.visibility = View.GONE
                    Toast.makeText(this, "Camera off", Toast.LENGTH_SHORT).show()
                } else {
                    cameraBtn.text = "📹 Turn Off"
                    cameraBtn.setBackgroundColor(getColor(android.R.color.darker_gray))
                    localView.visibility = View.VISIBLE
                    Toast.makeText(this, "Camera on", Toast.LENGTH_SHORT).show()
                }
            }

            Log.d(TAG, "Camera ${if (isCameraOff) "off" else "on"}")
        }
    }

    private fun initializeViews() {
        // Remote view (fullscreen background)
        remoteView.init(eglBase.eglBaseContext, null)
        remoteView.setMirror(false)
        remoteView.setZOrderMediaOverlay(false)
        remoteView.setEnableHardwareScaler(true)

        // Local view (small overlay on top)
        localView.init(eglBase.eglBaseContext, null)
        localView.setMirror(true)
        localView.setZOrderMediaOverlay(true)
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
            setupCallUI()
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
                    if (candidate != null && callId != null && !isCallEnded) {
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
                    if (track is VideoTrack && callType == "video") {
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
                                statusText.text = "Connected"
                                Toast.makeText(this@CallActivity, "Connected!", Toast.LENGTH_SHORT).show()
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED -> {
                                statusText.text = "Disconnected"
                                if (!isCallEnded) {
                                    Toast.makeText(this@CallActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                                }
                            }
                            PeerConnection.IceConnectionState.FAILED -> {
                                statusText.text = "Connection Failed"
                                if (!isCallEnded) {
                                    Toast.makeText(this@CallActivity, "Connection failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                            PeerConnection.IceConnectionState.CLOSED -> {
                                statusText.text = "Call Ended"
                                showCallEndedUI()
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

        // Setup local media
        setupLocalMedia()
    }

    private fun setupLocalMedia() {
        // Always get audio
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO", audioSource)

        // Only get video if video call
        if (callType == "video") {
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

            localVideoTrack?.addSink(localView)
            Log.d(TAG, "Local video sink added to localView")

            videoCapturer?.startCapture(640, 480, 30)
            Log.d(TAG, "Video capture started")

            localVideoTrack?.let { peerConnection?.addTrack(it, listOf("LOCAL_STREAM")) }
        }

        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("LOCAL_STREAM")) }
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

                if (isCallEnded) return@addSnapshotListener

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

        // Listen for Offer (SDP) and status changes
        callListener = callRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Error listening to call document", e)
                return@addSnapshotListener
            }

            if (isCallEnded) return@addSnapshotListener

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data

                // Check if call ended from web side
                val status = data?.get("status") as? String
                if (status == "ended" || status == "completed") {
                    runOnUiThread {
                        showCallEndedUI()
                    }
                    return@addSnapshotListener
                }

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
        if (isCallEnded) return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (callType == "video") "true" else "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null && !isCallEnded) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set, updating Firestore with answer")

                            if (isCallEnded) return

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

    private fun showCallEndedUI() {
        if (isCallEnded) return

        runOnUiThread {
            statusText.text = "Call Ended"
            hangupBtn.text = "Close"
            muteBtn.isEnabled = false
            cameraBtn.isEnabled = false
            Toast.makeText(this, "Call has ended", Toast.LENGTH_SHORT).show()
        }
    }

    private fun endCall() {
        if (isCallEnded) return
        isCallEnded = true

        Log.d(TAG, "Ending call...")

        // Stop video capture
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null

        // Remove sinks
        localVideoTrack?.removeSink(localView)
        localVideoTrack?.dispose()
        localVideoTrack = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        // Close peer connection
        peerConnection?.close()
        peerConnection = null

        // Remove listeners
        callListener?.remove()
        callListener = null

        offerCandidatesListener?.remove()
        offerCandidatesListener = null

        // Update call status in Firestore
        if (callId != null) {
            db.collection("calls").document(callId!!)
                .update("status", "ended")
                .addOnSuccessListener {
                    Log.d(TAG, "Call status updated to ended")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update call status", e)
                }
            deleteCallDocument(callId!!)
        }
    }

    private fun deleteCallDocument(callId: String) {
        val callRef = db.collection("calls").document(callId)

        // Delete offerCandidates
        callRef.collection("offerCandidates").get()
            .addOnSuccessListener { offerSnapshot ->
                val batch = db.batch()
                offerSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit()
            }

        // Delete answerCandidates
        callRef.collection("answerCandidates").get()
            .addOnSuccessListener { answerSnapshot ->
                val batch = db.batch()
                answerSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit()
            }

        // Delete the call document itself
        callRef.delete()
            .addOnSuccessListener {
                Log.d(TAG, "Call document deleted successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete call document", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        endCall()

        try {
            if (localView.isInitialized()) localView.release()
            if (remoteView.isInitialized()) remoteView.release()
            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    private fun SurfaceViewRenderer.isInitialized(): Boolean {
        return try {
            // Try to access the handler which is set during init
            this.handler != null
        } catch (e: Exception) {
            false
        }
    }
}