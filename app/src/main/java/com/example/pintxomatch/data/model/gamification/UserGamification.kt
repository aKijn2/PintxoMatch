package com.example.pintxomatch.data.model.gamification

import com.google.firebase.firestore.DocumentSnapshot

data class UserGamification(
    val xp: Int = 0,
    val currentStreak: Int = 0,
    val lastActionTimestamp: Long = 0L,
    val badges: List<String> = emptyList()
)

fun DocumentSnapshot.toUserGamification(): UserGamification {
    val rawBadges = get("badges") as? List<*> ?: emptyList<Any>()

    return UserGamification(
        xp = getLong("xp")?.toInt()?.coerceAtLeast(0) ?: 0,
        currentStreak = getLong("currentStreak")?.toInt()?.coerceAtLeast(0) ?: 0,
        lastActionTimestamp = getLong("lastActionTimestamp") ?: 0L,
        badges = rawBadges.mapNotNull { it as? String }.distinct()
    )
}
