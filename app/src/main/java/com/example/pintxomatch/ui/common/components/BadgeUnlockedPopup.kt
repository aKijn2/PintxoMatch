package com.example.pintxomatch.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BadgeUnlockedPopup(
    visible: Boolean,
    badgeId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 2300L
) {
    val glow = rememberInfiniteTransition(label = "badge_glow")
    val glowAlpha = glow.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_glow_alpha"
    )

    LaunchedEffect(visible, badgeId) {
        if (visible) {
            delay(autoDismissMillis)
            onDismiss()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.86f),
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .shadow(
                            elevation = 30.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = Color(0xFFFFA726).copy(alpha = glowAlpha.value),
                            spotColor = Color(0xFFFF7043).copy(alpha = glowAlpha.value)
                        ),
                    color = Color(0xFF111215),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFFB74D).copy(alpha = 0.75f),
                                Color(0xFFFF2D55).copy(alpha = 0.8f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFD54F),
                                            Color(0xFFFF8A65)
                                        )
                                    ),
                                    CircleShape
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Badge desbloqueado",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "Badge desbloqueado",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badgeId.toPremiumBadgeLabel(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tu constancia suma XP y reputacion en Food View X",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }
    }
}

fun String.toPremiumBadgeLabel(): String {
    return this
        .substringAfterLast('_')
        .replace('-', ' ')
        .uppercase()
}
