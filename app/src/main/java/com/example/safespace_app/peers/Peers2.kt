package com.example.safespace_app.peers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.Peer
import com.example.safespace_app.R

class Peers2 : Fragment() {

    private lateinit var adapter: MessagesAdapter
    private val messagesList = mutableListOf<Peer>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_peers2, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPeers)

        adapter = MessagesAdapter(messagesList) { peer ->
            // Placeholder click action
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadDummyMessages() // load placeholder chats

        return view
    }

    private fun loadDummyMessages() {
        messagesList.clear()
        for (i in 1..3) { // just show 3 placeholder messages
            messagesList.add(
                Peer(
                    uid = i.toString(),
                    name = "Peer $i",
                    photoUrl = "" // will show placeholder image
                )
            )
        }
        adapter.notifyDataSetChanged()
    }
}
