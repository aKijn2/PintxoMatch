package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pintxomatch.Pintxo
import com.example.pintxomatch.ui.components.PintxoCard
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSwipeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit // Nueva función para ir al chat
) {
    var pintxosFirebase by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Pintxos").get().addOnSuccessListener { result ->
            val listaNueva = result.map { doc ->
                Pintxo(
                    id = doc.id, // ID real de Firebase
                    name = doc.getString("nombre") ?: "",
                    barName = doc.getString("bar") ?: "",
                    location = doc.getString("ubicacion") ?: "",
                    price = doc.getDouble("precio") ?: 0.0,
                    imageUrl = doc.getString("imageUrl") ?: ""
                )
            }
            pintxosFirebase = listaNueva
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PintxoMatch", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToUpload) { Icon(Icons.Default.Add, "Subir") }
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.Person, "Perfil") }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pintxosFirebase.isEmpty()) {
                Text("¡Te has comido todo Gipuzkoa!", modifier = Modifier.align(Alignment.Center))
            } else {
                val currentPintxo = pintxosFirebase[0]

                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // La carta del pintxo
                    PintxoCard(pintxo = currentPintxo)

                    Spacer(modifier = Modifier.height(32.dp))

                    // BOTONES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Botón de rechazar
                        FloatingActionButton(onClick = { pintxosFirebase = pintxosFirebase.drop(1) }, containerColor = Color.White, contentColor = Color.Red) {
                            Icon(Icons.Default.Close, "Paso")
                        }

                        // BOTÓN DE MATCH (CORAZÓN VERDE)
                        FloatingActionButton(
                            onClick = { onNavigateToChat(currentPintxo.id) }, // Navega al chat con el ID real
                            containerColor = Color.White,
                            contentColor = Color(0xFF4CAF50)
                        ) {
                            Icon(Icons.Default.Favorite, "Match")
                        }
                    }
                }
            }
        }
    }
}