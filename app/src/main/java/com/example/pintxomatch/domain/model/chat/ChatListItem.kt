package com.example.pintxomatch.domain.model.chat

data class ChatListItem(
    val chatId: String,
    val title: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val messageCount: Int
)
