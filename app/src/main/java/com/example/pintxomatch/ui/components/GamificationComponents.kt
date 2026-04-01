package com.example.pintxomatch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.viewmodel.WeeklyChallengeUiItem

@Composable
fun GamificationProfileSection(
    xp: Int,
    level: Int,
    levelProgress: Float,
    currentStreak: Int,
    badges: List<String>,
    modifier: Modifier = Modifier
) {
    val animatedProgress = animateFloatAsState(
        targetValue = levelProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "level_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101012)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🔥", fontSize = 20.sp)
                    Text(
                        text = "$currentStreak dias",
                        color = Color(0xFFFF7043),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFFFF2D55),
                trackColor = Color.White.copy(alpha = 0.12f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Badges desbloqueados",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (badges.isEmpty()) {
                    Text(
                        text = "Aun no tienes badges. Completa retos semanales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
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
}

@Composable
fun WeeklyChallengeCard(
    challenge: WeeklyChallengeUiItem,
    modifier: Modifier = Modifier
) {
    val animatedProgress = animateFloatAsState(
        targetValue = challenge.progressFraction,
        animationSpec = tween(durationMillis = 850),
        label = "challenge_progress"
    )

    val accent = if (challenge.isCompleted) Color(0xFF38D39F) else Color(0xFFFF2D55)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151518)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
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
                color = accent,
                trackColor = Color.White.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.progressText,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = if (challenge.isCompleted) "Completado" else "Activo",
                    color = accent,
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
                    colors = listOf(Color(0xFFFF2D55), Color(0xFFFF9500))
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun prettifyBadgeName(badgeId: String): String {
    return badgeId
        .substringAfterLast('_')
        .replace('-', ' ')
        .uppercase()
}
