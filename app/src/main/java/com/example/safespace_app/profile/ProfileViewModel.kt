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
                // Append timestamp to bust Glide cache
                val cacheBusted = "$avatar?v=${System.currentTimeMillis()}"
                loadedUrl = cacheBusted
                _avatarUrl.postValue(cacheBusted)
            }
        }
    }

    fun updateAvatar(newAvatarUrl: String) {
        val cacheBusted = "$newAvatarUrl?v=${System.currentTimeMillis()}"
        loadedUrl = cacheBusted
        _avatarUrl.postValue(cacheBusted)
    }
}