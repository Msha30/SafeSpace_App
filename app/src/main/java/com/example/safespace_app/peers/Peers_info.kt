package com.example.safespace_app.peers

import android.os.Bundle
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
}
