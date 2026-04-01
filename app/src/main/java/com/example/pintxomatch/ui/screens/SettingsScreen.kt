package com.example.pintxomatch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.ImageRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onLogout: () -> Unit
) {
    val user = AuthRepository.currentUser
    val normalizedUserPhotoUrl = remember(user?.photoUrl?.toString()) {
        ImageRepository.normalizeImageUrlForCurrentProvider(user?.photoUrl?.toString())
    }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var reviewNotificationsEnabled by remember { mutableStateOf(true) }
    var supportNotificationsEnabled by remember { mutableStateOf(true) }
    var notificationOptionsExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
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
                Spacer(modifier = Modifier.height(4.dp))

                // Account card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = user?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // General section
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

                // Account section
                SettingsGroup(title = "Cuenta") {
                    SettingsActionRow(
                        icon = Icons.Default.Person,
                        label = "Ver perfil",
                        onClick = onNavigateToProfile
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsActionRow(
                        icon = Icons.Default.SupportAgent,
                        label = "Soporte",
                        onClick = onNavigateToSupport
                    )
                }

                // App info section
                SettingsGroup(title = "Aplicación") {
                    SettingsInfoRow(
                        icon = Icons.Default.Info,
                        label = "Versión",
                        value = "1.0.0"
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = onLogout,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Cerrar sesión",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Delete Account (Danger Zone)
                SettingsGroup(title = "Zona Peligrosa") {
                    SettingsActionRow(
                        icon = Icons.Default.Delete,
                        label = "Eliminar mi cuenta definitivamente",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // DIÁLOGO DE CONFIRMACIÓN DE ELIMINACIÓN
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deletePassword = ""
            },
            title = { Text("¿Deseas marcharte?") },
            text = {
                Column {
                    Text("Esta acción es irreversible.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Se borrará:")
                    Text("- Tu cuenta de acceso (email/contraseña)")
                    Text("- Tu sesión y tu perfil vinculado")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Se conservará:")
                    Text("- Los pintxos que ya compartiste")
                    Text("- Tus pintxos quedarán anonimizados como 'Usuario eliminado'")
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Introduce tu contraseña para confirmar:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Contraseña") },
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
                            // En un entorno real, usaríamos un snackbar. Por simplicidad aquí:
                            showDeleteDialog = false
                            return@Button
                        }
                        if (deletePassword.isBlank()) return@Button

                        // 1. Re-autenticar al usuario
                        val credential = EmailAuthProvider.getCredential(email, deletePassword)
                        currentUser.reauthenticate(credential)
                            .addOnSuccessListener {
                                // 2. Anonimizar o borrar pintxos
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
                                                // 3. Borrar la cuenta en Auth después de proteger los datos
                                                currentUser.delete()
                                                    .addOnSuccessListener { onLogout() }
                                            }
                                    }
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirmar eliminación")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deletePassword = ""
                }) {
                    Text("Seguir aquí")
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text = label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Notificaciones",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
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
                    if (it) onOptionsExpandedChange(true) else onOptionsExpandedChange(false)
                }
            )
        }

        if (!notificationsEnabled) {
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
            Text(
                text = "Activa las notificaciones para recibir avisos de reseñas y soporte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            return
        }

        HorizontalDivider(modifier = Modifier.padding(start = 52.dp))

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
                        title = "Reseñas y actividad",
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
                        title = "Reseñas y actividad",
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
        shape = RoundedCornerShape(14.dp),
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
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (textColor == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(text = label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = textColor)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text = label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
