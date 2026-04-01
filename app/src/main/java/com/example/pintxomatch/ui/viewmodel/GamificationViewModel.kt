package com.example.pintxomatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.model.GamificationActionType
import com.example.pintxomatch.data.repository.GamificationGateway
import com.example.pintxomatch.data.repository.GamificationRepository
import com.example.pintxomatch.domain.gamification.GamificationRules
import com.example.pintxomatch.domain.gamification.XP_PER_LEVEL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeeklyChallengeUiItem(
    val id: String,
    val title: String,
    val description: String,
    val progressCount: Int,
    val targetCount: Int,
    val isCompleted: Boolean
) {
    val progressFraction: Float
        get() = if (targetCount <= 0) 0f else (progressCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)

    val progressText: String
        get() = "$progressCount/$targetCount"
}

data class GamificationUiState(
    val isLoading: Boolean = false,
    val xp: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val xpToNextLevel: Int = XP_PER_LEVEL,
    val currentStreak: Int = 0,
    val badges: List<String> = emptyList(),
    val activeChallenges: List<WeeklyChallengeUiItem> = emptyList(),
    val errorMessage: String? = null
)

class GamificationViewModel(
    private val repository: GamificationGateway = GamificationRepository(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(GamificationUiState(isLoading = true))
    val uiState: StateFlow<GamificationUiState> = _uiState.asStateFlow()

    fun load(uid: String) {
        if (uid.isBlank()) {
            _uiState.value = GamificationUiState(isLoading = false)
            return
        }

        viewModelScope.launch(dispatcher) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.upsertDefaultWeeklyChallengesForCurrentWeek()
                val snapshot = repository.getGamificationState(uid)
                val levelInfo = GamificationRules.buildLevelInfo(snapshot.user.xp)

                _uiState.value = GamificationUiState(
                    isLoading = false,
                    xp = snapshot.user.xp,
                    level = levelInfo.level,
                    levelProgress = levelInfo.progressToNextLevel,
                    xpToNextLevel = levelInfo.xpNeededForNextLevel,
                    currentStreak = snapshot.user.currentStreak,
                    badges = snapshot.user.badges,
                    activeChallenges = snapshot.activeChallenges.map { challenge ->
                        WeeklyChallengeUiItem(
                            id = challenge.challengeId,
                            title = challenge.title,
                            description = challenge.description,
                            progressCount = challenge.progressCount,
                            targetCount = challenge.targetCount,
                            isCompleted = challenge.completed
                        )
                    },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "No se pudo cargar la gamificacion"
                )
            }
        }
    }

    fun grantXpForRating(uid: String) {
        grantXpForAction(uid, GamificationActionType.RATE_PINTXO)
    }

    fun grantXpForUpload(uid: String) {
        grantXpForAction(uid, GamificationActionType.UPLOAD_PINTXO)
    }

    private fun grantXpForAction(uid: String, actionType: GamificationActionType) {
        if (uid.isBlank()) return

        viewModelScope.launch(dispatcher) {
            try {
                repository.awardXpForAction(uid, actionType)
                load(uid)
            } catch (_: Exception) {
                // Keep UX resilient: XP updates are best-effort and should not block primary actions.
            }
        }
    }
}
