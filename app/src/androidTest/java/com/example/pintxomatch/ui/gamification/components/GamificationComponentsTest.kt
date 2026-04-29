package com.example.pintxomatch.ui.gamification.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pintxomatch.ui.gamification.WeeklyChallengeUiItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamificationComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gamificationProfileSection_rendersLevelStreakAndBadge() {
        composeRule.setContent {
            MaterialTheme {
                GamificationProfileSection(
                    xp = 220,
                    level = 3,
                    levelProgress = 0.2f,
                    currentStreak = 7,
                    badges = listOf("badge_2026W14_critic")
                )
            }
        }

        composeRule.onNodeWithTag("gamification_level_text").assertTextContains("Nivel 3")
        composeRule.onNodeWithTag("gamification_streak_text").assertTextContains("7")
        composeRule.onNodeWithText("CRITIC").assertTextContains("CRITIC")
    }

    @Test
    fun weeklyChallengeCard_rendersTitleAndProgress() {
        composeRule.setContent {
            MaterialTheme {
                WeeklyChallengeCard(
                    challenge = WeeklyChallengeUiItem(
                        id = "rate-3",
                        title = "Valora 3 pintxos",
                        description = "Completa valoraciones semanales",
                        progressCount = 2,
                        targetCount = 3,
                        isCompleted = false
                    )
                )
            }
        }

        composeRule.onNodeWithText("Valora 3 pintxos").assertTextContains("Valora 3")
        composeRule.onNodeWithTag("weekly_challenge_progress_rate-3").assertTextContains("2/3")
    }
}
