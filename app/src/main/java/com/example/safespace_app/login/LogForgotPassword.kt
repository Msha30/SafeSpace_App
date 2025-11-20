package com.example.safespace_app.login

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LogForgotPassword : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_log_forgot_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val emailInput = view.findViewById<TextInputEditText>(R.id.emailInput)
        val sendBtn = view.findViewById<MaterialButton>(R.id.btnSendReset)
        val resendBtn = view.findViewById<MaterialButton>(R.id.resendBtn)

        fun triggerReset() {
            val email = emailInput.text.toString().trim()

            // Empty
            if (email.isEmpty()) {
                emailInput.error = "Please enter your email"
                emailInput.requestFocus()
                return
            }

            // Basic format
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Invalid email format"
                emailInput.requestFocus()
                return
            }

            // Domain check
            if (!email.endsWith(".edu.ph")) {
                emailInput.error = "Email must be .edu.ph"
                emailInput.requestFocus()
                return
            }

            // Attempt to send reset email
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            requireContext(),
                            "Reset email sent! Check your inbox.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Return to Login activity
                        val intent = Intent(requireContext(), Login::class.java)
                        startActivity(intent)

                        // Close the current activity so back doesn't return here
                        requireActivity().finish()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        sendBtn.setOnClickListener { triggerReset() }
        resendBtn.setOnClickListener { triggerReset() }
    }
}
