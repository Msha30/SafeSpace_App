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
import com.google.firebase.firestore.FirebaseFirestore

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

        // Store all arguments passed from signup
        arguments?.let { bundle ->
            bundle.keySet().forEach { key ->
                val value = bundle.getString(key)
                if (value != null) {
                    editor.putString(key, value)
                    Log.d("SignupVerification", "Cached: $key = $value")
                }
            }
        }

        editor.apply()
        Log.d("SignupVerification", "User data cached locally")
    }

    private fun createUserInFirestoreAndLogin() {
        val fire = FirebaseFirestore.getInstance()
        val current = auth.currentUser ?: return

        fire.collection("account_details")
            .document(current.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Firestore missing user data → send cached data NOW
                    Log.d("SignupVerification", "User data not in Firestore, uploading cached data...")
                    uploadCachedUserData()
                } else {
                    Log.d("SignupVerification", "User data already exists in Firestore")
                }
                // Proceed to login
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
            .addOnFailureListener {
                Log.e("SignupVerification", "Failed to check Firestore: ${it.message}")
                // Try to upload anyway
                uploadCachedUserData()
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
    }

    private fun uploadCachedUserData() {
        val prefs = requireContext().getSharedPreferences("signup_cache", 0)
        val current = auth.currentUser ?: return
        val fire = FirebaseFirestore.getInstance()

        // Check if we have cached data
        if (!prefs.contains("userType")) {
            Log.e("SignupVerification", "No cached user data found!")
            return
        }

        val userType = prefs.getString("userType", "student") ?: "student"

        val data = mutableMapOf<String, Any>(
            "uid" to current.uid,
            "email" to (current.email ?: prefs.getString("email", "")!!),
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "userType" to userType
        )

        // Add type-specific fields
        when (userType) {
            "student" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["username"] = prefs.getString("username", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d("SignupVerification", "Uploading STUDENT data: fname=${data["fname"]}, lname=${data["lname"]}, program=${data["program"]}, username=${data["username"]}, studentId=${data["studentId"]}")
            }
            "peer" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["year_lvl"] = prefs.getString("year_lvl", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d("SignupVerification", "Uploading PEER data: fname=${data["fname"]}, lname=${data["lname"]}, program=${data["program"]}, year_lvl=${data["year_lvl"]}, studentId=${data["studentId"]}")
            }
        }

        // Upload to Firestore
        fire.collection("account_details")
            .document(current.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("SignupVerification", "✅ Cached user data successfully uploaded to Firestore")
                // Clear the cache after successful upload
                prefs.edit().clear().apply()
            }
            .addOnFailureListener { e ->
                Log.e("SignupVerification", "❌ Failed to upload cached user data: ${e.message}")
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

        // Disable back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Do nothing
        }

        // Save user data locally FIRST
        saveUserDataLocally()

        // Start auto-checking for email verification
        handler.post(autoCheckRunnable)

        emailTv = view.findViewById(R.id.email)
        resendBtn = view.findViewById(R.id.resendcode)
        verifyBtn = view.findViewById(R.id.btn)

        val email = arguments?.getString("email") ?: "your@email.com"
        emailTv.text = email

        user = auth.currentUser

        // Manual verify button
        verifyBtn.setOnClickListener {
            user?.reload()?.addOnCompleteListener {
                val isVerified = user?.isEmailVerified ?: false
                if (isVerified) {
                    Log.d("SignupVerification", "Email verified, proceeding to create account")
                    createUserInFirestoreAndLogin()
                } else {
                    Log.d("SignupVerification", "Email not verified yet")
                    verifyBtn.text = "Not verified yet"
                }
            }
        }

        // Resend verification email
        resendBtn.setOnClickListener {
            user?.sendEmailVerification()?.addOnSuccessListener {
                Log.d("SignupVerification", "Resent verification email to $email")
                startResendCooldown()
            }?.addOnFailureListener { e ->
                Log.e("SignupVerification", "Failed to resend verification email: ${e.message}")
            }
        }
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
        handler.removeCallbacks(autoCheckRunnable)
    }
}