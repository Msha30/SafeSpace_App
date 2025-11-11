package com.example.safespace_app.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.safespace_app.R

class SignupPeer : Fragment() {

    private val viewModel: SignupPeerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_peer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Dropdown setup ---
        val items = resources.getStringArray(R.array.yearlevel)
        val autoCompleteTextView = view.findViewById<AutoCompleteTextView>(R.id.yearlevel)

        // --- Button navigation to SignupVerification fragment ---
        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SignupVerification()) // replace with your container ID
                .addToBackStack(null) // so user can press back to return here
                .commit()
        }
    }
}
