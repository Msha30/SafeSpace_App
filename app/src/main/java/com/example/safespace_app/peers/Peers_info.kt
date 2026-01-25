package com.example.safespace_app.peers

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.Peer
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.FirebaseFirestore

class Peers_info : Fragment() {

    private lateinit var adapter: PeersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_peers_info, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPhotos)

        val spacingInDp = 7.5f
        val spacingInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            spacingInDp,
            resources.displayMetrics
        ).toInt()

        recyclerView.addItemDecoration(HalfScreenWidthItemDecoration(spacingInPixels))

        val backBtn = view.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        // Initialize Adapter with click listener
        adapter = PeersAdapter { peer ->
            // --- HERE IS THE CONNECTION ---
            showPeerInfoPopup(peer)
        }
        recyclerView.adapter = adapter

        // Observe cached peers
        UserCache.peersLiveData.observe(viewLifecycleOwner) { cachedPeers ->
            adapter.submitList(cachedPeers)
        }

        // Trigger cache load (Firestore + RTDB)
        UserCache.loadPeers()

        return view
    }

    // --- NEW POPUP LOGIC ---
    private fun showPeerInfoPopup(peer: Peer) {
        if (!isAdded || context == null) return

        // 1. Inflate the custom popup layout
        val dialogView = layoutInflater.inflate(R.layout.popup_peerinfo, null)

        // 2. Find Views
        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)
        val statusView = dialogView.findViewById<TextView>(R.id.status)
        val yearView = dialogView.findViewById<TextView>(R.id.year)
        val programView = dialogView.findViewById<TextView>(R.id.program)

        // 3. Initial Data Population
        nameView.text = peer.name

        // Handle Status Color & Text
        if (peer.isOnline) {
            statusView.text = "ONLINE"
            statusView.setBackgroundResource(R.drawable.f_rounded_green)
            statusView.setTextColor(requireContext().getColor(R.color.white))
        } else {
            statusView.text = "OFFLINE"
            statusView.setBackgroundResource(R.drawable.f_rounded_inactive)
            statusView.setTextColor(requireContext().getColor(R.color.textgrey))
        }

        // 4. Handle Avatar (Preset vs URL Logic)
        val photoUrl = peer.photoUrl
        if (photoUrl.isNotEmpty()) {
            if (photoUrl.startsWith("http")) {
                // Peer Side (URL)
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.img_placeholder)
                    .error(R.drawable.img_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // <--- FORCE REFRESH FROM NETWORK
                    .into(photoView)
            } else {
                // Student Side (Preset ID)
                val drawableRes = when (photoUrl) {
                    "image_1" -> R.drawable.avatar_panda
                    "image_2" -> R.drawable.avatar_butterfly
                    "image_3" -> R.drawable.avatar_wolf
                    "image_4" -> R.drawable.avatar_buffalo
                    else -> R.drawable.img_placeholder
                }
                photoView.setImageResource(drawableRes)
            }
        } else {
            photoView.setImageResource(R.drawable.img_placeholder)
        }

        // 5. Fetch Year and Program from Firestore
        // (Peer list might not have these details loaded)
        val db = FirebaseFirestore.getInstance()
        db.collection("account_details").document(peer.uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    yearView.text = doc.getString("year_lvl") ?: "Unknown Year"
                    programView.text = doc.getString("program") ?: "Unknown Program"
                }
            }

        // 6. Create and Show Dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Make background transparent for the "Popup" feel
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        // Close popup when clicking outside the card (clicking the transparent background)
        // Note: This relies on the root view in popup_peer_info.xml having ID popupRoot
        dialogView.findViewById<LinearLayout>(R.id.popupRoot).setOnClickListener {
            // Do nothing (clicking the card itself)
        }
        dialog.findViewById<View>(android.R.id.content).setOnClickListener {
            // Clicking the background
            dialog.dismiss()
        }
    }

    inner class HalfScreenWidthItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position % 2 != 0) {
                outRect.left = spacing
                outRect.right = 0
            } else {
                outRect.left = 0
                outRect.right = spacing
            }
        }
    }
}