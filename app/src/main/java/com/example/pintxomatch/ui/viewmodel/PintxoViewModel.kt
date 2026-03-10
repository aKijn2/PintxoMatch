package com.example.pintxomatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.model.LeaderboardPintxo
import com.example.pintxomatch.data.model.LeaderboardUser
import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.data.repository.PintxoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(val pintxos: List<Pintxo>) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(
        val users: List<LeaderboardUser>,
        val pintxos: List<LeaderboardPintxo>
    ) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}

class PintxoViewModel(
    private val repository: PintxoRepository = PintxoRepository()
) : ViewModel() {

    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _leaderboardState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val leaderboardState: StateFlow<LeaderboardUiState> = _leaderboardState.asStateFlow()

    init {
        loadFeed()
        loadLeaderboard()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading
            try {
                val pintxos = repository.getFeedPintxos()
                _feedState.value = FeedUiState.Success(pintxos)
            } catch (e: Exception) {
                _feedState.value = FeedUiState.Error("Error al cargar los pintxos")
            }
        }
    }

    fun onSwipe(pintxoId: String) {
        val currentState = _feedState.value
        if (currentState is FeedUiState.Success) {
            val newList = currentState.pintxos.filter { it.id != pintxoId }
            _feedState.value = FeedUiState.Success(newList)
        }
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            _leaderboardState.value = LeaderboardUiState.Loading
            try {
                val (users, topRatedPintxos) = repository.getLeaderboardData()
                _leaderboardState.value = LeaderboardUiState.Success(users, topRatedPintxos)
            } catch (e: Exception) {
                _leaderboardState.value = LeaderboardUiState.Error("Error al cargar el ranking")
            }
        }
    }
}
