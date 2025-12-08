package com.example.safespace_app.peers

import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.cache.UserCache

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

        adapter = PeersAdapter { peer ->
            // TODO: navigate to peer info screen
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
