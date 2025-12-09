package com.example.safespace_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class CallActivity : AppCompatActivity() {

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var btnEndCall: ImageButton
    private lateinit var btnToggleMic: ImageButton
    private lateinit var btnToggleCamera: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var tvCallStatus: TextView
    private lateinit var tvPeerName: TextView

    private var webRTCManager: WebRTCManager? = null
    private var sessionId: String = ""
    private var peerName: String = ""
    private var isVideoCall: Boolean = true
    private var isInitiator: Boolean = false

    private var isMicEnabled = true
    private var isCameraEnabled = true

    private lateinit var eglBase: EglBase

    private val webrtcThread = HandlerThread("WebRTCThread").apply { start() }
    private val webrtcHandler = Handler(webrtcThread.looper)

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_IS_VIDEO_CALL = "is_video_call"
        const val EXTRA_IS_INITIATOR = "is_initiator"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_call)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "User"
        isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, true)
        isInitiator = intent.getBooleanExtra(EXTRA_IS_INITIATOR, false)

        if (sessionId.isEmpty()) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        checkPermissionsAndStartCall()
    }

    private fun initializeViews() {
        localView = findViewById(R.id.local_view)
        remoteView = findViewById(R.id.remote_view)
        btnEndCall = findViewById(R.id.btn_end_call)
        btnToggleMic = findViewById(R.id.btn_toggle_mic)
        btnToggleCamera = findViewById(R.id.btn_toggle_camera)
        btnSwitchCamera = findViewById(R.id.btn_switch_camera)
        tvCallStatus = findViewById(R.id.tv_call_status)
        tvPeerName = findViewById(R.id.tv_peer_name)

        tvPeerName.text = peerName
        tvCallStatus.text = if (isInitiator) "Calling..." else "Connecting..."

        // Create EGL context ONCE - this will be shared
        eglBase = EglBase.create()

        // Initialize renderers with the SAME context
        localView.init(eglBase.eglBaseContext, null)
        remoteView.init(eglBase.eglBaseContext, null)

        localView.setZOrderMediaOverlay(true)
        localView.setEnableHardwareScaler(true)
        remoteView.setEnableHardwareScaler(true)

        localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        if (!isVideoCall) {
            localView.visibility = View.GONE
            btnToggleCamera.visibility = View.GONE
            btnSwitchCamera.visibility = View.GONE
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnEndCall.setOnClickListener { endCall() }

        btnToggleMic.setOnClickListener {
            isMicEnabled = !isMicEnabled
            webRTCManager?.toggleAudio(isMicEnabled)
            btnToggleMic.setImageResource(
                if (isMicEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off
            )
        }

        btnToggleCamera.setOnClickListener {
            isCameraEnabled = !isCameraEnabled
            webRTCManager?.toggleVideo(isCameraEnabled)
            btnToggleCamera.setImageResource(
                if (isCameraEnabled) R.drawable.ic_camera_on else R.drawable.ic_camera_off
            )
            localView.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
        }

        btnSwitchCamera.setOnClickListener { webRTCManager?.switchCamera() }
    }

    private fun checkPermissionsAndStartCall() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )

        if (isVideoCall) permissions.add(Manifest.permission.CAMERA)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else startCall()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCall()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startCall() {
        // Run WebRTC initialization on background thread
        webrtcHandler.post {
            webRTCManager = WebRTCManager(
                context = this,
                sessionId = sessionId,
                isInitiator = isInitiator,
                eglBaseContext = eglBase.eglBaseContext,  // ✅ Pass the SAME context
                onLocalStreamReady = { stream ->
                    runOnUiThread { setupLocalStream(stream) }
                },
                onRemoteStreamAdded = { stream ->
                    runOnUiThread {
                        tvCallStatus.text = "Connected"
                        setupRemoteStream(stream)
                    }
                },
                onCallEnded = { runOnUiThread { finish() } }
            )

            webRTCManager?.initialize(isVideoCall)
        }
    }

    private fun setupLocalStream(stream: MediaStream) {
        if (isVideoCall && stream.videoTracks.isNotEmpty()) {
            val videoTrack = stream.videoTracks[0]
            videoTrack.setEnabled(true)
            videoTrack.addSink(localView)
            android.util.Log.d("CallActivity", "Local video track added to view")
        }
    }

    private fun setupRemoteStream(stream: MediaStream) {
        if (stream.videoTracks.isNotEmpty()) {
            val videoTrack = stream.videoTracks[0]
            videoTrack.setEnabled(true)
            videoTrack.addSink(remoteView)
            android.util.Log.d("CallActivity", "Remote video track added to view")
        }
    }

    private fun endCall() {
        // Clear call status in Firestore
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("peer_session_requests")
            .document(sessionId)
            .update(mapOf(
                "callStatus" to null,
                "callInitiatorUid" to null
            ))
            .addOnSuccessListener {
                android.util.Log.d("CallActivity", "Call status cleared")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("CallActivity", "Failed to clear call status", e)
            }

        // Clean up signaling data
        val rtdb = com.google.firebase.database.FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        rtdb.getReference("call_signals/$sessionId").removeValue()

        // End WebRTC
        webRTCManager?.endCall()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        // End call on WebRTC thread
        webrtcHandler.post {
            try {
                webRTCManager?.endCall()
            } catch (e: Exception) {
                android.util.Log.e("CallActivity", "Error ending call", e)
            }
            webRTCManager = null
        }

        // Clear call status in Firestore
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("peer_session_requests")
            .document(sessionId)
            .update(mapOf(
                "callStatus" to null,
                "callInitiatorUid" to null
            ))

        // Clean up signaling data
        val rtdb = com.google.firebase.database.FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        rtdb.getReference("call_signals/$sessionId").removeValue()

        // Release views on UI thread
        runOnUiThread {
            try {
                localView.release()
                remoteView.release()
                eglBase.release()
            } catch (e: Exception) {
                android.util.Log.e("CallActivity", "Error releasing views", e)
            }
        }

        // Quit the thread
        webrtcThread.quitSafely()
    }
}