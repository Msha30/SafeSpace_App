package com.example.safespace_app

data class Peer(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String = "",
    var isOnline: Boolean = false
)
data class UnifiedSession(
    val sessionId: String,
    val userUid: String,      // student or peer UID
    val name: String,         // studentName or peerName
    val photoUrl: String,     // studentPhoto or peerPhoto
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0
)