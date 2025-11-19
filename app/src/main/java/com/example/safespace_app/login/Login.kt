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
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainNavigation::class.java))
                /* ADD LATER : Fetch user role from Firestore
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        val role = doc.getString("role") ?: "user"
                        when (role) {
                            "peer" -> startActivity(Intent(this, MainNavigation2::class.java))
                            else -> startActivity(Intent(this, MainNavigation::class.java))
                        }

                        finish()
                    }*/
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
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email == "user" && password == "user") {
                startActivity(Intent(this, MainNavigation::class.java))
                finish()
            }
            else if (email == "peer" && password == "peer") {
                startActivity(Intent(this, MainNavigation2::class.java))
                finish()
            }
            else {
                loginUser(email, password)
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
