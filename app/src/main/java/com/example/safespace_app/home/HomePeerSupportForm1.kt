package com.example.safespace_app.home
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton

class HomePeerSupportForm1 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_peer_support_form1, container, false)

        // Start button (navigate to next form/page)
        val btnStart = rootView.findViewById<MaterialButton>(R.id.btnnext)
        btnStart.setOnClickListener {
            // Make sure you have this action in nav_graph.xml
            findNavController().navigate(R.id.action_homePeerSupportForm1_to_homePeerSupportForm2)
        }

        return rootView
    }
}