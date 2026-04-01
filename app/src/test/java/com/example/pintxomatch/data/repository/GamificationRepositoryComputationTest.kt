package com.example.pintxomatch.data.repository

import com.example.pintxomatch.data.model.GamificationActionType
import com.example.pintxomatch.data.model.UserGamification
import com.example.pintxomatch.data.model.WeeklyChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationRepositoryComputationTest {

    private fun rateChallenge(target: Int = 3): WeeklyChallenge {
        return WeeklyChallenge(
            id = "rate-3",
            weekId = "2026-W14",
            title = "Valora 3 pintxos",
            description = "Valora pintxos en la semana",
            actionType = GamificationActionType.RATE_PINTXO,
            targetCount = target,
            badgeId = "badge_2026W14_critic",
            startsAt = 0L,
            endsAt = Long.MAX_VALUE,
            isActive = true
        )
    }

    @Test
    fun computeAwardComputation_unlocksBadgeWhenChallengeCompletes() {
        val challenge = rateChallenge()
        val now = 24L * 60L * 60L * 1000L

        val computation = computeAwardComputation(
            currentGamification = UserGamification(
                xp = 80,
                currentStreak = 2,
                lastActionTimestamp = 0L,
                badges = emptyList()
            ),
            matchingChallenges = listOf(challenge),
            previousProgressByChallenge = mapOf(
                challenge.id to ChallengeProgressState(progressCount = 2, completed = false)
            ),
            actionType = GamificationActionType.RATE_PINTXO,
            now = now
        )

        assertEquals(90, computation.updatedUser.xp)
        assertEquals(1, computation.updatedUser.currentStreak)
        assertTrue(computation.updatedUser.badges.contains(challenge.badgeId))
        assertEquals(listOf(challenge.badgeId), computation.unlockedBadges)

        val progress = computation.progressByChallenge.getValue(challenge.id)
        assertEquals(3, progress.progressCount)
        assertTrue(progress.completed)
        assertTrue(progress.completedNow)
    }

    @Test
    fun computeAwardComputation_doesNotDuplicateAlreadyUnlockedBadge() {
        val challenge = rateChallenge(target = 1)

        val computation = computeAwardComputation(
            currentGamification = UserGamification(
                xp = 10,
                currentStreak = 5,
                lastActionTimestamp = 100L,
                badges = listOf(challenge.badgeId)
            ),
            matchingChallenges = listOf(challenge),
            previousProgressByChallenge = mapOf(
                challenge.id to ChallengeProgressState(progressCount = 1, completed = true)
            ),
            actionType = GamificationActionType.RATE_PINTXO,
            now = 200L
        )

        assertEquals(20, computation.updatedUser.xp)
        assertEquals(5, computation.updatedUser.currentStreak)
        assertTrue(computation.updatedUser.badges.contains(challenge.badgeId))
        assertTrue(computation.unlockedBadges.isEmpty())

        val progress = computation.progressByChallenge.getValue(challenge.id)
        assertEquals(1, progress.progressCount)
        assertTrue(progress.completed)
        assertTrue(!progress.completedNow)
    }
}
