package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class ReviewItem(
    val id: String,
    val pintxoId: String,
    val pintxoName: String,
    val userName: String,
    val stars: Int,
    val text: String,
    val createdAt: Long
)

private data class RatedPintxoOption(
    val id: String,
    val name: String,
    val myStars: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(onNavigateBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var ratedPintxos by remember { mutableStateOf<List<RatedPintxoOption>>(emptyList()) }
    var selectedPintxoId by remember { mutableStateOf("") }
    var selectedPintxoName by remember { mutableStateOf("") }
    var selectionSearch by remember { mutableStateOf("") }
    var showPintxoDropdown by remember { mutableStateOf(false) }
    var reviewText by remember { mutableStateOf("") }
    var selectedStars by remember { mutableIntStateOf(0) }
    var reviews by remember { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        isLoading = true
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            isLoading = false
            alertMessage = "Sesion no valida"
            return
        }

        firestore.collection("Pintxos").get()
            .addOnSuccessListener { pintxoDocs ->
                val loadedRatedPintxos = pintxoDocs.documents.mapNotNull { doc ->
                    val name = doc.getString("nombre") ?: "Sin nombre"
                    val rawRatings = doc.get("ratings") as? Map<*, *> ?: return@mapNotNull null
                    val myRating = (rawRatings[uid] as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null

                    RatedPintxoOption(
                        id = doc.id,
                        name = name,
                        myStars = myRating
                    )
                }.sortedBy { it.name.lowercase() }

                ratedPintxos = loadedRatedPintxos

                if (loadedRatedPintxos.none { it.id == selectedPintxoId }) {
                    selectedPintxoId = ""
                    selectedPintxoName = ""
                    selectedStars = 0
                }

                firestore.collection("Reviews")
                    .orderBy("createdAt")
                    .get()
                    .addOnSuccessListener { reviewDocs ->
                        reviews = reviewDocs.documents.mapNotNull { doc ->
                            val stars = doc.getLong("stars")?.toInt() ?: return@mapNotNull null
                            ReviewItem(
                                id = doc.id,
                                pintxoId = doc.getString("pintxoId") ?: "",
                                pintxoName = doc.getString("pintxoName") ?: "Pintxo",
                                userName = doc.getString("userName") ?: "Usuario",
                                stars = stars.coerceIn(1, 5),
                                text = doc.getString("text") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }.sortedByDescending { it.createdAt }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        isLoading = false
                        alertMessage = "No se pudieron cargar las resenas"
                    }
            }
            .addOnFailureListener {
                isLoading = false
                alertMessage = "No se pudieron cargar los pintxos"
            }
    }

    fun submitReview() {
        val user = auth.currentUser
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            alertMessage = "Sesion no valida"
            return
        }
        if (selectedPintxoId.isBlank()) {
            alertMessage = "Selecciona un pintxo"
            return
        }
        val cleanText = reviewText.trim()
        if (cleanText.length < 8) {
            alertMessage = "Escribe una resena mas detallada"
            return
        }
        if (selectedStars !in 1..5) {
            alertMessage = "Selecciona de 1 a 5 estrellas"
            return
        }

        val userName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Usuario"

        isSaving = true
        val payload = mapOf(
            "pintxoId" to selectedPintxoId,
            "pintxoName" to selectedPintxoName,
            "userUid" to uid,
            "userName" to userName,
            "stars" to selectedStars,
            "text" to cleanText,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("Reviews")
            .add(payload)
            .addOnSuccessListener {
                val docRef = firestore.collection("Pintxos").document(selectedPintxoId)
                firestore.runTransaction { tx ->
                    val snapshot = tx.get(docRef)
                    val rawRatings = snapshot.get("ratings") as? Map<*, *> ?: emptyMap<String, Any>()
                    val ratings = rawRatings.entries.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = (v as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
                        key to value
                    }.toMap().toMutableMap()

                    val previous = ratings[uid] ?: 0
                    ratings[uid] = selectedStars
                    val ratingCount = if (previous == 0) ratings.size else ratings.size
                    val ratingTotal = ratings.values.sumOf { it.toDouble() }
                    val averageRating = if (ratingCount > 0) ratingTotal / ratingCount else 0.0

                    tx.update(
                        docRef,
                        mapOf(
                            "ratings.$uid" to selectedStars,
                            "ratingCount" to ratingCount,
                            "ratingTotal" to ratingTotal,
                            "averageRating" to averageRating
                        )
                    )
                }.addOnCompleteListener {
                    isSaving = false
                    reviewText = ""
                    selectedStars = 0
                    alertMessage = "Resena publicada"
                    loadData()
                }
            }
            .addOnFailureListener {
                isSaving = false
                alertMessage = "No se pudo publicar la resena"
            }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    BoxWithSnack(snackbarHostState = snackbarHostState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Resenas de la comunidad") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(horizontal = 24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Publica tu resena", fontWeight = FontWeight.Bold)
                                if (ratedPintxos.isEmpty()) {
                                    Text("Aun no has valorado ningun pintxo con estrellas.")
                                } else {
                                    val filteredOptions = ratedPintxos.filter {
                                        selectionSearch.isBlank() || it.name.contains(selectionSearch, ignoreCase = true)
                                    }

                                    ExposedDropdownMenuBox(
                                        expanded = showPintxoDropdown,
                                        onExpandedChange = { showPintxoDropdown = it }
                                    ) {
                                        OutlinedTextField(
                                            value = if (selectionSearch.isNotBlank()) {
                                                selectionSearch
                                            } else {
                                                selectedPintxoName
                                            },
                                            onValueChange = {
                                                selectionSearch = it
                                                showPintxoDropdown = true
                                            },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            label = { Text("Pintxo seleccionado") },
                                            placeholder = { Text("Pulsa para elegir el pintxo valorado") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPintxoDropdown)
                                            },
                                            singleLine = true
                                        )

                                        ExposedDropdownMenu(
                                            expanded = showPintxoDropdown,
                                            onDismissRequest = {
                                                showPintxoDropdown = false
                                                selectionSearch = ""
                                            }
                                        ) {
                                            if (filteredOptions.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("Sin resultados") },
                                                    onClick = {}
                                                )
                                            } else {
                                                filteredOptions.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text("${option.name} (${option.myStars} estrellas)") },
                                                        onClick = {
                                                            selectedPintxoId = option.id
                                                            selectedPintxoName = option.name
                                                            selectedStars = option.myStars
                                                            selectionSearch = ""
                                                            showPintxoDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        (1..5).forEach { star ->
                                            Icon(
                                                imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Se usaran las estrellas que ya diste al pintxo seleccionado.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = reviewText,
                                        onValueChange = { reviewText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 5,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences
                                        ),
                                        label = { Text("Tu resena") }
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { submitReview() },
                                            enabled = !isSaving
                                        ) {
                                            Text(if (isSaving) "Publicando..." else "Publicar")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Ultimas resenas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (reviews.isEmpty()) {
                        item {
                            Text("Todavia no hay resenas publicadas")
                        }
                    } else {
                        items(reviews) { review ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(review.pintxoName, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        (1..5).forEach { idx ->
                                            Icon(
                                                imageVector = if (idx <= review.stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(review.text)
                                    Text(
                                        text = "por ${review.userName}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithSnack(
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        content()
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
        )
    }
}
