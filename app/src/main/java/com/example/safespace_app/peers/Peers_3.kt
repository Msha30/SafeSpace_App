package com.example.safespace_app.peers

import android.app.Dialog
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Peers_3 : Fragment() {

    companion object {
        fun newInstance() = Peers_3()
    }

    private val viewModel: Peers_3ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_peers_3, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            delay(5000)
            val dialog = Dialog(requireContext())
            dialog.setContentView(R.layout.popup_paired) // your custom layout XML
            dialog.setCancelable(false)
            dialog.show()
            delay(3000)
            if (dialog.isShowing) dialog.dismiss()
            findNavController().navigate(R.id.action_peers_3_to_peers_Chat)
        }
    }
}