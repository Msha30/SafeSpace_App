package com.example.safespace_app.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.safespace_app.GroupChatMessage

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<GroupChatMessage>>(emptyList())
    val messages: LiveData<List<GroupChatMessage>> = _messages

    val userDisplayNameCache = mutableMapOf<String, String>()

    fun addMessages(newMessages: List<GroupChatMessage>) {
        val updated = (_messages.value ?: emptyList()) + newMessages
        _messages.value = updated
    }

    fun prependMessages(oldMessages: List<GroupChatMessage>) {
        val updated = oldMessages + (_messages.value ?: emptyList())
        _messages.value = updated
    }

    fun updateMessageSenderName(messageId: String, newName: String) {
        val current = _messages.value ?: return
        _messages.value = current.map {
            if (it.id == messageId) it.copy(senderName = newName) else it
        }
    }
}