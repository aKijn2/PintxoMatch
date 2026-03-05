package com.example.pintxomatch.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pintxomatch.data.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onNavigateBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val chatRef = FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
        .getReference("chats")
        .child(chatId)
    val messagesRef = chatRef.child("messages")
    val currentUid = auth.currentUser?.uid

    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(false) }
    var isCheckingAccess by remember { mutableStateOf(true) }
    var chatTitle by remember { mutableStateOf("Chat privado") }
    val snackbarHostState = remember { SnackbarHostState() }

    fun currentDisplayName(): String {
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Usuario"
    }

    LaunchedEffect(chatId, currentUid) {
        if (currentUid.isNullOrBlank()) {
            errorMessage = "Sesión no válida. Vuelve a iniciar sesión."
            isCheckingAccess = false
            onNavigateBack()
            return@LaunchedEffect
        }

        chatRef.child("participants").child(currentUid).get()
            .addOnSuccessListener { snapshot ->
                hasAccess = snapshot.getValue(Boolean::class.java) == true
                if (hasAccess) {
                    val name = currentDisplayName()
                    val metadataUpdates = mapOf<String, Any>(
                        "participants/$currentUid" to true,
                        "participantNames/$currentUid" to name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    chatRef.updateChildren(metadataUpdates)
                    chatRef.child("pintxoName").get().addOnSuccessListener { titleSnapshot ->
                        val pintxoName = titleSnapshot.getValue(String::class.java)
                        if (!pintxoName.isNullOrBlank()) {
                            chatTitle = pintxoName
                        }
                    }
                    isCheckingAccess = false
                    return@addOnSuccessListener
                }

                messagesRef.get()
                    .addOnSuccessListener { messagesSnapshot ->
                        val wroteInThisChat = messagesSnapshot.children.any {
                            it.child("senderId").getValue(String::class.java) == currentUid
                        }

                        if (wroteInThisChat) {
                            val name = currentDisplayName()
                            val metadataUpdates = mapOf<String, Any>(
                                "participants/$currentUid" to true,
                                "participantNames/$currentUid" to name,
                                "updatedAt" to System.currentTimeMillis()
                            )
                            chatRef.updateChildren(metadataUpdates)
                            chatRef.child("pintxoName").get().addOnSuccessListener { titleSnapshot ->
                                val pintxoName = titleSnapshot.getValue(String::class.java)
                                if (!pintxoName.isNullOrBlank()) {
                                    chatTitle = pintxoName
                                }
                            }
                            hasAccess = true
                            isCheckingAccess = false
                        } else {
                            isCheckingAccess = false
                            errorMessage = "No tienes acceso a este chat."
                            onNavigateBack()
                        }
                    }
                    .addOnFailureListener {
                        isCheckingAccess = false
                        errorMessage = "No se pudo validar acceso al chat."
                        onNavigateBack()
                    }
            }
            .addOnFailureListener {
                isCheckingAccess = false
                errorMessage = "No se pudo validar acceso al chat."
                onNavigateBack()
            }
    }

    val navigateBackWithCleanup: () -> Unit = navigateBackWithCleanup@{
        if (!hasAccess) {
            onNavigateBack()
            return@navigateBackWithCleanup
        }

        messagesRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    chatRef.removeValue()
                }
                onNavigateBack()
            }
            .addOnFailureListener {
                onNavigateBack()
            }
    }

    BackHandler {
        navigateBackWithCleanup()
    }

    // LEER MENSAJES EN TIEMPO REAL
    DisposableEffect(chatId, hasAccess) {
        if (!hasAccess) {
            onDispose { }
        } else {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    snapshot.children.forEach {
                        it.getValue(ChatMessage::class.java)?.let { msg -> list.add(msg) }
                    }
                    messages = list.sortedBy { it.timestamp }
                }
                override fun onCancelled(error: DatabaseError) {
                    errorMessage = "Error al leer chat: ${error.message}"
                }
            }

            messagesRef.addValueEventListener(listener)
            onDispose { messagesRef.removeEventListener(listener) }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatTitle) },
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (isCheckingAccess) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // LISTA DE MENSAJES
            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                items(messages) { msg ->
                    val isMine = msg.senderId == auth.currentUser?.uid
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
                                text = if (isMine) "Tú" else msg.senderName.ifBlank { "Usuario" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMine) Color.White else Color.DarkGray
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
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje...") }
                )
                IconButton(onClick = {
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        errorMessage = "Sesión no válida. Vuelve a iniciar sesión."
                        return@IconButton
                    }

                    val textToSend = messageText.trim()
                    if (textToSend.isNotBlank()) {
                        val senderName = currentDisplayName()
                        val msg = ChatMessage(
                            senderId = currentUser.uid,
                            senderName = senderName,
                            text = textToSend,
                            timestamp = System.currentTimeMillis()
                        )
                        messagesRef.push().setValue(msg)
                            .addOnSuccessListener {
                                chatRef.child("updatedAt").setValue(System.currentTimeMillis())
                                chatRef.child("participantNames").child(currentUser.uid).setValue(senderName)
                                messageText = ""
                            }
                            .addOnFailureListener { error ->
                                errorMessage = "No se pudo enviar: ${error.localizedMessage ?: "error desconocido"}"
                            }
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                }
            }
        }
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
                        chatRef.removeValue()
                            .addOnSuccessListener { onNavigateBack() }
                            .addOnFailureListener { error ->
                                errorMessage = "No se pudo borrar: ${error.localizedMessage ?: "error desconocido"}"
                            }
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