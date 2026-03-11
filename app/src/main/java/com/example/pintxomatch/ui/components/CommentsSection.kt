package com.example.pintxomatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pintxomatch.data.model.ProfileComment
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.UserRepository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Delete
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommentsSection(targetUserId: String, currentUserId: String?, commentsEnabled: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember { UserRepository() }
    
    var comments by remember { mutableStateOf<List<ProfileComment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newCommentText by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }

    // Paginación
    var currentPage by remember { mutableStateOf(0) }
    val itemsPerPage = 5

    fun loadComments() {
        coroutineScope.launch {
            isLoading = true
            comments = userRepository.getProfileComments(targetUserId)
            isLoading = false
        }
    }

    LaunchedEffect(targetUserId) {
        loadComments()
    }

    if (!commentsEnabled && currentUserId != targetUserId && comments.isEmpty()) {
        // Not showing anything as requested by the user
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            val title = if (currentUserId == targetUserId) "COMENTARIOS EN MI PERFIL" else "COMENTARIOS DE LA COMUNIDAD"
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (currentUserId == null) {
            Text("Inicia sesión para comentar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        } else if (!commentsEnabled && currentUserId != targetUserId) {
            Text("El usuario ha desactivado los comentarios.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = { Text("Escribe un comentario...", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    maxLines = 3,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newCommentText.isNotBlank() && !isPosting) {
                                    coroutineScope.launch {
                                        isPosting = true
                                        val me = AuthRepository.currentUser
                                        val comment = ProfileComment(
                                            senderId = currentUserId,
                                            senderName = me?.displayName ?: "Usuario",
                                            senderPhotoUrl = me?.photoUrl?.toString() ?: "",
                                            receiverId = targetUserId,
                                            text = newCommentText.trim()
                                        )
                                        val success = userRepository.leaveComment(comment)
                                        if (success) {
                                            newCommentText = ""
                                            loadComments()
                                        }
                                        isPosting = false
                                    }
                                }
                            },
                            enabled = newCommentText.isNotBlank() && !isPosting,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .background(if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Enviar",
                                    tint = if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (comments.isEmpty()) {
            val emptyMessage = if (currentUserId == targetUserId) "Aún no tienes comentarios en tu perfil."
                               else "Nadie ha comentado todavía. ¡Sé el primero!"
            Text(
                emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            )
        } else {
            val totalPages = (comments.size + itemsPerPage - 1) / itemsPerPage
            val startIndex = currentPage * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, comments.size)
            val currentComments = comments.subList(startIndex, endIndex)

            currentComments.forEach { comment ->
                CommentItem(
                    comment = comment,
                    canDelete = (currentUserId == comment.senderId || currentUserId == targetUserId),
                    onDelete = {
                        coroutineScope.launch {
                            val success = userRepository.deleteComment(targetUserId, comment.id)
                            if (success) loadComments()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Paginator Bottom
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Anterior")
                    }

                    (0 until totalPages).forEach { pageIndex ->
                        val isSelected = pageIndex == currentPage
                        TextButton(
                            onClick = { currentPage = pageIndex },
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("${pageIndex + 1}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    IconButton(
                        onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, "Siguiente")
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: ProfileComment,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        AsyncImage(
            model = comment.senderPhotoUrl.takeIf { it.isNotBlank() } ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80",
            contentDescription = "Avatar de ${comment.senderName}",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                Text(
                    text = sdf.format(Date(comment.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = comment.text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Borrar comentario",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
