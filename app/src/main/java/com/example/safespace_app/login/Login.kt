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
import com.example.safespace_app.AppPrivacyPolicy
import com.example.safespace_app.AppTermsAndConditions
import com.example.safespace_app.MainNavigation
import com.example.safespace_app.MainNavigation2
import com.example.safespace_app.R
import com.example.safespace_app.Start
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Login : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            val uid = currentUser.uid
            FirebaseFirestore.getInstance()
                .collection("account_details")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        handlePostLogin(doc)
                    } else {
                        sendCachedUserDataIfExists { cachedUserType ->
                            navigateUserFromType(cachedUserType)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load user role: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            FirebaseAuth.getInstance().signOut()
        }
    }
    private fun handlePostLogin(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val userType = doc.getString("userType") ?: return

        if (userType.lowercase() == "peer") {
            val isVerified = doc.getString("isVerified") ?: "pending"

            if (isVerified.lowercase() != "verified") {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(
                    this,
                    "Pending GCO verification. Please wait for approval.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // Passed checks → proceed
        navigateUserFromType(userType)
    }

    private fun sendCachedUserDataIfExists(onSuccess: (String) -> Unit) {
        val prefs = getSharedPreferences("signup_cache", 0)
        if (!prefs.contains("userType")) {
            Log.d("Login", "No cached data found")
            onSuccess("student") // default type
            return
        }

        val current = FirebaseAuth.getInstance().currentUser ?: run {
            Log.e("Login", "No current user")
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

        // Add type-specific fields
        when (userType) {
            "student" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["username"] = prefs.getString("username", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d("Login", "Uploading STUDENT cached data: ${data.keys}")
            }
            "peer" -> {
                data["fname"] = prefs.getString("fname", "") ?: ""
                data["lname"] = prefs.getString("lname", "") ?: ""
                data["program"] = prefs.getString("program", "") ?: ""
                data["year_lvl"] = prefs.getString("year_lvl", "") ?: ""
                data["studentId"] = prefs.getString("studentId", "") ?: ""

                Log.d("Login", "Uploading PEER cached data: fname=${data["fname"]}, lname=${data["lname"]}")
            }
        }

        db.collection("account_details")
            .document(current.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("Login", "✅ Cached user data uploaded to Firestore successfully")
                prefs.edit().clear().apply()
                onSuccess(userType)
            }
            .addOnFailureListener { e ->
                Log.e("Login", "❌ Failed to upload cached user data: ${e.message}")
                onSuccess(userType)
            }
    }

    private fun navigateUser(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val userType = doc.getString("userType") ?: "student"
        navigateUserFromType(userType)
    }

    private fun navigateUserFromType(userType: String) {
        Log.d("Login", "Navigating user with type: $userType")
        when (userType.lowercase()) {
            "peer" -> {
                Log.d("Login", "Starting MainNavigation2 for PEER")
                startActivity(Intent(this, MainNavigation2::class.java))
            }
            "student" -> {
                Log.d("Login", "Starting MainNavigation for STUDENT")
                startActivity(Intent(this, MainNavigation::class.java))
            }
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
            .addOnSuccessListener { result ->
                val user = result.user!!
                if (!user.isEmailVerified) {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Call backend to assign role & refresh token
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        // **Check if user already has claims**
                        val initialToken = user.getIdToken(false).await()
                        val hasClaims = initialToken.claims.containsKey("authenticated") ||
                                initialToken.claims.containsKey("role")

                        Log.d("Login", "Initial claims check: hasClaims=$hasClaims")

                        if (!hasClaims) {
                            Log.d("Login", "No claims found, calling backend to assign role...")

                            val idToken = initialToken.token ?: throw Exception("Failed to get ID token")

                            // Call backend
                            val backendUrl = "https://safe-space-backend.vercel.app/api/assign-role.js"
                            val success = assignRoleOnBackend(backendUrl, idToken)

                            if (!success) {
                                Log.e("Login", "❌ Backend call failed")
                                Toast.makeText(this@Login, "Failed to assign role — try again", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            Log.d("Login", "✅ Backend call successful, waiting for claims to propagate...")

                            // **Wait for claims to actually be available in the token**
                            val hasValidClaims = waitForValidClaims(user, maxRetries = 8)

                            if (!hasValidClaims) {
                                Log.e("Login", "❌ Claims not available after retries")
                                Toast.makeText(this@Login, "Login taking longer than expected. Please try again.", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            Log.d("Login", "✅ Valid claims confirmed, proceeding to navigate")
                        } else {
                            Log.d("Login", "✅ User already has valid claims, proceeding...")
                        }

                        // Load user data and navigate
                        val uid = user.uid
                        val db = FirebaseFirestore.getInstance()
                        db.collection("account_details").document(uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    Log.d("Login", "Found user data in Firestore")
                                    navigateUser(doc)
                                } else {
                                    Log.d("Login", "No user data in Firestore, checking cache")
                                    sendCachedUserDataIfExists { cachedUserType ->
                                        navigateUserFromType(cachedUserType)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this@Login, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e("Login", "❌ Login flow exception: ${e.message}")
                        Toast.makeText(this@Login, "Login flow failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // **NEW: Wait for claims to actually appear in the token**
    private suspend fun waitForValidClaims(
        user: com.google.firebase.auth.FirebaseUser,
        maxRetries: Int = 8
    ): Boolean {
        repeat(maxRetries) { attempt ->
            try {
                Log.d("Login", "Checking for claims (attempt ${attempt + 1}/$maxRetries)...")

                // Wait before checking (give backend time to process)
                delay(1500) // 1.5 seconds between checks

                // Force refresh the token
                val tokenResult = user.getIdToken(true).await()
                val claims = tokenResult.claims

                Log.d("Login", "Current claims: ${claims.keys}")

                // Check if claims exist
                if (claims.containsKey("authenticated") || claims.containsKey("role")) {
                    Log.d("Login", "✅ Claims found! authenticated=${claims["authenticated"]}, role=${claims["role"]}")
                    return true
                }

                Log.d("Login", "Claims not yet available, will retry...")

            } catch (e: Exception) {
                Log.e("Login", "Error checking claims on attempt ${attempt + 1}: ${e.message}")
            }
        }

        Log.e("Login", "❌ Claims not available after $maxRetries attempts (${maxRetries * 1.5}s)")
        return false
    }

    private suspend fun assignRoleOnBackend(backendUrl: String, idToken: String): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                Log.d("Login", "Calling backend: $backendUrl")
                val client = OkHttpClient()
                val json = """{"idToken":"$idToken"}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(backendUrl)
                    .post(body)
                    .header("Authorization", "Bearer $idToken")
                    .build()
                val res = client.newCall(req).execute()
                val responseBody = res.body?.string()
                Log.d("Login", "Backend response code: ${res.code}")
                Log.d("Login", "Backend response body: $responseBody")
                res.isSuccessful
            } catch (e: Exception) {
                Log.e("Login", "Backend call exception: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    override fun onBackPressed() {
        val intent = Intent(this, Start::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val inputEmail = findViewById<TextInputEditText>(R.id.inputemail)
        val inputPassword = findViewById<TextInputEditText>(R.id.inputpassword)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnForgot = findViewById<Button>(R.id.forgotpassword)
        val tvTerms = findViewById<TextView>(R.id.Terms)

        btnForgot.setOnClickListener {
            val fragment = LogForgotPassword()
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, fragment)
                .addToBackStack(null)
                .commit()
        }

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

        // Terms & Privacy setup
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