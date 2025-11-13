package com.example.safespace_app.peers

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton

class Peers_Chat : Fragment() {

    companion object {
        fun newInstance() = Peers_Chat()
    }

    private val viewModel: Peers_ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_peers_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val guide = view.findViewById<MaterialButton>(R.id.guide)
        val peer = view.findViewById<MaterialButton>(R.id.peer)

        guide.setOnClickListener {
            findNavController().navigate(R.id.action_peers_Chat_to_peers_Guide)
        }

        peer.setOnClickListener {
            findNavController().navigate(R.id.action_peers_Chat_to_peers_info)
        }
    }
}