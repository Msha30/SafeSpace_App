package com.example.safespace_app.login

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.MetricAffectingSpan
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.safespace_app.R

class Login : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tvTerms = findViewById<TextView>(R.id.Terms)
        val text = "By logging in, you are agreeing to the Terms and Conditions and Privacy Policy."
        val spannable = SpannableString(text)

// Load bold font
        val boldFont = ResourcesCompat.getFont(this, R.font.PSBold)

        // Custom TypefaceSpan
        class CustomTypefaceSpan(private val typeface: Typeface?) : MetricAffectingSpan() {
            override fun updateDrawState(ds: TextPaint) {
                apply(ds)
            }
            override fun updateMeasureState(paint: TextPaint) {
                apply(paint)
            }
            private fun apply(paint: TextPaint) {
                typeface?.let {
                    paint.typeface = it
                }
            }
        }

// Clickable for "Terms and Conditions"
        val termsStart = text.indexOf("Terms and Conditions")
        val termsEnd = termsStart + "Terms and Conditions".length

        val termsClick = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Example: open Terms Activity or URL
                // startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms")))
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.BLUE
                ds.isUnderlineText = false
            }
        }

        spannable.setSpan(termsClick, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(CustomTypefaceSpan(boldFont), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

// Clickable for "Privacy Policy"
        val privacyStart = text.indexOf("Privacy Policy")
        val privacyEnd = privacyStart + "Privacy Policy".length

        val privacyClick = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Example: open Privacy Activity or URL
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.BLUE
                ds.isUnderlineText = false
            }
        }

        spannable.setSpan(privacyClick, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(CustomTypefaceSpan(boldFont), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        tvTerms.text = spannable
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = Color.TRANSPARENT


    }
}