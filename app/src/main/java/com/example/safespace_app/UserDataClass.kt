package com.example.safespace_app

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

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
data class PeerSession(
    val sessionId: String,
    val studentUid: String,
    val peerUid: String,
    val selectedDate: String,
    val selectedTimeSlot: String,
    var location: String?,
    var status: String?,
    val topicOfConcern: String,
    val additionalConcern: String,
    val preferredMode: String,
    val sessionComplete: Boolean,
    val createdAt: Long,
    val requestId: String,
    var callStatus: String? = null,  // "waiting", "active", "ended"
    var callInitiatorUid: String? = null
)

data class Announcement(
    val title: String = "",
    val description: String = "",
    val represented_by: String = "",
    val created_by: String = "",
    val date_created: Timestamp = Timestamp.now(),
    val photo_urls: List<String> = emptyList()
)

data class SupportGroup(
    val id: String = "",
    val name: String = "",
    val pfpUrl: String? = null,
    val members: List<String> = emptyList()
)

data class GroupChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ModerationResponse(
    val flagged: Boolean,
    val categories: Map<String, Boolean>,
    val categoryScores: Map<String, Double>,
    val patternBased: Boolean = false,
    val mistralUsed: Boolean = false,
    val cached: Boolean = false,
    val error: String? = null
)
data class CounselingSession(
    val id: String = "",
    val title: String = "",
    val assigned_sched: AssignedSched? = null,
    val preferredPlatform: String = "",
    val status: String = "",
    val taken_by: String? = null,
    val started_by: String? = null,
    val createdBy: String = ""
)

data class AssignedSched(
    val date: String = "",
    val start: Timestamp? = null,
    val end: Timestamp? = null
)