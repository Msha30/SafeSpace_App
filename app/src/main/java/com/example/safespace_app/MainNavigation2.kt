package com.example.safespace_app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.safespace_app.databinding.ActivityMainNavigation2Binding
import com.google.firebase.Firebase
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.auth
import com.example.safespace_app.peers.PairingManager
import com.google.firebase.database.ValueEventListener

class MainNavigation2 : AppCompatActivity() {

    // --------------------------------------------------
    //  Data Classes
    // --------------------------------------------------

    data class IncomingRequest(
        val requestId: String,
        val studentUid: String
    )

    // --------------------------------------------------
    //  Properties
    // --------------------------------------------------

    lateinit var presenceManager: PresenceManager
    private lateinit var binding: ActivityMainNavigation2Binding
    private val prefs by lazy { getSharedPreferences("user_cache", Context.MODE_PRIVATE) }

    private val pairingManager = PairingManager()
    private var requestListener: ValueEventListener? = null
    private val peerUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    // NEW: Store FULL pending request information
    private val pendingRequestMap = mutableMapOf<String, IncomingRequest>()

    // Active fragment callback
    var onPairingRequestReceived: ((requestId: String, studentUid: String) -> Unit)? = null

    // --------------------------------------------------
    //  Lifecycle
    // --------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        super.onCreate(savedInstanceState)

        binding = ActivityMainNavigation2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshTokenAndInitSupabase()

        val auth = FirebaseAuth.getInstance()
        presenceManager = PresenceManager(auth)
        presenceManager.startTracking()

        setupNavigationUI()

        // Cache user data
        cacheUserDataIfNeeded()

        // Start RTDB listener for pairing
        startListeningForRequests()
    }

    fun hideBottomNav() {
        binding.navView.visibility = View.GONE
    }

    fun showBottomNav() {
        binding.navView.visibility = View.VISIBLE
    }
    // --------------------------------------------------
    //  Navigation Setup
    // --------------------------------------------------

    private fun setupNavigationUI() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main_navigation2)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home2, R.id.nav_peers2, R.id.nav_chat, R.id.nav_profile2
            )
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeSchedule, R.id.profNotification2, R.id.profInfo2,
                R.id.appTermsAndConditions2, R.id.appPrivacyPolicy2,
                R.id.homeSessionManagement,R.id.chatMessageFragment, R.id.chatMessageFragment2,
                R.id.photoManagerFragment, R.id.homeNewEvent, R.id.chatSupportGroupChat-> binding.navView.visibility = View.GONE
                else -> binding.navView.visibility = View.VISIBLE
            }
        }

        navView.setupWithNavController(navController)
    }

    // --------------------------------------------------
    //  Pairing Request Listener
    // --------------------------------------------------

    private fun startListeningForRequests() {
        if (requestListener != null) {
            Log.d("MainNavigation2", "Request listener already active")
            return
        }

        Log.d("MainNavigation2", "Listening for requests for peer: $peerUid")

        requestListener = pairingManager.listenForRequests(peerUid) { requestId, studentUid ->

            Log.d("MainNavigation2", "RTDB → Incoming request: $requestId from $studentUid")

            // Save request even if current fragment is not Peers2
            if (!pendingRequestMap.containsKey(requestId)) {
                pendingRequestMap[requestId] = IncomingRequest(requestId, studentUid)
                Log.d("MainNavigation2", "Stored request: $requestId")
            }

            // Notify active fragment (if open)
            runOnUiThread {
                onPairingRequestReceived?.invoke(requestId, studentUid)
            }
        }
    }

    // --------------------------------------------------
    //  Called by Peers2 to fetch all pending requests
    // --------------------------------------------------

    fun checkPendingRequests(callback: (requestId: String, studentUid: String) -> Unit) {

        Log.d("MainNavigation2", "Peers2 requests pending list: ${pendingRequestMap.size}")

        for (req in pendingRequestMap.values) {
            Log.d("MainNavigation2", "Forwarding stored request: ${req.requestId}")

            runOnUiThread {
                callback(req.requestId, req.studentUid)
            }
        }
    }

    // --------------------------------------------------
    //  Remove handled request
    // --------------------------------------------------

    fun markRequestHandled(requestId: String) {
        pendingRequestMap.remove(requestId)
        Log.d("MainNavigation2", "Request $requestId removed from pending map")
    }

    // --------------------------------------------------
    //  Support Functions
    // --------------------------------------------------

    private fun refreshTokenAndInitSupabase() {
        val currentUser = Firebase.auth.currentUser ?: return

        currentUser.getIdToken(true)
            .addOnSuccessListener { result: GetTokenResult ->
                val role = result.claims["role"] as? String
                Log.d("MainNavigation2", "Loaded claims: ${result.claims}")

                if (role == "authenticated") {
                    SupaClient.init()
                } else {
                    Log.w("MainNavigation2", "User missing 'authenticated' claim")
                }
            }
            .addOnFailureListener {
                Log.e("MainNavigation2", "Failed refreshing token", it)
            }
    }

    private fun cacheUserDataIfNeeded() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val editor = prefs.edit()

        db.collection("account_details").document(uid).get()
            .addOnSuccessListener { doc ->
                val data = doc.data ?: return@addOnSuccessListener
                var updated = false

                for ((key, value) in data) {
                    if (prefs.all[key] != value.toString()) {
                        editor.putString(key, value.toString())
                        updated = true
                    }
                }

                if (updated) editor.apply()
            }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up listener
        requestListener?.let {
            pairingManager.removeListener(it)
        }

        pendingRequestMap.clear()
        onPairingRequestReceived = null

        Log.d("MainNavigation2", "Cleaned up all pairing listeners")
    }
}
