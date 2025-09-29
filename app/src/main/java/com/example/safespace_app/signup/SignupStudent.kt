package com.example.safespace_app.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.safespace_app.R

class SignupStudent : Fragment() {

    private val viewModel: SignupStudentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_student, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, SignupVerification()) // replace with your container ID
                .addToBackStack(null) // allows back navigation
                .commit()
        }
    }
}
