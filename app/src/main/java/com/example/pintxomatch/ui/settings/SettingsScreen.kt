package com.example.pintxomatch.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.chat.ChatRepository
import com.example.pintxomatch.data.repository.media.ImageRepository
import com.example.pintxomatch.ui.common.components.ModernTopToast
import com.example.pintxomatch.ui.support.SupportTicketDraftStore
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onLogout: () -> Unit
) {
    val user = AuthRepository.currentUser
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }
    val normalizedUserPhotoUrl = remember(user?.photoUrl?.toString()) {
        ImageRepository.normalizeImageUrlForCurrentProvider(user?.photoUrl?.toString())
    }
    val pageBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFDF8),
                Color(0xFFFFF8F1),
                Color(0xFFFFF4EA)
            )
        )
    }

    var notificationsEnabled by remember { mutableStateOf(true) }
    var reviewNotificationsEnabled by remember { mutableStateOf(true) }
    var supportNotificationsEnabled by remember { mutableStateOf(true) }
    var notificationOptionsExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var showSupportTicketDialog by remember { mutableStateOf(false) }
    var supportTicketTitle by remember { mutableStateOf("") }
    var checkingSupportTicket by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(alertMessage) {
        if (alertMessage != null) {
            delay(3000)
            alertMessage = null
        }
    }

    fun openSupportFlow() {
        val currentUser = user
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            alertMessage = "Inicia sesion para usar soporte"
            return
        }
        if (checkingSupportTicket) return

        checkingSupportTicket = true
        coroutineScope.launch {
            val alreadyHasTicket = try {
                chatRepository.hasSupportTicket(uid)
            } catch (_: Exception) {
                false
            }

            checkingSupportTicket = false
            if (alreadyHasTicket) {
                onNavigateToSupport()
            } else {
                showSupportTicketDialog = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBrush)
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
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                            text = "CONTROL",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = "Ajustes",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val isWideLayout = maxWidth >= 600.dp

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    )
                                ) {
                                    if (!normalizedUserPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = normalizedUserPhotoUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user?.displayName?.takeIf { it.isNotBlank() }
                                            ?: user?.email?.substringBefore("@")
                                            ?: "Usuario",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user?.email ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Cuenta activa",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Gestiona perfil, soporte y preferencias",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                    }

                    SettingsGroup(title = "General") {
                        NotificationPreferencesCard(
                            notificationsEnabled = notificationsEnabled,
                            onNotificationsEnabledChange = { notificationsEnabled = it },
                            reviewNotificationsEnabled = reviewNotificationsEnabled,
                            onReviewNotificationsEnabledChange = { reviewNotificationsEnabled = it },
                            supportNotificationsEnabled = supportNotificationsEnabled,
                            onSupportNotificationsEnabledChange = { supportNotificationsEnabled = it },
                            optionsExpanded = notificationOptionsExpanded,
                            onOptionsExpandedChange = { notificationOptionsExpanded = it },
                            isWideLayout = isWideLayout
                        )
                    }

                    SettingsGroup(title = "Cuenta") {
                        SettingsActionRow(
                            icon = Icons.Default.Groups,
                            label = "Amigos",
                            subtitle = "Solicitudes, estados y chat con tu gente",
                            onClick = onNavigateToFriends
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Person,
                            label = "Ver perfil",
                            subtitle = "Edita tu perfil y revisa tus aportaciones",
                            onClick = onNavigateToProfile
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        SettingsActionRow(
                            icon = Icons.Default.SupportAgent,
                            label = "Soporte",
                            subtitle = "Abre o revisa tus tickets de ayuda",
                            onClick = { openSupportFlow() }
                        )
                    }

                    SettingsGroup(title = "Aplicacion") {
                        SettingsInfoRow(
                            icon = Icons.Default.Info,
                            label = "Version",
                            value = "1.0.0",
                            subtitle = "Compilacion actual instalada"
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        onClick = onLogout,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cerrar sesion",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Salir de esta cuenta en el dispositivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                    SettingsGroup(title = "Zona peligrosa") {
                        SettingsActionRow(
                            icon = Icons.Default.Delete,
                            label = "Eliminar mi cuenta",
                            subtitle = "Borra tu acceso y anonimiza tus datos publicos",
                            textColor = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        ModernTopToast(
            message = alertMessage,
            onDismiss = { alertMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deletePassword = ""
            },
            title = { Text("Deseas marcharte?") },
            text = {
                Column {
                    Text("Esta accion es irreversible.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Se borrara:")
                    Text("- Tu cuenta de acceso")
                    Text("- Tu sesion y tu perfil vinculado")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Se conservara:")
                    Text("- Los pintxos que ya compartiste")
                    Text("- Tus pintxos quedaran anonimizados como 'Usuario eliminado'")
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Introduce tu contrasena para confirmar:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Contrasena") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentUser = user
                        val uid = currentUser?.uid
                        val email = currentUser?.email
                        if (currentUser == null || uid.isNullOrBlank() || email.isNullOrBlank()) {
                            showDeleteDialog = false
                            return@Button
                        }
                        if (deletePassword.isBlank()) return@Button

                        val credential = EmailAuthProvider.getCredential(email, deletePassword)
                        currentUser.reauthenticate(credential)
                            .addOnSuccessListener {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("Pintxos")
                                    .whereEqualTo("uploaderUid", uid)
                                    .get()
                                    .addOnSuccessListener { result ->
                                        if (result.isEmpty) {
                                            currentUser.delete()
                                                .addOnSuccessListener { onLogout() }
                                            return@addOnSuccessListener
                                        }

                                        val batch = db.batch()
                                        result.documents.forEach { doc ->
                                            batch.update(
                                                doc.reference,
                                                mapOf(
                                                    "uploaderUid" to "",
                                                    "uploaderEmail" to "",
                                                    "uploaderDisplayName" to "Usuario eliminado"
                                                )
                                            )
                                        }

                                        batch.commit()
                                            .addOnSuccessListener {
                                                currentUser.delete()
                                                    .addOnSuccessListener { onLogout() }
                                            }
                                    }
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirmar eliminacion")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deletePassword = ""
                }) {
                    Text("Seguir aqui")
                }
            }
        )
    }

    if (showSupportTicketDialog) {
        AlertDialog(
            onDismissRequest = { showSupportTicketDialog = false },
            title = { Text("Abrir ticket de soporte") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Describe el tema principal para abrir tu ticket.")
                    OutlinedTextField(
                        value = supportTicketTitle,
                        onValueChange = { supportTicketTitle = it },
                        singleLine = true,
                        label = { Text("Titulo del ticket") },
                        placeholder = { Text("Ej: Error al subir foto") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalTitle = supportTicketTitle.trim()
                        if (finalTitle.isBlank()) {
                            alertMessage = "Escribe un titulo para continuar"
                            return@TextButton
                        }
                        SupportTicketDraftStore.pendingTitle = finalTitle
                        showSupportTicketDialog = false
                        supportTicketTitle = ""
                        onNavigateToSupport()
                    }
                ) {
                    Text("Abrir ticket")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSupportTicketDialog = false
                        supportTicketTitle = ""
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            )
        ) {
            content()
        }
    }
}

@Composable
private fun NotificationPreferencesCard(
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    reviewNotificationsEnabled: Boolean,
    onReviewNotificationsEnabledChange: (Boolean) -> Unit,
    supportNotificationsEnabled: Boolean,
    onSupportNotificationsEnabledChange: (Boolean) -> Unit,
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    isWideLayout: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOptionsExpandedChange(!optionsExpanded) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notificaciones",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Controla alertas y avisos importantes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (optionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (optionsExpanded) "Ocultar opciones" else "Mostrar opciones",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = {
                    onNotificationsEnabledChange(it)
                    onOptionsExpandedChange(it)
                }
            )
        }

        if (!notificationsEnabled) {
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            Text(
                text = "Activa las notificaciones para recibir avisos de resenas y soporte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            return
        }

        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

        AnimatedVisibility(visible = optionsExpanded) {
            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NotificationOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Notifications,
                        title = "Resenas y actividad",
                        subtitle = "Avisos cuando haya movimiento en tus valoraciones.",
                        checked = reviewNotificationsEnabled,
                        onCheckedChange = onReviewNotificationsEnabledChange
                    )
                    NotificationOptionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SupportAgent,
                        title = "Mensajes de soporte",
                        subtitle = "Respuestas nuevas en tus tickets.",
                        checked = supportNotificationsEnabled,
                        onCheckedChange = onSupportNotificationsEnabledChange
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NotificationOptionTile(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Notifications,
                        title = "Resenas y actividad",
                        subtitle = "Avisos cuando haya movimiento en tus valoraciones.",
                        checked = reviewNotificationsEnabled,
                        onCheckedChange = onReviewNotificationsEnabledChange
                    )
                    NotificationOptionTile(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.SupportAgent,
                        title = "Mensajes de soporte",
                        subtitle = "Respuestas nuevas en tus tickets.",
                        checked = supportNotificationsEnabled,
                        onCheckedChange = onSupportNotificationsEnabledChange
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationOptionTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (textColor == MaterialTheme.colorScheme.error) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                }
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (textColor == MaterialTheme.colorScheme.error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
