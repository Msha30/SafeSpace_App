package com.example.safespace_app.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.safespace_app.R

class SignupPeer : Fragment() {

    companion object {
        fun newInstance() = SignupPeer()
    }

    private val viewModel: SignupPeerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_peer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dropdown setup
        val items = resources.getStringArray(R.array.yearlevel)
        val adapter = ArrayAdapter(requireContext(), R.layout.f_list_item, items)

        val autoCompleteTextView = view.findViewById<AutoCompleteTextView>(R.id.yearlevel)
        autoCompleteTextView.setAdapter(adapter)
    }
}
