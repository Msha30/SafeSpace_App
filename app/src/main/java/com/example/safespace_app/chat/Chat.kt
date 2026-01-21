package com.example.safespace_app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.SupportGroup
import com.example.safespace_app.chat.adapter.SupportGroupAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Chat : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SupportGroupAdapter
    private val db = FirebaseFirestore.getInstance()

    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerView = view.findViewById(R.id.supportGroup_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SupportGroupAdapter(mutableListOf()) { group ->
            // TODO: open support group page or group chat list
            // openSupportGroup(group.id)
        }

        recyclerView.adapter = adapter

        listenToSupportGroups()

        return view
    }

    private fun listenToSupportGroups() {
        listener = db.collection("supportgroup")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val groups = snapshot.documents.map { doc ->
                    SupportGroup(
                        id = doc.id,
                        name = doc.getString("supportgroup_name") ?: "",
                        pfpUrl = doc.getString("supportgroup_pfp_URL"),
                        members = doc.get("member_list") as? List<String> ?: emptyList()
                    )
                }

                adapter.updateData(groups)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}
