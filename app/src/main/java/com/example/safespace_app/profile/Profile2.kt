package com.example.safespace_app.profile

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R

class Profile2 : Fragment() {

    companion object {
        fun newInstance() = Profile2()
    }

    private val viewModel: Profile2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val info = view.findViewById<LinearLayout>(R.id.info)
        val notif = view.findViewById<LinearLayout>(R.id.notif)
        val terms = view.findViewById<LinearLayout>(R.id.terms)
        val privacy = view.findViewById<LinearLayout>(R.id.privacy)
        val feedback = view.findViewById<LinearLayout>(R.id.feedback)
        //val signout = view.findViewById<LinearLayout>(R.id.signout)

        info.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile2_to_profInfo2)
        }

        notif.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile2_to_profNotification2)
        }

        terms.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile2_to_appTermsAndConditions2)
        }

        privacy.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile2_to_appPrivacyPolicy2)
        }

    }
}