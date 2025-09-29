package com.example.safespace_app.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.example.safespace_app.R

class LogForgotPassword : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_log_forgot_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Button Setup ---
        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            // ✅ Navigate to LogNewPassword fragment instead of using Intent
            val fragment = LogNewPassword()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main, fragment) // make sure "main" is your container in activity_login.xml
                .addToBackStack(null)
                .commit()
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

        // Attach watchers and key listeners
        for (i in editTexts.indices) {
            val current = editTexts[i]
            val next = editTexts.getOrNull(i + 1)
            val prev = editTexts.getOrNull(i - 1)

            current.addTextChangedListener(GenericTextWatcher(current, next))
            current.setOnKeyListener(GenericKeyEvent(current, prev))
        }
    }
}

// --- Helper Classes ---
class GenericKeyEvent(
    private val currentView: EditText,
    private val previousView: EditText?
) : View.OnKeyListener {
    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN &&
            keyCode == android.view.KeyEvent.KEYCODE_DEL &&
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
