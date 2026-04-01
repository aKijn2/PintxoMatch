package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                        val currentUserPhotoUrl = ImageRepository
                            .normalizeImageUrlForCurrentProvider(auth.currentUser?.photoUrl?.toString())
                            .orEmpty()

                        reviews = reviewDocs.documents.mapNotNull { doc ->
                            val stars = doc.getLong("stars")?.toInt() ?: return@mapNotNull null
                            val userUidFromDoc = doc.getString("userUid") ?: ""
                            val storedPhotoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(
                                doc.getString("photoUrl")
                            ).orEmpty()

                            val resolvedPhotoUrl = if (userUidFromDoc == uid && currentUserPhotoUrl.isNotBlank()) {
                                currentUserPhotoUrl
                            } else {
                                storedPhotoUrl
                            }

                            if (userUidFromDoc == uid && resolvedPhotoUrl.isNotBlank() && resolvedPhotoUrl != storedPhotoUrl) {
                                firestore.collection("Reviews").document(doc.id)
                                    .update("photoUrl", resolvedPhotoUrl)
                            }

                            ReviewItem(
                                id = doc.id,
                                pintxoId = doc.getString("pintxoId") ?: "",
                                userUid = userUidFromDoc,
                                pintxoName = doc.getString("pintxoName") ?: "Pintxo",
                                userName = doc.getString("userName") ?: "Usuario",
                                photoUrl = resolvedPhotoUrl,
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

    val colorBackground = MaterialTheme.colorScheme.background
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = colorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reseñas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorBackground
                )
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Write review card ──────────────────────────────────
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
                            shadowElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Escribe tu reseña",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (ratedPintxos.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = colorOnSurfaceVariant.copy(alpha = 0.07f)
                                    ) {
                                        Text(
                                            text = "Valora un pintxo primero para poder escribir una reseña.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorOnSurfaceVariant,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                } else {
                                    val filteredOptions = ratedPintxos.filter {
                                        selectionSearch.isBlank() ||
                                            it.name.contains(selectionSearch, ignoreCase = true) ||
                                            it.barName.contains(selectionSearch, ignoreCase = true)
                                    }

                                    // Pintxo picker
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
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            label = { Text("Pintxo") },
                                            placeholder = { Text("Elige el pintxo a reseñar") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPintxoDropdown)
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colorPrimary,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                            )
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
                                                    text = { Text("Sin resultados", color = colorOnSurfaceVariant) },
                                                    onClick = {}
                                                )
                                            } else {
                                                filteredOptions.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(option.name, fontWeight = FontWeight.SemiBold)
                                                                Text(
                                                                    "${option.barName} · ${option.myStars}★",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = colorOnSurfaceVariant
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

                                    // Star picker
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Tu valoración",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorOnSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            (1..5).forEach { star ->
                                                Icon(
                                                    imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                    contentDescription = "$star estrellas",
                                                    tint = if (star <= selectedStars) Color(0xFFFFC107)
                                                    else colorOnSurfaceVariant.copy(alpha = 0.35f),
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) { selectedStars = star }
                                                )
                                            }
                                        }
                                    }

                                    // Review text
                                    OutlinedTextField(
                                        value = reviewText,
                                        onValueChange = { reviewText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        label = { Text("Tu reseña") },
                                        placeholder = { Text("Comparte tu experiencia con este pintxo…") },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colorPrimary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Submit button
                                    FilledTonalButton(
                                        onClick = { submitReview() },
                                        enabled = !isSaving,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = colorPrimary.copy(alpha = 0.14f),
                                            contentColor = colorPrimary
                                        )
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = colorPrimary
                                            )
                                        } else {
                                            Text(
                                                text = if (editingReviewId != null) "Actualizar reseña" else "Publicar reseña",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Section header ─────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Últimas reseñas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (reviews.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = colorPrimary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = "${reviews.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // ── Review list ────────────────────────────────────────
                    if (reviews.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 760.dp)
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Todavía no hay reseñas publicadas.",
                                    color = colorOnSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(reviews) { review ->
                            ReviewCard(
                                review = review,
                                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewItem, modifier: Modifier = Modifier) {
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val colorPrimary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: avatar + name/pintxo + stars
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = colorPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, colorPrimary.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (review.photoUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = review.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                error = {
                                    Text(
                                        text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorPrimary
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorPrimary
                            )
                        }
                    }
                }

                // Name + pintxo name
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = review.userName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = review.pintxoName,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Star rating badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFC107).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${review.stars}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8B6914)
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )

            // Review text
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colorOnSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}
