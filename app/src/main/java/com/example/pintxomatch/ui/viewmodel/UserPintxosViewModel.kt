package com.example.pintxomatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.PintxoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UserPintxosUiState {
    object Loading : UserPintxosUiState()
    data class Success(val pintxos: List<Pintxo>) : UserPintxosUiState()
    data class Error(val message: String) : UserPintxosUiState()
}

class UserPintxosViewModel(
    private val repository: PintxoRepository = PintxoRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserPintxosUiState>(UserPintxosUiState.Loading)
    val uiState: StateFlow<UserPintxosUiState> = _uiState.asStateFlow()

    fun loadUserPintxos() {
        val uid = AuthRepository.currentUserId
        if (uid == null) {
            _uiState.value = UserPintxosUiState.Error("No estás logueado")
            return
        }

        viewModelScope.launch {
            _uiState.value = UserPintxosUiState.Loading
            try {
                val pintxos = repository.getUserPintxos(uid)
                _uiState.value = UserPintxosUiState.Success(pintxos)
            } catch (e: Exception) {
                _uiState.value = UserPintxosUiState.Error("Error al cargar tus pintxos")
            }
        }
    }
}
