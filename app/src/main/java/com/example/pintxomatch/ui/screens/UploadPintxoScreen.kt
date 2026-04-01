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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.pintxomatch.ui.components.ModernTopToast
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.ImageRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
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
        if (alertMessage != null) {
            delay(3000)
            alertMessage = null
        }
    }

    val colorBackground = MaterialTheme.colorScheme.background
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorPrimary = MaterialTheme.colorScheme.primary

    fun publishPintxo() {
        if (nombrePintxo.isBlank()) {
            alertMessage = "Faltan datos clave"
            return
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colorBackground,
            topBar = {
                CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Subir pintxo",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Surface(
                        onClick = { if (!isUploading) publishPintxo() },
                        shape = RoundedCornerShape(50),
                        color = colorPrimary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.28f)),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "Publicar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorPrimary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorBackground
                )
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val isTablet = maxWidth > 700.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = if (isTablet) 760.dp else 600.dp)
                    ) {
                    Spacer(modifier = Modifier.height(18.dp))

                    UploadImagePanel(imageUri = pickedImageUri)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            title = "Abrir cámara",
                            icon = Icons.Default.PhotoCamera,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                        ActionCard(
                            title = "Elegir galería",
                            icon = Icons.Default.Image,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.14f)),
                        shadowElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Detalles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorOnSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            PremiumInput(
                                value = nombrePintxo,
                                onValueChange = { nombrePintxo = it },
                                label = "Nombre del pintxo",
                                icon = Icons.Default.Restaurant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            PremiumInput(
                                value = nombreBar,
                                onValueChange = { nombreBar = it },
                                label = "Nombre del bar",
                                icon = Icons.Default.Store
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            PremiumInput(
                                value = ubicacion,
                                onValueChange = { ubicacion = it },
                                label = "Ubicación",
                                icon = Icons.Default.LocationOn
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            PremiumInput(
                                value = precio,
                                onValueChange = { precio = it },
                                label = "Precio (€)",
                                icon = Icons.Default.Euro,
                                keyboardType = KeyboardType.Decimal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    }
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
private fun UploadImagePanel(imageUri: Uri?) {
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.22f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Vista previa",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Surface(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomStart),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Foto lista",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorOnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = colorOnSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Añade una foto de tu pintxo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Usa cámara o galería",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = colorOnSurface, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold,
                color = colorOnSurface
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

