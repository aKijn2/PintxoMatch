package com.example.pintxomatch.ui.viewmodel

import com.example.pintxomatch.data.model.GamificationActionType
import com.example.pintxomatch.data.model.UserGamification
import com.example.pintxomatch.data.model.WeeklyChallengeProgress
import com.example.pintxomatch.data.repository.AwardXpResult
import com.example.pintxomatch.data.repository.GamificationGateway
import com.example.pintxomatch.data.repository.GamificationSnapshot
import com.example.pintxomatch.domain.gamification.RATE_PINTXO_XP
import com.example.pintxomatch.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_updatesUiStateWithRepositoryData() = runTest {
        val fakeGateway = FakeGamificationGateway(
            snapshot = GamificationSnapshot(
                user = UserGamification(
                    xp = 135,
                    currentStreak = 4,
                    lastActionTimestamp = 1000L,
                    badges = listOf("badge_2026W14_critic")
                ),
                activeChallenges = listOf(
                    WeeklyChallengeProgress(
                        challengeId = "rate-3",
                        title = "Valora 3 pintxos",
                        description = "Valora pintxos esta semana",
                        badgeId = "badge_2026W14_critic",
                        targetCount = 3,
                        progressCount = 2,
                        completed = false
                    )
                )
            )
        )

        val viewModel = GamificationViewModel(
            repository = fakeGateway,
            dispatcher = mainDispatcherRule.dispatcher
        )

        viewModel.load("user-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(135, state.xp)
        assertEquals(2, state.level)
        assertEquals(4, state.currentStreak)
        assertEquals(1, state.activeChallenges.size)
        assertEquals("2/3", state.activeChallenges.first().progressText)
    }

    @Test
    fun load_whenRepositoryFails_setsErrorMessage() = runTest {
        val fakeGateway = FakeGamificationGateway(
            snapshot = GamificationSnapshot(UserGamification(), emptyList()),
            failOnGet = true
        )
        val viewModel = GamificationViewModel(
            repository = fakeGateway,
            dispatcher = mainDispatcherRule.dispatcher
        )

        viewModel.load("user-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun grantXpForRating_awardsXpAndReloadsState() = runTest {
        val fakeGateway = FakeGamificationGateway(
            snapshot = GamificationSnapshot(
                user = UserGamification(xp = 0, currentStreak = 0, lastActionTimestamp = 0L, badges = emptyList()),
                activeChallenges = emptyList()
            )
        )
        val viewModel = GamificationViewModel(
            repository = fakeGateway,
            dispatcher = mainDispatcherRule.dispatcher
        )

        viewModel.grantXpForRating("user-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, fakeGateway.awardCalls)
        assertEquals(1, fakeGateway.loadCalls)
        assertTrue(state.xp >= RATE_PINTXO_XP)
    }

    private class FakeGamificationGateway(
        var snapshot: GamificationSnapshot,
        private val failOnGet: Boolean = false
    ) : GamificationGateway {
        var awardCalls: Int = 0
        var loadCalls: Int = 0

        override suspend fun upsertDefaultWeeklyChallengesForCurrentWeek(now: Long) {
            // No-op in fake.
        }

        override suspend fun getGamificationState(uid: String): GamificationSnapshot {
            loadCalls += 1
            if (failOnGet) {
                throw IllegalStateException("forced error")
            }
            return snapshot
        }

        override suspend fun awardXpForAction(uid: String, actionType: GamificationActionType): AwardXpResult {
            awardCalls += 1
            val updatedXp = snapshot.user.xp + actionType.xpReward
            snapshot = snapshot.copy(
                user = snapshot.user.copy(
                    xp = updatedXp,
                    lastActionTimestamp = snapshot.user.lastActionTimestamp + 1L
                )
            )
            return AwardXpResult(
                xp = updatedXp,
                currentStreak = snapshot.user.currentStreak,
                unlockedBadges = emptyList()
            )
        }
    }
}
