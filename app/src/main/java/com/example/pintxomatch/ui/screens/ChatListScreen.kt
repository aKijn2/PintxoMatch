package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pintxomatch.data.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class ChatListItem(
    val chatId: String,
    val title: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val messageCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val chatsRef = FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
        .getReference("chats")

    var chatItems by remember { mutableStateOf<List<ChatListItem>>(emptyList()) }
    var chatToDelete by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<ChatListItem>()

                snapshot.children.forEach { chatSnapshot ->
                    val chatId = chatSnapshot.key ?: return@forEach
                    val isParticipant = currentUid?.let { uid ->
                        chatSnapshot.child("participants").child(uid).getValue(Boolean::class.java) == true
                    } ?: false

                    if (!isParticipant) {
                        return@forEach
                    }

                    val messagesSnapshot = chatSnapshot.child("messages")

                    if (!messagesSnapshot.hasChildren()) {
                        chatSnapshot.ref.removeValue()
                        return@forEach
                    }

                    val parsedMessages = mutableListOf<ChatMessage>()
                    messagesSnapshot.children.forEach { msgSnapshot ->
                        msgSnapshot.getValue(ChatMessage::class.java)?.let { parsedMessages.add(it) }
                    }

                    if (parsedMessages.isEmpty()) {
                        chatSnapshot.ref.removeValue()
                        return@forEach
                    }

                    val lastMsg = parsedMessages.maxByOrNull { it.timestamp } ?: return@forEach
                    val pintxoName = chatSnapshot.child("pintxoName").getValue(String::class.java)
                        ?: "Chat de pintxo"
                    val otherUid = chatSnapshot.child("participants").children
                        .mapNotNull { it.key }
                        .firstOrNull { it != currentUid }
                    val otherName = if (otherUid != null) {
                        chatSnapshot.child("participantNames").child(otherUid)
                            .getValue(String::class.java)
                    } else {
                        null
                    }
                    val title = if (otherName.isNullOrBlank()) {
                        pintxoName
                    } else {
                        "$pintxoName · $otherName"
                    }

                    newList.add(
                        ChatListItem(
                            chatId = chatId,
                            title = title,
                            lastMessage = lastMsg.text,
                            lastTimestamp = lastMsg.timestamp,
                            messageCount = parsedMessages.size
                        )
                    )
                }

                chatItems = newList.sortedByDescending { it.lastTimestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Error cargando chats: ${error.message}"
            }
        }

        chatsRef.addValueEventListener(listener)
        onDispose { chatsRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tus chats") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (chatItems.isEmpty()) {
                Text("No tienes chats activos todavía.")
            } else {
                chatItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(item.chatId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.lastMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${item.messageCount} mensajes",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            IconButton(onClick = { chatToDelete = item.chatId }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar chat")
                            }
                        }
                    }
                }
            }
        }
    }

    if (chatToDelete != null) {
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Eliminar chat") },
            text = { Text("Esta acción borrará todos los mensajes de este chat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetChatId = chatToDelete
                        if (targetChatId != null) {
                            chatsRef.child(targetChatId).removeValue()
                        }
                        chatToDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            chatToDelete = null
        }
    }
}
