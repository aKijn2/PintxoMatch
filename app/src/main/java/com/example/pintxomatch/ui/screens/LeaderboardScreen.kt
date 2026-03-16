package com.example.pintxomatch.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pintxomatch.data.model.LeaderboardPintxo
import com.example.pintxomatch.data.model.LeaderboardUser
import com.example.pintxomatch.ui.viewmodel.LeaderboardUiState
import com.example.pintxomatch.ui.viewmodel.PintxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: PintxoViewModel = viewModel()
) {
    val leaderboardState by viewModel.leaderboardState.collectAsState()

    val (users, topRatedPintxos) = when (val state = leaderboardState) {
        is LeaderboardUiState.Success -> state.users to state.pintxos
        else -> emptyList<LeaderboardUser>() to emptyList<LeaderboardPintxo>()
    }

    val topCount = users.firstOrNull()?.totalUploads?.coerceAtLeast(1) ?: 1
    val colorBackground = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = colorBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Ranking",
                        fontWeight = FontWeight.SemiBold
                    )
                },
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
    ) { padding ->
        when (leaderboardState) {
            is LeaderboardUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is LeaderboardUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (leaderboardState as LeaderboardUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                if (users.isEmpty() && topRatedPintxos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aún no hay datos suficientes para el ranking",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
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
                            // Podium card
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    ),
                                    shadowElevation = 1.dp
                                ) {
                                    PodiumHeroCard(users = users.take(3))
                                }
                            }

                            // Users full list
                            if (users.size > 3) {
                                item {
                                    SectionLabel(
                                        title = "Clasificación completa",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
                                            .padding(top = 6.dp, bottom = 2.dp)
                                    )
                                }
                                itemsIndexed(users.drop(3)) { i, user ->
                                    UserRankingRow(
                                        index = i + 3,
                                        user = user,
                                        progress = user.totalUploads.toFloat() / topCount,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
                                    )
                                }
                            }

                            // Pintxos section header
                            item {
                                SectionLabel(
                                    title = "Pintxos mejor valorados",
                                    subtitle = "Ordenados por nota media · empates por nº reseñas",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp)
                                        .padding(top = 6.dp, bottom = 2.dp)
                                )
                            }

                            if (topRatedPintxos.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Todavía no hay pintxos valorados.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(topRatedPintxos.take(20)) { index, pintxo ->
                                    PintxoRankingRow(
                                        index = index,
                                        pintxo = pintxo,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 760.dp)
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

@Composable
private fun SectionLabel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PodiumHeroCard(users: List<LeaderboardUser>) {
    val goldColor   = Color(0xFFFFD700)
    val silverColor = Color(0xFFB0BEC5)
    val bronzeColor = Color(0xFFCD7F32)

    val first  = users.getOrNull(0)
    val second = users.getOrNull(1)
    val third  = users.getOrNull(2)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 28.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "TOP PINTXEROS",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Ranking semanal",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            if (second != null) {
                PodiumSlot(user = second, position = 2, medalColor = silverColor, avatarSize = 68.dp)
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (first != null) {
                PodiumSlot(user = first, position = 1, medalColor = goldColor, avatarSize = 86.dp, isCrown = true)
            }

            if (third != null) {
                PodiumSlot(user = third, position = 3, medalColor = bronzeColor, avatarSize = 60.dp)
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    user: LeaderboardUser,
    position: Int,
    medalColor: Color,
    avatarSize: Dp,
    isCrown: Boolean = false
) {
    Column(
        modifier = Modifier.width(avatarSize + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (isCrown) {
            Text(text = "\uD83D\uDC51", fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
        } else {
            Surface(
                modifier = Modifier.padding(bottom = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = medalColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, medalColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "$position",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = medalColor
                )
            }
        }

        if (user.profileImageUrl.isNotBlank()) {
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = user.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(3.dp, medalColor, CircleShape)
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(avatarSize)
                    .border(3.dp, medalColor, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(avatarSize * 0.5f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = user.displayName.replaceFirstChar { it.uppercase() },
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${user.totalUploads} pintxos",
            style = MaterialTheme.typography.labelSmall,
            color = if (position == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (position == 1) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UserRankingRow(
    index: Int,
    user: LeaderboardUser,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "#${index + 1}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = colorOnSurfaceVariant,
                    modifier = Modifier.width(34.dp),
                    textAlign = TextAlign.Center
                )

                if (user.profileImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = colorOnSurfaceVariant
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (user.totalUploads == 1) "1 pintxo" else "${user.totalUploads} pintxos",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorOnSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "${user.totalUploads}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PintxoRankingRow(
    index: Int,
    pintxo: LeaderboardPintxo,
    modifier: Modifier = Modifier
) {
    val goldColor   = Color(0xFFFFD700)
    val silverColor = Color(0xFFB0BEC5)
    val bronzeColor = Color(0xFFCD7F32)

    val medalColor = when (index) {
        0 -> goldColor
        1 -> silverColor
        2 -> bronzeColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val isTopThree = index < 3
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isTopThree) medalColor.copy(alpha = 0.35f) else colorOnSurfaceVariant.copy(alpha = 0.12f)
        ),
        shadowElevation = if (isTopThree) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (isTopThree) medalColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isTopThree) BorderStroke(1.5.dp, medalColor.copy(alpha = 0.5f)) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isTopThree) 14.sp else 12.sp,
                        color = if (isTopThree) medalColor else colorOnSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pintxo.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pintxo.barName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", pintxo.averageRating),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = if (pintxo.ratingCount == 1) "1 voto" else "${pintxo.ratingCount} votos",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorOnSurfaceVariant
                )
            }
        }
    }
}
