package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.pintxomatch.data.repository.ImageRepository

data class ReviewItem(
    val id: String,
    val pintxoId: String,
    val userUid: String,
    val pintxoName: String,
    val userName: String,
    val photoUrl: String,
    val stars: Int,
    val text: String,
    val createdAt: Long
)

private data class RatedPintxoOption(
    val id: String,
    val name: String,
    val barName: String,
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
    var editingReviewId by remember { mutableStateOf<String?>(null) }
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
                    val barName = doc.getString("bar") ?: "Bar desconocido"
                    val rawRatings = doc.get("ratings") as? Map<*, *> ?: return@mapNotNull null
                    val myRating = (rawRatings[uid] as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
                    RatedPintxoOption(id = doc.id, name = name, barName = barName, myStars = myRating)
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
                                userUid = doc.getString("userUid") ?: "",
                                pintxoName = doc.getString("pintxoName") ?: "Pintxo",
                                userName = doc.getString("userName") ?: "Usuario",
                                photoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(doc.getString("photoUrl")) ?: "",
                                stars = stars.coerceIn(1, 5),
                                text = doc.getString("text") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }.sortedByDescending { it.createdAt }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        isLoading = false
                        alertMessage = "No se pudieron cargar las reseñas"
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
        if (uid.isNullOrBlank()) { alertMessage = "Sesion no valida"; return }
        if (selectedPintxoId.isBlank()) { alertMessage = "Selecciona un pintxo"; return }
        val cleanText = reviewText.trim()
        if (cleanText.length < 8) { alertMessage = "Escribe una reseña mas detallada"; return }
        if (selectedStars !in 1..5) { alertMessage = "Selecciona de 1 a 5 estrellas"; return }

        val userName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Usuario"

        isSaving = true
        val existingMine = reviews
            .filter { it.userUid == uid && it.pintxoId == selectedPintxoId }
            .maxByOrNull { it.createdAt }

        val targetReviewDocId = existingMine?.id ?: "${uid}_$selectedPintxoId"
        val payload = mapOf(
            "pintxoId" to selectedPintxoId,
            "pintxoName" to selectedPintxoName,
            "userUid" to uid,
            "userName" to userName,
            "photoUrl" to (ImageRepository.normalizeImageUrlForCurrentProvider(user.photoUrl?.toString()) ?: ""),
            "stars" to selectedStars,
            "text" to cleanText,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("Reviews")
            .document(targetReviewDocId)
            .set(payload)
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

                    ratings[uid] = selectedStars
                    val ratingCount = ratings.size
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
                    editingReviewId = null
                    alertMessage = if (existingMine == null) "Reseña publicada" else "Reseña actualizada"
                    loadData()
                }
            }
            .addOnFailureListener {
                isSaving = false
                alertMessage = "No se pudo publicar la reseña"
            }
    }

    LaunchedEffect(Unit) { loadData() }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Reseñas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "de la comunidad",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Escribe tu reseña",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (ratedPintxos.isEmpty()) {
                                    Text(
                                        text = "Valora un pintxo primero para poder escribir una reseña.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val filteredOptions = ratedPintxos.filter {
                                        selectionSearch.isBlank() ||
                                            it.name.contains(selectionSearch, ignoreCase = true) ||
                                            it.barName.contains(selectionSearch, ignoreCase = true)
                                    }

                                    ExposedDropdownMenuBox(
                                        expanded = showPintxoDropdown,
                                        onExpandedChange = { showPintxoDropdown = it }
                                    ) {
                                        OutlinedTextField(
                                            value = if (selectionSearch.isNotBlank()) selectionSearch else selectedPintxoName,
                                            onValueChange = {
                                                selectionSearch = it
                                                showPintxoDropdown = true
                                            },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            label = { Text("Pintxo") },
                                            placeholder = { Text("Elige el pintxo a reseñar") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPintxoDropdown)
                                            },
                                            shape = RoundedCornerShape(12.dp),
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
                                                        text = {
                                                            Column {
                                                                Text(option.name, fontWeight = FontWeight.Medium)
                                                                Text(
                                                                    "${option.barName} · ${option.myStars}★",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedPintxoId = option.id
                                                            selectedPintxoName = option.name
                                                            val myReview = reviews
                                                                .filter { it.userUid == auth.currentUser?.uid && it.pintxoId == option.id }
                                                                .maxByOrNull { it.createdAt }
                                                            if (myReview != null) {
                                                                editingReviewId = myReview.id
                                                                reviewText = myReview.text
                                                                selectedStars = myReview.stars
                                                            } else {
                                                                editingReviewId = null
                                                                reviewText = ""
                                                                selectedStars = option.myStars
                                                            }
                                                            selectionSearch = ""
                                                            showPintxoDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Valoración",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            (1..5).forEach { star ->
                                                Icon(
                                                    imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                    contentDescription = "$star estrellas",
                                                    tint = if (star <= selectedStars) Color(0xFFFFC107)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) { selectedStars = star }
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = reviewText,
                                        onValueChange = { reviewText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 5,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        label = { Text("Tu reseña") },
                                        placeholder = { Text("Comparte tu experiencia con este pintxo...") },
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Button(
                                        onClick = { submitReview() },
                                        enabled = !isSaving,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isSaving -> "Guardando..."
                                                editingReviewId != null -> "Actualizar reseña"
                                                else -> "Publicar reseña"
                                            },
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Últimas reseñas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (reviews.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${reviews.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (reviews.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Todavía no hay reseñas publicadas.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(reviews) { review ->
                            ReviewCard(review = review)
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

@Composable
private fun ReviewCard(review: ReviewItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (review.photoUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = review.photoUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            error = {
                                Text(
                                    text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        )
                    } else {
                        Text(
                            text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.userName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = review.pintxoName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { idx ->
                        Icon(
                            imageVector = if (idx <= review.stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (idx <= review.stars) Color(0xFFFFC107)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            Text(
                text = review.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
