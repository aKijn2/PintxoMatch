package com.example.pintxomatch.data.model.leaderboard

data class LeaderboardUser(
    val uid: String,
    val displayName: String,
    val totalUploads: Int,
    val profileImageUrl: String = ""
)

data class LeaderboardPintxo(
    val id: String,
    val name: String,
    val barName: String,
    val averageRating: Double,
    val ratingCount: Int
)
