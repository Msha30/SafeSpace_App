package com.example.safespace_app.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.safespace_app.R
import com.google.android.material.textfield.TextInputEditText

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

        // --- original Activity inputs ---
        val fname = arguments?.getString("fname") ?: ""
        val lname = arguments?.getString("lname") ?: ""
        val email = arguments?.getString("email") ?: ""
        val programFromActivity = arguments?.getString("program") ?: ""

        val username = view.findViewById<TextInputEditText>(R.id.username)
        val studentId = view.findViewById<TextInputEditText>(R.id.studentid)
        val password = view.findViewById<TextInputEditText>(R.id.password)
        val confirmPassword = view.findViewById<TextInputEditText>(R.id.confirmpassword)

        val btn = view.findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            val i_uname = username.text.toString()
            val i_studentid = studentId.text.toString()
            val i_pass = password.text.toString()
            val i_cpass = confirmPassword.text.toString()

            var hasError = false

            if (i_uname.isEmpty()) {
                username.error = "Please enter your username"
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



            com.google.firebase.auth.FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, i_pass)
                .addOnSuccessListener { authResult ->

                    val firebaseUser = authResult.user!!
                    val uid = firebaseUser.uid

                    // === Send email verification ===
                    firebaseUser.sendEmailVerification()
                        .addOnSuccessListener {
                            android.util.Log.d("Signup", "Verification email sent to $email")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("Signup", "Failed to send verification email: ${e.message}")
                        }

                    // === Save full user data to Firestore ===
                    val userData = hashMapOf(
                        "uid" to uid,
                        "fname" to fname,
                        "lname" to lname,
                        "email" to email,
                        "program" to programFromActivity,
                        "username" to i_uname,
                        "studentId" to i_studentid,
                        "userType" to "student",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("account_details")
                        .document(uid)
                        .set(userData)
                        .addOnSuccessListener {
                            android.util.Log.d("Signup", "User data saved to Firestore")

                            // Navigate to verification fragment
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.main, SignupVerification()) // your container ID
                                .addToBackStack(null)
                                .commit()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("Signup", "Failed to save user data: ${e.message}")
                        }

                }
                .addOnFailureListener { e ->
                    android.util.Log.e("Signup", "Signup failed: ${e.message}")
                }
        }
    }
}
