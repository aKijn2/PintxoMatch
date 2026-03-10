package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pintxomatch.Pintxo
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPintxosScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    var pintxos by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    LaunchedEffect(currentUser?.uid) {
        if (currentUser == null) {
            isLoading = false
            return@LaunchedEffect
        }
        firestore.collection("Pintxos")
            .whereEqualTo("uploaderUid", currentUser.uid)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    val ratingsRaw = doc.get("ratings") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val ratings = ratingsRaw.entries.mapNotNull { (k, v) ->
                        val uid = k as? String ?: return@mapNotNull null
                        val rating = (v as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
                        uid to rating
                    }.toMap()
                    val ratingCount = doc.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: ratings.size
                    val ratingTotal = doc.getDouble("ratingTotal") ?: ratings.values.sumOf { it.toDouble() }
                    val averageRating = if (ratingCount > 0) {
                        (doc.getDouble("averageRating") ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)
                    } else 0.0

                    Pintxo(
                        id = doc.id,
                        name = doc.getString("nombre") ?: "Sin nombre",
                        barName = doc.getString("bar") ?: "Bar desconocido",
                        location = doc.getString("ubicacion") ?: "Ubicación desconocida",
                        price = doc.getDouble("precio") ?: 0.0,
                        imageUrl = doc.getString("imageUrl") ?: "",
                        averageRating = averageRating,
                        ratingCount = ratingCount,
                        userRating = ratings[currentUser.uid] ?: 0
                    )
                }
                pintxos = list
                isLoading = false
            }
            .addOnFailureListener {
                alertMessage = "Error al cargar tus pintxos"
                isLoading = false
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
                    title = { Text("Mis Pintxos") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (pintxos.isEmpty()) {
                    Text(
                        "No has subido ningún pintxo todavía.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val filteredPintxos = pintxos.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.barName.contains(searchQuery, ignoreCase = true)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            placeholder = { Text("Buscar por nombre o bar...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (filteredPintxos.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No se encontraron resultados para '$searchQuery'")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredPintxos) { pintxo ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToEdit(pintxo.id) },
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = pintxo.imageUrl.takeIf { it.isNotBlank() } ?: "https://images.unsplash.com/photo-1541592106381-b31e9677c0e5?q=80&w=300&auto=format&fit=crop",
                                                contentDescription = pintxo.name,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pintxo.name, style = MaterialTheme.typography.titleMedium)
                                                Text(pintxo.barName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { onNavigateToEdit(pintxo.id) }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                                            }
                                        }
                                    }
                                }
                            }
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
}
