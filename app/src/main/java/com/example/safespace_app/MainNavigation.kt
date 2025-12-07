package com.example.safespace_app

import android.os.Bundle
import android.view.View
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.WindowManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.safespace_app.databinding.ActivityMainNavigationBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainNavigation : AppCompatActivity() {
    lateinit var presenceManager: PresenceManager
    private lateinit var binding: ActivityMainNavigationBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        super.onCreate(savedInstanceState)

        binding = ActivityMainNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_cache", Context.MODE_PRIVATE)

        // Refresh token, then init Supabase client
        refreshTokenAndInitSupabase()

        val auth = FirebaseAuth.getInstance()
        presenceManager = PresenceManager(auth)
        presenceManager.startTracking()

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main_navigation)

        // Hide bottom nav for certain destinations
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_peers, R.id.nav_chat, R.id.nav_profile
            )
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeCounseling,R.id.homeCounselingForm,R.id.homePeerSupportForm1,
                R.id.homePeerSupportForm2,R.id.profInfo, R.id.profFeedback, R.id.profNotification,
                R.id.appTermsAndConditions, R.id.appPrivacyPolicy, R.id.profChangeAvatar,
                R.id.homePeerSupport -> binding.navView.visibility = View.GONE
                else -> binding.navView.visibility = View.VISIBLE
            }
        }

        navView.setupWithNavController(navController)

        // Load user data into cache
        loadUserData()
    }
    fun hideBottomNav() {
        binding.navView.visibility = View.GONE
    }

    fun showBottomNav() {
        binding.navView.visibility = View.VISIBLE
    }

    private fun refreshTokenAndInitSupabase() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Log.w("MainNavigation", "No Firebase user signed in — cannot init Supabase")
            return
        }

        // Force refresh to ensure custom claims are included
        currentUser.getIdToken(true)
            .addOnSuccessListener { result: GetTokenResult ->
                val claims = result.claims
                val role = claims["role"] as? String
                Log.d("MainNavigation", "Firebase ID token claims: $claims")

                if (role == "authenticated") {
                    // Good — initialize Supabase
                    SupaClient.init()
                } else {
                    Log.w("MainNavigation", "User missing 'authenticated' role claim — you may need to assign it on backend")
                    // Optionally handle this case: e.g. show message or redirect
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainNavigation", "Failed to refresh token", e)
                // Handle: maybe sign out user or retry
            }
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val editor = prefs.edit()

        db.collection("account_details").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val firebaseData = doc.data ?: return@addOnSuccessListener
                var updated = false

                // Loop through all keys in the document
                for ((key, value) in firebaseData) {
                    val cachedValue = prefs.all[key]
                    if (cachedValue != value.toString()) {
                        editor.putString(key, value.toString())
                        updated = true
                    }
                }

                if (updated) editor.apply()
            }
            .addOnFailureListener {
                // Optional: handle failure
            }
    }
}
