package com.example.safespace_app

import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.tasks.await

object SupaClient {

    lateinit var client: SupabaseClient
        private set

    fun init() {
        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Storage)

            // Use Firebase token for authentication
            accessToken = {
                try {
                    // Get Firebase ID token
                    val firebaseUser = FirebaseAuth.getInstance().currentUser
                    val tokenResult = firebaseUser?.getIdToken(false)?.await()
                    tokenResult?.token
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}