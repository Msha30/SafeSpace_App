package com.example.safespace_app.peers

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.MainNavigation2
import com.example.safespace_app.Peer
import com.example.safespace_app.R
import com.example.safespace_app.cache.UserCache
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Peers2 : Fragment() {

    private lateinit var adapter: MessagesAdapter
    private val messagesList = mutableListOf<Peer>()
    private val pairingManager = PairingManager()
    private val peerUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private var currentDialog: AlertDialog? = null

    // Requests already shown **inside this fragment session**
    private val shownRequests = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_peers2, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPeers)

        adapter = MessagesAdapter(messagesList) { peer ->
            Log.d("Peers2", "Clicked on peer: ${peer.name}")
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadDummyMessages()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UserCache.loadPeers()

        registerPairingCallback()

        // Allow small delay so fragment is fully active before checking requests
        lifecycleScope.launch {
            delay(200)
            checkForPendingRequests()
        }
    }

    // ------------------------------------------------------------
    // Register callback from Activity
    // ------------------------------------------------------------
    private fun registerPairingCallback() {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Registering pairing request callback")

        activity.onPairingRequestReceived = { requestId, studentUid ->
            if (!shownRequests.contains(requestId)) {
                Log.d("Peers2", "Incoming request while visible: $requestId")
                showPairingRequestDialog(requestId, studentUid)
            } else {
                Log.d("Peers2", "Request $requestId already shown (live), ignoring")
            }
        }
    }

    // ------------------------------------------------------------
    // Check stored pending requests (from activity)
    // ------------------------------------------------------------
    private fun checkForPendingRequests() {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Checking pending stored requests")

        activity.checkPendingRequests { requestId, studentUid ->
            if (!shownRequests.contains(requestId)) {
                Log.d("Peers2", "Pending request found: $requestId → showing dialog")
                showPairingRequestDialog(requestId, studentUid)
            } else {
                Log.d("Peers2", "Pending request $requestId already shown earlier")
            }
        }
    }

    private fun unregisterPairingCallback() {
        val activity = requireActivity() as? MainNavigation2 ?: return
        activity.onPairingRequestReceived = null
        Log.d("Peers2", "Callback unregistered on fragment destroy")
    }

    // ------------------------------------------------------------
    // Dialog showing
    // ------------------------------------------------------------
    private fun showPairingRequestDialog(requestId: String, studentUid: String) {
        if (!isAdded || context == null) return

        shownRequests.add(requestId)

        Log.d("Peers2", "Displaying pairing dialog for: $requestId")

        currentDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.popup_request, null)
        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)
        val btnYes = dialogView.findViewById<MaterialButton>(R.id.btnyes)
        val btnNo = dialogView.findViewById<MaterialButton>(R.id.btnno)

        nameView.text = "Student"
        photoView.setImageResource(R.drawable.img_placeholder)

        fetchStudentInfo(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.img_placeholder)
                        .error(R.drawable.img_placeholder)
                        .into(photoView)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnYes.setOnClickListener {
            dialog.dismiss()
            acceptRequest(requestId, studentUid)
        }

        btnNo.setOnClickListener {
            dialog.dismiss()
            declineRequest(requestId)
        }

        try {
            currentDialog = dialog
            dialog.show()
            Log.d("Peers2", "Dialog shown")
        } catch (e: Exception) {
            Log.e("Peers2", "Dialog error", e)
        }
    }

    // ------------------------------------------------------------
    // Accept / Reject
    // ------------------------------------------------------------
    private fun acceptRequest(requestId: String, studentUid: String) {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Accepting request $requestId")

        activity.markRequestHandled(requestId)

        pairingManager.acceptRequest(
            requestId = requestId,
            studentUid = studentUid,
            peerUid = peerUid,
            onSuccess = { sessionId ->
                Log.d("Peers2", "Request accepted → Session: $sessionId")
                showAcceptedDialog(studentUid)
            },
            onFailure = {
                showErrorDialog("Failed to accept pairing request")
            }
        )
    }

    private fun declineRequest(requestId: String) {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Declining request $requestId")

        activity.markRequestHandled(requestId)

        pairingManager.declineRequest(requestId) {
            Log.d("Peers2", "Declined + removed")
        }
    }

    // ------------------------------------------------------------
    // Accepted Dialog
    // ------------------------------------------------------------
    private fun showAcceptedDialog(studentUid: String) {
        if (!isAdded || context == null) return

        val dialogView = layoutInflater.inflate(R.layout.popup_paired, null)
        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)

        nameView.text = "Student"
        photoView.setImageResource(R.drawable.img_placeholder)

        fetchStudentInfo(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this).load(photoUrl).into(photoView)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        lifecycleScope.launch {
            delay(2500)
            if (isAdded && dialog.isShowing) dialog.dismiss()
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private fun showErrorDialog(message: String) {
        if (!isAdded || context == null) return

        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun fetchStudentInfo(studentUid: String, callback: (String, String?) -> Unit) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(studentUid)
            .get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("fname") ?: ""
                val last = doc.getString("lname") ?: ""
                val photo = doc.getString("avatarUrl")

                val name = "$first $last".trim().ifEmpty { "Student" }

                callback(name, photo)
            }
            .addOnFailureListener {
                callback("Student", null)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterPairingCallback()
        currentDialog?.dismiss()
        currentDialog = null
        shownRequests.clear()
        Log.d("Peers2", "Fragment destroyed. Callback cleared.")
    }

    private fun loadDummyMessages() {
        messagesList.clear()
        for (i in 1..3) {
            messagesList.add(Peer(i.toString(), "Peer $i", ""))
        }
        adapter.notifyDataSetChanged()
    }
}
