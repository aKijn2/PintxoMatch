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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pintxomatch.ui.components.ModernTopToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.pintxomatch.data.repository.ImageRepository
import kotlinx.coroutines.delay
import java.util.Locale

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

private enum class ReviewsSectionTab {
    Write,
    Community
}

private data class PintxoCommunitySummary(
    val id: String,
    val name: String,
    val barName: String,
    val imageUrl: String,
    val averageRating: Double,
    val ratingCount: Int
)

private data class PintxoCommunityGroup(
    val summary: PintxoCommunitySummary,
    val reviews: List<ReviewItem>
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
    var pintxosCommunity by remember { mutableStateOf<List<PintxoCommunitySummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedSectionTab by remember { mutableStateOf(ReviewsSectionTab.Write) }
    var communitySearchQuery by remember { mutableStateOf("") }
    var expandedCommunityGroup by remember { mutableStateOf<PintxoCommunityGroup?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

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
                val loadedPintxosCommunity = pintxoDocs.documents.map { doc ->
                    val rawRatings = doc.get("ratings") as? Map<*, *> ?: emptyMap<String, Any>()
                    val numericRatings = rawRatings.values.mapNotNull { rating ->
                        (rating as? Number)?.toInt()?.coerceIn(1, 5)
                    }
                    val fallbackRatingCount = numericRatings.size
                    val ratingCount = doc.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: fallbackRatingCount
                    val ratingTotal = doc.getDouble("ratingTotal") ?: numericRatings.sumOf { it.toDouble() }
                    val averageRating = if (ratingCount > 0) {
                        (doc.getDouble("averageRating") ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)
                    } else {
                        0.0
                    }

                    PintxoCommunitySummary(
                        id = doc.id,
                        name = doc.getString("nombre") ?: "Pintxo",
                        barName = doc.getString("bar") ?: "Bar desconocido",
                        imageUrl = ImageRepository.normalizeImageUrlForCurrentProvider(
                            doc.getString("imageUrl")
                        ).orEmpty(),
                        averageRating = averageRating,
                        ratingCount = ratingCount
                    )
                }

                val loadedRatedPintxos = pintxoDocs.documents.mapNotNull { doc ->
                    val name = doc.getString("nombre") ?: "Sin nombre"
                    val barName = doc.getString("bar") ?: "Bar desconocido"
                    val rawRatings = doc.get("ratings") as? Map<*, *> ?: return@mapNotNull null
                    val myRating = (rawRatings[uid] as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
                    RatedPintxoOption(id = doc.id, name = name, barName = barName, myStars = myRating)
                }.sortedBy { it.name.lowercase() }

                pintxosCommunity = loadedPintxosCommunity
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
        if (alertMessage != null) {
            delay(3000)
            alertMessage = null
        }
    }

    val colorBackground = MaterialTheme.colorScheme.background
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val currentUid = auth.currentUser?.uid.orEmpty()
    val myReviewsCount = remember(reviews, currentUid) {
        if (currentUid.isBlank()) 0 else reviews.count { it.userUid == currentUid }
    }
    val communityGroups = remember(reviews, pintxosCommunity) {
        buildCommunityGroups(reviews = reviews, summaries = pintxosCommunity)
    }
    val filteredCommunityGroups = remember(communityGroups, communitySearchQuery) {
        val query = communitySearchQuery.trim()
        if (query.isBlank()) {
            communityGroups
        } else {
            communityGroups.filter { group ->
                group.summary.name.contains(query, ignoreCase = true) ||
                    group.summary.barName.contains(query, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            }
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
                        item {
                            ReviewsSectionSwitcher(
                                selectedTab = selectedSectionTab,
                                communityReviewsCount = reviews.size,
                                onTabSelected = { selectedSectionTab = it },
                                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                            )
                        }

                        if (selectedSectionTab == ReviewsSectionTab.Write) {
                            item {
                                ReviewsWriteHighlights(
                                    ratedPintxosCount = ratedPintxos.size,
                                    myReviewsCount = myReviewsCount,
                                    communityReviewsCount = reviews.size,
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                                )
                            }
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.16f)),
                                    shadowElevation = 4.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp),
                                            color = colorPrimary.copy(alpha = 0.10f),
                                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.20f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Tu opinión cuenta",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (editingReviewId != null) {
                                                        Surface(
                                                            shape = RoundedCornerShape(50),
                                                            color = colorPrimary,
                                                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.9f))
                                                        ) {
                                                            Text(
                                                                text = "Editando",
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = "Selecciona un pintxo valorado y comparte una reseña clara y útil para la comunidad.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorOnSurfaceVariant
                                                )
                                            }
                                        }

                                        if (ratedPintxos.isEmpty()) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                color = colorOnSurfaceVariant.copy(alpha = 0.07f),
                                                border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.18f))
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
                                                    placeholder = { Text("Busca o elige un pintxo") },
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
                                                                            "${option.barName} · tu nota ${option.myStars}★",
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            color = colorOnSurfaceVariant
                                                                        )
                                                                    }
                                                                },
                                                                onClick = {
                                                                    selectedPintxoId = option.id
                                                                    selectedPintxoName = option.name
                                                                    val myReview = reviews
                                                                        .filter { it.userUid == currentUid && it.pintxoId == option.id }
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

                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "Tu valoración",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colorOnSurfaceVariant
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    (1..5).forEach { star ->
                                                        val selected = star <= selectedStars
                                                        Surface(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(44.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null
                                                                ) { selectedStars = star },
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = if (selected) Color(0xFFFFC107).copy(alpha = 0.18f)
                                                            else MaterialTheme.colorScheme.surfaceContainerLow,
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (selected) Color(0xFFFFC107).copy(alpha = 0.5f)
                                                                else colorOnSurfaceVariant.copy(alpha = 0.20f)
                                                            )
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxSize(),
                                                                horizontalArrangement = Arrangement.Center,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                                    contentDescription = "$star estrellas",
                                                                    tint = if (selected) Color(0xFFFFC107) else colorOnSurfaceVariant.copy(alpha = 0.45f),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = star.toString(),
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (selected) Color(0xFF8B6914) else colorOnSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (selectedStars > 0) {
                                                    Text(
                                                        text = "Has seleccionado $selectedStars estrellas",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = colorPrimary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            OutlinedTextField(
                                                value = reviewText,
                                                onValueChange = { reviewText = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 4,
                                                maxLines = 7,
                                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                                label = { Text("Tu reseña") },
                                                placeholder = { Text("Habla del sabor, textura, presentación y si lo recomendarías") },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = colorPrimary,
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                                )
                                            )

                                            Text(
                                                text = "${reviewText.trim().length} caracteres",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorOnSurfaceVariant,
                                                modifier = Modifier.align(Alignment.End)
                                            )

                                            FilledTonalButton(
                                                onClick = { submitReview() },
                                                enabled = !isSaving,
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = colorPrimary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                if (isSaving) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
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
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp)
                                        .padding(top = 4.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Pintxos valorados por la comunidad",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (communityGroups.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = colorPrimary.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.25f))
                                        ) {
                                            Text(
                                                text = "${communityGroups.size}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = communitySearchQuery,
                                    onValueChange = { communitySearchQuery = it },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                                    singleLine = true,
                                    label = { Text("Buscar pintxo") },
                                    placeholder = { Text("Nombre del pintxo o bar") },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorPrimary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            if (filteredCommunityGroups.isEmpty()) {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = if (communitySearchQuery.isBlank()) {
                                                "Todavía no hay reseñas de comunidad para mostrar."
                                            } else {
                                                "No hay pintxos que coincidan con tu búsqueda."
                                            },
                                            modifier = Modifier.padding(18.dp),
                                            color = colorOnSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                items(filteredCommunityGroups) { group ->
                                    PintxoCommunityCard(
                                        group = group,
                                        onOpenDetails = { expandedCommunityGroup = group },
                                        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        expandedCommunityGroup?.let { group ->
            CommunityReviewsDialog(
                group = group,
                onDismiss = { expandedCommunityGroup = null }
            )
        }

        ModernTopToast(
            message = alertMessage,
            onDismiss = { alertMessage = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private fun buildCommunityGroups(
    reviews: List<ReviewItem>,
    summaries: List<PintxoCommunitySummary>
): List<PintxoCommunityGroup> {
    val summariesById = summaries.associateBy { it.id }

    return reviews
        .groupBy { it.pintxoId }
        .map { (pintxoId, groupedReviews) ->
            val sortedReviews = groupedReviews.sortedByDescending { it.createdAt }
            val avgFromReviews = sortedReviews.map { it.stars }.average().takeIf { !it.isNaN() } ?: 0.0
            val summary = summariesById[pintxoId]?.let { source ->
                source.copy(
                    averageRating = if (source.averageRating > 0.0) source.averageRating else avgFromReviews,
                    ratingCount = maxOf(source.ratingCount, sortedReviews.size)
                )
            } ?: PintxoCommunitySummary(
                id = if (pintxoId.isBlank()) "unknown_${sortedReviews.first().id}" else pintxoId,
                name = sortedReviews.firstOrNull()?.pintxoName ?: "Pintxo",
                barName = "Comunidad",
                imageUrl = "",
                averageRating = avgFromReviews,
                ratingCount = sortedReviews.size
            )

            PintxoCommunityGroup(summary = summary, reviews = sortedReviews)
        }
        .sortedWith(
            compareByDescending<PintxoCommunityGroup> { it.summary.averageRating }
                .thenByDescending { it.reviews.size }
        )
}

@Composable
private fun ReviewsSectionSwitcher(
    selectedTab: ReviewsSectionTab,
    communityReviewsCount: Int,
    onTabSelected: (ReviewsSectionTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(ReviewsSectionTab.Write, ReviewsSectionTab.Community).forEach { tab ->
                val selected = tab == selectedTab
                val containerColor by animateColorAsState(
                    targetValue = if (selected) colorPrimary else colorPrimary.copy(alpha = 0.10f),
                    label = "reviewsTabContainer"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else colorPrimary,
                    label = "reviewsTabText"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTabSelected(tab) },
                    color = containerColor,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = when (tab) {
                            ReviewsSectionTab.Write -> "Escribir"
                            ReviewsSectionTab.Community -> "Comunidad"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewsWriteHighlights(
    ratedPintxosCount: Int,
    myReviewsCount: Int,
    communityReviewsCount: Int,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colorPrimary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewStatChip(
                title = "Valorados",
                value = ratedPintxosCount.toString(),
                modifier = Modifier.weight(1f)
            )
            ReviewStatChip(
                title = "Tus reseñas",
                value = myReviewsCount.toString(),
                modifier = Modifier.weight(1f)
            )
            ReviewStatChip(
                title = "Comunidad",
                value = communityReviewsCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReviewStatChip(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PintxoCommunityCard(
    group: PintxoCommunityGroup,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val topReviews = group.reviews.take(1)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                if (group.summary.imageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = group.summary.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colorPrimary.copy(alpha = 0.14f))
                            )
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorPrimary.copy(alpha = 0.14f))
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenDetails() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "+",
                            color = colorPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ver más",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.44f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.summary.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = group.summary.barName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatRating(group.summary.averageRating),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${group.summary.ratingCount} valoraciones · ${group.reviews.size} reseñas",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorOnSurfaceVariant
                )

                topReviews.forEach { review ->
                    CommunityReviewRow(review = review)
                }
            }
        }
    }
}

@Composable
private fun CommunityReviewRow(review: ReviewItem) {
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = colorPrimary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.24f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (review.photoUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = review.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            error = {
                                Text(
                                    text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = colorPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    } else {
                        Text(
                            text = review.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = colorPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = review.userName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = review.stars.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8B6914),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorOnSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun CommunityReviewsDialog(
    group: PintxoCommunityGroup,
    onDismiss: () -> Unit
) {
    var selectedPage by remember(group.summary.id) { mutableIntStateOf(0) }
    val reviewsPerPage = 5
    val pageCount = (group.reviews.size + reviewsPerPage - 1) / reviewsPerPage
    val safePageCount = pageCount.coerceAtLeast(1)
    val pageStart = selectedPage * reviewsPerPage
    val pageReviews = group.reviews.drop(pageStart).take(reviewsPerPage)
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val canGoPrevious = selectedPage > 0
    val canGoNext = selectedPage < safePageCount - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.18f)),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    ) {
                        if (group.summary.imageUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = group.summary.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colorPrimary.copy(alpha = 0.14f))
                                    )
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colorPrimary.copy(alpha = 0.14f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f))
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.95f))
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cerrar",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.42f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.summary.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = group.summary.barName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = formatRating(group.summary.averageRating),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${group.summary.ratingCount} valoraciones · ${group.reviews.size} reseñas",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorOnSurfaceVariant
                        )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageReviews) { review ->
                            CommunityReviewRow(review = review)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canGoPrevious) {
                                    if (canGoPrevious) selectedPage -= 1
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (canGoPrevious) colorPrimary.copy(alpha = 0.10f) else colorOnSurfaceVariant.copy(alpha = 0.09f),
                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.20f))
                        ) {
                            Text(
                                text = "Anterior",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (canGoPrevious) colorPrimary else colorOnSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(safePageCount) { pageIndex ->
                                val selected = pageIndex == selectedPage
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedPage = pageIndex },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) colorPrimary else colorPrimary.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = (pageIndex + 1).toString(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else colorPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canGoNext) {
                                    if (canGoNext) selectedPage += 1
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (canGoNext) colorPrimary.copy(alpha = 0.10f) else colorOnSurfaceVariant.copy(alpha = 0.09f),
                            border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.20f))
                        ) {
                            Text(
                                text = "Siguiente",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (canGoNext) colorPrimary else colorOnSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun formatRating(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}
