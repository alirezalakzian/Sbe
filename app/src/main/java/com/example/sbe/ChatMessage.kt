package com.example.sbe

data class ChatMessage(
    val senderId: String,
    val receiverId: String,
    val messageText: String,
    val timestamp: Long
)
