package com.example.safespace_app.signup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.safespace_app.R
import com.example.safespace_app.login.Login
import com.google.firebase.auth.FirebaseAuth

class SignupVerification : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_verification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Button Setup ---
        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            val intent = Intent(requireContext(), Login::class.java)
            startActivity(intent)
        }

        // --- OTP EditTexts ---
        val editTexts = listOf(
            view.findViewById<EditText>(R.id.editText1),
            view.findViewById<EditText>(R.id.editText2),
            view.findViewById<EditText>(R.id.editText3),
            view.findViewById<EditText>(R.id.editText4),
            view.findViewById<EditText>(R.id.editText5),
            view.findViewById<EditText>(R.id.editText6)
        )

        for (i in editTexts.indices) {
            val current = editTexts[i]
            val next = editTexts.getOrNull(i + 1)
            val prev = editTexts.getOrNull(i - 1)

            current.addTextChangedListener(GenericTextWatcher(current, next))
            current.setOnKeyListener(GenericKeyEvent(current, prev))
        }
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // Start a periodic check
        val handler = android.os.Handler()
        val runnable = object : Runnable {
            override fun run() {
                user?.reload()?.addOnCompleteListener {
                    if (user.isEmailVerified) {
                        // Email verified, go to login screen
                        startActivity(Intent(requireContext(), Login::class.java))
                        requireActivity().finish()
                    } else {
                        // Retry after 3 seconds
                        handler.postDelayed(this, 3000)
                    }
                }
            }
        }
        handler.post(runnable)

    }
}

// --- Helper Classes ---

class GenericKeyEvent(
    private val currentView: EditText,
    private val previousView: EditText?
) : View.OnKeyListener {
    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN &&
            keyCode == KeyEvent.KEYCODE_DEL &&
            currentView.text.isEmpty()
        ) {
            previousView?.setText("")
            previousView?.requestFocus()
            return true
        }
        return false
    }
}

class GenericTextWatcher(
    private val currentView: View,
    private val nextView: View?
) : TextWatcher {
    override fun afterTextChanged(editable: Editable?) {
        if (editable?.length == 1) {
            nextView?.requestFocus()
        }
    }
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
