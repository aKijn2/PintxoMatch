package com.example.pintxomatch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.model.ChatMessage
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.ChatRepository
import com.example.pintxomatch.ui.screens.SupportThreadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SupportInboxUiState {
    object Loading : SupportInboxUiState()
    data class Success(val threads: List<SupportThreadItem>) : SupportInboxUiState()
    data class Error(val message: String) : SupportInboxUiState()
}

class SupportInboxViewModel(
    private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<SupportInboxUiState>(SupportInboxUiState.Loading)
    val uiState: StateFlow<SupportInboxUiState> = _uiState.asStateFlow()

    init {
        loadThreads()
    }

    private fun loadThreads() {
        viewModelScope.launch {
            try {
                repository.getSupportThreadsFlow().collect { threads ->
                    _uiState.value = SupportInboxUiState.Success(threads)
                }
            } catch (e: Exception) {
                _uiState.value = SupportInboxUiState.Error("Error cargando soporte")
            }
        }
    }
}

// ---------------------------------------------------------

data class SupportUiMessage(val id: String, val payload: ChatMessage)

sealed class SupportChatUiState {
    object Loading : SupportChatUiState()
    data class Active(
        val messages: List<SupportUiMessage>,
        val ticketStatus: String
    ) : SupportChatUiState()
    data class Error(val message: String) : SupportChatUiState()
}

class SupportChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    private val providedThreadId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow<SupportChatUiState>(SupportChatUiState.Loading)
    val uiState: StateFlow<SupportChatUiState> = _uiState.asStateFlow()

    private var messages = listOf<SupportUiMessage>()
    private var ticketStatus = "open"

    val effectiveThreadId: String
        get() = providedThreadId ?: AuthRepository.currentUserId.orEmpty()
        
    val isAdmin: Boolean = false // For now defaulting to false, could check user claims
    val currentUid: String
        get() = AuthRepository.currentUserId.orEmpty()

    fun initialize() {
        if (effectiveThreadId.isBlank()) {
            _uiState.value = SupportChatUiState.Error("ID de hilo invalido")
            return
        }

        viewModelScope.launch {
            launch {
                repository.getSupportMessagesFlow(effectiveThreadId).collect { msgsMap ->
                    messages = msgsMap.map { SupportUiMessage(it.key, it.value) }.sortedBy { it.payload.timestamp }
                    emitActive()
                }
            }
            launch {
                repository.getSupportTicketStatusFlow(effectiveThreadId).collect { status ->
                    ticketStatus = status
                    emitActive()
                }
            }
        }
    }

    private fun emitActive() {
        _uiState.value = SupportChatUiState.Active(messages, ticketStatus)
    }

    fun sendMessage(text: String) {
        val user = AuthRepository.currentUser
        if (user == null || currentUid.isBlank() || text.isBlank()) return
        val email = user.email.orEmpty()
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").ifBlank { "Usuario" }

        viewModelScope.launch {
            try {
                repository.sendSupportMessage(effectiveThreadId, currentUid, email, displayName, text)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun updateStatus(resolved: Boolean) {
        if (currentUid.isBlank()) return
        viewModelScope.launch {
            try {
                repository.updateSupportTicketStatus(effectiveThreadId, resolved, currentUid)
            } catch (e: Exception) { }
        }
    }

    fun deleteTicket(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteSupportTicket(effectiveThreadId)
                onSuccess()
            } catch (e: Exception) { }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSupportMessage(effectiveThreadId, messageId)
            } catch (e: Exception) { }
        }
    }
}
