package com.example.safespace_app.peers

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.safespace_app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class Peers : Fragment() {

    private val viewModel: PeersViewModel by viewModels()
    private val pairingManager = PairingManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private var hasNavigated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_peers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChildNav()
        checkExistingSession()
    }

    private fun setupChildNav() {
        // required to initialize child NavController
        childFragmentManager.findFragmentById(R.id.container) as NavHostFragment
    }

    private fun checkExistingSession() {
        lifecycleScope.launch {
            pairingManager.getActiveSession(studentUid) { sessionId, peerUid ->
                if (!isAdded || hasNavigated) return@getActiveSession

                if (sessionId != null && peerUid != null) {
                    goToChat()
                }
            }
        }
    }

    private fun goToChat() {
        val navHostFragment = childFragmentManager.findFragmentById(R.id.container)
                as NavHostFragment
        val navController = navHostFragment.navController

        // Only navigate if still in peers start location
        if (navController.currentDestination?.id == R.id.peers_1) {
            hasNavigated = true
            navController.navigate(R.id.action_peers_1_to_peers_Chat)
        }
    }
}
