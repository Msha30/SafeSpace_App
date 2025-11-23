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
                    Log.d("SignupVerification", "Email verified automatically, creating account...")
                    createUserInFirestoreAndLogin()
                } else {
                    handler.postDelayed(this, pollingInterval)
                }

            }
        }
    }
    private fun saveUserDataLocally() {
        val prefs = requireContext().getSharedPreferences("signup_cache", 0)
        val editor = prefs.edit()

        // Store all arguments passed
        arguments?.keySet()?.forEach { key ->
            editor.putString(key, arguments?.getString(key))
        }

        editor.apply()
    }
    private fun sendCachedUserDataIfNeeded() {
        val prefs = requireContext().getSharedPreferences("signup_cache", 0)
        if (!prefs.contains("email")) return // nothing to send

        val current = auth.currentUser ?: return
        val fire = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val userType = prefs.getString("userType", "student") ?: "student"
        val data = mutableMapOf<String, Any>(
            "uid" to current.uid,
            "email" to current.email!!,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "userType" to userType
        )

        if (userType == "student") {
            data["fname"] = prefs.getString("fname", "")!!
            data["lname"] = prefs.getString("lname", "")!!
            data["program"] = prefs.getString("program", "")!!
            data["username"] = prefs.getString("username", "")!!
            data["studentId"] = prefs.getString("studentId", "")!!
        }

        if (userType == "peer") {
            data["fname"] = prefs.getString("fname", "")!!
            data["lname"] = prefs.getString("lname", "")!!
            data["username"] = prefs.getString("username", "")!!
        }

        fire.collection("account_details")
            .document(current.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("SignupVerification", "Cached user data uploaded to Firestore")
                // clear the cache
                prefs.edit().clear().apply()
            }
            .addOnFailureListener { e ->
                Log.e("SignupVerification", "Failed to upload cached user data: ${e.message}")
            }
    }


    private fun createUserInFirestoreAndLogin() {
        val fire = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val current = auth.currentUser ?: return

        fire.collection("account_details")
            .document(current.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Firestore missing user data → send cached data
                    sendCachedUserDataIfNeeded()
                }
                // proceed to login
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
            .addOnFailureListener {
                Log.e("SignupVerification", "Failed to check Firestore: ${it.message}")
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
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
        saveUserDataLocally()
        handler.post(autoCheckRunnable)

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
                    createUserInFirestoreAndLogin()

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
