package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.viewmodel.UserPintxosUiState
import com.example.pintxomatch.ui.viewmodel.UserPintxosViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPintxosScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: UserPintxosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val colorBackground = MaterialTheme.colorScheme.background
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        viewModel.loadUserPintxos()
    }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    Scaffold(
        containerColor = colorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Pintxos", fontWeight = FontWeight.SemiBold) },
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
        snackbarHost = {
            AppSnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is UserPintxosUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UserPintxosUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UserPintxosUiState.Success -> {
                val pintxos = state.pintxos

                if (pintxos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No has subido ningún pintxo todavía.",
                            color = colorOnSurfaceVariant
                        )
                    }
                } else {
                    val filteredPintxos = pintxos.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.barName.contains(searchQuery, ignoreCase = true)
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.TopCenter),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = 32.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Search field
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp),
                                    placeholder = { Text("Buscar por nombre o bar…") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = colorPrimary.copy(alpha = 0.6f)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Borrar")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorPrimary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            if (filteredPintxos.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No hay resultados para «$searchQuery»",
                                            color = colorOnSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(filteredPintxos) { pintxo ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
                                            .clickable { onNavigateToEdit(pintxo.id) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            colorOnSurfaceVariant.copy(alpha = 0.12f)
                                        ),
                                        shadowElevation = 1.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            AsyncImage(
                                                model = pintxo.imageUrl.takeIf { it.isNotBlank() },
                                                contentDescription = pintxo.name,
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(RoundedCornerShape(14.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    pintxo.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    pintxo.barName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorOnSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = colorPrimary.copy(alpha = 0.10f),
                                                border = BorderStroke(1.dp, colorPrimary.copy(alpha = 0.22f))
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Editar",
                                                    tint = colorPrimary,
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .size(18.dp)
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
        }
    }
}
