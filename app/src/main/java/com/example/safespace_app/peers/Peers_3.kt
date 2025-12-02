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

class Peers_3 : Fragment() {

    private val pairingManager = PairingManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private var hasAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load peers before pairing
        UserCache.loadPeers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_peers_3, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wait until peers + presence loaded
        UserCache.peersLiveData.observe(viewLifecycleOwner, Observer { peers ->
            if (!hasAttempted && peers.isNotEmpty()) {
                hasAttempted = true
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(600) // ensures isOnline updated
                    attemptPairing()
                }
            }
        })
    }

    private fun attemptPairing() {
        pairingManager.pairStudent(
            studentUid,
            onPaired = { sessionId, peerUid ->
                showSuccessDialog(peerUid) {
                    findNavController().navigate(R.id.action_peers_3_to_peers_Chat)
                }
            },
            onFailure = {
                showFailureDialog()
            }
        )
    }

    private fun showSuccessDialog(peerUid: String, onDismiss: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.popup_paired, null)
        val peer = UserCache.peersLiveData.value?.find { it.uid == peerUid }

        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)

        nameView.text = peer?.name ?: "Paired!"

        if (!peer?.photoUrl.isNullOrEmpty()) {
            Glide.with(this).load(peer!!.photoUrl).into(photoView)
        } else {
            photoView.setImageResource(R.drawable.img_placeholder)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        lifecycleScope.launch {
            delay(2500)
            dialog.dismiss()
            onDismiss()
        }
    }

    private fun showFailureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.popup_nopair, null)
        val button = dialogView.findViewById<MaterialButton>(R.id.btnout)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        button.setOnClickListener {
            dialog.dismiss()
            findNavController().popBackStack(
                findNavController().graph.startDestinationId,
                false
            )
        }
    }
}
