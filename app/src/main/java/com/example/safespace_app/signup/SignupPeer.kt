package com.example.safespace_app.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.safespace_app.R
import com.google.android.material.textfield.TextInputEditText

class SignupPeer : Fragment() {

    private val viewModel: SignupPeerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_signup_peer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Data from previous signup screens
        val fname = arguments?.getString("fname") ?: ""
        val lname = arguments?.getString("lname") ?: ""
        val email = arguments?.getString("email") ?: ""
        val program = arguments?.getString("program") ?: ""

        val studentId = view.findViewById<TextInputEditText>(R.id.studentid)
        val password = view.findViewById<TextInputEditText>(R.id.password)
        val confirmPassword = view.findViewById<TextInputEditText>(R.id.confirmpassword)

        // --- DROPDOWN ---
        val yearLevels = listOf("1st Year","2nd Year","3rd Year","4th Year")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, yearLevels)

        val yrlvl = view.findViewById<AutoCompleteTextView>(R.id.yearlevel)
        yrlvl.setAdapter(adapter)
        yrlvl.threshold = 1
        yrlvl.setOnClickListener { yrlvl.showDropDown() }
        yrlvl.setOnItemClickListener { _, _, _, _ -> yrlvl.error = null }

        // --- BUTTON ---
        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {

            val i_yrlvl = yrlvl.text.toString()
            val i_studentid = studentId.text.toString()
            val i_pass = password.text.toString()
            val i_cpass = confirmPassword.text.toString()

            var hasError = false

            if (i_yrlvl.isEmpty() || !yearLevels.contains(i_yrlvl)) {
                yrlvl.error = "Please select a year level"
                hasError = true
            }
            if (i_studentid.isEmpty()) {
                studentId.error = "Please enter your student ID"
                hasError = true
            }
            if (i_pass.isEmpty()) {
                password.error = "Please enter your password"
                hasError = true
            }
            if (i_pass.length < 6) {
                password.error = "Password must be 6 or more characters"
                hasError = true
            }
            if (i_cpass != i_pass) {
                confirmPassword.error = "Passwords do not match"
                hasError = true
            }
            if (hasError) return@setOnClickListener

            // === Create FirebaseAuth user ===
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, i_pass)
                .addOnSuccessListener { result ->

                    val firebaseUser = result.user!!

                    // Send verification email
                    firebaseUser.sendEmailVerification()

                    // Send ALL data to SignupVerification
                    val bundle = Bundle().apply {
                        putString("fname", fname)
                        putString("lname", lname)
                        putString("email", email)
                        putString("program", program)
                        putString("year_lvl", i_yrlvl)
                        putString("studentId", i_studentid)
                        putString("userType", "peer")
                    }

                    val fragment = SignupVerification().apply { arguments = bundle }

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("SignupPeer", "Signup failed: ${e.message}")
                }
        }
    }
}
