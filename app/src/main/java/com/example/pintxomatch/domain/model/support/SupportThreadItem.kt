package com.example.pintxomatch.domain.model.support

data class SupportThreadItem(
    val threadId: String,
    val userName: String,
    val ticketTitle: String,
    val userEmail: String,
    val lastMessage: String,
    val status: String,
    val updatedAt: Long
)
