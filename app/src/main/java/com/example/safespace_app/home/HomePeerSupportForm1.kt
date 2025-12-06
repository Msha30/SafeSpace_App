package com.example.safespace_app.home

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.example.safespace_app.cache.UserCache
import com.google.android.material.button.MaterialButton

class HomePeerSupportForm1 : Fragment(R.layout.fragment_home_peer_support_form1) {

    private lateinit var peersRadioGroup: RadioGroup
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var topicRadioGroup: RadioGroup
    private lateinit var generalConcernEditText: com.google.android.material.textfield.TextInputEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peersRadioGroup = view.findViewById(R.id.peerslist)
        modeRadioGroup = view.findViewById(R.id.modeRadioGroup)
        topicRadioGroup = view.findViewById(R.id.topicRadioGroup)
        generalConcernEditText = view.findViewById(R.id.generalConcernEditText)

        // Load peers from cache
        UserCache.loadPeers()
        UserCache.peersLiveData.observe(viewLifecycleOwner) { peers ->
            populatePeers(peers)
        }

        val btnNext = view.findViewById<MaterialButton>(R.id.btnnext)
        btnNext.setOnClickListener {
            val selectedPeerUid = getSelectedPeerUid()
            val selectedModeId = modeRadioGroup.checkedRadioButtonId
            val selectedTopicId = topicRadioGroup.checkedRadioButtonId
            val generalConcernText = generalConcernEditText.text.toString()

            if (selectedPeerUid != null && selectedModeId != -1 && selectedTopicId != -1) {
                val preferredMode = modeRadioGroup.findViewById<RadioButton>(selectedModeId).text.toString()
                val topicOfConcern = topicRadioGroup.findViewById<RadioButton>(selectedTopicId).text.toString()

                // Pass data using Bundle
                val bundle = Bundle().apply {
                    putString("selectedPeerUid", selectedPeerUid)
                    putString("preferredMode", preferredMode)
                    putString("topicOfConcern", topicOfConcern)
                    putString("generalConcernText", generalConcernText)
                }

                findNavController().navigate(R.id.homePeerSupportForm2, bundle)
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populatePeers(peers: List<com.example.safespace_app.Peer>) {
        peersRadioGroup.removeAllViews()
        for (peer in peers) {
            val radioButton = RadioButton(requireContext()).apply {
                text = peer.name
                id = View.generateViewId()
                setTextColor(resources.getColor(R.color.black, null))
                textSize = resources.getDimension(R.dimen.reg) / resources.displayMetrics.scaledDensity
                typeface = resources.getFont(R.font.ps)
            }
            peersRadioGroup.addView(radioButton)
        }
    }

    private fun getSelectedPeerUid(): String? {
        val selectedId = peersRadioGroup.checkedRadioButtonId
        val selectedIndex = peersRadioGroup.indexOfChild(peersRadioGroup.findViewById(selectedId))
        return if (selectedIndex != -1) {
            UserCache.peersLiveData.value?.get(selectedIndex)?.uid
        } else null
    }
}
