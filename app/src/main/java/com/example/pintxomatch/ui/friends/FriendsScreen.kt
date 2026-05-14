package com.example.pintxomatch.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pintxomatch.data.model.friends.FriendListItem
import com.example.pintxomatch.data.model.friends.FriendRequestItem
import com.example.pintxomatch.data.model.friends.PresenceStatus
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.chat.ChatRepository
import com.example.pintxomatch.data.repository.media.ImageRepository
import com.example.pintxomatch.data.repository.user.UserRepository
import com.example.pintxomatch.ui.common.components.ModernTopToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val user = AuthRepository.currentUser
    val currentUid = user?.uid
    val userRepository = remember { UserRepository() }
    val chatRepository = remember { ChatRepository() }
    val coroutineScope = rememberCoroutineScope()

    var friends by remember { mutableStateOf<List<FriendListItem>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var presenceStatus by remember { mutableStateOf(PresenceStatus.ONLINE) }
    var isLoading by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var busyActionUid by remember { mutableStateOf<String?>(null) }

    fun loadAll() {
        if (currentUid.isNullOrBlank()) {
            isLoading = false
            return
        }
        coroutineScope.launch {
            isLoading = true
            friends = userRepository.getFriends(currentUid)
            incomingRequests = userRepository.getIncomingFriendRequests(currentUid)
            outgoingRequests = userRepository.getOutgoingFriendRequests(currentUid)
            presenceStatus = userRepository.getPresenceStatus(currentUid)
            isLoading = false
        }
    }

    LaunchedEffect(currentUid) {
        loadAll()
    }

    LaunchedEffect(alertMessage) {
        if (alertMessage != null) {
            delay(3000)
            alertMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFDF8),
                        Color(0xFFFFF8F2),
                        Color(0xFFFFF4EA)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SOCIAL",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            letterSpacing = 1.3.sp
                        )
                        Text(
                            text = "Amigos",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                }
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SocialSummaryCard(
                            friendsCount = friends.size,
                            incomingCount = incomingRequests.size,
                            outgoingCount = outgoingRequests.size
                        )
                    }

                    item {
                        PresencePanel(
                            status = presenceStatus,
                            onStatusSelected = { selected ->
                                if (!currentUid.isNullOrBlank()) {
                                    coroutineScope.launch {
                                        if (userRepository.updatePresenceStatus(currentUid, selected)) {
                                            presenceStatus = selected
                                        }
                                    }
                                }
                            }
                        )
                    }

                    if (incomingRequests.isNotEmpty()) {
                        item { SectionTitle("Solicitudes recibidas") }
                        items(incomingRequests, key = { it.uid }) { request ->
                            IncomingRequestCard(
                                item = request,
                                busy = busyActionUid == request.uid,
                                onAccept = {
                                    if (currentUid.isNullOrBlank()) return@IncomingRequestCard
                                    coroutineScope.launch {
                                        busyActionUid = request.uid
                                        if (userRepository.acceptFriendRequest(currentUid, request.uid)) {
                                            alertMessage = "Ahora sois amigos"
                                            loadAll()
                                        } else {
                                            alertMessage = "No se pudo aceptar la solicitud"
                                        }
                                        busyActionUid = null
                                    }
                                },
                                onReject = {
                                    if (currentUid.isNullOrBlank()) return@IncomingRequestCard
                                    coroutineScope.launch {
                                        busyActionUid = request.uid
                                        if (userRepository.rejectFriendRequest(currentUid, request.uid)) {
                                            alertMessage = "Solicitud rechazada"
                                            loadAll()
                                        } else {
                                            alertMessage = "No se pudo rechazar la solicitud"
                                        }
                                        busyActionUid = null
                                    }
                                }
                            )
                        }
                    }

                    if (outgoingRequests.isNotEmpty()) {
                        item { SectionTitle("Solicitudes enviadas") }
                        items(outgoingRequests, key = { it.uid }) { request ->
                            OutgoingRequestCard(
                                item = request,
                                busy = busyActionUid == request.uid,
                                onCancel = {
                                    if (currentUid.isNullOrBlank()) return@OutgoingRequestCard
                                    coroutineScope.launch {
                                        busyActionUid = request.uid
                                        if (userRepository.cancelFriendRequest(currentUid, request.uid)) {
                                            alertMessage = "Solicitud cancelada"
                                            loadAll()
                                        } else {
                                            alertMessage = "No se pudo cancelar la solicitud"
                                        }
                                        busyActionUid = null
                                    }
                                }
                            )
                        }
                    }

                    item { SectionTitle("Tus amigos") }

                    if (friends.isEmpty()) {
                        item { EmptyFriendsCard() }
                    } else {
                        items(friends, key = { it.uid }) { friend ->
                            FriendCard(
                                item = friend,
                                busy = busyActionUid == friend.uid,
                                onOpenProfile = { onOpenProfile(friend.uid) },
                                onOpenChat = {
                                    val myName = user?.displayName?.takeIf { it.isNotBlank() }
                                        ?: user?.email?.substringBefore("@")
                                        ?: "Usuario"
                                    val myPhoto = ImageRepository.normalizeImageUrlForCurrentProvider(user?.photoUrl?.toString()).orEmpty()
                                    coroutineScope.launch {
                                        busyActionUid = friend.uid
                                        val chatId = chatRepository.createOrGetDirectChat(
                                            currentUid = currentUid.orEmpty(),
                                            currentDisplayName = myName,
                                            currentPhotoUrl = myPhoto,
                                            targetUid = friend.uid,
                                            targetDisplayName = friend.displayName,
                                            targetPhotoUrl = friend.photoUrl
                                        )
                                        busyActionUid = null
                                        if (chatId.isNotBlank()) {
                                            onOpenChat(chatId)
                                        } else {
                                            alertMessage = "No se pudo abrir el chat"
                                        }
                                    }
                                },
                                onRemove = {
                                    if (currentUid.isNullOrBlank()) return@FriendCard
                                    coroutineScope.launch {
                                        busyActionUid = friend.uid
                                        if (userRepository.removeFriend(currentUid, friend.uid)) {
                                            alertMessage = "Amigo eliminado"
                                            loadAll()
                                        } else {
                                            alertMessage = "No se pudo eliminar al amigo"
                                        }
                                        busyActionUid = null
                                    }
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        ModernTopToast(
            message = alertMessage,
            onDismiss = { alertMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun SocialSummaryCard(
    friendsCount: Int,
    incomingCount: Int,
    outgoingCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column {
                    Text("Tu circulo foodie", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Solicitudes, presencia y chat privado en un solo sitio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryCell(modifier = Modifier.weight(1f), value = friendsCount, label = "Amigos")
                SummaryCell(modifier = Modifier.weight(1f), value = incomingCount, label = "Recibidas")
                SummaryCell(modifier = Modifier.weight(1f), value = outgoingCount, label = "Enviadas")
            }
        }
    }
}

@Composable
private fun SummaryCell(
    modifier: Modifier = Modifier,
    value: Int,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PresencePanel(
    status: PresenceStatus,
    onStatusSelected: (PresenceStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Estado de presencia",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Elige como te ven tus amigos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresenceOption(
                    modifier = Modifier.weight(1f),
                    label = "Online",
                    selected = status == PresenceStatus.ONLINE,
                    accent = Color(0xFF2E7D32),
                    onClick = { onStatusSelected(PresenceStatus.ONLINE) }
                )
                PresenceOption(
                    modifier = Modifier.weight(1f),
                    label = "Ocupado",
                    selected = status == PresenceStatus.BUSY,
                    accent = Color(0xFFF57C00),
                    onClick = { onStatusSelected(PresenceStatus.BUSY) }
                )
                PresenceOption(
                    modifier = Modifier.weight(1f),
                    label = "Invisible",
                    selected = status == PresenceStatus.INVISIBLE,
                    accent = Color(0xFF616161),
                    onClick = { onStatusSelected(PresenceStatus.INVISIBLE) }
                )
            }
        }
    }
}

@Composable
private fun PresenceOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.32f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = accent) {
                Box(modifier = Modifier.size(10.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        letterSpacing = 1.1.sp
    )
}

@Composable
private fun IncomingRequestCard(
    item: FriendRequestItem,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    FriendRequestShell(
        item = item,
        badgeText = "Nueva solicitud",
        badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        badgeTextColor = MaterialTheme.colorScheme.primary,
        busy = busy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aceptar")
            }
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rechazar")
            }
        }
    }
}

