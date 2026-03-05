package com.example.pintxomatch.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPintxoScreen(onNavigateBack: () -> Unit) {
    // VARIABLES DE ESTADO
    var nombrePintxo by remember { mutableStateOf("") }
    var nombreBar by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") } // El link de la foto
    var isUploading by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
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
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(48.dp))
                            Text("Pega un link abajo para ver la foto")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. CAMPOS DE TEXTO
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("URL de la imagen (Link)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://ejemplo.com/foto.jpg") }
            )

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
                    if (nombrePintxo.isNotBlank() && imageUrl.isNotBlank()) {
                        isUploading = true
                        val db = FirebaseFirestore.getInstance()
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        val pintxo = hashMapOf(
                            "nombre" to nombrePintxo,
                            "bar" to nombreBar,
                            "ubicacion" to ubicacion,
                            "precio" to (precio.toDoubleOrNull() ?: 0.0),
                            "imageUrl" to imageUrl,
                            "uploaderUid" to (currentUser?.uid ?: ""),
                            "uploaderEmail" to (currentUser?.email ?: ""),
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
                    } else {
                        alertMessage = "Faltan datos clave"
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