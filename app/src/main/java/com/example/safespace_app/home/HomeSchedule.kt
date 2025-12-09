package com.example.safespace_app.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.DayAvailability
import com.example.safespace_app.R
import com.example.safespace_app.TimeSlot
import com.example.safespace_app.UserCache
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class HomeSchedule : Fragment() {

    private lateinit var adapter: AvailabilityAdapter
    private var daysList = mutableListOf<DayAvailability>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = FirebaseAuth.getInstance().currentUser!!.uid

        val recycler = view.findViewById<RecyclerView>(R.id.availabilityRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // 🚀 SHOW DEFAULT LIST IMMEDIATELY (THIS WAS MISSING)
        daysList = defaultDays()
        adapter = AvailabilityAdapter(requireContext(), daysList)
        recycler.adapter = adapter

        // NOW observe cache / firestore updates
        UserCache.availabilityLiveData.observe(viewLifecycleOwner) { weekly ->
            Log.d("HomeSchedule", "Availability received: $weekly")
            if (weekly.isNullOrEmpty()) return@observe

            val dayOrder = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
            daysList = weekly.sortedBy { dayOrder.indexOf(it.day) }.map {
                DayAvailability(it.day, it.slots.map { s -> TimeSlot(s.label, s.selected) }.toMutableList())
            }.toMutableList()

            adapter = AvailabilityAdapter(requireContext(), daysList)
            recycler.adapter = adapter
        }


        // load from cache or Firestore
        UserCache.loadPeerAvailability(uid)

        view.findViewById<MaterialButton>(R.id.saveBtn).setOnClickListener {
            UserCache.savePeerAvailability(uid, daysList) { success ->
                if (success) {
                    Log.d("HomeSchedule", "Saved!")
                    // Go back after saving
                    findNavController().navigateUp()
                } else {
                    Log.w("HomeSchedule", "Failed to save availability")
                }
            }
        }

        // back
        view.findViewById<ImageView>(R.id.backbtn).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun defaultDays(): MutableList<DayAvailability> {
        val defaultSlots = listOf(
            TimeSlot("7:00 - 9:00"),
            TimeSlot("9:00 - 11:00"),
            TimeSlot("1:00 - 3:00"),
            TimeSlot("3:00 - 5:00")
        )

        return mutableListOf(
            DayAvailability("Monday", defaultSlots.toMutableList()),
            DayAvailability("Tuesday", defaultSlots.toMutableList()),
            DayAvailability("Wednesday", defaultSlots.toMutableList()),
            DayAvailability("Thursday", defaultSlots.toMutableList()),
            DayAvailability("Friday", defaultSlots.toMutableList()),
            DayAvailability("Saturday", defaultSlots.toMutableList()),
            DayAvailability("Sunday", defaultSlots.toMutableList()),
        )
    }

}