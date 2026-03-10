package com.example.pintxomatch.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.data.repository.AuthRepository
import com.example.pintxomatch.data.repository.ImageRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subir un Pintxo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 1. VISTA PREVIA DE LA IMAGEN (Si el link es válido, se verá aquí)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                when {
                    pickedImageUri != null -> {
                        AsyncImage(
                            model = pickedImageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(48.dp))
                            Text("Haz foto o elige de galería")
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    Text("Sacar foto")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = nombrePintxo, onValueChange = { nombrePintxo = it }, label = { Text("Nombre del Pintxo") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = nombreBar, onValueChange = { nombreBar = it }, label = { Text("Bar") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = ubicacion, onValueChange = { ubicacion = it }, label = { Text("Ubicación") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = precio,
                onValueChange = { precio = it },
                label = { Text("Precio €") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. BOTÓN DE PUBLICAR (Directo a Firestore, Gratis)
            Button(
                onClick = {
                    if (nombrePintxo.isBlank()) {
                        alertMessage = "Faltan datos clave"
                        return@Button
                    }

                    coroutineScope.launch {
                        isUploading = true

                        val finalImageUrl = when {
                            pickedImageUri != null -> {
                                ImageRepository.uploadImage(
                                    context = context,
                                    uri = pickedImageUri!!
                                )
                            }
                            else -> null
                        }

                        if (finalImageUrl.isNullOrBlank()) {
                            isUploading = false
                            alertMessage = "Selecciona una foto de cámara o galería"
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
                            "uploaderUid" to (currentUser?.uid ?: ""),
                            "uploaderEmail" to (currentUser?.email ?: ""),
                            "uploaderDisplayName" to (currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: ""),
                            "uploaderPhotoUrl" to (currentUser?.photoUrl?.toString() ?: ""),
                            "timestamp" to System.currentTimeMillis()
                        )

                        db.collection("Pintxos")
                            .add(pintxo)
                            .addOnSuccessListener {
                                isUploading = false
                                alertMessage = "Pintxo publicado"
                                onNavigateBack()
                            }
                            .addOnFailureListener {
                                isUploading = false
                                alertMessage = "Error al subir"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isUploading
            ) {
                if (isUploading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("¡Publicar!")
            }
        }
        
        }
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box
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

