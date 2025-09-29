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

class Home : Fragment() {

    companion object {
        fun newInstance() = Home()
    }

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You can initialize data here, but not UI elements yet
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val btnPeerSupport = view.findViewById<ShapeableImageView>(R.id.peersupport)
        val btnCounseling = view.findViewById<ShapeableImageView>(R.id.counseling)

        btnCounseling.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_homeCounseling)
        }

        btnPeerSupport.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_homePeerSupport)
        }
    }
}
