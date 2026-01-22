package com.example.safespace_app.profile

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R

class ProfFeedback : Fragment() {

    companion object {
        fun newInstance() = ProfFeedback()
    }

    private val viewModel: ProfFeedbackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_prof_feedback, container, false)

        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        // Phone number click listener
        val phoneNum = rootView.findViewById<TextView>(R.id.phoneNum)
        phoneNum.setOnClickListener {
            val phoneNumber = phoneNum.text.toString().replace(" ", "")
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to open dialer", Toast.LENGTH_SHORT).show()
            }
        }

        // Email click listener
        val email = rootView.findViewById<TextView>(R.id.Email)
        email.setOnClickListener {
            val emailAddress = email.text.toString()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$emailAddress")
            }
            try {
                startActivity(Intent.createChooser(intent, "Send email via"))
            } catch (e: Exception) {
                Toast.makeText(context, "No email apps available", Toast.LENGTH_SHORT).show()
            }
        }

        // Facebook link click listener
        val fbLink = rootView.findViewById<TextView>(R.id.fbLink)
        fbLink.setOnClickListener {
            val facebookUrl = "https://${fbLink.text}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(facebookUrl)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
            }
        }

        return rootView
    }
}