package com.example.safespace_app.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeCounseling.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeCounseling : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_counseling, container, false)

        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        val btnStart = rootView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnstart)
        btnStart.setOnClickListener {
            // Make sure you have this action in nav_graph.xml
            findNavController().navigate(R.id.action_homeCounseling_to_homeCounselingForm)
        }

        return rootView
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeCounseling().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
