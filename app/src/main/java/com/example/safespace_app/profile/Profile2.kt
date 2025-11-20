package com.example.safespace_app.profile

import android.content.Context
import android.content.Intent
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.example.safespace_app.login.Login
import com.google.firebase.auth.FirebaseAuth

class Profile2 : Fragment() {

    companion object {
        fun newInstance() = Profile2()
    }

    private val viewModel: Profile2ViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val info = view.findViewById<LinearLayout>(R.id.info)
        val notif = view.findViewById<LinearLayout>(R.id.notif)
        val terms = view.findViewById<LinearLayout>(R.id.terms)
        val privacy = view.findViewById<LinearLayout>(R.id.privacy)
        val feedback = view.findViewById<LinearLayout>(R.id.feedback)
        val signout = view.findViewById<LinearLayout>(R.id.signout)
        val name = view.findViewById<TextView>(R.id.name)
        val prefname = view.findViewById<TextView>(R.id.prefname)

        // Load cached user data
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val firstName = prefs.getString("fname", "") ?: ""
        val lastName = prefs.getString("lname", "") ?: ""
        val preferred = prefs.getString("username", "") ?: ""

        name.text = "$firstName $lastName"
        prefname.text = preferred

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


        signout.setOnClickListener {
            // Clear cached user data
            prefs.edit().clear().apply()

            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // Return to Login activity
            val intent = Intent(requireContext(), Login::class.java)
            startActivity(intent)
            requireActivity().finish() // Close main nav so user can't go back
        }
    }
}
