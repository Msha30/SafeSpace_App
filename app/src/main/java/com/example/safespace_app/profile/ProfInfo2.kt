package com.example.safespace_app.profile

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R

class ProfInfo2 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_prof_info2, container, false)

        val fname = rootView.findViewById<TextView>(R.id.fname)
        val lname = rootView.findViewById<TextView>(R.id.lname)
        val yr_lvl = rootView.findViewById<TextView>(R.id.yr_lvl)
        val email = rootView.findViewById<TextView>(R.id.email)
        val program = rootView.findViewById<TextView>(R.id.program)
        val student_id = rootView.findViewById<TextView>(R.id.student_id)
        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)

        // Load cached peer data
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        fname.text = prefs.getString("fname", "")
        lname.text = prefs.getString("lname", "")
        yr_lvl.text = prefs.getString("year_lvl", "") // if you stored it
        email.text = prefs.getString("email", "")
        program.text = prefs.getString("program", "")
        student_id.text = prefs.getString("studentId", "")

        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        return rootView
    }
}
