package com.example.pintxomatch.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.ui.components.ModernTopToast
import com.example.pintxomatch.ui.viewmodel.SingleChatUiState
import com.example.pintxomatch.ui.viewmodel.SingleChatViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay

class SingleChatViewModelFactory(private val chatId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SingleChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SingleChatViewModel(chatId = chatId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    viewModel: SingleChatViewModel = viewModel(factory = SingleChatViewModelFactory(chatId))
) {
    val uiState by viewModel.uiState.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasExited by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileDialogName by remember { mutableStateOf("Usuario") }
    var profileDialogPhoto by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()

    fun exitChatOnce() {
        if (hasExited) return
        hasExited = true
        onNavigateBack()
    }

    LaunchedEffect(Unit) {
        viewModel.initializeChat()
    }

    LaunchedEffect(uiState) {
        if (uiState is SingleChatUiState.ErrorAndExit) {
            val errState = uiState as SingleChatUiState.ErrorAndExit
            errorMessage = errState.message
            exitChatOnce()
        }
    }

    val navigateBackWithCleanup: () -> Unit = {
        viewModel.cleanupAndLeave()
        exitChatOnce()
    }

    BackHandler {
        navigateBackWithCleanup()
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000)
            errorMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val title = if (uiState is SingleChatUiState.Active) (uiState as SingleChatUiState.Active).chatTitle else "Cargando..."
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = navigateBackWithCleanup) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar chat")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is SingleChatUiState.Loading -> {
                        Box(
                            modifier = Modifier.padding(padding).fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is SingleChatUiState.ErrorAndExit -> {
                        // Handled by LaunchedEffect
                    }
                    is SingleChatUiState.Active -> {
                        LaunchedEffect(state.messages.size) {
                            if (state.messages.isNotEmpty()) {
                                listState.animateScrollToItem(state.messages.lastIndex)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .imePadding()
                        ) {
                            // LISTA DE MENSAJES
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(state.messages) { msg ->
                                    val isMine = msg.senderId == AuthRepository.currentUserId
                                    val senderPhoto = state.participantPhotos[msg.senderId]
                                    val senderDisplayName = if (isMine) "Tú" else msg.senderName.ifBlank { "Usuario" }
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    if (isMine) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = senderDisplayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isMine) Color.White else Color.DarkGray,
                                                modifier = Modifier.clickable {
                                                    profileDialogName = senderDisplayName
                                                    profileDialogPhoto = if (isMine) AuthRepository.currentUser?.photoUrl?.toString() else senderPhoto
                                                    showProfileDialog = true
                                                }
                                            )
                                            Text(
                                                text = msg.text,
                                                color = if (isMine) Color.White else Color.Black
                                            )
                                        }
                                    }
                                }
                            }

                            // BARRA DE ESCRITURA
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Escribe un mensaje...") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (messageText.isNotBlank()) {
                                                viewModel.sendMessage(messageText)
                                                messageText = ""
                                            }
                                        }
                                    ),
                                    maxLines = 4
                                )
                                IconButton(onClick = {
                                    if (messageText.isNotBlank()) {
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                                }
                            }
                        }
                    }
                }
            }
        }
        ModernTopToast(
            message = errorMessage,
            onDismiss = { errorMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Perfil") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!profileDialogPhoto.isNullOrBlank()) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.LightGray)
                        ) {
                            AsyncImage(
                                model = profileDialogPhoto,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.LightGray))
                    }
                    Text(text = profileDialogName, style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar chat") },
            text = { Text("Se eliminarán todos los mensajes de este chat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteChat()
                        // deletion will trigger ErrorAndExit, which navigates back
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
