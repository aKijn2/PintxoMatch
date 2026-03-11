package com.example.pintxomatch.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.data.repository.ImageRepository
import com.example.pintxomatch.data.repository.AuthRepository
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import java.io.File
import com.example.pintxomatch.ui.components.CommentsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    profileUid: String? = null,
    onNavigateBack: () -> Unit, 
    onNavigateToUserPintxos: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember { com.example.pintxomatch.data.repository.UserRepository() }

    val user = AuthRepository.currentUser
    val currentUserId = user?.uid
    val isMyProfile = profileUid == null || profileUid == currentUserId
    val targetUid = profileUid ?: currentUserId
    if (targetUid == null) return

    // ESTADOS
    var totalPintxos by remember { mutableIntStateOf(0) }
    var isEditing by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Estados adicionales para perfil publico
    var publicProfile by remember { mutableStateOf<com.example.pintxomatch.data.model.LeaderboardUser?>(null) }
    var isFriend by remember { mutableStateOf(false) }
    var loadingFriendAction by remember { mutableStateOf(false) }
    
    // Nuevos estados para ajustes de comentarios y amigos
    var friendsCount by remember { mutableIntStateOf(0) }
    var commentsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    // Estados para el formulario de edición
    var nuevoNombre by remember { mutableStateOf(user?.displayName ?: "") }
    var selectedProfileImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedProfileImageUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedProfileImageUri = pendingCameraUri
        } else {
            pendingCameraUri = null
            alertMessage = "No se pudo abrir o completar la cámara"
        }
    }

    fun launchCameraCapture() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            alertMessage = "Este dispositivo no tiene cámara disponible"
            return
        }

        val uri = createTempImageUriForProfile(context)
        if (uri == null) {
            alertMessage = "No se pudo preparar la cámara"
            return
        }

        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            alertMessage = "Permiso de cámara denegado"
        }
    }

    // Cargar estadísticas de Firestore o perfil público
    LaunchedEffect(targetUid) {
        isLoading = true
        coroutineScope.launch {
            friendsCount = userRepository.getFriendsCount(targetUid)
            commentsEnabled = userRepository.areCommentsEnabled(targetUid)
        }
        if (isMyProfile) {
            val db = FirebaseFirestore.getInstance()
            db.collection("Pintxos")
                .whereEqualTo("uploaderUid", targetUid)
                .get()
                .addOnSuccessListener { result ->
                    totalPintxos = result.size()
                    isLoading = false
                }
                .addOnFailureListener {
                    totalPintxos = 0
                    isLoading = false
                }
        } else {
            publicProfile = userRepository.getPublicProfile(targetUid)
            totalPintxos = publicProfile?.totalUploads ?: 0
            if (currentUserId != null) {
                isFriend = userRepository.isFriend(currentUserId, targetUid)
            }
            isLoading = false
        }
    }

    // Theme Colors based on MaterialTheme (Unifying with the rest of the app)
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorBackground = MaterialTheme.colorScheme.background
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh // Modern M3 container
    val colorAccent = MaterialTheme.colorScheme.tertiary // For levels/badges
    val colorOnline = Color(0xFF4CAF50) // Standard green for status

    // Nivel basado en pintxos (cada 5 pintxos es un nivel)
    val level = (totalPintxos / 5).coerceAtLeast(0)
    val progressToNextLevel = (totalPintxos % 5) / 5f

    Box(modifier = Modifier.fillMaxSize().background(colorBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("PERFIL DE LA COMUNIDAD", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorBackground.copy(alpha = 0.9f),
                        titleContentColor = colorOnSurface,
                        navigationIconContentColor = colorOnSurface
                    ),
                    actions = {
                        if (isMyProfile) {
                            TextButton(onClick = { isEditing = !isEditing }) {
                                Text(if (isEditing) "CANCELAR" else "EDITAR PERFIL", color = colorPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Responsive container (max 600dp for tablets)
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                ) {
                // 1. BANNER & AVATAR SECTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val displayPhotoUrl = if (isMyProfile) user?.photoUrl?.toString() else publicProfile?.profileImageUrl
                    val finalPhotoUrl = displayPhotoUrl.takeIf { !it.isNullOrBlank() } ?: "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=800&q=80"
                    
                    // Banner Image (Blurred)
                    coil.compose.AsyncImage(
                        model = finalPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(30.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, colorBackground),
                                    startY = 100f
                                )
                            )
                    )

                    // Profile Content Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Avatar with Custom Frame
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .border(BorderStroke(4.dp, colorSurface), CircleShape)
                                .clip(CircleShape)
                                .background(colorSurface)
                        ) {
                            val avatarUrl = if (isMyProfile && selectedProfileImageUri != null) selectedProfileImageUri 
                                            else if (isMyProfile) user?.photoUrl 
                                            else publicProfile?.profileImageUrl
                            coil.compose.AsyncImage(
                                model = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = if (isMyProfile) user?.displayName ?: "Comidista" else publicProfile?.displayName ?: "Usuario",
                                color = colorOnSurface,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colorOnline)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$friendsCount Amigos",
                                    color = colorOnline,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Level Badge
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .size(48.dp)
                                .background(Brush.linearGradient(listOf(colorPrimary, colorAccent)), CircleShape)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$level",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                // 2. MAIN CONTENT AREA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (isEditing && isMyProfile) {
                        // EDITING FORM
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = colorPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CONFIGURACIÓN DEL PERFIL", color = colorPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                OutlinedTextField(
                                    value = nuevoNombre,
                                    onValueChange = { nuevoNombre = it },
                                    label = { Text("Nombre de usuario", color = colorOnSurfaceVariant) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colorOnSurfaceVariant) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorPrimary,
                                        unfocusedBorderColor = colorOnSurfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Foto de perfil", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorOnSurface)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorSurface, contentColor = colorOnSurface),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { 
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Galería", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) 
                                    }
                                    Button(
                                        onClick = { 
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorSurface, contentColor = colorOnSurface),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { 
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cámara", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) 
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(colorContainer, RoundedCornerShape(12.dp)).border(1.dp, colorOnSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Permitir comentarios", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorOnSurface)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Otros usuarios podrán escribir en tu tablón", fontSize = 12.sp, color = colorOnSurfaceVariant, lineHeight = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Switch(
                                        checked = commentsEnabled,
                                        onCheckedChange = { commentsEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorPrimary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        val trimmedName = nuevoNombre.trim()
                                        if (trimmedName.isEmpty()) { alertMessage = "Nombre vacío"; return@Button }
                                        coroutineScope.launch {
                                            isSavingProfile = true
                                            userRepository.updateCommentsEnabled(targetUid, commentsEnabled)
                                            val uploadedUrl = if (selectedProfileImageUri != null) ImageRepository.uploadImage(context, selectedProfileImageUri!!, "pintxomatch/profiles") else user?.photoUrl?.toString()
                                            val updates = userProfileChangeRequest { displayName = trimmedName; if (!uploadedUrl.isNullOrBlank()) photoUri = Uri.parse(uploadedUrl) }
                                            user?.updateProfile(updates)?.addOnSuccessListener {
                                                isSavingProfile = false; isEditing = false; selectedProfileImageUri = null; alertMessage = "Perfil actualizado"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSavingProfile
                                ) {
                                    if (isSavingProfile) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    else Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                                }
                            }
                        }
                    } else {
                        // PROGRESS BLOCK
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.StarOutline, contentDescription = null, tint = colorPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXPERIENCIA PINTXO", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nivel $level", color = colorOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${totalPintxos % 5} / 5 XP", color = colorOnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progressToNextLevel },
                                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                    color = colorPrimary,
                                    trackColor = colorOnSurface.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Aporta más pintxos para subir de nivel y desbloquear insignias especiales.", color = colorOnSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // SHOWCASE SECTION
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = colorPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VITRINA DE LOGROS", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AchievementIcon(Icons.Default.Restaurant, "Crítico", totalPintxos >= 1)
                            AchievementIcon(Icons.Default.Star, "Estrella", totalPintxos >= 5)
                            AchievementIcon(Icons.Default.LocationOn, "Ruta", totalPintxos >= 10)
                            AchievementIcon(Icons.Default.Badge, "Leyenda", totalPintxos >= 50)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // SUMMARY
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = colorPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESUMEN DE ACTIVIDAD", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$totalPintxos", color = colorPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("PINTXOS\nCOMPARTIDOS", color = colorOnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, lineHeight = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                if (isMyProfile) {
                                    Button(
                                        onClick = onNavigateToUserPintxos,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorPrimary.copy(alpha = 0.1f), contentColor = colorPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.3f))
                                    ) {
                                        Text("GESTIONAR MIS APORTACIONES", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                } else if (currentUserId != null) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                loadingFriendAction = true
                                                if (isFriend) {
                                                    val removed = userRepository.removeFriend(currentUserId, targetUid)
                                                    if (removed) isFriend = false
                                                } else {
                                                    val added = userRepository.addFriend(currentUserId, targetUid)
                                                    if (added) isFriend = true
                                                }
                                                loadingFriendAction = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFriend) colorContainer else colorPrimary,
                                            contentColor = if (isFriend) colorPrimary else MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (isFriend) colorPrimary else Color.Transparent),
                                        enabled = !loadingFriendAction
                                    ) {
                                        if (loadingFriendAction) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                if (isFriend) Icons.Default.Check else Icons.Default.PersonAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isFriend) "Amigos" else "Añadir amigo", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // COMMENTS
                        CommentsSection(targetUserId = targetUid, currentUserId = currentUserId, commentsEnabled = commentsEnabled) 
                }
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }

    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // closes content lambda of Scaffold
    } // closes Box
} // closes UserProfileScreen

@Composable
private fun RowScope.AchievementIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, unlocked: Boolean) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorBackground = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorGray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .background(colorBackground, RoundedCornerShape(12.dp))
                .border(
                    BorderStroke(2.dp, if (unlocked) colorPrimary.copy(alpha = 0.5f) else colorGray.copy(alpha = 0.2f)),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (unlocked) colorPrimary else colorGray,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name, 
            color = if (unlocked) colorOnSurface else colorGray, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold
        )
    }
}

private fun createTempImageUriForProfile(context: Context): Uri? {
    return try {
        val tempFile = File.createTempFile(
            "profile_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        )
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    } catch (_: Exception) {
        null
    }
}

