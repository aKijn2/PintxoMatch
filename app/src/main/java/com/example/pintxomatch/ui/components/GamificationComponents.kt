package com.example.pintxomatch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.viewmodel.WeeklyChallengeUiItem
import kotlinx.coroutines.delay

@Composable
fun GamificationProfileSection(
    xp: Int,
    level: Int,
    levelProgress: Float,
    currentStreak: Int,
    badges: List<String>,
    modifier: Modifier = Modifier
) {
    val surfaceRed = Color(0xFFD32F2F)
    var debugSequenceTrigger by remember { mutableStateOf(0) }

    val animatedProgress = animateFloatAsState(
        targetValue = levelProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "level_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gamification_profile_section"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceRed),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nivel $level",
                        modifier = Modifier.testTag("gamification_level_text"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$xp XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Row(
                    modifier = Modifier.clickable {
                        // Temporary debug replay hook: tap streak icon row to inspect trophy sequence.
                        debugSequenceTrigger += 1
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🔥", fontSize = 20.sp)
                    Text(
                        text = "$currentStreak dias",
                        modifier = Modifier.testTag("gamification_streak_text"),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Badges desbloqueados",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )

                if (badges.isEmpty()) {
                    Text(
                        text = "Aun no tienes badges. Completa retos semanales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(badges) { badgeId ->
                            BadgePill(label = prettifyBadgeName(badgeId))
                        }
                    }
                }
            }
        }
    }

    DebugTrophySequenceOverlay(
        trigger = debugSequenceTrigger,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun WeeklyChallengeCard(
    challenge: WeeklyChallengeUiItem,
    modifier: Modifier = Modifier
) {
    val surfaceRed = Color(0xFFD32F2F)
    val animatedProgress = animateFloatAsState(
        targetValue = challenge.progressFraction,
        animationSpec = tween(durationMillis = 850),
        label = "challenge_progress"
    )

    Card(
        modifier = modifier.testTag("weekly_challenge_card_${challenge.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceRed),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = challenge.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = challenge.description,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )

            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.progressText,
                    modifier = Modifier.testTag("weekly_challenge_progress_${challenge.id}"),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = if (challenge.isCompleted) "Completado" else "Activo",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BadgePill(label: String) {
    Box(
        modifier = Modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DebugTrophySequenceOverlay(
    trigger: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val overlayAlpha = remember { Animatable(0f) }
    val trophyOffsetX = remember { Animatable(0f) }
    val trophyAlpha = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }

        LaunchedEffect(trigger, widthPx) {
            if (trigger <= 0 || widthPx <= 0f) return@LaunchedEffect

            visible = true
            overlayAlpha.snapTo(0f)
            trophyAlpha.snapTo(1f)
            trophyOffsetX.snapTo(-widthPx)

            // 1) Darken the red corporate background to focus the animation.
            overlayAlpha.animateTo(
                targetValue = 0.58f,
                animationSpec = tween(durationMillis = 280)
            )

            // 2) Trophy enters from left edge and lands at center.
            trophyOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
            )

            delay(420)

            // 3) Fade out trophy and remove overlay.
            trophyAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260)
            )
            overlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 230)
            )

            visible = false
        }

        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3B0000).copy(alpha = overlayAlpha.value))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = trophyOffsetX.value
                        alpha = trophyAlpha.value
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                        ),
                        shape = CircleShape
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Debug trophy",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

private fun prettifyBadgeName(badgeId: String): String {
    return badgeId
        .substringAfterLast('_')
        .replace('-', ' ')
        .uppercase()
}
