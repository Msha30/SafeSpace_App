package com.example.safespace_app

data class Peer(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String = "",
    var isOnline: Boolean = false
)
