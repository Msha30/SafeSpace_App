package com.example.safespace_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.login.Login
import com.example.safespace_app.signup.Signup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Start : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnSignup = findViewById<Button>(R.id.btnsignup)

        btnLogin.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // User already logged in, go straight to main nav
                val uid = currentUser.uid
                val db = FirebaseFirestore.getInstance()

                db.collection("account_details")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        val userType = doc.getString("userType") ?: "student"
                        val intent = if (userType.lowercase() == "peer") {
                            Intent(this, MainNavigation2::class.java)
                        } else {
                            Intent(this, MainNavigation::class.java)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        // Firestore failed, fallback to login
                        startActivity(Intent(this, Login::class.java))
                    }
            } else {
                // No logged-in user, go to login normally
                startActivity(Intent(this, Login::class.java))
            }
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
        }
    }
}
