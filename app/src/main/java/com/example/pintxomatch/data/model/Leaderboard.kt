package com.example.pintxomatch.data.model

data class LeaderboardUser(
    val uid: String,
    val displayName: String,
    val totalUploads: Int
)

data class LeaderboardPintxo(
    val id: String,
    val name: String,
    val barName: String,
    val averageRating: Double,
    val ratingCount: Int
)
