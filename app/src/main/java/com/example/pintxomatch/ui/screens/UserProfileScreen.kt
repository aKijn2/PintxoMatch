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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(onNavigateBack: () -> Unit, onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cloudName = "dm99kc8ky"
    val uploadPreset = "pintxomatch"

    // ESTADOS
    var totalPintxos by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
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
                actions = {
                    // Botón de cerrar sesión
                    IconButton(onClick = {
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, "Salir", tint = Color.Red)
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
                                    uploadUriToCloudinaryForProfile(
                                        context = context,
                                        uri = selectedProfileImageUri!!,
                                        cloudName = cloudName,
                                        uploadPreset = uploadPreset
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

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // 4. OPCIÓN PELIGROSA: ELIMINAR CUENTA
            TextButton(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Eliminar mi cuenta definitivamente")
            }
        }
        
        }
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box

    // DIÁLOGO DE CONFIRMACIÓN
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false 
                deletePassword = ""
            },
            title = { Text("¿Deseas marcharte?") },
            text = { 
                Column {
                    Text("Esta acción es irreversible. Se borrarán tus datos de acceso pero tus pintxos compartidos seguirán ayudando a otros. Introduce tu contraseña para confirmar:")
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
                            alertMessage = "No se pudo validar la cuenta"
                            return@Button
                        }
                        if (deletePassword.isBlank()) {
                            alertMessage = "Introduce tu contraseña para continuar"
                            return@Button
                        }

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
                                                .addOnSuccessListener {
                                                    alertMessage = "Cuenta eliminada"
                                                    onLogout()
                                                }
                                                .addOnFailureListener {
                                                    alertMessage = "No se pudo eliminar la cuenta: ${it.localizedMessage}"
                                                }
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
                                                    .addOnSuccessListener {
                                                        alertMessage = "Cuenta eliminada"
                                                        onLogout()
                                                    }
                                                    .addOnFailureListener {
                                                        alertMessage = "No se pudo eliminar la cuenta: ${it.localizedMessage}"
                                                    }
                                            }
                                            .addOnFailureListener {
                                                alertMessage = "No se pudieron anonimizar tus pintxos"
                                            }
                                    }
                                    .addOnFailureListener {
                                        alertMessage = "No se pudo preparar la eliminación"
                                    }
                            }
                            .addOnFailureListener {
                                alertMessage = "Contraseña incorrecta o error de re-autenticación"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
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

private suspend fun uploadUriToCloudinaryForProfile(
    context: Context,
    uri: Uri,
    cloudName: String,
    uploadPreset: String
): String? {
    val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } ?: return null

    return uploadBytesToCloudinaryForProfile(
        bytes = bytes,
        fileName = "profile_${System.currentTimeMillis()}.jpg",
        cloudName = cloudName,
        uploadPreset = uploadPreset
    )
}

private suspend fun uploadBytesToCloudinaryForProfile(
    bytes: ByteArray,
    fileName: String,
    cloudName: String,
    uploadPreset: String
): String? = withContext(Dispatchers.IO) {
    try {
        val boundary = "Boundary-${UUID.randomUUID()}"
        val url = URL("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(connection.outputStream).use { out ->
            fun writeTextPart(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.writeBytes(value)
                out.writeBytes("\r\n")
            }

            writeTextPart("upload_preset", uploadPreset)
            writeTextPart("folder", "pintxomatch/profiles")

            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n")
            out.writeBytes("--$boundary--\r\n")
            out.flush()
        }

        val responseCode = connection.responseCode
        val response = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }

        if (responseCode !in 200..299 || response.isNullOrBlank()) {
            null
        } else {
            JSONObject(response).optString("secure_url")
        }
    } catch (_: Exception) {
        null
    }
}
