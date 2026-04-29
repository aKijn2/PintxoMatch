package com.example.pintxomatch.data.model.chat

data class ChatMessage(
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0
)