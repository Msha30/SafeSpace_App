package com.example.safespace_app

import android.app.Application
import com.example.safespace_app.profile.NotificationSettingsManager
import com.google.firebase.database.FirebaseDatabase

class SupabaseClass : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Supabase globally
        NotificationSettingsManager.createNotificationChannel(this)
        SupaClient.init()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}