@Composable
private fun OutgoingRequestCard(
    item: FriendRequestItem,
    busy: Boolean,
    onCancel: () -> Unit
) {
    FriendRequestShell(
        item = item,
        badgeText = "Pendiente",
        badgeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        badgeTextColor = MaterialTheme.colorScheme.tertiary,
        busy = busy
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Cancelar solicitud")
        }
    }
}

@Composable
private fun FriendRequestShell(
    item: FriendRequestItem,
    badgeText: String,
    badgeColor: Color,
    badgeTextColor: Color,
    busy: Boolean,
    actions: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ProfileThumb(item.photoUrl, fallbackIcon = Icons.Default.PersonAdd)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Conexion social pendiente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = badgeColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = badgeTextColor, modifier = Modifier.size(14.dp))
                            Text(
                                text = badgeText,
                                color = badgeTextColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (busy) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            } else {
                actions()
            }
        }
    }
}

@Composable
private fun FriendCard(
    item: FriendListItem,
    busy: Boolean,
    onOpenProfile: () -> Unit,
    onOpenChat: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileThumb(
                    photoUrl = item.photoUrl,
                    fallbackIcon = Icons.Default.Groups,
                    onClick = onOpenProfile
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(shape = CircleShape, color = presenceColor(item.presenceStatus)) {
                            Box(modifier = Modifier.size(10.dp))
                        }
                        Text(
                            text = presenceLabel(item.presenceStatus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenChat,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hablar")
                    }
                }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Quitar")
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = "Todavia no tienes amigos en PintxoMatch",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Envia solicitudes desde los perfiles y cuando te acepten podras hablar con ellos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileThumb(
    photoUrl: String,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun presenceLabel(status: PresenceStatus): String {
    return when (status) {
        PresenceStatus.ONLINE -> "Online"
        PresenceStatus.BUSY -> "Ocupado"
        PresenceStatus.INVISIBLE -> "Invisible"
    }
}

private fun presenceColor(status: PresenceStatus): Color {
    return when (status) {
        PresenceStatus.ONLINE -> Color(0xFF2E7D32)
        PresenceStatus.BUSY -> Color(0xFFF57C00)
        PresenceStatus.INVISIBLE -> Color(0xFF616161)
    }
}
