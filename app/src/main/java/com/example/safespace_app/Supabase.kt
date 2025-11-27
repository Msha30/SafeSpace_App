package com.example.safespace_app

import android.app.Application

class SupabaseClass : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Supabase globally
        SupaClient.init()
    }
}