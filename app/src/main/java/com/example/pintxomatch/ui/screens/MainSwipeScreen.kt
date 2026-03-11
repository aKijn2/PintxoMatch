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
import com.example.pintxomatch.ui.components.PintxoCard
import com.example.pintxomatch.ui.viewmodel.PintxoViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.viewmodel.FeedUiState
import androidx.lifecycle.viewmodel.compose.viewModel
@Composable
fun MainSwipeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: PintxoViewModel = viewModel()
) {
    val feedState by viewModel.feedState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToProfile,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
                ) { 
                    Icon(Icons.Default.Person, "Perfil", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) 
                }

                Text(
                    "PintxoMatch", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 26.sp, 
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )

                IconButton(
                    onClick = onNavigateToUpload,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
                ) { 
                    Icon(Icons.Default.Add, "Subir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) 
                }
            }

            // Swipe Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                when (val state = feedState) {
                    is FeedUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    }
                    is FeedUiState.Error -> {
                        Text(state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                    is FeedUiState.Success -> {
                        if (state.pintxos.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("¡Te has comido todo Gipuzkoa!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Vuelve pronto para ver más pintxos.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val currentPintxo = state.pintxos[0]
                            PintxoCard(pintxo = currentPintxo, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // Bottom Buttons
            if (feedState is FeedUiState.Success && (feedState as FeedUiState.Success).pintxos.isNotEmpty()) {
                val currentPintxo = (feedState as FeedUiState.Success).pintxos[0]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pass Button
                    IconButton(
                        onClick = { viewModel.onSwipe(currentPintxo.id) },
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Paso", tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))
                    }

                    // Match Button
                    IconButton(
                        onClick = { onNavigateToChat(currentPintxo.id) },
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(Icons.Default.Favorite, "Match", tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
