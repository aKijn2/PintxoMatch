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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onNavigateBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid

    var users by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
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

                users = leaderboard
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

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Aún no hay aportaciones para el ranking")
            }
            return@Scaffold
        }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Top Pintxeros de la semana",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "Sube más pintxos y escala posiciones",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (myRank != null) {
                            Text(
                                text = "Tu posición actual: #$myRank",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            itemsIndexed(users.take(30)) { index, user ->
                val medal = when (index) {
                    0 -> "1"
                    1 -> "2"
                    2 -> "3"
                    else -> "#${index + 1}"
                }

                val progress = (user.totalUploads.toFloat() / topCount.toFloat()).coerceIn(0f, 1f)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (index < 3) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = medal, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.size(10.dp))

                                val displayName = user.displayName.replaceFirstChar { it.uppercase() }

                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }

                            val label = if (user.totalUploads == 1) "Pintxo" else "Pintxos"

                            Text(
                                text = "${user.totalUploads} $label",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            trackColor = Color.LightGray,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
