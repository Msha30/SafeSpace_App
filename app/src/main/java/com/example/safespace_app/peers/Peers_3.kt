package com.example.safespace_app.peers

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import android.widget.TextView
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.database.ValueEventListener

class Peers_3 : Fragment() {

    private val pairingManager = PairingManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private var hasAttempted = false
    private var pairingInProgress = false
    private var requestListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Peers_3", "Fragment created, loading peers...")
        UserCache.loadPeers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_peers_3, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("Peers_3", "View created, checking for active session...")

        // --- WATCH SESSION ---
        UserCache.watchSession(studentUid)
        UserCache.sessionLiveData.observe(viewLifecycleOwner, Observer { session ->
            if (session != null) {
                val (sessionId, peerUid) = session
                Log.d("Peers_3", "Session active: $sessionId with peer: $peerUid")
                // Show dialog + navigate
                showSuccessDialog(peerUid) { navigateToChat() }
            }
        })

        // --- CHECK IF ACTIVE SESSION EXISTS BEFORE SENDING REQUEST ---
        pairingManager.getActiveSession(studentUid) { sessionId, peerUid ->
            // Check if fragment is still added and view exists
            if (!isAdded || view == null) {
                Log.d("Peers_3", "Fragment not added or view destroyed, ignoring callback")
                return@getActiveSession
            }

            if (sessionId != null && peerUid != null) {
                Log.d("Peers_3", "Active session found: $sessionId")
                showSuccessDialog(peerUid) { navigateToChat() }
            } else {
                observePeersAndSendRequest()
            }
        }
    }

    private fun observePeersAndSendRequest() {
        // Additional safety check
        if (!isAdded || view == null) {
            Log.d("Peers_3", "Cannot observe peers, view not ready")
            return
        }

        UserCache.peersLiveData.observe(viewLifecycleOwner, Observer { peers ->
            val onlineCount = peers.count { it.isOnline }
            Log.d("Peers_3", "Peers updated: ${peers.size}, online: $onlineCount")

            if (!hasAttempted && !pairingInProgress && peers.isNotEmpty()) {
                hasAttempted = true
                pairingInProgress = true

                viewLifecycleOwner.lifecycleScope.launch {
                    delay(800) // small delay to allow presence sync
                    sendPairingRequest()
                }
            }
        })
    }

    private fun sendPairingRequest() {
        Log.d("Peers_3", "Sending pairing request for student: $studentUid")

        pairingManager.sendPairingRequest(
            studentUid = studentUid,
            onRequestSent = { requestId, peerUid ->
                // Check if fragment is still valid
                if (!isAdded) {
                    Log.d("Peers_3", "Fragment not added, ignoring request sent callback")
                    return@sendPairingRequest
                }

                Log.d("Peers_3", "Request sent: $requestId to peer: $peerUid")

                // Listen for acceptance/decline/timeout
                requestListener = pairingManager.waitForRequestResponse(
                    requestId = requestId,
                    timeoutSeconds = 30,
                    onAccepted = { sessionId, acceptedPeerUid ->
                        if (!isAdded) return@waitForRequestResponse

                        pairingInProgress = false
                        Log.d("Peers_3", "Request accepted! Session: $sessionId")
                        // The session watcher will also pick this up, so dialog + navigation is safe
                    },
                    onDeclined = {
                        if (!isAdded) return@waitForRequestResponse

                        pairingInProgress = false
                        Log.d("Peers_3", "Request declined by peer")
                        showDeclinedDialog()
                    },
                    onTimeout = {
                        if (!isAdded) return@waitForRequestResponse

                        pairingInProgress = false
                        Log.d("Peers_3", "Request timeout - no response within 30 seconds")
                        showTimeoutDialog()
                    }
                )
            },
            onFailure = {
                if (!isAdded) return@sendPairingRequest

                pairingInProgress = false
                Log.d("Peers_3", "Failed to send request - no peers available")
                showFailureDialog()
            }
        )
    }

    private fun showSuccessDialog(peerUid: String, onDismiss: () -> Unit) {
        if (!isAdded || context == null) return

        val dialogView = layoutInflater.inflate(R.layout.popup_paired, null)
        val peer = UserCache.peersLiveData.value?.find { it.uid == peerUid }

        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)

        nameView.text = peer?.name ?: "Paired!"

        if (!peer?.photoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(peer!!.photoUrl)
                .placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder)
                .into(photoView)
        } else {
            photoView.setImageResource(R.drawable.img_placeholder)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        try {
            dialog.show()
            lifecycleScope.launch {
                delay(2500)
                if (isAdded && dialog.isShowing) {
                    dialog.dismiss()
                    onDismiss()
                }
            }
        } catch (e: Exception) {
            Log.e("Peers_3", "Error showing success dialog", e)
            onDismiss()
        }
    }

    private fun showDeclinedDialog() = showGenericPopup(
        "Peer Unavailable",
        "The peer is currently unavailable.\nPlease try again."
    )

    private fun showTimeoutDialog() = showGenericPopup(
        "No Response",
        "The peer didn't respond in time.\nPlease try again."
    )

    private fun showFailureDialog() = showGenericPopup(
        "Failed",
        "Unable to send pairing request.\nNo peers available."
    )

    private fun showGenericPopup(titleText: String, contentText: String) {
        if (!isAdded || context == null) return
        val dialogView = layoutInflater.inflate(R.layout.popup_nopair, null)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val contentView = dialogView.findViewById<TextView>(R.id.content)
        val button = dialogView.findViewById<MaterialButton>(R.id.btnout)

        titleView.text = titleText
        contentView.text = contentText

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        try {
            dialog.show()
            button.setOnClickListener {
                if (dialog.isShowing) dialog.dismiss()
                navigateBack()
            }
        } catch (e: Exception) {
            Log.e("Peers_3", "Error showing popup", e)
            navigateBack()
        }
    }

    private fun navigateToChat() {
        if (isAdded) {
            try {
                findNavController().navigate(R.id.action_peers_3_to_peers_Chat)
            } catch (e: Exception) {
                Log.e("Peers_3", "Navigation error", e)
            }
        }
    }

    private fun navigateBack() {
        if (isAdded) {
            try {
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e("Peers_3", "Navigation error", e)
            }
        }
    }

    override fun onDestroyView() {
        hasAttempted = false
        pairingInProgress = false
        requestListener?.let { pairingManager.removeListener(it) }
        super.onDestroyView()
    }
}