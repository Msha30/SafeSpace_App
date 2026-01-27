package com.example.safespace_app.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {

    // Check if notification permission is granted
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For Android 12 and below, notifications are enabled by default
            true
        }
    }

    // Show dialog explaining why we need permission
    fun showPermissionRationaleDialog(context: Context, onPositive: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Enable Notifications")
            .setMessage("To receive message notifications and stay updated, please enable notifications for SafeSpace.")
            .setPositiveButton("Enable") { dialog, _ ->
                dialog.dismiss()
                onPositive()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Open app settings for manual permission grant
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}