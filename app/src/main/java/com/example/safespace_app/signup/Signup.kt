package com.example.safespace_app.signup

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.R

class Signup : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val btnStudent = findViewById<RadioButton>(R.id.btnstudent)
        val btnPeer = findViewById<RadioButton>(R.id.btnpeer)
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val btnNext = findViewById<Button>(R.id.btnnext)
        val items = resources.getStringArray(R.array.programs)
        val adapter = ArrayAdapter(this, R.layout.f_list_item, items)
        val autoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.program)

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

        autoCompleteTextView.setAdapter(adapter)
        btnNext.setOnClickListener {
            when (radioGroup.checkedRadioButtonId) {
                R.id.btnpeer -> {
                    val fragment = SignupPeer()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment) // make sure activity_signup.xml has FrameLayout with this id
                        .addToBackStack(null)
                        .commit()
                }
                R.id.btnstudent -> {
                    val fragment = SignupStudent()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                else -> {
                    Toast.makeText(this, "Please select Student or Peer", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }
}
