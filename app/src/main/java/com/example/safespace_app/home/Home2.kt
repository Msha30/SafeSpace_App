package com.example.safespace_app.home

import android.graphics.Rect
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.google.android.material.imageview.ShapeableImageView
import androidx.recyclerview.widget.GridLayoutManager // 👈 Import GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager


class Home2 : Fragment() {

    companion object {
        fun newInstance() = Home2()
    }

    private val viewModel: Home2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Use the ViewModel
    }

    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home2, container, false)

        upcomingRecyclerView = view.findViewById(R.id.upcoming)
        val upcomingAdapter = UpcomingAdapter(listOf("Session 1", "Session 2", "Session 3", "Session 4"))
        upcomingRecyclerView.adapter = upcomingAdapter
        upcomingRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val spacingInDp = 15f
        val spacingInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            spacingInDp,
            resources.displayMetrics
        ).toInt()

        upcomingRecyclerView.addItemDecoration(HalfScreenWidthItemDecoration(spacingInPixels))
        notificationsRecyclerView = view.findViewById(R.id.notifications)

        val notificationAdapter = NotificationAdapter(listOf("Session 1", "Session 2", "Session 3", "Session 4"))
        notificationsRecyclerView.adapter = notificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        return view
    }

    inner class HalfScreenWidthItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == 0) {
                outRect.left = 0
            } else {
                outRect.left = spacing
            }
        }
    }

    inner class UpcomingAdapter(private val items: List<String>) :
        RecyclerView.Adapter<UpcomingAdapter.CardViewHolder>() {

        inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_upcoming, parent, false)
            return CardViewHolder(view)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val params = holder.itemView.layoutParams
            params.width =
                (holder.itemView.context.resources.displayMetrics.widthPixels * 0.42).toInt()
            holder.itemView.layoutParams = params
        }

        override fun getItemCount() = items.size
    }

    inner class NotificationAdapter(private val dataList: List<String>) :
        RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notif, parent, false)
            return NotificationViewHolder(view)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        }

        override fun getItemCount() = dataList.size
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now we can safely find views
        val btnPeerSupport = view.findViewById<ShapeableImageView>(R.id.peersupport)
        val btnschedule = view.findViewById<ShapeableImageView>(R.id.counseling)

        btnschedule.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeSchedule)
        }

        btnPeerSupport.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeSessionManagement)
        }
    }
}