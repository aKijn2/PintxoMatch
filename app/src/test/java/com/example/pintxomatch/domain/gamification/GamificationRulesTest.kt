package com.example.pintxomatch.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationRulesTest {

    @Test
    fun buildLevelInfo_returnsExpectedLevelAndProgress() {
        val levelInfo = GamificationRules.buildLevelInfo(250)

        assertEquals(3, levelInfo.level)
        assertEquals(50, levelInfo.xpInCurrentLevel)
        assertEquals(50, levelInfo.xpNeededForNextLevel)
        assertEquals(0.5f, levelInfo.progressToNextLevel)
    }

    @Test
    fun calculateUpdatedStreak_whenFirstAction_returnsOne() {
        val streak = GamificationRules.calculateUpdatedStreak(
            previousStreak = 0,
            previousLastActionTimestamp = 0L,
            actionTimestamp = 1000L
        )

        assertEquals(1, streak)
    }

    @Test
    fun calculateUpdatedStreak_whenLastActionWasYesterday_increments() {
        val oneDay = 24L * 60L * 60L * 1000L
        val previous = 7L * oneDay
        val current = previous + oneDay

        val streak = GamificationRules.calculateUpdatedStreak(
            previousStreak = 4,
            previousLastActionTimestamp = previous,
            actionTimestamp = current
        )

        assertEquals(5, streak)
    }

    @Test
    fun calculateUpdatedStreak_whenElapsedIsOver48Hours_resetsToZero() {
        val previous = 1_000_000L
        val current = previous + (49L * 60L * 60L * 1000L)

        val streak = GamificationRules.calculateUpdatedStreak(
            previousStreak = 9,
            previousLastActionTimestamp = previous,
            actionTimestamp = current
        )

        assertEquals(0, streak)
    }

    @Test
    fun calculateUpdatedStreak_whenSameDay_keepsCurrentStreak() {
        val previous = 10_000L
        val current = previous + 1_000L

        val streak = GamificationRules.calculateUpdatedStreak(
            previousStreak = 3,
            previousLastActionTimestamp = previous,
            actionTimestamp = current
        )

        assertTrue(streak >= 3)
    }
}
