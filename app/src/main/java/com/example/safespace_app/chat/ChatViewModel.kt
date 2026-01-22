package com.example.safespace_app.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.safespace_app.GroupChatMessage

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<GroupChatMessage>>(emptyList())
    val messages: LiveData<List<GroupChatMessage>> = _messages

    // Canonical source of truth
    private val messageMap = LinkedHashMap<String, GroupChatMessage>()

    val userDisplayNameCache = mutableMapOf<String, String>()

    fun addMessages(newMessages: List<GroupChatMessage>) {
        var changed = false

        for (msg in newMessages) {
            if (!messageMap.containsKey(msg.id)) {
                messageMap[msg.id] = msg
                changed = true
            }
        }

        if (changed) {
            _messages.value = messageMap.values.sortedBy { it.timestamp }
        }
    }

    fun prependMessages(oldMessages: List<GroupChatMessage>) {
        var changed = false

        for (msg in oldMessages) {
            if (!messageMap.containsKey(msg.id)) {
                messageMap[msg.id] = msg
                changed = true
            }
        }

        if (changed) {
            _messages.value = messageMap.values.sortedBy { it.timestamp }
        }
    }

    fun updateMessageSenderName(messageId: String, name: String) {
        val msg = messageMap[messageId] ?: return
        messageMap[messageId] = msg.copy(senderName = name)
        _messages.value = messageMap.values.sortedBy { it.timestamp }
    }
}
