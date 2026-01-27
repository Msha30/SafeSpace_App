package com.example.safespace_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        checkUserAndNavigate()
    }

    private fun checkUserAndNavigate() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            // User logged in, assign role and navigate
            assignRoleAndNavigate(currentUser.uid)
        } else {
            // Not logged in, go to Start page
            startActivity(Intent(this, Start::class.java))
            finish()
        }
    }

    private fun assignRoleAndNavigate(uid: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Get ID token
                val idTokenResult = currentUser.getIdToken(false).await()
                val idToken = idTokenResult.token ?: throw Exception("Failed to get ID token")
                Log.d("FirebaseToken", "ID Token: $idToken")

                // Call backend to assign role
                val backendUrl = "https://safe-space-backend.vercel.app/api/assign-role.js"
                val success = assignRoleOnBackend(backendUrl, idToken)

                if (!success) {
                    Toast.makeText(this@MainActivity, "Failed to assign role – try again", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MainActivity, Start::class.java))
                    finish()
                    return@launch
                }

                // Force refresh token so claims are updated
                currentUser.getIdToken(true).await()

                // ============================================
                // CRITICAL: Request and save FCM token
                // ============================================
                requestAndSaveFCMToken(uid)

                // Load user data from Firestore
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("account_details").document(uid).get().await()
                if (doc.exists()) {
                    val userType = doc.getString("userType") ?: "student"
                    navigateUserFromType(userType)
                } else {
                    // No account details, fallback to Start
                    startActivity(Intent(this@MainActivity, Start::class.java))
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, Start::class.java))
                finish()
            }
        }
    }

    /**
     * Request FCM token and save it to Firestore
     * This is CRITICAL for notifications to work
     */
    private fun requestAndSaveFCMToken(uid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("FCM", "🔄 Requesting FCM token for user: $uid")

                // Request the token
                val token = FirebaseMessaging.getInstance().token.await()

                Log.d("FCM", "✅ FCM Token received: ${token.take(20)}...")

                // Save to Firestore
                val db = FirebaseFirestore.getInstance()
                db.collection("account_details")
                    .document(uid)
                    .update("fcmToken", token)
                    .await()

                Log.d("FCM", "✅✅✅ FCM token SAVED to Firestore! ✅✅✅")

                // Verify it was saved
                val doc = db.collection("account_details").document(uid).get().await()
                val savedToken = doc.getString("fcmToken")

                if (savedToken == token) {
                    Log.d("FCM", "✅ VERIFIED: Token is saved correctly in Firestore")
                } else {
                    Log.e("FCM", "❌ WARNING: Token in Firestore doesn't match!")
                }

            } catch (e: Exception) {
                Log.e("FCM", "❌ Failed to save FCM token", e)

                // Don't fail the login - just log the error
                // Notifications won't work but user can still use the app
            }
        }
    }

    private suspend fun assignRoleOnBackend(backendUrl: String, idToken: String): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val json = """{"idToken":"$idToken"}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(backendUrl)
                    .post(body)
                    .header("Authorization", "Bearer $idToken")
                    .build()
                val res = client.newCall(req).execute()
                res.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun navigateUserFromType(userType: String) {
        when (userType.lowercase()) {
            "peer" -> startActivity(Intent(this, MainNavigation2::class.java))
            "student" -> startActivity(Intent(this, MainNavigation::class.java))
            else -> {
                Toast.makeText(this, "Unknown user type: $userType", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Start::class.java))
            }
        }
        finish()
    }
}