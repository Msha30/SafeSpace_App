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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.safespace_app.*
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {
    private fun loginUser(email: String, password: String) {

        // Simple domain check before touching Firebase (0 cost)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            return
        }

        // Prevent spam-tapping (disable button for 2 sec)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        btnLogin.isEnabled = false
        btnLogin.postDelayed({ btnLogin.isEnabled = true }, 2000)

        // Firebase login
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                val db = FirebaseFirestore.getInstance()

                db.collection("account_details")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            Toast.makeText(this, "No account details found!", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val userType = doc.getString("userType") ?: "student"

                        // Redirect based on userType
                        when (userType.lowercase()) {
                            "peer" -> {
                                Toast.makeText(this, "Logged in as: $userType", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainNavigation2::class.java))
                            }
                            "student" -> {
                                Toast.makeText(this, "Logged in as: $userType", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainNavigation::class.java))
                            }
                            else -> {
                                Toast.makeText(this, "Unknown user type: $userType", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }
                        }

                        finish() // prevent back navigation to login
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load user role: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // <-- use activity layout

        val inputEmail = findViewById<TextInputEditText>(R.id.inputemail)
        val inputPassword = findViewById<TextInputEditText>(R.id.inputpassword)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnForgot = findViewById<Button>(R.id.forgotpassword)
        val tvTerms = findViewById<TextView>(R.id.Terms)

        // === Forgot Password ===
        btnForgot.setOnClickListener {
            val fragment = LogForgotPassword()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, fragment)
                .addToBackStack(null)
                .commit()
        }

        // === Login ===
        btnLogin.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            var hasError = false

            // Clear previous errors
            inputEmail.error = null
            inputPassword.error = null

            // --- Email validation --- temp
            if (email.isEmpty()) {
                inputEmail.error = "Please enter your email"
                hasError = true
            } /*else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                inputEmail.error = "Enter a valid email"
                hasError = true
            } else if (!email.endsWith(".edu.ph")) {
                inputEmail.error = "Email must be an edu.ph domain"
                hasError = true
            }*/

            // --- Password validation ---
            if (password.isEmpty()) {
                inputPassword.error = "Please enter your password"
                hasError = true
            }

            // Stop if there are errors
            if (hasError) return@setOnClickListener

            // --- Test accounts ---
            if (email == "user" && password == "user") {
                startActivity(Intent(this, MainNavigation::class.java))
                finish()
            } else if (email == "peer" && password == "peer") {
                startActivity(Intent(this, MainNavigation2::class.java))
                finish()
            } else {
                loginUser(email, password) // Firebase login
            }


            /* TEMP REMOVE
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            } else if (email == "user" && password == "user") {
                startActivity(Intent(this, MainNavigation::class.java))
                finish()
            } else if (email == "peer" && password == "peer") {
                startActivity(Intent(this, MainNavigation2::class.java))
                finish()
            } else {
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
            }*/
        }

        // === Terms & Privacy ===
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
