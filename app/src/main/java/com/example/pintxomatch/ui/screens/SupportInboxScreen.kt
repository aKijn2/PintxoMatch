package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class SupportThreadItem(
    val threadId: String,
    val userName: String,
    val userEmail: String,
    val lastMessage: String,
    val updatedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportInboxScreen(
    onNavigateBack: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val ref = FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
        .getReference("support_chats")

    var items by remember { mutableStateOf<List<SupportThreadItem>>(emptyList()) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = snapshot.children.mapNotNull { thread ->
                    val id = thread.key ?: return@mapNotNull null
                    val meta = thread.child("meta")
                    SupportThreadItem(
                        threadId = id,
                        userName = meta.child("userName").getValue(String::class.java) ?: "Usuario",
                        userEmail = meta.child("userEmail").getValue(String::class.java) ?: "",
                        lastMessage = meta.child("lastMessage").getValue(String::class.java) ?: "Sin mensajes",
                        updatedAt = meta.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.updatedAt }
                items = loaded
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bandeja de soporte (admin)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text("No hay conversaciones de soporte activas")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(item.threadId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.userName, fontWeight = FontWeight.SemiBold)
                                if (item.userEmail.isNotBlank()) {
                                    Text(item.userEmail, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    item.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
