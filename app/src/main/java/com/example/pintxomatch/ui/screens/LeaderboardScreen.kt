package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class LeaderboardUser(
    val uid: String,
    val displayName: String,
    val totalUploads: Int
)

data class LeaderboardPintxo(
    val id: String,
    val name: String,
    val barName: String,
    val averageRating: Double,
    val ratingCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onNavigateBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid

    var users by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var topRatedPintxos by remember { mutableStateOf<List<LeaderboardPintxo>>(emptyList()) }
    var myRank by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("Pintxos")
            .get()
            .addOnSuccessListener { result ->
                val grouped = result.documents.groupBy { doc ->
                    doc.getString("uploaderUid").orEmpty()
                }

                val leaderboard = grouped
                    .filterKeys { it.isNotBlank() }
                    .map { (uid, docs) ->
                        val first = docs.firstOrNull()
                        val uploaderName = first?.getString("uploaderDisplayName")
                            ?: first?.getString("uploaderEmail")?.substringBefore("@")
                            ?: "Usuario"

                        LeaderboardUser(
                            uid = uid,
                            displayName = uploaderName,
                            totalUploads = docs.size
                        )
                    }
                    .sortedByDescending { it.totalUploads }

                val ratingsLeaderboard = result.documents.mapNotNull { doc ->
                    val rawRatings = doc.get("ratings") as? Map<*, *>
                    val fallbackRatings = rawRatings?.values
                        ?.mapNotNull { (it as? Number)?.toInt()?.coerceIn(1, 5) }
                        .orEmpty()
                    val ratingCount = doc.getLong("ratingCount")?.toInt()?.coerceAtLeast(0)
                        ?: fallbackRatings.size
                    if (ratingCount <= 0) {
                        return@mapNotNull null
                    }

                    val ratingTotal = doc.getDouble("ratingTotal")
                        ?: fallbackRatings.sumOf { it.toDouble() }
                    val averageRating = (doc.getDouble("averageRating")
                        ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)

                    LeaderboardPintxo(
                        id = doc.id,
                        name = doc.getString("nombre") ?: "Sin nombre",
                        barName = doc.getString("bar") ?: "Bar desconocido",
                        averageRating = averageRating,
                        ratingCount = ratingCount
                    )
                }.sortedWith(
                    compareByDescending<LeaderboardPintxo> { it.averageRating }
                        .thenByDescending { it.ratingCount }
                        .thenBy { it.name.lowercase() }
                )

                users = leaderboard
                topRatedPintxos = ratingsLeaderboard
                myRank = leaderboard.indexOfFirst { it.uid == currentUid }
                    .takeIf { it != -1 }
                    ?.plus(1)
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                alertMessage = "No se pudo cargar el ranking"
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ranking") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (users.isEmpty() && topRatedPintxos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aún no hay datos suficientes para el ranking")
                }
            } else {

        val topCount = users.firstOrNull()?.totalUploads?.coerceAtLeast(1) ?: 1

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Lo que más se mueve",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Aquí ves quién comparte más pintxos y cuáles están mejor valorados.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RankingStatPill(
                                label = "Pintxeros",
                                value = users.size.toString()
                            )
                            RankingStatPill(
                                label = "Valorados",
                                value = topRatedPintxos.size.toString()
                            )
                        }
                        if (myRank != null) {
                            Text(
                                text = "Tu posición actual entre quienes comparten: #$myRank",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Sube un pintxo para entrar en la clasificación.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                RankingSectionHeader(
                    title = "Quien más comparte",
                    subtitle = "Ordenado por número de pintxos subidos"
                )
            }

            if (users.isNotEmpty()) {
                itemsIndexed(users.take(30)) { index, user ->
                    val progress = (user.totalUploads.toFloat() / topCount.toFloat()).coerceIn(0f, 1f)
                    UserRankingRow(
                        index = index,
                        user = user,
                        progress = progress
                    )
                }
            }

            item {
                RankingSectionHeader(
                    title = "Pintxos mejor valorados",
                    subtitle = "La nota media manda; el número de reseñas decide los empates"
                )
            }

            if (topRatedPintxos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Todavía no hay pintxos valorados.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(topRatedPintxos.take(20)) { index, pintxo ->
                    val medal = when (index) {
                        0 -> "1"
                        1 -> "2"
                        2 -> "3"
                        else -> "#${index + 1}"
                    }
                    PintxoRankingRow(
                        medal = medal,
                        index = index,
                        pintxo = pintxo
                    )
                }
            }
            } // Close the 'else' block containing LazyColumn
            
        } // Close Box
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box
    }
}

@Composable
private fun RankingSectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RankingStatPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RankBadge(
    label: String,
    highlight: Boolean
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UserRankingRow(
    index: Int,
    user: LeaderboardUser,
    progress: Float
) {
    val medal = when (index) {
        0 -> "1"
        1 -> "2"
        2 -> "3"
        else -> "#${index + 1}"
    }
    val displayName = user.displayName.replaceFirstChar { it.uppercase() }
    val label = if (user.totalUploads == 1) "Pintxo compartido" else "Pintxos compartidos"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (index < 3) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RankBadge(label = medal, highlight = index < 3)
                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = user.totalUploads.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                trackColor = Color.LightGray,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PintxoRankingRow(
    medal: String,
    index: Int,
    pintxo: LeaderboardPintxo
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (index < 3) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RankBadge(label = medal, highlight = index < 3)
                Column {
                    Text(
                        text = pintxo.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pintxo.barName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", pintxo.averageRating),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (pintxo.ratingCount == 1) "1 reseña" else "${pintxo.ratingCount} reseñas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


