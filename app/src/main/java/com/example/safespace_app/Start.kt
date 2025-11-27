package com.example.safespace_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.login.Login
import com.example.safespace_app.signup.Signup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Start : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnSignup = findViewById<Button>(R.id.btnsignup)

        btnLogin.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && currentUser.isEmailVerified) {
                // User logged in and verified - assign role and navigate
                assignRoleAndNavigate(currentUser.uid)
            } else {
                // User not logged in OR not verified → go to login normally
                startActivity(Intent(this, Login::class.java))
            }
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
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
                val backendUrl = "https://safe-space-backend.vercel.app/api/assign-role"
                val success = assignRoleOnBackend(backendUrl, idToken)

                if (!success) {
                    Toast.makeText(this@Start, "Failed to assign role — try again", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@Start, Login::class.java))
                    return@launch
                }

                // Force refresh token so claims are updated
                currentUser.getIdToken(true).await()

                // Load user data and navigate
                val db = FirebaseFirestore.getInstance()
                db.collection("account_details").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val userType = doc.getString("userType") ?: "student"
                            navigateUserFromType(userType)
                        } else {
                            // No account details found, go to login
                            startActivity(Intent(this@Start, Login::class.java))
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@Start, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@Start, Login::class.java))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@Start, "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@Start, Login::class.java))
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
                return
            }
        }
        finish()
    }
}