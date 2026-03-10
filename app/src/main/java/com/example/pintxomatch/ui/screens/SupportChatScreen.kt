package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.viewmodel.SupportChatUiState
import com.example.pintxomatch.ui.viewmodel.SupportChatViewModel
import androidx.compose.foundation.layout.Box

class SupportChatViewModelFactory(private val threadId: String?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SupportChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SupportChatViewModel(providedThreadId = threadId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    onNavigateBack: () -> Unit,
    threadId: String? = null,
    isAdmin: Boolean = false,
    viewModel: SupportChatViewModel = viewModel(factory = SupportChatViewModelFactory(threadId))
) {
    val uiState by viewModel.uiState.collectAsState()

    var text by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteTicketDialog by remember { mutableStateOf(false) }
    var messageToDeleteId by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val ticketStatus = if (uiState is SupportChatUiState.Active) (uiState as SupportChatUiState.Active).ticketStatus else "open"
                
                TopAppBar(
                    title = { Text("Soporte en tiempo real") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        // Acciones si está cargado
                        if (uiState is SupportChatUiState.Active) {
                            IconButton(onClick = { viewModel.updateStatus(ticketStatus != "resolved") }) {
                                Icon(
                                    imageVector = if (ticketStatus == "resolved") Icons.Default.Refresh else Icons.Default.Done,
                                    contentDescription = if (ticketStatus == "resolved") "Reabrir caso" else "Marcar resuelto"
                                )
                            }
                            IconButton(onClick = { showDeleteTicketDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar ticket")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = uiState) {
                    is SupportChatUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is SupportChatUiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is SupportChatUiState.Active -> {
                        LaunchedEffect(state.messages.size) {
                            if (state.messages.isNotEmpty()) {
                                listState.animateScrollToItem(state.messages.lastIndex)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Canal para ayuda, incidencias y mejoras de la app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = if (state.ticketStatus == "resolved") "Estado: Resuelto" else "Estado: Abierto",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.ticketStatus == "resolved") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.messages, key = { it.id }) { uiMsg ->
                                    val msg = uiMsg.payload
                                    val mine = msg.senderId == viewModel.currentUid
                                    val canDeleteMessage = mine || isAdmin
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                                    ) {
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = msg.senderName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(msg.text)
                                                if (canDeleteMessage) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        TextButton(onClick = { messageToDeleteId = uiMsg.id }) {
                                                            Text("Borrar")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Escribe al soporte") },
                                    enabled = state.ticketStatus != "resolved",
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (text.isNotBlank()) {
                                            viewModel.sendMessage(text)
                                            text = ""
                                        }
                                    })
                                )
                                IconButton(
                                    onClick = {
                                        if (text.isNotBlank()) {
                                            viewModel.sendMessage(text)
                                            text = ""
                                        }
                                    },
                                    enabled = state.ticketStatus != "resolved"
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                                }
                            }
                        }
                    }
                }
            }
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (showDeleteTicketDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteTicketDialog = false },
                title = { Text("Borrar ticket") },
                text = { Text("Se borrara toda la conversacion de soporte. Esta accion no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteTicketDialog = false
                        viewModel.deleteTicket(onSuccess = onNavigateBack)
                    }) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteTicketDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (messageToDeleteId != null) {
            AlertDialog(
                onDismissRequest = { messageToDeleteId = null },
                title = { Text("Borrar mensaje") },
                text = { Text("Quieres borrar este mensaje?") },
                confirmButton = {
                    TextButton(onClick = {
                        val target = messageToDeleteId
                        messageToDeleteId = null
                        if (!target.isNullOrBlank()) {
                            viewModel.deleteMessage(target)
                        }
                    }) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToDeleteId = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
