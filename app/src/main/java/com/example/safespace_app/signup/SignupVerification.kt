package com.example.safespace_app.signup

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.example.safespace_app.R
import com.example.safespace_app.login.Login
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupVerification : Fragment() {

    private val TAG = "SignupVerification"
    private lateinit var emailTv: TextView
    private lateinit var titleVerify: TextView

    private val auth = FirebaseAuth.getInstance()
    private var user = auth.currentUser

    private val handler = Handler()
    private val pollingInterval = 3000L // 3 seconds
    private val autoCheckTimeout = 300_000L // 5 minutes (extended for better UX)

    private var autoCheckCount = 0
    private val maxAutoCheckCount = (autoCheckTimeout / pollingInterval).toInt()

    private val autoCheckRunnable = object : Runnable {
        override fun run() {
            autoCheckCount++
            Log.d(TAG, "Auto-check #$autoCheckCount of $maxAutoCheckCount")

            user?.reload()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val isVerified = user?.isEmailVerified ?: false
                    Log.d(TAG, "Email verification status: $isVerified")

                    if (isVerified) {
                        Log.d(TAG, "✅ Email verified automatically!")
                        handler.removeCallbacks(this)
                        showEmailVerifiedPopup()
                    } else {
                        if (autoCheckCount < maxAutoCheckCount) {
                            Log.d(TAG, "Not verified yet, checking again in ${pollingInterval}ms")
                            handler.postDelayed(this, pollingInterval)
                        } else {
                            Log.d(TAG, "⏰ Timeout reached, stopping auto-check")
                            handler.removeCallbacks(this)
                            showTimeoutMessage()
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to reload user: ${task.exception?.message}")
                    if (autoCheckCount < maxAutoCheckCount) {
                        handler.postDelayed(this, pollingInterval)
                    } else {
                        showTimeoutMessage()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Inflating fragment layout")
        return inflater.inflate(R.layout.fragment_signup_verification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Starting verification fragment setup")

        user = auth.currentUser

        if (user == null) {
            Log.e(TAG, "❌ No authenticated user found! Redirecting to login...")
            startActivity(Intent(requireContext(), Login::class.java))
            requireActivity().finish()
            return
        }

        Log.d(TAG, "Current user: ${user?.email} (UID: ${user?.uid})")
        Log.d(TAG, "Email verified status: ${user?.isEmailVerified}")

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            Log.d(TAG, "Back button pressed, navigating to Login activity")
            startActivity(Intent(requireContext(), Login::class.java))
            requireActivity().finish()
        }

        // Save user data locally
        saveUserDataLocally()

        // Initialize views
        emailTv = view.findViewById(R.id.email)
        titleVerify = view.findViewById(R.id.titleVerify)

        val email = arguments?.getString("email") ?: user?.email ?: "your@email.com"
        emailTv.text = email
        Log.d(TAG, "Email address set to: $email")

        // Send verification email immediately
        Log.d(TAG, "Sending verification email automatically...")
        user?.sendEmailVerification()?.addOnSuccessListener {
            Log.d(TAG, "✅ Verification email sent to ${user?.email}")
        }?.addOnFailureListener { e ->
            Log.e(TAG, "❌ Failed to send verification email: ${e.message}")
            e.printStackTrace()
        }

        // Start auto-checking immediately
        Log.d(TAG, "🔄 Starting auto-check polling (every ${pollingInterval}ms for ${autoCheckTimeout}ms)")
        handler.postDelayed(autoCheckRunnable, pollingInterval)
    }

    private fun showTimeoutMessage() {
        Log.d(TAG, "=== TIMEOUT REACHED - VERIFICATION NOT DETECTED ===")
        titleVerify.text = "Verification timeout"
        emailTv.text = "Please check your email and try signing in after verifying."

        // Optionally redirect to login after a delay
        handler.postDelayed({
            Log.d(TAG, "Redirecting to login after timeout")
            startActivity(Intent(requireContext(), Login::class.java))
            requireActivity().finish()
        }, 3000)
    }

    private fun showEmailVerifiedPopup() {
        Log.d(TAG, "=== SHOWING EMAIL VERIFIED POPUP ===")

        handler.removeCallbacks(autoCheckRunnable)
        Log.d(TAG, "Stopped automatic verification polling")

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.popup_emailverified)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        Log.d(TAG, "Email verified dialog created and configured")

        val btnOkay = dialog.findViewById<MaterialButton>(R.id.btnokay)
        btnOkay.setOnClickListener {
            Log.d(TAG, "Okay button clicked in popup")
            dialog.dismiss()
            Log.d(TAG, "Dialog dismissed")

            createUserInFirestoreAndLogin()
        }

        dialog.show()
        Log.d(TAG, "Email verified dialog shown to user")
    }

    private fun saveUserDataLocally() {
        Log.d(TAG, "=== SAVING USER DATA LOCALLY ===")
        val prefs = requireContext().getSharedPreferences("signup_cache", 0)
        val editor = prefs.edit()

        arguments?.let { bundle ->
            Log.d(TAG, "Processing ${bundle.keySet().size} arguments from bundle")
            bundle.keySet().forEach { key ->
                val value = bundle.getString(key)
                if (value != null) {
                    editor.putString(key, value)
                    Log.d(TAG, "Cached: $key = $value")
                }
            }
        }

        editor.apply()
        Log.d(TAG, "✅ User data cached locally in SharedPreferences")
    }

    private fun createUserInFirestoreAndLogin() {
        Log.d(TAG, "=== CREATING USER IN FIRESTORE ===")
        val fire = FirebaseFirestore.getInstance()
        val current = auth.currentUser

        if (current == null) {
            Log.e(TAG, "❌ Cannot create user - current user is null")
            return
        }

        Log.d(TAG, "Current user UID: ${current.uid}")

        fire.collection("account_details")
            .document(current.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.d(TAG, "User data not found in Firestore, uploading cached data...")
                    uploadCachedUserData()
                } else {
                    Log.d(TAG, "✅ User data already exists in Firestore")
                }

                Log.d(TAG, "Signing out user before navigating to Login")
                auth.signOut()

                Log.d(TAG, "Navigating to Login activity")
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to check Firestore: ${e.message}")
                uploadCachedUserData()

                Log.d(TAG, "Signing out user before navigating to Login")
                auth.signOut()

                Log.d(TAG, "Navigating to Login activity despite error")
                startActivity(Intent(requireContext(), Login::class.java))
                requireActivity().finish()
            }
    }

    private fun uploadCachedUserData() {
        Log.d(TAG, "=== UPLOADING CACHED USER DATA ===")
        val prefs = requireContext().getSharedPreferences("signup_cache", 0)
        val current = auth.currentUser

        if (current == null) {
            Log.e(TAG, "❌ Cannot upload - current user is null")
            return
        }

        val fire = FirebaseFirestore.getInstance()

        if (!prefs.contains("userType")) {
            Log.e(TAG, "❌ No cached user data found in SharedPreferences!")
            return
        }

        val userType = prefs.getString("userType", "student") ?: "student"
        Log.d(TAG, "User type: $userType")

        val data = mutableMapOf<String, Any>(
            "uid" to current.uid,
            "email" to (current.email ?: prefs.getString("email", "")!!),
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "userType" to userType
        )

        when (userType) {
            "student" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["username"] = prefs.getString("username", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d(TAG, "STUDENT data prepared: fname=${data["fname"]}, lname=${data["lname"]}, program=${data["program"]}, username=${data["username"]}, studentId=${data["studentId"]}")
            }
            "peer" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["year_lvl"] = prefs.getString("year_lvl", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d(TAG, "PEER data prepared: fname=${data["fname"]}, lname=${data["lname"]}, program=${data["program"]}, year_lvl=${data["year_lvl"]}, studentId=${data["studentId"]}")
            }
        }

        Log.d(TAG, "Uploading to Firestore collection 'account_details', document: ${current.uid}")
        fire.collection("account_details")
            .document(current.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅✅✅ Cached user data successfully uploaded to Firestore")
                prefs.edit().clear().apply()
                Log.d(TAG, "Cleared cached data from SharedPreferences")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌❌❌ Failed to upload cached user data: ${e.message}")
                e.printStackTrace()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "=== onDestroyView: Cleaning up ===")
        handler.removeCallbacks(autoCheckRunnable)
        Log.d(TAG, "Removed all callbacks")
    }
}