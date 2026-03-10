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
import androidx.compose.material.icons.filled.Delete
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPintxoScreen(
    pintxoId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val firestore = FirebaseFirestore.getInstance()
    val currentUser = AuthRepository.currentUser

    // VARIABLES DE ESTADO
    var isLoading by remember { mutableStateOf(true) }
    var nombrePintxo by remember { mutableStateOf("") }
    var nombreBar by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var originalImageUrl by remember { mutableStateOf<String?>(null) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pintxoId) {
        if (currentUser == null) {
            alertMessage = "No estás logueado"
            isLoading = false
            return@LaunchedEffect
        }
        firestore.collection("Pintxos").document(pintxoId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getString("uploaderUid") == currentUser.uid) {
                    nombrePintxo = doc.getString("nombre") ?: ""
                    nombreBar = doc.getString("bar") ?: ""
                    ubicacion = doc.getString("ubicacion") ?: ""
                    val precioDouble = doc.getDouble("precio") ?: 0.0
                    if (precioDouble > 0) {
                        precio = precioDouble.toString()
                    }
                    originalImageUrl = doc.getString("imageUrl")
                } else {
                    alertMessage = "No tienes permiso para editar este pintxo o no existe"
                }
                isLoading = false
            }
            .addOnFailureListener {
                alertMessage = "Error al cargar el pintxo"
                isLoading = false
            }
    }

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

        val uri = createTempImageUriEdit(context)
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
                    title = { Text("Editar Pintxo") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Borrar Pintxo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            val model = pickedImageUri ?: originalImageUrl
                            if (model != null && model.toString().isNotBlank()) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Search, null, modifier = Modifier.size(48.dp))
                                        Text("Haz foto o elige de galería")
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

                        Button(
                            onClick = {
                                if (nombrePintxo.isBlank()) {
                                    alertMessage = "El nombre no puede estar vacío"
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
                                        else -> originalImageUrl
                                    }

                                    if (pickedImageUri != null && finalImageUrl.isNullOrBlank()) {
                                        isUploading = false
                                        alertMessage = "Error al subir la nueva imagen"
                                        return@launch
                                    }

                                    dbUpdates(
                                        pintxoId = pintxoId,
                                        nombrePintxo = nombrePintxo,
                                        nombreBar = nombreBar,
                                        ubicacion = ubicacion,
                                        precio = precio,
                                        finalImageUrl = finalImageUrl,
                                        firestore = firestore,
                                        onSuccess = {
                                            isUploading = false
                                            alertMessage = "Pintxo actualizado"
                                            onNavigateBack()
                                        },
                                        onFailure = {
                                            isUploading = false
                                            alertMessage = "Error al actualizar"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !isUploading && !isDeleting
                        ) {
                            if (isUploading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Guardar Cambios")
                        }
                    }
                }
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar Pintxo?") },
            text = { Text("Esta acción es irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        showDeleteDialog = false
                        firestore.collection("Pintxos").document(pintxoId).delete()
                            .addOnSuccessListener {
                                isDeleting = false
                                onNavigateBack()
                            }
                            .addOnFailureListener {
                                isDeleting = false
                                alertMessage = "Error al borrar"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun dbUpdates(
    pintxoId: String,
    nombrePintxo: String,
    nombreBar: String,
    ubicacion: String,
    precio: String,
    finalImageUrl: String?,
    firestore: FirebaseFirestore,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    val updates = mutableMapOf<String, Any>(
        "nombre" to nombrePintxo,
        "bar" to nombreBar,
        "ubicacion" to ubicacion,
        "precio" to (precio.toDoubleOrNull() ?: 0.0)
    )
    if (finalImageUrl != null) {
        updates["imageUrl"] = finalImageUrl
    }
    
    firestore.collection("Pintxos").document(pintxoId).update(updates)
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure() }
}

private fun createTempImageUriEdit(context: Context): Uri? {
    return try {
        val tempFile = File.createTempFile("camera_edit_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    } catch (_: Exception) { null }
}
