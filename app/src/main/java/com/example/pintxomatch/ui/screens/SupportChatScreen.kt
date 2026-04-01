package com.example.pintxomatch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pintxomatch.ui.components.ModernTopToast
import com.example.pintxomatch.ui.viewmodel.SupportChatUiState
import com.example.pintxomatch.ui.viewmodel.SupportChatViewModel
import kotlinx.coroutines.delay

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
    var revealedDeleteMessageId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000)
            errorMessage = null
        }
    }

    fun sendCurrentMessage() {
        if (text.isNotBlank()) {
            viewModel.sendMessage(text)
            text = ""
        }
    }

    val colorBackground = MaterialTheme.colorScheme.background
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val isCompactPhone = LocalConfiguration.current.screenWidthDp < 380

    val ticketStatus = if (uiState is SupportChatUiState.Active) {
        (uiState as SupportChatUiState.Active).ticketStatus
    } else {
        "open"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colorBackground,
            topBar = {
                CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isCompactPhone) "Soporte" else "Soporte en vivo",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (uiState is SupportChatUiState.Active) {
                        FilledTonalIconButton(
                            onClick = { viewModel.updateStatus(ticketStatus != "resolved") },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colorPrimary,
                                contentColor = colorOnPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (ticketStatus == "resolved") Icons.Default.Refresh else Icons.Default.Done,
                                contentDescription = if (ticketStatus == "resolved") "Reabrir caso" else "Marcar resuelto"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { showDeleteTicketDialog = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colorPrimary,
                                contentColor = colorOnPrimary
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar ticket")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorBackground
                )
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
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
                    val isResolved = state.ticketStatus == "resolved"

                    LaunchedEffect(state.messages.size) {
                        if (state.messages.isNotEmpty()) {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.14f)),
                            shadowElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Canal para ayuda, incidencias y mejoras de la app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorOnSurfaceVariant
                                )
                                if (state.ticketTitle.isNotBlank()) {
                                    Text(
                                        text = "Ticket: ${state.ticketTitle}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (isResolved) colorPrimary.copy(alpha = 0.14f)
                                    else colorOnSurfaceVariant.copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isResolved) colorPrimary.copy(alpha = 0.25f)
                                        else colorOnSurfaceVariant.copy(alpha = 0.22f)
                                    )
                                ) {
                                    Text(
                                        text = if (isResolved) "Estado: Resuelto" else "Estado: Abierto",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isResolved) colorPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.messages, key = { it.id }) { uiMsg ->
                                val msg = uiMsg.payload
                                val mine = msg.senderId == viewModel.currentUid
                                val canDeleteMessage = mine || isAdmin
                                val showDeleteAction = revealedDeleteMessageId == uiMsg.id && canDeleteMessage
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val isHovered = showDeleteAction || isPressed
                                val bubbleScale by animateFloatAsState(
                                    targetValue = if (isHovered) 1.015f else 1f,
                                    animationSpec = tween(durationMillis = 140),
                                    label = "supportBubbleScale"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .graphicsLayer {
                                                scaleX = bubbleScale
                                                scaleY = bubbleScale
                                            }
                                            .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = {
                                                    if (revealedDeleteMessageId == uiMsg.id) {
                                                        revealedDeleteMessageId = null
                                                    }
                                                },
                                                onLongClick = {
                                                    if (canDeleteMessage) {
                                                        revealedDeleteMessageId = uiMsg.id
                                                    }
                                                }
                                            ),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (mine) 16.dp else 6.dp,
                                            bottomEnd = if (mine) 6.dp else 16.dp
                                        ),
                                        color = if (mine) colorPrimary.copy(alpha = 0.13f)
                                        else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isHovered) colorPrimary.copy(alpha = 0.35f)
                                            else if (mine) colorPrimary.copy(alpha = 0.24f)
                                            else colorOnSurfaceVariant.copy(alpha = 0.14f)
                                        ),
                                        shadowElevation = if (isHovered) 4.dp else 1.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = msg.senderName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (mine) colorPrimary else colorOnSurfaceVariant
                                            )
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            AnimatedVisibility(
                                                visible = showDeleteAction,
                                                enter = fadeIn(tween(120)) + scaleIn(
                                                    animationSpec = tween(120),
                                                    initialScale = 0.85f
                                                ),
                                                exit = fadeOut(tween(100)) + scaleOut(
                                                    animationSpec = tween(100),
                                                    targetScale = 0.85f
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            revealedDeleteMessageId = null
                                                            messageToDeleteId = uiMsg.id
                                                        },
                                                        modifier = Modifier.size(36.dp),
                                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                            containerColor = colorPrimary,
                                                            contentColor = colorOnPrimary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Borrar mensaje",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Escribe al soporte") },
                                    enabled = !isResolved,
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorPrimary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                    )
                                )
                                FilledTonalIconButton(
                                    onClick = { sendCurrentMessage() },
                                    enabled = !isResolved && text.isNotBlank(),
                                    modifier = Modifier.size(46.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = colorPrimary,
                                        contentColor = colorOnPrimary,
                                        disabledContainerColor = colorPrimary.copy(alpha = 0.35f),
                                        disabledContentColor = colorOnPrimary.copy(alpha = 0.7f)
                                    )
                                ) {
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
}
