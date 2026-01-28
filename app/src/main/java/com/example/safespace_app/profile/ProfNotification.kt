package com.example.safespace_app.profile

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import kotlinx.coroutines.launch

class ProfNotification : Fragment() {

    private lateinit var allNotificationsSwitch: SwitchCompat
    private lateinit var newMessagesSwitch: SwitchCompat
    private lateinit var newGroupMessagesSwitch: SwitchCompat
    private lateinit var eventsSwitch: SwitchCompat
    private lateinit var announcementsSwitch: SwitchCompat
    private lateinit var counselingSwitch: SwitchCompat
    private lateinit var peerSupportSwitch: SwitchCompat
    private lateinit var reminderSwitch: SwitchCompat

    private var isLoadingSettings = false

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isLoadingSettings = true
        allNotificationsSwitch.isChecked = isGranted
        isLoadingSettings = false

        if (isGranted) {
            setAllSwitchesEnabled(true)
            saveSettings()
        } else {
            setAllSwitchesEnabled(false)
            updateAllSwitches(false)
            saveSettings()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_prof_notification, container, false)

        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        initializeSwitches(rootView)
        setupSwitchListeners()
        loadSettings()

        return rootView
    }

    override fun onResume() {
        super.onResume()

        // Update switch based on system permission
        isLoadingSettings = true
        val systemPermissionGranted = NotificationPermissionHelper.isNotificationPermissionGranted(requireContext())
        allNotificationsSwitch.isChecked = systemPermissionGranted
        setAllSwitchesEnabled(systemPermissionGranted)
        isLoadingSettings = false
    }

    private fun initializeSwitches(view: View) {
        allNotificationsSwitch = view.findViewById(R.id.switchAllNotifications)
        newMessagesSwitch = view.findViewById(R.id.switchNewMessages)
        newGroupMessagesSwitch = view.findViewById(R.id.switchNewGroupMessages)
    }

    private fun setupSwitchListeners() {
        // All notifications master switch
        allNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener

            if (isChecked) {
                // User wants to enable notifications - request system permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!NotificationPermissionHelper.isNotificationPermissionGranted(requireContext())) {
                        // Need to request permission
                        NotificationPermissionHelper.showPermissionRationaleDialog(requireContext()) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        // Reset switch until permission granted
                        isLoadingSettings = true
                        allNotificationsSwitch.isChecked = false
                        isLoadingSettings = false
                        return@setOnCheckedChangeListener
                    }
                }

                // Permission granted, enable switches
                setAllSwitchesEnabled(true)
            } else {
                // User wants to disable - open system settings
                NotificationPermissionHelper.openAppSettings(requireContext())

                // Disable all switches in our app
                setAllSwitchesEnabled(false)
                updateAllSwitches(false)
            }

            saveSettings()
        }

        // Individual switches
        newMessagesSwitch.setOnCheckedChangeListener { _, _ ->
            if (!isLoadingSettings) saveSettings()
        }

        newGroupMessagesSwitch.setOnCheckedChangeListener { _, _ ->
            if (!isLoadingSettings) saveSettings()
        }
    }

    private fun loadSettings() {
        isLoadingSettings = true

        lifecycleScope.launch {
            // Check system permission first
            val systemPermissionGranted = NotificationPermissionHelper.isNotificationPermissionGranted(requireContext())

            val settings = NotificationSettingsManager.loadSettings()

            // If system permission denied, override all settings
            if (!systemPermissionGranted) {
                allNotificationsSwitch.isChecked = false
                updateAllSwitches(false)
                setAllSwitchesEnabled(false)
            } else {
                allNotificationsSwitch.isChecked = settings.allNotifications
                newMessagesSwitch.isChecked = settings.newMessages
                newGroupMessagesSwitch.isChecked = settings.newGroupMessages

                setAllSwitchesEnabled(settings.allNotifications)
            }

            isLoadingSettings = false
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            val settings = NotificationSettingsManager.NotificationSettings(
                allNotifications = allNotificationsSwitch.isChecked,
                newMessages = newMessagesSwitch.isChecked,
                newGroupMessages = newGroupMessagesSwitch.isChecked,
            )

            NotificationSettingsManager.saveSettings(settings)
        }
    }

    private fun setAllSwitchesEnabled(enabled: Boolean) {
        newMessagesSwitch.isEnabled = enabled
        newGroupMessagesSwitch.isEnabled = enabled
    }

    private fun updateAllSwitches(checked: Boolean) {
        newMessagesSwitch.isChecked = checked
        newGroupMessagesSwitch.isChecked = checked
    }
}