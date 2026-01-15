package com.example.safespace_app.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.login.Login
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth

class Profile2 : Fragment() {

    companion object {
        fun newInstance() = Profile2()
    }

    // ✅ Shared ViewModel across Profile, Profile2, and ProfChangeAvatar
    private val viewModel: ProfileViewModel by activityViewModels()

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
        val profilePhoto = view.findViewById<ShapeableImageView>(R.id.photo)

        // Load cached user data
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val firstName = prefs.getString("fname", "") ?: ""
        val lastName = prefs.getString("lname", "") ?: ""
        val preferred = prefs.getString("username", "") ?: ""

        // Observe avatar changes from shared ViewModel
        viewModel.avatarUrl.observe(viewLifecycleOwner) { avatarUrl ->
            Glide.with(this)
                .load(avatarUrl.takeIf { it.isNotEmpty() })
                .placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder)
                .into(profilePhoto)
        }

        // Load avatar once (from cache or Firestore)
        viewModel.loadAvatar()

        name.text = "$firstName $lastName"
        prefname.text = preferred

        info.setOnClickListener { findNavController().navigate(R.id.action_nav_profile2_to_profInfo2) }
        notif.setOnClickListener { findNavController().navigate(R.id.action_nav_profile2_to_profNotification2) }
        terms.setOnClickListener { findNavController().navigate(R.id.action_nav_profile2_to_appTermsAndConditions2) }
        privacy.setOnClickListener { findNavController().navigate(R.id.action_nav_profile2_to_appPrivacyPolicy2) }

        signout.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.popup_logout, null)
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create()

            val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btncancel)
            val btnOut = dialogView.findViewById<MaterialButton>(R.id.btnout)

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnOut.setOnClickListener {
                dialog.dismiss()
                // Clear cached user data
                requireContext().getSharedPreferences("signup_cache", Context.MODE_PRIVATE).edit().clear().apply()
                requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE).edit().clear().apply()
                FirebaseAuth.getInstance().signOut()

                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
            dialog.show()
        }
    }
}
