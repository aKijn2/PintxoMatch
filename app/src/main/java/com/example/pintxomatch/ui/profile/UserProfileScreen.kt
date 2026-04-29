package com.example.pintxomatch.ui.profile

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
import com.example.pintxomatch.ui.common.components.ModernTopToast
import com.example.pintxomatch.data.repository.media.ImageRepository
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import java.io.File
import com.example.pintxomatch.ui.profile.components.CommentsSection
import com.example.pintxomatch.ui.gamification.components.GamificationProfileSection
import com.example.pintxomatch.ui.gamification.components.WeeklyChallengeCard
import com.example.pintxomatch.ui.gamification.GamificationViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    profileUid: String? = null,
    onNavigateBack: () -> Unit, 
    onNavigateToUserPintxos: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember { com.example.pintxomatch.data.repository.user.UserRepository() }
    val gamificationViewModel: GamificationViewModel = viewModel()
    val gamificationState by gamificationViewModel.uiState.collectAsState()

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

    // Estados adicionales para perfil publico
    var publicProfile by remember { mutableStateOf<com.example.pintxomatch.data.model.leaderboard.LeaderboardUser?>(null) }
    var isFriend by remember { mutableStateOf(false) }
    var loadingFriendAction by remember { mutableStateOf(false) }
    
    // Nuevos estados para ajustes de comentarios y amigos
    var friendsCount by remember { mutableIntStateOf(0) }
    var commentsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(alertMessage) {
        if (alertMessage != null) {
            delay(3000)
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
        gamificationViewModel.load(targetUid)
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

    Box(modifier = Modifier.fillMaxSize().background(colorBackground)) {
        // CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)
            ) {
                val isWide = maxWidth > 600.dp
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. IMMERSIVE HEADER SECTION
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isWide) 320.dp else 280.dp)
                    ) {
                        val displayPhotoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(
                            if (isMyProfile) user?.photoUrl?.toString() else publicProfile?.profileImageUrl
                        )
                        val finalPhotoUrl = displayPhotoUrl.takeIf { !it.isNullOrBlank() } ?: "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=1200&q=80"
                        
                        // Banner Image (Blurred & Darkened)
                        coil.compose.AsyncImage(
                            model = finalPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Dual Gradient Overlay for depth
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            colorBackground
                                        )
                                    )
                                )
                        )

                        // Top Bar Actions (Floating over banner)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 8.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                            }
                            
                            if (isMyProfile) {
                                Surface(
                                    onClick = { isEditing = !isEditing },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.3f),
                                    contentColor = Color.White
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isEditing) "LISTO" else "AJUSTES", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }

                        // Identity Card (Overlapping Banner)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 40.dp)
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Glass Background Layer (Blurred & Transparent)
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(if (android.os.Build.VERSION.SDK_INT >= 31) 12.dp else 0.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = colorSurface.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {}

                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar with status ring
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .padding(2.dp)
                                        .border(2.dp, colorPrimary, CircleShape)
                                        .clip(CircleShape)
                                        .background(colorSurface)
                                ) {
                                    val avatarUrl = if (isMyProfile && selectedProfileImageUri != null) {
                                        selectedProfileImageUri
                                    } else {
                                        ImageRepository.normalizeImageUrlForCurrentProvider(
                                            if (isMyProfile) user?.photoUrl?.toString() else publicProfile?.profileImageUrl
                                        )
                                    }
                                    coil.compose.AsyncImage(
                                        model = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
                                        contentDescription = "Foto de perfil",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isMyProfile) user?.displayName ?: "Comidista" else publicProfile?.displayName ?: "Usuario",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = colorOnSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PersonAdd, 
                                            contentDescription = null, 
                                            modifier = Modifier.size(12.dp), 
                                            tint = colorOnSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$friendsCount Amigos",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = colorOnSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                // Level Orb
                                Column(
                                    modifier = Modifier.padding(end = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                Brush.linearGradient(listOf(colorPrimary, colorAccent)),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${gamificationState.level}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("NIVEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = colorPrimary, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(60.dp)) // Extra space for the overlapping card

                    // 2. MAIN CONTENT AREA
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colorPrimary)
                            }
                        } else if (isEditing && isMyProfile) {
                            // MODERN EDITING FORM
                            EditProfileForm(
                                nuevoNombre = nuevoNombre,
                                onNombreChange = { nuevoNombre = it },
                                selectedUri = selectedProfileImageUri,
                                commentsEnabled = commentsEnabled,
                                onCommentsToggle = { commentsEnabled = it },
                                isSaving = isSavingProfile,
                                onPickMedia = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onLaunchCamera = { 
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                onSave = {
                                    val trimmedName = nuevoNombre.trim()
                                    if (trimmedName.isEmpty()) { alertMessage = "Nombre vacío"; return@EditProfileForm }
                                    coroutineScope.launch {
                                        isSavingProfile = true
                                        try {
                                            userRepository.updateCommentsEnabled(targetUid, commentsEnabled)

                                            val oldImageUrl = user?.photoUrl?.toString()
                                            val uploadedUrl = if (selectedProfileImageUri != null) {
                                                ImageRepository.uploadImage(context, selectedProfileImageUri!!, "pintxomatch/profiles")
                                            } else {
                                                oldImageUrl
                                            }

                                            val updates = userProfileChangeRequest {
                                                displayName = trimmedName
                                                if (!uploadedUrl.isNullOrBlank()) {
                                                    photoUri = Uri.parse(uploadedUrl)
                                                }
                                            }

                                            user?.updateProfile(updates)?.await()

                                            if (user != null) {
                                                userRepository.syncUploaderProfileToPintxos(
                                                    uid = user.uid,
                                                    displayName = trimmedName,
                                                    photoUrl = uploadedUrl.orEmpty()
                                                )
                                            }

                                            // Delete old image if it was replaced with a new one
                                            if (selectedProfileImageUri != null && !oldImageUrl.isNullOrBlank() && uploadedUrl != oldImageUrl) {
                                                val oldId = ImageRepository.extractPublicIdFromUrl(oldImageUrl)
                                                if (!oldId.isNullOrBlank()) {
                                                    coroutineScope.launch { ImageRepository.deleteImageByToken(oldId) }
                                                }
                                            }

                                            isSavingProfile = false
                                            isEditing = false
                                            selectedProfileImageUri = null
                                            alertMessage = "Perfil actualizado"
                                        } catch (e: Exception) {
                                            isSavingProfile = false
                                            alertMessage = "No se pudo actualizar el perfil"
                                        }
                                    }
                                }
                            )
                        } else {
                            // PREMIUM V2 DASHBOARD
                            DashboardContent(
                                totalPintxos = totalPintxos,
                                level = gamificationState.level,
                                progress = gamificationState.levelProgress,
                                isMyProfile = isMyProfile,
                                isFriend = isFriend,
                                loadingFriend = loadingFriendAction,
                                onAction = {
                                    if (isMyProfile) onNavigateToUserPintxos()
                                    else if (currentUserId != null) {
                                        coroutineScope.launch {
                                            loadingFriendAction = true
                                            if (isFriend) {
                                                if (userRepository.removeFriend(currentUserId, targetUid)) isFriend = false
                                            } else {
                                                if (userRepository.addFriend(currentUserId, targetUid)) isFriend = true
                                            }
                                            loadingFriendAction = false
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            GamificationProfileSection(
                                xp = gamificationState.xp,
                                level = gamificationState.level,
                                levelProgress = gamificationState.levelProgress,
                                currentStreak = gamificationState.currentStreak,
                                badges = gamificationState.badges
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                "RETOS SEMANALES",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (gamificationState.activeChallenges.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Black.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(
                                        text = "No hay retos activos ahora mismo",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorOnSurfaceVariant
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    gamificationState.activeChallenges.forEach { challenge ->
                                        WeeklyChallengeCard(
                                            challenge = challenge,
                                            modifier = Modifier.width(260.dp)
                                        )
                                    }
                                }
                            }

                            gamificationState.errorMessage?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorOnSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // BENTO ACHIEVEMENT GRID
                            Text(
                                "VITRINA DE LOGROS",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            AchievementBentoGrid(totalPintxos = totalPintxos)

                            Spacer(modifier = Modifier.height(32.dp))

                            // COMMENTS SECTION (Full dashboard width)
                            CommentsSection(targetUserId = targetUid, currentUserId = currentUserId, commentsEnabled = commentsEnabled)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

    ModernTopToast(
        message = alertMessage,
        onDismiss = { alertMessage = null },
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // closes Box
} // closes UserProfileScreen

@Composable
private fun DashboardContent(
    totalPintxos: Int,
    level: Int,
    progress: Float,
    isMyProfile: Boolean,
    isFriend: Boolean,
    loadingFriend: Boolean,
    onAction: () -> Unit
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Stats Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Main Stat Card
            Card(
                modifier = Modifier.weight(1.3f).height(160.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colorPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.Analytics, null, tint = Color.White.copy(alpha = 0.7f))
                    Column {
                        Text("$totalPintxos", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        Text("PINTXOS COMPARTIDOS", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // XP Progress Card
            Card(
                modifier = Modifier.weight(1f).height(160.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colorContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(64.dp),
                            color = colorPrimary,
                            strokeWidth = 6.dp,
                            trackColor = colorOnSurface.copy(alpha = 0.05f)
                        )
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nivel $level",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PRÓXIMO NIVEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = colorOnSurfaceVariant)
                }
            }
        }

        // Action Button
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMyProfile || !isFriend) colorPrimary else colorSurface,
                contentColor = if (isMyProfile || !isFriend) Color.White else colorPrimary
            ),
            border = if (!isMyProfile && isFriend) BorderStroke(2.dp, colorPrimary) else null,
            enabled = !loadingFriend
        ) {
            if (loadingFriend) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colorPrimary, strokeWidth = 2.dp)
            } else {
                val icon = if (isMyProfile) Icons.Default.Restaurant else if (isFriend) Icons.Default.Check else Icons.Default.PersonAdd
                val label = if (isMyProfile) "MIS APORTACIONES" else if (isFriend) "SOMOS AMIGOS" else "AÑADIR A AMIGOS"
                
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun AchievementBentoGrid(totalPintxos: Int) {
    val achievements = listOf(
        Triple(Icons.Default.Restaurant, "Crítico", totalPintxos >= 1),
        Triple(Icons.Default.Star, "Estrella", totalPintxos >= 5),
        Triple(Icons.Default.LocationOn, "Ruta", totalPintxos >= 10),
        Triple(Icons.Default.Badge, "Leyenda", totalPintxos >= 50)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AchievementTile(achievements[0], Modifier.weight(1f))
            AchievementTile(achievements[1], Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AchievementTile(achievements[2], Modifier.weight(1f))
            AchievementTile(achievements[3], Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementTile(data: Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Boolean>, modifier: Modifier) {
    val (icon, name, unlocked) = data
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorGray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) colorPrimary.copy(alpha = 0.08f) else colorContainer.copy(alpha = 0.5f)
        ),
        border = if (unlocked) BorderStroke(1.5.dp, colorPrimary.copy(alpha = 0.15f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (unlocked) Brush.linearGradient(listOf(colorPrimary, colorPrimary.copy(alpha = 0.6f)))
                        else Brush.linearGradient(listOf(colorGray, colorGray.copy(alpha = 0.5f))),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    name.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (unlocked) colorPrimary else colorOnSurface.copy(alpha = 0.4f),
                    letterSpacing = 0.5.sp
                )
                Text(
                    if (unlocked) "Completado" else "Bloqueado",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = colorOnSurface.copy(alpha = if (unlocked) 0.6f else 0.25f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileForm(
    nuevoNombre: String,
    onNombreChange: (String) -> Unit,
    selectedUri: Uri?,
    commentsEnabled: Boolean,
    onCommentsToggle: (Boolean) -> Unit,
    isSaving: Boolean,
    onPickMedia: () -> Unit,
    onLaunchCamera: () -> Unit,
    onSave: () -> Unit
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = colorContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("DETALLES PÚBLICOS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = colorPrimary)
            
            OutlinedTextField(
                value = nuevoNombre,
                onValueChange = onNombreChange,
                label = { Text("Nombre de usuario") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorPrimary,
                    unfocusedBorderColor = colorOnSurfaceVariant.copy(alpha = 0.2f)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onPickMedia,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colorPrimary.copy(alpha = 0.1f),
                    contentColor = colorPrimary
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GALERÍA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Surface(
                    onClick = onLaunchCamera,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colorPrimary.copy(alpha = 0.1f),
                    contentColor = colorPrimary
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CÁMARA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorOnSurface.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Privacidad", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Permitir que otros comenten", style = MaterialTheme.typography.bodySmall, color = colorOnSurfaceVariant)
                }
                Switch(
                    checked = commentsEnabled,
                    onCheckedChange = onCommentsToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = colorPrimary)
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                else Text("ACTUALIZAR PERFIL", fontWeight = FontWeight.Black)
            }
        }
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

