package com.example.safespace_app.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.safespace_app.UserCache
import com.google.firebase.auth.FirebaseAuth

class Profile2ViewModel : ViewModel() {
    private val _avatarUrl = MutableLiveData<String>()
    val avatarUrl: LiveData<String> = _avatarUrl

    private var loaded = false

    fun loadAvatar(forceRefresh: Boolean = false) {
        if (loaded && !forceRefresh) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        UserCache.getUserDetails(uid, forceRefresh) { _, avatar ->
            _avatarUrl.postValue(avatar)
            loaded = true
        }
    }
}