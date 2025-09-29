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

class HomePeerSupportForm2 : Fragment() {

    companion object {
        fun newInstance() = HomePeerSupportForm2()
    }

    private val viewModel: HomePeerSupportForm2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home_peer_support_form2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnsubmit)
        btnSubmit.setOnClickListener {
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
