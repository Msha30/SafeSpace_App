package com.example.safespace_app.signup

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.R
import com.google.android.material.textfield.TextInputEditText

class Signup : AppCompatActivity() {

    private var email = ""
    private var password = ""
    private var userType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val btnStudent = findViewById<RadioButton>(R.id.btnstudent)
        val btnPeer = findViewById<RadioButton>(R.id.btnpeer)
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val btnNext = findViewById<Button>(R.id.btnnext)
        val items = resources.getStringArray(R.array.programs)
        val autoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.program)

        val inputFname = findViewById<TextInputEditText>(R.id.fname)
        val inputLname = findViewById<TextInputEditText>(R.id.lname)
        val inputEmail = findViewById<TextInputEditText>(R.id.email)
        val inputProgram = findViewById<AutoCompleteTextView>(R.id.program)

        val programs = listOf(
            // College of Architecture
            "BS Architecture",

            // College of Arts & Sciences
            "BS Psychology",
            "AB Communication",

            // College of Business and Accountancy
            "BS Business Administration",
            "BS Accountancy",

            // College of Engineering and Technology
            "BS Civil Engineering",
            "BS Computer Engineering",
            "BS Information Technology",

            // College of Tourism and Hospitality Management
            "BS Tourism Management",
            "BS Hospitality Management",

            //Senior High School
            "Accountancy, Business and Management (ABM) Strand",
            "Humanities and Social Sciences (HUMSS) Strand",
            "Science, Technology, Engineering and Mathematics (STEM) Strand"
        )

        val adapter = ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line,
            programs
        )

        val programBox = inputProgram
        programBox.setAdapter(adapter)
        programBox.threshold = 1

        programBox.setOnClickListener {
            programBox.showDropDown()
        }

        programBox.setOnItemClickListener { _, _, _, _ ->
            programBox.error = null
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.btnstudent -> {
                    btnStudent.setBackgroundResource(R.drawable.f_rounded_blue)
                    btnStudent.setTextColor(getColor(R.color.white))

                    btnPeer.setBackgroundResource(R.drawable.f_rounded_inactive)
                    btnPeer.setTextColor(getColor(R.color.textgrey))
                }
                R.id.btnpeer -> {
                    btnPeer.setBackgroundResource(R.drawable.f_rounded_blue)
                    btnPeer.setTextColor(getColor(R.color.white))

                    btnStudent.setBackgroundResource(R.drawable.f_rounded_inactive)
                    btnStudent.setTextColor(getColor(R.color.textgrey))
                }
            }
        }

        btnNext.setOnClickListener {
            val i_fname = inputFname.text.toString()
            val i_lname = inputLname.text.toString()
            val i_email = inputEmail.text.toString()
            val i_program = inputProgram.text.toString()
            var hasError = false

            if (i_fname.isEmpty()) {
                inputFname.error = "Please enter your first name"
                hasError = true
            }
            if (i_lname.isEmpty()) {
                inputLname.error = "Please enter your last name"
                hasError = true
            }
            if (i_email.isEmpty()) {
                inputEmail.error = "Please enter your email"
                hasError = true
            }
            if (!i_email.endsWith(".edu.ph")) {
                inputEmail.error = "Email must be an edu.ph domain"
                hasError = true
            }
            if (i_program.isEmpty() || !programs.contains(i_program)) {
                programBox.error = "Please select a program from the list"
                programBox.setText("")
                hasError = true
            }

            if (hasError) return@setOnClickListener

            val bundle = Bundle().apply {
                putString("fname", i_fname)
                putString("lname", i_lname)
                putString("email", i_email)
                putString("program", i_program)
            }

            when (radioGroup.checkedRadioButtonId) {

                R.id.btnpeer -> {
                    val fragment = SignupPeer()
                    fragment.arguments = bundle
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                R.id.btnstudent -> {
                    val fragment = SignupStudent()
                    fragment.arguments = bundle
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                else -> {
                    val fragment = SignupStudent()
                    fragment.arguments = bundle
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment) // make sure activity_signup.xml has FrameLayout with this id
                        .addToBackStack(null)
                        .commit()
                }
            }
        }

    }
}
