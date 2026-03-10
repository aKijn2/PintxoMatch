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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(onNavigateBack: () -> Unit, onNavigateToUserPintxos: () -> Unit) {
    val user = AuthRepository.currentUser
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ESTADOS
    var totalPintxos by remember { mutableIntStateOf(0) }
    var isEditing by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Cargar estadísticas de Firestore
    LaunchedEffect(user?.uid) {
        val db = FirebaseFirestore.getInstance()
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            totalPintxos = 0
        } else {
            db.collection("Pintxos")
                .whereEqualTo("uploaderUid", uid)
                .get()
                .addOnSuccessListener { result ->
                    totalPintxos = result.size()
                }
                .addOnFailureListener {
                    totalPintxos = 0
                }
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
                        TextButton(onClick = { isEditing = !isEditing }) {
                            Text(if (isEditing) "CANCELAR" else "EDITAR PERFIL", color = colorPrimary, fontWeight = FontWeight.Bold)
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
                    // Banner Image (Blurred)
                    coil.compose.AsyncImage(
                        model = user?.photoUrl ?: "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=800&q=80",
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
                                .border(BorderStroke(3.dp, colorPrimary), RoundedCornerShape(12.dp))
                                .padding(4.dp)
                                .background(colorSurface, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = selectedProfileImageUri ?: user?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
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
                                text = user?.displayName ?: "Comidista",
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
                                    text = "Explorador de Bares",
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
                                .size(45.dp)
                                .border(BorderStroke(2.dp, colorAccent), CircleShape)
                                .clip(CircleShape)
                                .background(colorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$level",
                                color = colorOnSurface,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
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
                    if (isEditing) {
                        // EDITING FORM
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("CONFIGURACIÓN DEL PERFIL", color = colorPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = nuevoNombre,
                                    onValueChange = { nuevoNombre = it },
                                    label = { Text("Nombre real") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorSurface, contentColor = colorOnSurface),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.2f))
                                    ) { Text("Galería") }
                                    Button(
                                        onClick = { 
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorSurface, contentColor = colorOnSurface),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.2f))
                                    ) { Text("Cámara") }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val trimmedName = nuevoNombre.trim()
                                        if (trimmedName.isEmpty()) { alertMessage = "Nombre vacío"; return@Button }
                                        coroutineScope.launch {
                                            isSavingProfile = true
                                            val uploadedUrl = if (selectedProfileImageUri != null) ImageRepository.uploadImage(context, selectedProfileImageUri!!, "pintxomatch/profiles") else user?.photoUrl?.toString()
                                            val updates = userProfileChangeRequest { displayName = trimmedName; if (!uploadedUrl.isNullOrBlank()) photoUri = Uri.parse(uploadedUrl) }
                                            user?.updateProfile(updates)?.addOnSuccessListener {
                                                isSavingProfile = false; isEditing = false; selectedProfileImageUri = null; alertMessage = "Perfil actualizado"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isSavingProfile
                                ) {
                                    if (isSavingProfile) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    else Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // PROGRESS BLOCK
                        Text("EXPERIENCIA PINTXO", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Nivel $level", color = colorOnSurfaceVariant, fontSize = 12.sp)
                                    Text("${totalPintxos % 5} / 5 XP", color = colorOnSurfaceVariant, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressToNextLevel },
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)),
                                    color = colorPrimary,
                                    trackColor = colorOnSurface.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Aporta más pintxos para subir de nivel y desbloquear insignias.", color = colorOnSurfaceVariant, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SHOWCASE SECTION
                        Text("VITRINA DE LOGROS", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AchievementIcon(Icons.Default.Restaurant, "Crítico", totalPintxos >= 1)
                            AchievementIcon(Icons.Default.Star, "Estrella", totalPintxos >= 5)
                            AchievementIcon(Icons.Default.LocationOn, "Ruta", totalPintxos >= 10)
                            AchievementIcon(Icons.Default.Badge, "Leyenda", totalPintxos >= 50)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SUMMARY
                        Text("RESUMEN", color = colorOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$totalPintxos", color = colorOnSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("PINTXOS COMPARTIDOS", color = colorOnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onNavigateToUserPintxos,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary.copy(alpha = 0.1f), contentColor = colorPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.3f))
                                ) {
                                    Text("GESTIONAR MIS APORTACIONES", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
private fun AchievementIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, unlocked: Boolean) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorBackground = MaterialTheme.colorScheme.surface
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorGray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(colorBackground, RoundedCornerShape(8.dp))
                .border(
                    BorderStroke(1.dp, if (unlocked) colorPrimary else colorGray),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
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

