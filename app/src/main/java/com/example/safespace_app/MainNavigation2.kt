package com.example.safespace_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.safespace_app.databinding.ActivityMainNavigation2Binding
import com.google.firebase.Firebase
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.auth

class MainNavigation2 : AppCompatActivity() {

    private lateinit var binding: ActivityMainNavigation2Binding
    private val prefs by lazy { getSharedPreferences("user_cache", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNavigation2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // Refresh token, then init Supabase client
        refreshTokenAndInitSupabase()


        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main_navigation2)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home2, R.id.nav_peers2, R.id.nav_chat, R.id.nav_profile2
            )
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeSchedule, R.id.profNotification2, R.id.profInfo2,
                R.id.appTermsAndConditions2, R.id.appPrivacyPolicy2,
                R.id.homeSessionManagement -> binding.navView.visibility = View.GONE
                else -> binding.navView.visibility = View.VISIBLE
            }
        }

        navView.setupWithNavController(navController)

        // Load user data into cache
        cacheUserDataIfNeeded()
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


    private fun cacheUserDataIfNeeded() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val editor = prefs.edit()

        db.collection("account_details").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val firebaseData = doc.data ?: return@addOnSuccessListener
                var updated = false

                // Loop through all fields in Firestore and update cache if changed
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
                // Optional: handle errors
            }
    }
}
