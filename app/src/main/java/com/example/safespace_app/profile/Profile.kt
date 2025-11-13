package com.example.safespace_app.profile

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.imageview.ShapeableImageView

class Profile : Fragment() {

    companion object {
        fun newInstance() = Profile()
    }

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val pic = view.findViewById<ImageView>(R.id.changebtn)
        val info = view.findViewById<LinearLayout>(R.id.info)
        val notif = view.findViewById<LinearLayout>(R.id.notif)
        val terms = view.findViewById<LinearLayout>(R.id.terms)
        val privacy = view.findViewById<LinearLayout>(R.id.privacy)
        val feedback = view.findViewById<LinearLayout>(R.id.feedback)
        //val signout = view.findViewById<LinearLayout>(R.id.signout)

        pic.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_profChangeAvatar)
        }

        info.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_profInfo)
        }

        notif.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_profNotification)
        }

        terms.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_appTermsAndConditions)
        }

        privacy.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_appPrivacyPolicy)
        }

        feedback.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_profFeedback)
        }



    }
}