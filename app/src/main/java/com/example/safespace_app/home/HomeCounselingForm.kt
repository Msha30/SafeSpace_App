package com.example.safespace_app.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class HomeCounselingForm : Fragment() {

    private val viewModel: HomeCounselingFormViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_counseling_form, container, false)

        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)

        rootView.findViewById<TextView>(R.id.fname).text = prefs.getString("fname", "")
        rootView.findViewById<TextView>(R.id.lname).text = prefs.getString("lname", "")
        rootView.findViewById<TextView>(R.id.program).text = prefs.getString("program", "")
        rootView.findViewById<TextView>(R.id.student_id).text = prefs.getString("studentId", "")

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnsubmit)

        btnSubmit.setOnClickListener {
            saveFormData()
        }
    }

    private fun saveFormData() {
        val fname = view?.findViewById<TextView>(R.id.fname)?.text.toString()
        val lname = view?.findViewById<TextView>(R.id.lname)?.text.toString()
        val program = view?.findViewById<TextView>(R.id.program)?.text.toString()
        val studentId = view?.findViewById<TextView>(R.id.student_id)?.text.toString()

        val age = view?.findViewById<TextInputEditText>(R.id.age)?.text.toString()
        val genConcern = view?.findViewById<TextInputEditText>(R.id.GenConcern)?.text.toString()
        val prefSched = view?.findViewById<TextInputEditText>(R.id.PrefSched)?.text.toString()

        val assignedSex = getSelectedText(view?.findViewById(R.id.AssignedSex))
        val genderId = getSelectedText(view?.findViewById(R.id.GenderID))
        val prefPlat = getSelectedText(view?.findViewById(R.id.PrefPlat))
        val prefCslr = getSelectedText(view?.findViewById(R.id.PrefCslr))
        val urgency = getSelectedText(view?.findViewById(R.id.Urgency))

        val uid = auth.currentUser?.uid ?: ""

        val formData = hashMapOf(
            "fname" to fname,
            "lname" to lname,
            "program" to program,
            "studentId" to studentId,
            "age" to age,
            "assignedSex" to assignedSex,
            "genderId" to genderId,
            "preferredPlatform" to prefPlat,
            "preferredCounselor" to prefCslr,
            "urgent" to urgency,
            "generalConcern" to genConcern,
            "preferredSchedule" to prefSched,
            "createdAt" to Timestamp.now(),
            "createdBy" to uid
        )

        firestore.collection("CounselingForm")
            .add(formData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Form Submitted!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.nav_home, false)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to submit form.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getSelectedText(radioGroup: RadioGroup?): String {
        val selectedId = radioGroup?.checkedRadioButtonId ?: return ""
        val radioButton = view?.findViewById<TextView>(selectedId)
        return radioButton?.text?.toString() ?: ""
    }
}
