package com.example.pintxomatch.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pintxomatch.Pintxo
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.components.PintxoCard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onNavigateToSupportInbox: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var pintxos by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var selectedFooterTab by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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
            userRating = currentUid?.let { ratings[it] } ?: 0
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
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "PintxoResenas",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToProfile) {
                            Icon(Icons.Default.Person, contentDescription = "Perfil")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onNavigateToSupportInbox) {
                                    Icon(
                                        imageVector = Icons.Default.SupportAgent,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(" Inbox admin")
                                }
                            }
                        }

                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            NavigationBarItem(
                                selected = selectedFooterTab == "resenas",
                                onClick = {
                                    selectedFooterTab = "resenas"
                                    onNavigateToReviews()
                                },
                                icon = { Icon(Icons.Default.Edit, contentDescription = "Reseñas") },
                                label = { Text("Reseñas") },
                                alwaysShowLabel = false
                            )
                            NavigationBarItem(
                                selected = selectedFooterTab == "ranking",
                                onClick = {
                                    selectedFooterTab = "ranking"
                                    onNavigateToLeaderboard()
                                },
                                icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Ranking") },
                                label = { Text("Ranking") },
                                alwaysShowLabel = false
                            )
                            NavigationBarItem(
                                selected = selectedFooterTab == "subir",
                                onClick = {
                                    selectedFooterTab = "subir"
                                    onNavigateToUpload()
                                },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Subir") },
                                label = { Text("Subir") },
                                alwaysShowLabel = false
                            )
                            NavigationBarItem(
                                selected = selectedFooterTab == "mapa",
                                onClick = {
                                    selectedFooterTab = "mapa"
                                    onNavigateToNearby()
                                },
                                icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                                label = { Text("Mapa") },
                                alwaysShowLabel = false
                            )
                            NavigationBarItem(
                                selected = selectedFooterTab == "soporte",
                                onClick = {
                                    selectedFooterTab = "soporte"
                                    onNavigateToSupport()
                                },
                                icon = { Icon(Icons.Default.SupportAgent, contentDescription = "Soporte") },
                                label = { Text("Soporte") },
                                alwaysShowLabel = false
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
                    val current = pintxos[currentIndex.coerceIn(0, pintxos.lastIndex)]
                    val swipeOffsetX = remember(current.id) { Animatable(0f) }
                    val coroutineScope = rememberCoroutineScope()
                    val springSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                                .graphicsLayer {
                                    rotationZ = swipeOffsetX.value / 30f
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
                                                val threshold = 220f
                                                when {
                                                    swipeOffsetX.value > threshold -> {
                                                        swipeOffsetX.animateTo(900f)
                                                        previousPintxo()
                                                        swipeOffsetX.snapTo(0f)
                                                    }
                                                    swipeOffsetX.value < -threshold -> {
                                                        swipeOffsetX.animateTo(-900f)
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
                                onRatePintxo = { stars -> submitRating(current, stars) }
                            )
                        }

                        Text(
                            text = if (abs(swipeOffsetX.value) > 20f) {
                                "Suelta para cambiar de pintxo"
                            } else {
                                "Desliza a izquierda o derecha para cambiar"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
