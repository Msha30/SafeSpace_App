package com.example.safespace_app.login

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.MetricAffectingSpan
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.safespace_app.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    override fun onStart() {
        super.onStart()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            val uid = currentUser.uid
            val db = FirebaseFirestore.getInstance()
            db.collection("account_details").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val userType = doc.getString("userType") ?: "student"
                        navigateUserFromType(userType)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load user role: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            FirebaseAuth.getInstance().signOut()
        }
    }

    private fun sendCachedUserDataIfExists(onSuccess: (String) -> Unit) {
        val prefs = getSharedPreferences("signup_cache", 0)
        if (!prefs.contains("email")) {
            onSuccess("student") // default type
            return
        }

        val current = FirebaseAuth.getInstance().currentUser ?: run {
            onSuccess("student")
            return
        }

        val userType = prefs.getString("userType", "student") ?: "student"
        val db = FirebaseFirestore.getInstance()

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

        db.collection("account_details")
            .document(current.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("Login", "Cached user data uploaded to Firestore")
                prefs.edit().clear().apply()
                onSuccess(userType) // pass before clearing
            }
            .addOnFailureListener { e ->
                Log.e("Login", "Failed to upload cached user data: ${e.message}")
                onSuccess(userType)
            }
    }

    private fun navigateUser(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val userType = doc.getString("userType") ?: "student"
        navigateUserFromType(userType)
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

    private fun loginUser(email: String, password: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return
        }

        val btnLogin = findViewById<Button>(R.id.btnlogin)
        btnLogin.isEnabled = false
        btnLogin.postDelayed({ btnLogin.isEnabled = true }, 2000)

        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = FirebaseAuth.getInstance().currentUser!!

                if (!user.isEmailVerified) {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val uid = user.uid
                val db = FirebaseFirestore.getInstance()

                db.collection("account_details")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            navigateUser(doc)
                        } else {
                            sendCachedUserDataIfExists { cachedUserType ->
                                navigateUserFromType(cachedUserType)
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load user role: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    override fun onBackPressed() {
        // Go to Start activity
        val intent = Intent(this, Start::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish() // remove Login from back stack
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val inputEmail = findViewById<TextInputEditText>(R.id.inputemail)
        val inputPassword = findViewById<TextInputEditText>(R.id.inputpassword)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnForgot = findViewById<Button>(R.id.forgotpassword)
        val tvTerms = findViewById<TextView>(R.id.Terms)

        // Forgot Password
        btnForgot.setOnClickListener {
            val fragment = LogForgotPassword()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, fragment)
                .addToBackStack(null)
                .commit()
        }

        // Login button
        btnLogin.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            var hasError = false

            inputEmail.error = null
            inputPassword.error = null

            if (email.isEmpty()) {
                inputEmail.error = "Please enter your email"
                hasError = true
            }
            if (password.isEmpty()) {
                inputPassword.error = "Please enter your password"
                hasError = true
            }
            if (hasError) return@setOnClickListener

            // Test accounts
            when {
                email == "user" && password == "user" -> {
                    startActivity(Intent(this, MainNavigation::class.java))
                    finish()
                }
                email == "peer" && password == "peer" -> {
                    startActivity(Intent(this, MainNavigation2::class.java))
                    finish()
                }
                else -> loginUser(email, password)
            }
        }

        // Terms & Privacy
        val text = "Terms and Conditions and Privacy Policy."
        val spannable = SpannableString(text)
        val boldFont = ResourcesCompat.getFont(this, R.font.psbold)

        val termsStart = text.indexOf("Terms and Conditions")
        val termsEnd = termsStart + "Terms and Conditions".length
        val privacyStart = text.indexOf("Privacy Policy")
        val privacyEnd = privacyStart + "Privacy Policy".length

        class CustomTypefaceSpan(private val typeface: Typeface?) : MetricAffectingSpan() {
            override fun updateDrawState(ds: TextPaint) = apply(ds)
            override fun updateMeasureState(paint: TextPaint) = apply(paint)
            private fun apply(paint: TextPaint) { typeface?.let { paint.typeface = it } }
        }

        val termsClick = object : ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main, AppTermsAndConditions())
                    .addToBackStack(null)
                    .commit()
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.BLUE
                ds.isUnderlineText = false
            }
        }

        val privacyClick = object : ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main, AppPrivacyPolicy())
                    .addToBackStack(null)
                    .commit()
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.BLUE
                ds.isUnderlineText = false
            }
        }

        spannable.setSpan(termsClick, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(CustomTypefaceSpan(boldFont), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(privacyClick, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(CustomTypefaceSpan(boldFont), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        tvTerms.text = spannable
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = Color.TRANSPARENT
    }
}
