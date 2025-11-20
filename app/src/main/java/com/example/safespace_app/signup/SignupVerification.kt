package com.example.safespace_app.signup

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.example.safespace_app.R
import com.example.safespace_app.login.Login
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class SignupVerification : Fragment() {

    private lateinit var resendBtn: MaterialButton
    private lateinit var verifyBtn: MaterialButton
    private lateinit var emailTv: TextView
    private val cooldownMillis = 30_000L // 30 seconds cooldown

    private val auth = FirebaseAuth.getInstance()
    private var user = auth.currentUser

    private val handler = Handler()
    private val pollingInterval = 3000L // 3 seconds
    private val autoCheckRunnable = object : Runnable {
        override fun run() {
            user?.reload()?.addOnCompleteListener {
                val isVerified = user?.isEmailVerified ?: false
                if (isVerified) {
                    Log.d("SignupVerification", "Email verified automatically, proceeding to Login")
                    startActivity(Intent(requireContext(), Login::class.java))
                    requireActivity().finish()
                } else {
                    handler.postDelayed(this, pollingInterval)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_verification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Disable back button ---
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Do nothing
        }

        emailTv = view.findViewById(R.id.email)
        resendBtn = view.findViewById(R.id.resendcode)
        verifyBtn = view.findViewById(R.id.btn)

        val email = arguments?.getString("email") ?: "your@email.com"
        emailTv.text = email

        user = auth.currentUser

        // --- Manual verify button ---
        verifyBtn.setOnClickListener {
            user?.reload()?.addOnCompleteListener {
                val isVerified = user?.isEmailVerified ?: false
                if (isVerified) {
                    Log.d("SignupVerification", "Email verified, proceeding to Login")
                    startActivity(Intent(requireContext(), Login::class.java))
                    requireActivity().finish()
                } else {
                    Log.d("SignupVerification", "Email not verified yet")
                    verifyBtn.text = "Not verified yet"
                }
            }
        }

        // --- Resend verification email ---
        resendBtn.setOnClickListener {
            user?.sendEmailVerification()?.addOnSuccessListener {
                Log.d("SignupVerification", "Resent verification email to $email")
                startResendCooldown()
            }?.addOnFailureListener { e ->
                Log.e("SignupVerification", "Failed to resend verification email: ${e.message}")
            }
        }

        // --- Start auto-checking ---
        handler.post(autoCheckRunnable)
    }

    private fun startResendCooldown() {
        resendBtn.isEnabled = false
        resendBtn.setBackgroundColor(resources.getColor(R.color.navgrey, null))
        object : CountDownTimer(cooldownMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                resendBtn.text = "Resend (${millisUntilFinished / 1000}s)"
            }
            override fun onFinish() {
                resendBtn.isEnabled = true
                resendBtn.text = "Resend Code"
                resendBtn.setBackgroundResource(android.R.color.transparent)
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(autoCheckRunnable) // stop polling when fragment is destroyed
    }
}
