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
import com.example.pintxomatch.ui.viewmodel.FeedUiState
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSwipeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: PintxoViewModel = viewModel()
) {
    val feedState by viewModel.feedState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PintxoMatch", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToUpload) { Icon(Icons.Default.Add, "Subir") }
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.Person, "Perfil") }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = feedState) {
                is FeedUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is FeedUiState.Error -> {
                    Text(state.message, modifier = Modifier.align(Alignment.Center), color = Color.Red)
                }
                is FeedUiState.Success -> {
                    if (state.pintxos.isEmpty()) {
                        Text("¡Te has comido todo Gipuzkoa!", modifier = Modifier.align(Alignment.Center))
                    } else {
                        val currentPintxo = state.pintxos[0]

                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            PintxoCard(pintxo = currentPintxo)

                            Spacer(modifier = Modifier.height(32.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FloatingActionButton(
                                    onClick = { viewModel.onSwipe(currentPintxo.id) },
                                    containerColor = Color.White,
                                    contentColor = Color.Red
                                ) {
                                    Icon(Icons.Default.Close, "Paso")
                                }

                                FloatingActionButton(
                                    onClick = { onNavigateToChat(currentPintxo.id) },
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF4CAF50)
                                ) {
                                    Icon(Icons.Default.Favorite, "Match")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
