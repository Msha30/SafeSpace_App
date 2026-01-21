package com.example.safespace_app.home

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.firebase.firestore.FirebaseFirestore

class HomeCounseling : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val rootView = inflater.inflate(R.layout.fragment_home_counseling, container, false)

        val formTitle = rootView.findViewById<TextView>(R.id.form_title)
        val formDesc  = rootView.findViewById<TextView>(R.id.form_desc)

        // 🔹 Load form metadata from Firestore
        loadRequestFormMetadata(formTitle, formDesc)

        // Back button
        rootView.findViewById<ImageView>(R.id.backbtn).setOnClickListener {
            findNavController().navigateUp()
        }

        // Start button
        rootView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnstart)
            .setOnClickListener {
                findNavController().navigate(
                    R.id.action_homeCounseling_to_homeCounselingForm
                )
            }

        return rootView
    }

    private fun loadRequestFormMetadata(
        titleView: TextView,
        descView: TextView
    ) {
        db.collection("CounselingForm")
            .document("RequestForm_Format")
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w("HomeCounseling", "RequestForm_Format not found")
                    return@addOnSuccessListener
                }

                titleView.text = doc.getString("title") ?: "Counseling Request"
                descView.text  = doc.getString("description") ?: ""
            }
            .addOnFailureListener { e ->
                Log.e("HomeCounseling", "Failed to load form metadata", e)
            }
    }
}
