package com.example.pintxomatch.data.model

import com.example.pintxomatch.domain.gamification.RATE_PINTXO_XP
import com.example.pintxomatch.domain.gamification.UPLOAD_PINTXO_XP
import com.google.firebase.firestore.DocumentSnapshot

enum class GamificationActionType(
    val firestoreKey: String,
    val xpReward: Int
) {
    RATE_PINTXO("RATE_PINTXO", RATE_PINTXO_XP),
    UPLOAD_PINTXO("UPLOAD_PINTXO", UPLOAD_PINTXO_XP);

    companion object {
        fun fromFirestoreKey(key: String?): GamificationActionType? {
            return entries.firstOrNull { it.firestoreKey == key }
        }
    }
}

data class WeeklyChallenge(
    val id: String,
    val weekId: String,
    val title: String,
    val description: String,
    val actionType: GamificationActionType,
    val targetCount: Int,
    val badgeId: String,
    val startsAt: Long,
    val endsAt: Long,
    val isActive: Boolean
)

data class WeeklyChallengeProgress(
    val challengeId: String,
    val title: String,
    val description: String,
    val badgeId: String,
    val targetCount: Int,
    val progressCount: Int,
    val completed: Boolean
)

fun DocumentSnapshot.toWeeklyChallengeOrNull(): WeeklyChallenge? {
    val action = GamificationActionType.fromFirestoreKey(getString("actionType")) ?: return null

    return WeeklyChallenge(
        id = id,
        weekId = getString("weekId") ?: "",
        title = getString("title") ?: "Reto semanal",
        description = getString("description") ?: "",
        actionType = action,
        targetCount = getLong("targetCount")?.toInt()?.coerceAtLeast(1) ?: 1,
        badgeId = getString("badgeId") ?: "badge_$id",
        startsAt = getLong("startsAt") ?: 0L,
        endsAt = getLong("endsAt") ?: Long.MAX_VALUE,
        isActive = getBoolean("isActive") ?: true
    )
}
