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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.pintxomatch.data.ChatMessage
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private data class SupportUiMessage(
    val id: String,
    val payload: ChatMessage
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    onNavigateBack: () -> Unit,
    threadId: String? = null,
    isAdmin: Boolean = false
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val uid = user?.uid
    val email = user?.email.orEmpty()

    val effectiveThreadId = threadId ?: uid.orEmpty()
    val db = FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
        .getReference("support_chats")
        .child(effectiveThreadId)
    val messagesRef = db.child("messages")

    var messages by remember { mutableStateOf<List<SupportUiMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var ticketStatus by remember { mutableStateOf("open") }
    var showDeleteTicketDialog by remember { mutableStateOf(false) }
    var messageToDeleteId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val senderName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@").ifBlank { "Usuario" }

    fun send() {
        val clean = text.trim()
        if (uid.isNullOrBlank()) {
            errorMessage = "Sesion no valida"
            return
        }
        if (clean.isBlank()) return

        val msg = ChatMessage(
            senderId = uid,
            senderName = senderName,
            text = clean,
            timestamp = System.currentTimeMillis()
        )

        messagesRef.push().setValue(msg)
            .addOnSuccessListener {
                db.child("meta").updateChildren(
                    hashMapOf<String, Any?>(
                        "userUid" to (threadId ?: uid),
                        "userEmail" to email,
                        "userName" to senderName,
                        "lastMessage" to clean,
                        "updatedAt" to System.currentTimeMillis(),
                        "status" to "open",
                        "resolvedBy" to null,
                        "resolvedAt" to null
                    )
                )
                text = ""
            }
            .addOnFailureListener {
                errorMessage = "No se pudo enviar el mensaje"
            }
    }

    DisposableEffect(effectiveThreadId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = snapshot.children.mapNotNull { node ->
                    val id = node.key ?: return@mapNotNull null
                    val msg = node.getValue(ChatMessage::class.java) ?: return@mapNotNull null
                    SupportUiMessage(id = id, payload = msg)
                }.sortedBy { it.payload.timestamp }
                messages = loaded
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Error leyendo soporte: ${error.message}"
            }
        }
        messagesRef.addValueEventListener(listener)
        onDispose { messagesRef.removeEventListener(listener) }
    }

    DisposableEffect(effectiveThreadId) {
        val metaRef = db.child("meta")
        val metaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ticketStatus = snapshot.child("status").getValue(String::class.java) ?: "open"
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Error leyendo estado del ticket"
            }
        }

        metaRef.addValueEventListener(metaListener)
        onDispose { metaRef.removeEventListener(metaListener) }
    }

    fun updateTicketStatus(resolved: Boolean) {
        if (uid.isNullOrBlank()) {
            errorMessage = "Sesion no valida"
            return
        }

        db.child("meta").updateChildren(
            hashMapOf<String, Any?>(
                "status" to if (resolved) "resolved" else "open",
                "updatedAt" to System.currentTimeMillis(),
                "resolvedBy" to if (resolved) uid else null,
                "resolvedAt" to if (resolved) System.currentTimeMillis() else null
            )
        ).addOnFailureListener {
            errorMessage = "No se pudo cambiar el estado"
        }
    }

    fun deleteTicket() {
        db.removeValue()
            .addOnSuccessListener { onNavigateBack() }
            .addOnFailureListener {
                errorMessage = "No se pudo borrar el ticket"
            }
    }

    fun deleteMessage(messageId: String) {
        messagesRef.child(messageId).removeValue()
            .addOnFailureListener {
                errorMessage = "No se pudo borrar el mensaje"
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Soporte en tiempo real") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { updateTicketStatus(ticketStatus != "resolved") }) {
                            Icon(
                                imageVector = if (ticketStatus == "resolved") Icons.Default.Refresh else Icons.Default.Done,
                                contentDescription = if (ticketStatus == "resolved") "Reabrir caso" else "Marcar resuelto"
                            )
                        }
                        IconButton(onClick = { showDeleteTicketDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar ticket")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Canal para ayuda, incidencias y mejoras de la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = if (ticketStatus == "resolved") "Estado: Resuelto" else "Estado: Abierto",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ticketStatus == "resolved") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { uiMsg ->
                        val msg = uiMsg.payload
                        val mine = msg.senderId == uid
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
                        enabled = ticketStatus != "resolved",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() })
                    )
                    IconButton(
                        onClick = { send() },
                        enabled = ticketStatus != "resolved"
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar")
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
                        deleteTicket()
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
                            deleteMessage(target)
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
