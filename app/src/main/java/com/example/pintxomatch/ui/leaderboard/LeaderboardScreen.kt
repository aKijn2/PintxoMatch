package com.example.pintxomatch.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocationOn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pintxomatch.data.model.leaderboard.LeaderboardPintxo
import com.example.pintxomatch.data.model.leaderboard.LeaderboardUser
import com.example.pintxomatch.ui.feed.LeaderboardUiState
import com.example.pintxomatch.ui.feed.PintxoViewModel

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
                            "Aun no hay datos suficientes para el ranking",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        val maxCardWidth = maxWidth.coerceAtMost(760.dp)

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
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = maxCardWidth),
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

                            if (users.size > 3) {
                                item {
                                    SectionLabel(
                                        title = "Clasificacion completa",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = maxCardWidth)
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
                                            .widthIn(max = maxCardWidth)
                                    )
                                }
                            }

                            item {
                                SectionLabel(
                                    title = "Pintxos mejor valorados",
                                    subtitle = "Una seccion mas visual, deliciosa y facil de escanear",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = maxCardWidth)
                                        .padding(top = 10.dp, bottom = 4.dp)
                                )
                            }

                            if (topRatedPintxos.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = maxCardWidth)
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Todavia no hay pintxos valorados.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                item {
                                    PintxoTopShowcase(
                                        pintxos = topRatedPintxos.take(3),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = maxCardWidth)
                                    )
                                }

                                if (topRatedPintxos.size > 3) {
                                    item {
                                        SectionLabel(
                                            title = "Mas joyas de la comunidad",
                                            subtitle = "Ordenadas por nota media y fuerza de votos",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .widthIn(max = maxCardWidth)
                                                .padding(top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                    itemsIndexed(topRatedPintxos.drop(3).take(17)) { index, pintxo ->
                                        PintxoRankingRow(
                                            index = index + 3,
                                            pintxo = pintxo,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .widthIn(max = maxCardWidth)
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
    val goldColor = Color(0xFFFFD700)
    val silverColor = Color(0xFFB0BEC5)
    val bronzeColor = Color(0xFFCD7F32)

    val first = users.getOrNull(0)
    val second = users.getOrNull(1)
    val third = users.getOrNull(2)

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
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PintxoTopShowcase(
    pintxos: List<LeaderboardPintxo>,
    modifier: Modifier = Modifier
) {
    val first = pintxos.getOrNull(0)
    val second = pintxos.getOrNull(1)
    val third = pintxos.getOrNull(2)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (first != null) {
            FeaturedPintxoCard(
                pintxo = first,
                position = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (second != null || third != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                second?.let {
                    SecondaryPintxoCard(
                        pintxo = it,
                        position = 2,
                        modifier = Modifier.width(260.dp)
                    )
                }
                third?.let {
                    SecondaryPintxoCard(
                        pintxo = it,
                        position = 3,
                        modifier = Modifier.width(260.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedPintxoCard(
    pintxo: LeaderboardPintxo,
    position: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            if (pintxo.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = pintxo.imageUrl,
                    contentDescription = pintxo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFD32F2F), Color(0xFF1B1B1B))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.08f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFD54F).copy(alpha = 0.96f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#$position",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color(0xFF5D4037)
                    )
                    Text(
                        text = "TOP DEL DIA",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037),
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", pintxo.averageRating),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = pintxo.name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = pintxo.barName,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricChip(
                        icon = Icons.Default.LocalFireDepartment,
                        label = if (pintxo.ratingCount == 1) "1 voto" else "${pintxo.ratingCount} votos"
                    )
                    MetricChip(
                        icon = Icons.Default.AutoAwesome,
                        label = "Favorito del ranking"
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryPintxoCard(
    pintxo: LeaderboardPintxo,
    position: Int,
    modifier: Modifier = Modifier
) {
    val accent = when (position) {
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        shadowElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (pintxo.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = pintxo.imageUrl,
                        contentDescription = pintxo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(accent.copy(alpha = 0.9f), MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.46f))
                            )
                        )
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = "#$position",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Text(
                    text = String.format(java.util.Locale.US, "%.1f ★", pintxo.averageRating),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = pintxo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pintxo.barName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pintxo.ratingCount == 1) "1 valoracion" else "${pintxo.ratingCount} valoraciones",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
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
    val accentColor = MaterialTheme.colorScheme.primary
    val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colorOnSurfaceVariant.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
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
                color = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = accentColor
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
                    text = "${pintxo.barName} · ${if (pintxo.ratingCount == 1) "1 voto" else "${pintxo.ratingCount} votos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", pintxo.averageRating),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
