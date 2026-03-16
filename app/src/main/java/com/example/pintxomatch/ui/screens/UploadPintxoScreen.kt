package com.example.pintxomatch.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.ImageRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPintxoScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // VARIABLES DE ESTADO
    var nombrePintxo by remember { mutableStateOf("") }
    var nombreBar by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pickedImageUri = pendingCameraUri
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

        val uri = createTempImageUri(context)
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

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorBackground = MaterialTheme.colorScheme.background
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colorBackground)) {
        val maxWidth = maxWidth
        val isTablet = maxWidth > 600.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // IMMERSIVE HEADER (Adaptive)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTablet) 320.dp else 260.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(colorPrimary.copy(alpha = 0.8f), colorBackground)
                        )
                    )
            ) {
                // Top Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, start = 16.dp, end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, "Volver", tint = colorOnSurface, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "SUBIR EXPERIENCIA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = colorOnSurface
                    )
                }

                // HERO IMAGE PREVIEW (Adaptive Width)
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp)
                        .offset(y = 40.dp)
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(colorContainer)) {
                        if (pickedImageUri != null) {
                            AsyncImage(
                                model = pickedImageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorPrimary.copy(alpha = 0.1f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PhotoCamera, 
                                            null, 
                                            modifier = Modifier.size(32.dp), 
                                            tint = colorPrimary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "CAPTURA EL MOMENTO",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = colorOnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // CONTENT WRAPPER (Max Width for Tablets)
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // SELECTION ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        title = "CÁMARA",
                        icon = Icons.Default.PhotoCamera,
                        color = colorPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCameraPermission) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                    ActionCard(
                        title = "GALERÍA",
                        icon = Icons.Default.Image,
                        color = colorPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // THE EXPERIENCE CARD (Grouped form)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = colorSurface,
                    border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.05f)),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "DETALLES DEL PINTXO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = colorPrimary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        PremiumInput(
                            value = nombrePintxo,
                            onValueChange = { nombrePintxo = it },
                            label = "Nombre del Pintxo",
                            icon = Icons.Default.Restaurant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PremiumInput(
                            value = nombreBar,
                            onValueChange = { nombreBar = it },
                            label = "Nombre del Bar",
                            icon = Icons.Default.Store
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        PremiumInput(
                            value = ubicacion,
                            onValueChange = { ubicacion = it },
                            label = "Ubicación",
                            icon = Icons.Default.LocationOn
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        PremiumInput(
                            value = precio,
                            onValueChange = { precio = it },
                            label = "Precio (€)",
                            icon = Icons.Default.Euro,
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // PUBLISH ACTION
                Button(
                    onClick = {
                        if (nombrePintxo.isBlank()) {
                            alertMessage = "Faltan datos clave"
                            return@Button
                        }

                        coroutineScope.launch {
                            isUploading = true
                            val uploadAttempt = when {
                                pickedImageUri != null -> ImageRepository.uploadImageAttempt(context, pickedImageUri!!)
                                else -> null
                            }
                            val uploadResult = uploadAttempt?.result
                            val finalImageUrl = uploadResult?.secureUrl

                            if (finalImageUrl.isNullOrBlank()) {
                                isUploading = false
                                alertMessage = if (pickedImageUri == null) {
                                    "Selecciona una foto"
                                } else {
                                    uploadAttempt?.errorMessage ?: "Error al subir la imagen"
                                }
                                return@launch
                            }

                            val db = FirebaseFirestore.getInstance()
                            val currentUser = AuthRepository.currentUser
                            val pintxo = hashMapOf(
                                "nombre" to nombrePintxo,
                                "bar" to nombreBar,
                                "ubicacion" to ubicacion,
                                "precio" to (precio.toDoubleOrNull() ?: 0.0),
                                "imageUrl" to finalImageUrl,
                                "imagePublicId" to (uploadResult?.publicId ?: ""),
                                "imageDeleteToken" to (uploadResult?.deleteToken ?: ""),
                                "imageDeleteTokenCreatedAt" to (uploadResult?.uploadedAtMillis ?: 0L),
                                "uploaderUid" to (currentUser?.uid ?: ""),
                                "uploaderEmail" to (currentUser?.email ?: ""),
                                "uploaderDisplayName" to (currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: ""),
                                "uploaderPhotoUrl" to (currentUser?.photoUrl?.toString() ?: ""),
                                "timestamp" to System.currentTimeMillis()
                            )

                            db.collection("Pintxos").add(pintxo)
                                .addOnSuccessListener {
                                    isUploading = false
                                    alertMessage = "Pintxo publicado con éxito"
                                    onNavigateBack()
                                }
                                .addOnFailureListener {
                                    isUploading = false
                                    alertMessage = "Error al subir"
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(68.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorPrimary),
                    enabled = !isUploading,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(
                            "PUBLICAR EXPERIENCIA", 
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    } // end BoxWithConstraints
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp), tint = colorPrimary.copy(alpha = 0.6f)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorPrimary,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = colorContainer.copy(alpha = 0.5f),
            unfocusedContainerColor = colorContainer.copy(alpha = 0.5f)
        )
    )
}

private fun createTempImageUri(context: Context): Uri? {
    return try {
        val tempFile = File.createTempFile(
            "camera_${System.currentTimeMillis()}",
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

