package com.example.pintxomatch.ui.screens

import android.widget.Toast
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(onNavigateBack: () -> Unit, onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current

    // ESTADOS
    var totalPintxos by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Estados para el formulario de edición
    var nuevoNombre by remember { mutableStateOf(user?.displayName ?: "") }
    var nuevaFotoUrl by remember { mutableStateOf(user?.photoUrl?.toString() ?: "") }

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
                model = user?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80",
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
                OutlinedTextField(
                    value = nuevaFotoUrl,
                    onValueChange = { nuevaFotoUrl = it },
                    label = { Text("URL de la foto de perfil") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://enlace.com/foto.jpg") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val profileUpdates = userProfileChangeRequest {
                                displayName = nuevoNombre
                                photoUri = android.net.Uri.parse(nuevaFotoUrl)
                            }
                            user?.updateProfile(profileUpdates)?.addOnSuccessListener {
                                isEditing = false
                                Toast.makeText(context, "Perfil actualizado ✨", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar")
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
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
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

    // DIÁLOGO DE CONFIRMACIÓN
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Deseas marcharte?") },
            text = { Text("Esta acción es irreversible. Se borrarán tus datos de acceso pero tus pintxos compartidos seguirán ayudando a otros.") },
            confirmButton = {
                Button(
                    onClick = {
                        user?.delete()?.addOnSuccessListener {
                            Toast.makeText(context, "Cuenta eliminada", Toast.LENGTH_SHORT).show()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Confirmar eliminación")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Seguir aquí")
                }
            }
        )
    }
}