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

class ProfInfo : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_prof_info, container, false)

        val fname = rootView.findViewById<TextView>(R.id.fname)
        val lname = rootView.findViewById<TextView>(R.id.lname)
        val pname = rootView.findViewById<TextView>(R.id.pname)
        val email = rootView.findViewById<TextView>(R.id.email)
        val program = rootView.findViewById<TextView>(R.id.program)
        val student_id = rootView.findViewById<TextView>(R.id.student_id)
        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)

        // Load from SharedPreferences (cached data)
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)

        fname.text = prefs.getString("fname", "")
        lname.text = prefs.getString("lname", "")
        pname.text = prefs.getString("username", "") // pref name for display
        email.text = prefs.getString("email", "")
        program.text = prefs.getString("program", "")
        student_id.text = prefs.getString("studentId", "")

        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        return rootView
    }
}
