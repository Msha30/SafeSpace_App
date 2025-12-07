package com.example.safespace_app.home

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.example.safespace_app.R
import com.example.safespace_app.cache.UserCache
import com.example.safespace_app.DayAvailability
import com.example.safespace_app.TimeSlot
import java.text.SimpleDateFormat
import java.util.*

class HomePeerSupportForm2 : Fragment(R.layout.fragment_home_peer_support_form2) {

    private var selectedPeerUid: String? = null
    private var preferredMode: String? = null
    private var topicOfConcern: String? = null
    private var generalConcernText: String? = null

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AvailabilityAdapter
    private var daysList = mutableListOf<DayAvailability>()

    private var selectedStudentSlot: TimeSlot? = null
    private var selectedStudentDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            selectedPeerUid = bundle.getString("selectedPeerUid")
            preferredMode = bundle.getString("preferredMode")
            topicOfConcern = bundle.getString("topicOfConcern")
            generalConcernText = bundle.getString("generalConcernText")
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.availabilityRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter = AvailabilityAdapter(
            context = requireContext(),
            days = daysList,
            studentSelectionMode = true
        ) { dayIndex, slotIndex ->
            selectedStudentSlot = daysList[dayIndex].slots[slotIndex]
            selectedStudentDate = daysList[dayIndex].dayName
        }

        recycler.adapter = adapter

        selectedPeerUid?.let { uid ->
            UserCache.loadPeerAvailability(uid)
            UserCache.availabilityLiveData.observe(viewLifecycleOwner) { weekly ->
                val next7Dates = getNext7Dates()
                daysList.clear()
                daysList.addAll(
                    weekly.take(7).mapIndexed { index, day ->
                        DayAvailability(
                            dayName = next7Dates.getOrElse(index) { day.day },
                            slots = day.slots.map { TimeSlot(it.label, it.selected) }.toMutableList()
                        )
                    }
                )
                adapter.notifyDataSetChanged()
            }
        }

        view.findViewById<MaterialButton>(R.id.btnsubmit).setOnClickListener {
            if (selectedStudentSlot == null) {
                Toast.makeText(requireContext(), "Please select a time slot", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: submit session booking to Firestore / backend

            findNavController().navigate(
                findNavController().graph.startDestinationId,
                null,
                androidx.navigation.navOptions {
                    popUpTo(findNavController().graph.startDestinationId) { inclusive = true }
                }
            )
        }
    }

    private fun getNext7Dates(): List<String> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        return List(7) {
            val date = sdf.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            date
        }
    }
}
