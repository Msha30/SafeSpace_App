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
    private var isLoadingSettings = false

    // Permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isLoadingSettings = true
        allNotificationsSwitch.isChecked = isGranted
        isLoadingSettings = false
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

        allNotificationsSwitch = rootView.findViewById(R.id.switchAllNotifications)

        setupSwitchListeners()
        loadSettings()

        return rootView
    }

    override fun onResume() {
        super.onResume()

        // Sync switch with actual system permission
        isLoadingSettings = true
        val granted = NotificationPermissionHelper
            .isNotificationPermissionGranted(requireContext())
        allNotificationsSwitch.isChecked = granted
        isLoadingSettings = false
    }

    private fun setupSwitchListeners() {
        allNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener

            if (isChecked) {
                // Enable → request runtime permission (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationPermissionHelper.isNotificationPermissionGranted(requireContext())
                ) {
                    NotificationPermissionHelper.showPermissionRationaleDialog(requireContext()) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    // Reset until permission actually granted
                    isLoadingSettings = true
                    allNotificationsSwitch.isChecked = false
                    isLoadingSettings = false
                    return@setOnCheckedChangeListener
                }
            } else {
                // Disable → open system notification settings
                NotificationPermissionHelper.openAppSettings(requireContext())
            }

            saveSettings()
        }
    }

    private fun loadSettings() {
        isLoadingSettings = true

        lifecycleScope.launch {
            val systemGranted = NotificationPermissionHelper
                .isNotificationPermissionGranted(requireContext())

            val settings = NotificationSettingsManager.loadSettings()

            allNotificationsSwitch.isChecked =
                if (!systemGranted) false else settings.allNotifications

            isLoadingSettings = false
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            val settings = NotificationSettingsManager.NotificationSettings(
                allNotifications = allNotificationsSwitch.isChecked
            )
            NotificationSettingsManager.saveSettings(settings)
        }
    }
}
