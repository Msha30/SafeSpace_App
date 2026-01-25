package com.example.safespace_app.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.safespace_app.UserCache
import com.google.firebase.auth.FirebaseAuth

class ProfileViewModel : ViewModel() {

    private val _avatarUrl = MutableLiveData<String>()
    val avatarUrl: LiveData<String> = _avatarUrl

    private var loadedUrl: String? = null  // store last loaded URL in memory

    fun loadAvatar(forceRefresh: Boolean = false) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // If we already have a URL cached in memory, use it
        if (!forceRefresh && loadedUrl != null) {
            _avatarUrl.postValue(loadedUrl)
            return
        }

        UserCache.getUserDetails(uid, forceRefresh) { _, avatar ->
            if (avatar.isNotEmpty()) {
                // Only append timestamp if it's a URL (Peer side)
                // If it's a preset ID (Student side), keep it clean
                val processedUrl = if (avatar.startsWith("http")) {
                    "$avatar?v=${System.currentTimeMillis()}"
                } else {
                    avatar
                }

                loadedUrl = processedUrl
                _avatarUrl.postValue(processedUrl)
            }
        }
    }

    fun updateAvatar(newAvatarUrl: String) {
        // Same logic here: check if it's a URL or a preset ID
        val processedUrl = if (newAvatarUrl.startsWith("http")) {
            "$newAvatarUrl?v=${System.currentTimeMillis()}"
        } else {
            newAvatarUrl
        }

        loadedUrl = processedUrl
        _avatarUrl.postValue(processedUrl)
    }
}