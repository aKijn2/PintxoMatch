package com.example.pintxomatch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.components.PintxoCard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private data class HomeRatingUpdate(
    val averageRating: Double,
    val ratingCount: Int,
    val userRating: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeReviewScreen(
    isAdmin: Boolean,
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToReviews: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToNearby: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToSupportInbox: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPublicProfile: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var pintxos by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var selectedFooterTab by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userPhotoUrl = remember { auth.currentUser?.photoUrl?.toString() }

    fun nextPintxo() {
        if (pintxos.isEmpty()) return
        currentIndex = if (currentIndex >= pintxos.lastIndex) 0 else currentIndex + 1
    }

    fun previousPintxo() {
        if (pintxos.isEmpty()) return
        currentIndex = if (currentIndex <= 0) pintxos.lastIndex else currentIndex - 1
    }

    fun notify(message: String) {
        alertMessage = message
    }

    fun extractRatings(snapshot: DocumentSnapshot): Map<String, Int> {
        val rawRatings = snapshot.get("ratings") as? Map<*, *> ?: return emptyMap()
        return rawRatings.entries.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val rating = (value as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
            uid to rating
        }.toMap()
    }

    fun mapDocumentToPintxo(snapshot: DocumentSnapshot, currentUid: String?): Pintxo {
        val ratings = extractRatings(snapshot)
        val ratingCount = snapshot.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: ratings.size
        val ratingTotal = snapshot.getDouble("ratingTotal") ?: ratings.values.sumOf { it.toDouble() }
        val averageRating = if (ratingCount > 0) {
            (snapshot.getDouble("averageRating") ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)
        } else {
            0.0
        }

        return Pintxo(
            id = snapshot.id,
            name = snapshot.getString("nombre") ?: "Sin nombre",
            barName = snapshot.getString("bar") ?: "Bar desconocido",
            location = snapshot.getString("ubicacion") ?: "Gipuzkoa",
            price = snapshot.getDouble("precio") ?: 0.0,
            imageUrl = snapshot.getString("imageUrl") ?: "",
            averageRating = averageRating,
            ratingCount = ratingCount,
            userRating = currentUid?.let { ratings[it] } ?: 0,
            uploaderUid = snapshot.getString("uploaderUid") ?: "",
            uploaderDisplayName = snapshot.getString("uploaderDisplayName") ?: "Usuario Anónimo",
            uploaderPhotoUrl = snapshot.getString("uploaderPhotoUrl") ?: ""
        )
    }

    fun reloadPintxos() {
        isLoading = true
        val currentUid = auth.currentUser?.uid
        firestore.collection("Pintxos")
            .orderBy(FieldPath.documentId())
            .get()
            .addOnSuccessListener { result ->
                pintxos = result.map { doc -> mapDocumentToPintxo(doc, currentUid) }
                currentIndex = 0
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                notify("Error cargando pintxos")
            }
    }

    fun submitRating(pintxo: Pintxo, stars: Int) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            notify("Inicia sesion para valorar")
            return
        }

        val newRating = stars.coerceIn(1, 5)
        val docRef = firestore.collection("Pintxos").document(pintxo.id)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val existingRatings = extractRatings(snapshot)
            val previousRating = existingRatings[uid] ?: 0
            val baseCount = snapshot.getLong("ratingCount")?.toInt()?.coerceAtLeast(0)
                ?: existingRatings.size
            val baseTotal = snapshot.getDouble("ratingTotal")
                ?: existingRatings.values.sumOf { it.toDouble() }

            val ratingCount = if (previousRating == 0) baseCount + 1 else baseCount
            val ratingTotal = if (previousRating == 0) {
                baseTotal + newRating.toDouble()
            } else {
                baseTotal - previousRating.toDouble() + newRating.toDouble()
            }
            val averageRating = if (ratingCount > 0) ratingTotal / ratingCount else 0.0

            transaction.update(
                docRef,
                mapOf(
                    "ratings.$uid" to newRating,
                    "ratingCount" to ratingCount,
                    "ratingTotal" to ratingTotal,
                    "averageRating" to averageRating
                )
            )

            HomeRatingUpdate(
                averageRating = averageRating,
                ratingCount = ratingCount,
                userRating = newRating
            )
        }.addOnSuccessListener { updated ->
            pintxos = pintxos.map {
                if (it.id == pintxo.id) {
                    it.copy(
                        averageRating = updated.averageRating,
                        ratingCount = updated.ratingCount,
                        userRating = updated.userRating
                    )
                } else {
                    it
                }
            }
            notify("Valoracion guardada")
        }.addOnFailureListener {
            notify("No se pudo guardar la valoracion")
        }
    }

    LaunchedEffect(Unit) {
        reloadPintxos()
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
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "PintxoMatch",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = onNavigateToProfile) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                 if (!userPhotoUrl.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = userPhotoUrl,
                                        contentDescription = "Perfil",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape),
                                        loading = {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        error = {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Perfil",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isAdmin) {
                        TextButton(
                            onClick = onNavigateToSupportInbox,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                " Inbox admin",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(32.dp),
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(32.dp)
                            )
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavPillItem(
                                icon = Icons.Default.Edit,
                                label = "Reviews",
                                selected = selectedFooterTab == "resenas",
                                onClick = { selectedFooterTab = "resenas"; onNavigateToReviews() }
                            )
                            NavPillItem(
                                icon = Icons.Default.Leaderboard,
                                label = "Top",
                                selected = selectedFooterTab == "ranking",
                                onClick = { selectedFooterTab = "ranking"; onNavigateToLeaderboard() }
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = CircleShape,
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { selectedFooterTab = "subir"; onNavigateToUpload() }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Subir",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            NavPillItem(
                                icon = Icons.Default.Map,
                                label = "Mapa",
                                selected = selectedFooterTab == "mapa",
                                onClick = { selectedFooterTab = "mapa"; onNavigateToNearby() }
                            )
                            NavPillItem(
                                icon = Icons.Default.SupportAgent,
                                label = "Soporte",
                                selected = selectedFooterTab == "soporte",
                                onClick = { selectedFooterTab = "soporte"; onNavigateToSupport() }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (pintxos.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay pintxos publicados todavia")
                        Button(onClick = { reloadPintxos() }) {
                            Text("Recargar")
                        }
                    }
                } else {
                    val safeIndex = currentIndex.coerceIn(0, pintxos.lastIndex)
                    val current = pintxos[safeIndex]
                    val next = pintxos.getOrNull(if (safeIndex + 1 <= pintxos.lastIndex) safeIndex + 1 else 0)

                    val swipeOffsetX = remember(current.id) { Animatable(0f) }
                    val coroutineScope = rememberCoroutineScope()
                    val springSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )

                    // Derived visual values from drag offset
                    val dragFraction = (swipeOffsetX.value / 500f).coerceIn(-1f, 1f)
                    val cardRotation = swipeOffsetX.value / 22f
                    val likeAlpha = (dragFraction).coerceAtLeast(0f)
                    val nopeAlpha = (-dragFraction).coerceAtLeast(0f)
                    // Next card scales up as current card is dragged away
                    val nextCardScale = 0.88f + (0.12f * kotlin.math.abs(dragFraction))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Next card (behind, smaller)
                            if (next != null && next.id != current.id) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = nextCardScale
                                            scaleY = nextCardScale
                                            alpha = 0.72f + 0.28f * kotlin.math.abs(dragFraction)
                                        }
                                ) {
                                    PintxoCard(
                                        pintxo = next, 
                                        onRatePintxo = null,
                                        onUploaderClick = { uid -> 
                                            if (uid.isNotBlank()) onNavigateToPublicProfile(uid)
                                            else notify("Este pintxo es antiguo y no tiene perfil asigando")
                                        }
                                    )
                                }
                            }

                            // Current card (on top)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationX = swipeOffsetX.value
                                        rotationZ = cardRotation
                                    }
                                    .pointerInput(current.id) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                                                }
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    val threshold = 200f
                                                    when {
                                                        swipeOffsetX.value > threshold -> {
                                                            swipeOffsetX.animateTo(1200f)
                                                            previousPintxo()
                                                            swipeOffsetX.snapTo(0f)
                                                        }
                                                        swipeOffsetX.value < -threshold -> {
                                                            swipeOffsetX.animateTo(-1200f)
                                                            nextPintxo()
                                                            swipeOffsetX.snapTo(0f)
                                                        }
                                                        else -> {
                                                            swipeOffsetX.animateTo(0f, springSpec)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                PintxoCard(
                                    pintxo = current,
                                    onRatePintxo = { stars -> submitRating(current, stars) },
                                    onUploaderClick = { uid -> 
                                        if (uid.isNotBlank()) onNavigateToPublicProfile(uid)
                                        else notify("Este pintxo es antiguo y no tiene perfil asignado")
                                    }
                                )

                                // Swipe right tint overlay (previous)
                                if (likeAlpha > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFF00C853).copy(alpha = likeAlpha * 0.55f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .graphicsLayer { alpha = likeAlpha },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .padding(start = 28.dp)
                                                .size(56.dp)
                                                .graphicsLayer { alpha = likeAlpha.coerceIn(0f, 1f) }
                                        )
                                    }
                                }

                                // Swipe left tint overlay (next)
                                if (nopeAlpha > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = nopeAlpha * 0.55f)
                                                    )
                                                )
                                            )
                                            .graphicsLayer { alpha = nopeAlpha },
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .padding(end = 28.dp)
                                                .size(56.dp)
                                                .graphicsLayer { alpha = nopeAlpha.coerceIn(0f, 1f) }
                                        )
                                    }
                                }
                            }
                        }

                        // Dot progress indicators
                        val visibleDots = minOf(pintxos.size, 7)
                        val startIndex = (safeIndex - visibleDots / 2).coerceIn(0, (pintxos.size - visibleDots).coerceAtLeast(0))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (startIndex until startIndex + visibleDots).forEach { i ->
                                val isActive = i == safeIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 10.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                )
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

@Composable
private fun NavPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "navIconColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "navBgColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(color = bgColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        if (selected) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
