package com.example.pintxomatch.domain.gamification

import kotlin.math.max

const val XP_PER_LEVEL = 100
const val RATE_PINTXO_XP = 10
const val UPLOAD_PINTXO_XP = 50

private const val MILLIS_IN_DAY = 24L * 60L * 60L * 1000L
private const val MILLIS_48_HOURS = 48L * 60L * 60L * 1000L

data class LevelInfo(
    val level: Int,
    val progressToNextLevel: Float,
    val xpInCurrentLevel: Int,
    val xpNeededForNextLevel: Int
)

object GamificationRules {

    fun buildLevelInfo(xp: Int): LevelInfo {
        val safeXp = xp.coerceAtLeast(0)
        val level = (safeXp / XP_PER_LEVEL) + 1
        val xpInCurrentLevel = safeXp % XP_PER_LEVEL
        val progress = xpInCurrentLevel / XP_PER_LEVEL.toFloat()

        return LevelInfo(
            level = level,
            progressToNextLevel = progress.coerceIn(0f, 1f),
            xpInCurrentLevel = xpInCurrentLevel,
            xpNeededForNextLevel = XP_PER_LEVEL - xpInCurrentLevel
        )
    }

    fun calculateUpdatedStreak(
        previousStreak: Int,
        previousLastActionTimestamp: Long,
        actionTimestamp: Long
    ): Int {
        if (previousLastActionTimestamp <= 0L) return 1

        val previousDay = previousLastActionTimestamp / MILLIS_IN_DAY
        val currentDay = actionTimestamp / MILLIS_IN_DAY
        val dayDelta = currentDay - previousDay

        if (dayDelta <= 0L) {
            return max(previousStreak, 1)
        }

        if (dayDelta == 1L) {
            return max(previousStreak, 0) + 1
        }

        val elapsed = actionTimestamp - previousLastActionTimestamp
        return if (elapsed > MILLIS_48_HOURS) 0 else max(previousStreak, 1)
    }
}
