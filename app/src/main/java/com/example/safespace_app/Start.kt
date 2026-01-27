package com.example.safespace_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.login.Login
import com.example.safespace_app.signup.Signup
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.example.safespace_app.profile.NotificationPermissionHelper
import com.example.safespace_app.profile.NotificationSettingsManager
import com.google.android.material.snackbar.Snackbar

class Start : AppCompatActivity() {

    private var hasAskedForPermission = false

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("Start", "Notification permission granted")
        } else {
            Log.d("Start", "Notification permission denied")
            // Show message explaining they can enable it later
            Snackbar.make(
                findViewById(android.R.id.content),
                "Notifications disabled. You can enable them in settings.",
                Snackbar.LENGTH_LONG
            ).setAction("Settings") {
                NotificationPermissionHelper.openAppSettings(this)
            }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        // Initialize notification channel
        NotificationSettingsManager.createNotificationChannel(this)

        // Request permission after a short delay (better UX)
        window.decorView.postDelayed({
            requestNotificationPermission()
        }, 1000)

        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnSignup = findViewById<Button>(R.id.btnsignup)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        // Check permission status when returning to app
        if (hasAskedForPermission) {
            val isGranted = NotificationPermissionHelper.isNotificationPermissionGranted(this)
            Log.d("Start", "Notification permission status: $isGranted")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                NotificationPermissionHelper.isNotificationPermissionGranted(this) -> {
                    // Already granted
                    Log.d("Start", "Notification permission already granted")
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show explanation before asking
                    NotificationPermissionHelper.showPermissionRationaleDialog(this) {
                        hasAskedForPermission = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                else -> {
                    // First time asking
                    hasAskedForPermission = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}