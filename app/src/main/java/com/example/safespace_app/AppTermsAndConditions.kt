package com.example.safespace_app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.text.HtmlCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AppTermsAndConditions : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var db: FirebaseFirestore
    private var listenerRegistration: ListenerRegistration? = null
    private lateinit var contentTextView: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_app_terms_and_conditions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        val backButton = view.findViewById<ImageView>(R.id.back)
        contentTextView = view.findViewById<TextView>(R.id.contentTextView)
        progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Set up real-time listener for Terms and Conditions
        setupFirestoreListener()
    }

    private fun setupFirestoreListener() {
        // Show loading indicator
        progressBar.visibility = View.VISIBLE
        contentTextView.visibility = View.GONE

        // Set up real-time listener
        listenerRegistration = db.collection("TermsAndPrivacy")
            .document("TermsAndConditions")
            .addSnapshotListener { snapshot, error ->
                // Hide loading indicator
                progressBar.visibility = View.GONE
                contentTextView.visibility = View.VISIBLE

                if (error != null) {
                    // Handle error
                    contentTextView.text = "Error loading terms and conditions. Please try again later."
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // Get content from Firestore
                    val content = snapshot.getString("content")
                    if (content != null) {
                        // Parse HTML content to preserve formatting
                        contentTextView.text = HtmlCompat.fromHtml(
                            content,
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        )
                    } else {
                        contentTextView.text = "No content available."
                    }
                } else {
                    contentTextView.text = "Terms and conditions not found."
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove the listener when the fragment is destroyed
        listenerRegistration?.remove()
    }

    companion object {
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AppTermsAndConditions().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}