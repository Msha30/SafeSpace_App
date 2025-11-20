package com.example.safespace_app

import android.os.Bundle
import android.view.View
import android.content.Context
import android.content.SharedPreferences
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.safespace_app.databinding.ActivityMainNavigationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainNavigation : AppCompatActivity() {

    private lateinit var binding: ActivityMainNavigationBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_cache", Context.MODE_PRIVATE)

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
