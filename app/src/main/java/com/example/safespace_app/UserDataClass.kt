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
data class DayAvailability(
    val dayName: String,
    var slots: MutableList<TimeSlot>
)

data class TimeSlot(
    var label: String,   // "8:00 - 10:00"
    var selected: Boolean = false
)

data class CachedAvailability(
    val day: String,
    val slots: List<TimeSlot>
)
