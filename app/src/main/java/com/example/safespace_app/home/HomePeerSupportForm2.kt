package com.example.safespace_app.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R

class HomePeerSupportForm2 : Fragment(R.layout.fragment_home_peer_support_form2) {

    private var selectedPeerUid: String? = null
    private var preferredMode: String? = null
    private var topicOfConcern: String? = null
    private var generalConcernText: String? = null

    private val viewModel: HomePeerSupportForm2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from Bundle
        arguments?.let { bundle ->
            selectedPeerUid = bundle.getString("selectedPeerUid")
            preferredMode = bundle.getString("preferredMode")
            topicOfConcern = bundle.getString("topicOfConcern")
            generalConcernText = bundle.getString("generalConcernText")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // You can now use the data for display or submission
        // e.g., viewModel.submitSession(selectedPeerUid, preferredMode, topicOfConcern, generalConcernText)

        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnsubmit)
        btnSubmit.setOnClickListener {
            // Navigate back to home and clear back stack
            findNavController().navigate(
                findNavController().graph.startDestinationId,
                null,
                androidx.navigation.navOptions {
                    popUpTo(findNavController().graph.startDestinationId) { inclusive = true }
                }
            )
        }
    }
}

