package com.example.pintxomatch.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.data.repository.ImageRepository
import com.example.pintxomatch.data.repository.AuthRepository
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {}
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()), // Permite scroll al editar
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 1. FOTO DE PERFIL CIRCULAR
            coil.compose.AsyncImage(
                model = selectedProfileImageUri
                    ?: user?.photoUrl
                    ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. BLOQUE DE INFORMACIÓN O EDICIÓN
            if (isEditing) {
                // FORMULARIO DE EDICIÓN
                OutlinedTextField(
                    value = nuevoNombre,
                    onValueChange = { nuevoNombre = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Galería")
                    }

                    OutlinedButton(
                        onClick = {
                            val hasCameraPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasCameraPermission) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cámara")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isEditing = false
                            selectedProfileImageUri = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val trimmedName = nuevoNombre.trim()
                            if (trimmedName.isEmpty()) {
                                alertMessage = "El nombre no puede estar vacío"
                                return@Button
                            }
                            if (!trimmedName.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
                                alertMessage = "El nombre solo puede contener letras y números"
                                return@Button
                            }

                            coroutineScope.launch {
                                isSavingProfile = true

                                val uploadedPhotoUrl = if (selectedProfileImageUri != null) {
                                    ImageRepository.uploadImage(
                                        context = context,
                                        uri = selectedProfileImageUri!!,
                                        folder = "pintxomatch/profiles"
                                    )
                                } else {
                                    user?.photoUrl?.toString()
                                }

                                if (selectedProfileImageUri != null && uploadedPhotoUrl.isNullOrBlank()) {
                                    isSavingProfile = false
                                    alertMessage = "No se pudo subir la foto"
                                    return@launch
                                }

                                val profileUpdates = userProfileChangeRequest {
                                    displayName = trimmedName
                                    if (!uploadedPhotoUrl.isNullOrBlank()) {
                                        photoUri = Uri.parse(uploadedPhotoUrl)
                                    }
                                }

                                user?.updateProfile(profileUpdates)?.addOnSuccessListener {
                                    isSavingProfile = false
                                    isEditing = false
                                    selectedProfileImageUri = null
                                    alertMessage = "Perfil actualizado"
                                    nuevoNombre = trimmedName
                                }?.addOnFailureListener {
                                    isSavingProfile = false
                                    alertMessage = "No se pudo actualizar el perfil"
                                }
                            }
                        },
                        enabled = !isSavingProfile,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSavingProfile) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Guardar")
                        }
                    }
                }
            } else {
                // VISTA NORMAL
                Text(
                    text = user?.displayName ?: "Cazador de Pintxos",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.email ?: "",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { isEditing = true }) {
                    Text("Editar Perfil")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. TARJETA DE ESTADÍSTICAS
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Aportaciones a la Comunidad", fontSize = 16.sp)
                    Text(
                        text = "$totalPintxos",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Pintxos subidos", fontSize = 14.sp, fontWeight = FontWeight.Light)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToUserPintxos,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Editar mis Pintxos", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))


        }
        
        }
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box

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

