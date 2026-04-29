package com.example.pintxomatch.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.chat.ChatRepository
import com.example.pintxomatch.domain.model.chat.ChatListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ChatListUiState {
    object Loading : ChatListUiState()
    data class Success(val chats: List<ChatListItem>) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _chatListState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val chatListState: StateFlow<ChatListUiState> = _chatListState.asStateFlow()

    init {
        loadUserChats()
    }

    private fun loadUserChats() {
        val currentUid = AuthRepository.currentUserId
        if (currentUid.isNullOrBlank()) {
            _chatListState.value = ChatListUiState.Error("Sesión no válida")
            return
        }

        viewModelScope.launch {
            try {
                repository.getUserChatsFlow(currentUid).collect { chats ->
                    _chatListState.value = ChatListUiState.Success(chats)
                }
            } catch (e: Exception) {
                _chatListState.value = ChatListUiState.Error("Error cargando chats: ${e.message}")
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
            } catch (e: Exception) {
                // handle error silently or emit event
            }
        }
    }
}
