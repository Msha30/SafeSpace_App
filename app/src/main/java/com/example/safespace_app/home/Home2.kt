package com.example.safespace_app.home

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.imageview.ShapeableImageView

class Home2 : Fragment() {

    companion object {
        fun newInstance() = Home2()
    }

    private val viewModel: Home2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home2, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val btnPeerSupport = view.findViewById<ShapeableImageView>(R.id.peersupport)
        val btnCounseling = view.findViewById<ShapeableImageView>(R.id.counseling)

        btnCounseling.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeCounseling)
        }

        btnPeerSupport.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homePeerSupport)
        }
    }
}
