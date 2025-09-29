package com.example.safespace_app.home

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton

class HomeCounselingForm : Fragment() {

    companion object {
        fun newInstance() = HomeCounselingForm()
    }

    private val viewModel: HomeCounselingFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home_counseling_form, container, false)
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