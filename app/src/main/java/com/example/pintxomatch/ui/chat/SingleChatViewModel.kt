package com.example.pintxomatch.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.model.chat.ChatMessage
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.chat.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SingleChatUiState {
    object Loading : SingleChatUiState()
    data class Active(
        val messages: List<ChatMessage>,
        val participantPhotos: Map<String, String>,
        val chatTitle: String,
        val hasAccess: Boolean
    ) : SingleChatUiState()
    data class ErrorAndExit(val message: String) : SingleChatUiState()
}

class SingleChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    private val chatId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<SingleChatUiState>(SingleChatUiState.Loading)
    val uiState: StateFlow<SingleChatUiState> = _uiState.asStateFlow()

    private var chatTitle = "Chat privado"
    private var participantPhotos = mapOf<String, String>()
    private var messages = listOf<ChatMessage>()

    fun initializeChat() {
        val currentUid = AuthRepository.currentUserId
        val currentUser = AuthRepository.currentUser
        
        if (currentUid.isNullOrBlank() || currentUser == null) {
            _uiState.value = SingleChatUiState.ErrorAndExit("Sesión no válida. Vuelve a iniciar sesión.")
            return
        }

        val displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.email?.substringBefore("@") ?: "Usuario"
        val photoUrl = currentUser.photoUrl?.toString().orEmpty()

        viewModelScope.launch {
            try {
                // Validate access
                val (hasAccess, title, isActive) = repository.updateChatMetadataAndValidateAccess(
                    chatId = chatId,
                    currentUid = currentUid,
                    displayName = displayName,
                    photoUrl = photoUrl
                )

                if (!isActive || !hasAccess) {
                    _uiState.value = SingleChatUiState.ErrorAndExit("No tienes acceso a este chat o fue eliminado.")
                    return@launch
                }

                chatTitle = title
                startObservers()
            } catch (e: Exception) {
                _uiState.value = SingleChatUiState.ErrorAndExit("No se pudo validar acceso al chat.")
            }
        }
    }

    private fun startObservers() {
        viewModelScope.launch {
            launch {
                repository.getChatParticipantPhotosFlow(chatId).collect { photos ->
                    participantPhotos = photos
                    emitActiveState()
                }
            }
            launch {
                repository.getChatMessagesFlow(chatId).collect { msgs ->
                    messages = msgs
                    emitActiveState()
                }
            }
        }
    }

    private fun emitActiveState() {
        if (_uiState.value !is SingleChatUiState.ErrorAndExit) {
            _uiState.value = SingleChatUiState.Active(
                messages = messages,
                participantPhotos = participantPhotos,
                chatTitle = chatTitle,
                hasAccess = true
            )
        }
    }

    fun sendMessage(text: String) {
        val currentUid = AuthRepository.currentUserId
        val currentUser = AuthRepository.currentUser
        if (currentUid.isNullOrBlank() || currentUser == null) return
        
        val displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.email?.substringBefore("@") ?: "Usuario"
        val photoUrl = currentUser.photoUrl?.toString().orEmpty()

        viewModelScope.launch {
            try {
                repository.sendMessage(chatId, currentUid, displayName, photoUrl, text)
            } catch (e: Exception) {
                // error sending message
            }
        }
    }

    fun cleanupAndLeave() {
        viewModelScope.launch {
            try {
                repository.cleanupChatIfEmptyAndLeave(chatId)
            } catch (e: Exception) {}
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
                _uiState.value = SingleChatUiState.ErrorAndExit("Chat eliminado")
            } catch (e: Exception) {}
        }
    }
}
