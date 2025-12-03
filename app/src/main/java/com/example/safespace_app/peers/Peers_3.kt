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
import com.example.safespace_app.cache.UserCache
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import android.widget.TextView
import android.util.Log

class Peers_3 : Fragment() {

    private val pairingManager = PairingManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private var hasAttempted = false
    private var pairingInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Peers_3", "Fragment created, loading peers...")
        // Load peers before pairing
        UserCache.loadPeers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_peers_3, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("Peers_3", "View created, observing peers...")

        // First check if student already has an active session
        pairingManager.getActiveSession(studentUid) { sessionId, peerUid ->
            if (sessionId != null && peerUid != null) {
                Log.d("Peers_3", "Active session found: $sessionId")
                // Already paired, navigate to chat
                showSuccessDialog(peerUid) {
                    navigateToChat()
                }
            } else {
                // No active session, proceed with pairing
                observePeersAndPair()
            }
        }
    }

    private fun observePeersAndPair() {
        // Wait until peers + presence loaded
        UserCache.peersLiveData.observe(viewLifecycleOwner, Observer { peers ->
            Log.d("Peers_3", "Peers updated: ${peers.size} total")
            val onlineCount = peers.count { it.isOnline }
            Log.d("Peers_3", "Online peers: $onlineCount")

            if (!hasAttempted && !pairingInProgress && peers.isNotEmpty()) {
                hasAttempted = true
                pairingInProgress = true

                viewLifecycleOwner.lifecycleScope.launch {
                    // Give extra time for presence to fully sync
                    delay(800)
                    attemptPairing()
                }
            }
        })
    }

    private fun attemptPairing() {
        Log.d("Peers_3", "Attempting to pair student: $studentUid")

        pairingManager.pairStudent(
            studentUid,
            onPaired = { sessionId, peerUid ->
                pairingInProgress = false
                Log.d("Peers_3", "Successfully paired with peer: $peerUid")
                showSuccessDialog(peerUid) {
                    navigateToChat()
                }
            },
            onFailure = {
                pairingInProgress = false
                Log.d("Peers_3", "Pairing failed")
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

    private fun showFailureDialog() {
        if (!isAdded || context == null) return

        val dialogView = layoutInflater.inflate(R.layout.popup_nopair, null)
        val button = dialogView.findViewById<MaterialButton>(R.id.btnout)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        try {
            dialog.show()

            button.setOnClickListener {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                navigateBack()
            }
        } catch (e: Exception) {
            Log.e("Peers_3", "Error showing failure dialog", e)
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
                findNavController().popBackStack(
                    findNavController().graph.startDestinationId,
                    false
                )
            } catch (e: Exception) {
                Log.e("Peers_3", "Navigation error", e)
            }
        }
    }

    override fun onDestroyView() {
        hasAttempted = false
        pairingInProgress = false
        super.onDestroyView()
    }
}