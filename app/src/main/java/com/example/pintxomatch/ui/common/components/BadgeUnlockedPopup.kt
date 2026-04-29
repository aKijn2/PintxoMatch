package com.example.pintxomatch.ui.common.components
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun BadgeUnlockedPopup(
    visible: Boolean,
    badgeId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 2300L
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val overlayAlpha = remember { Animatable(0f) }
    val trophyOffsetX = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    var textVisible by remember { mutableStateOf(false) }
    var internalVisible by remember { mutableStateOf(false) }
    val badgeLabel = badgeId.toBadgeDisplayLabel()

    LaunchedEffect(visible, badgeId) {
        if (visible && badgeId.isNotBlank()) {
            internalVisible = true
        }
    }

    if (internalVisible) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            BoxWithConstraints(
                modifier = modifier.fillMaxSize()
            ) {
                val widthPx = with(density) { maxWidth.toPx() }

                LaunchedEffect(badgeId, widthPx) {
                    if (badgeId.isBlank() || widthPx <= 0f) return@LaunchedEffect

                    triggerAchievementVibration(context)
                    textVisible = false
                    overlayAlpha.snapTo(0f)
                    contentAlpha.snapTo(1f)
                    trophyOffsetX.snapTo(-widthPx)

                    overlayAlpha.animateTo(
                        targetValue = 0.8f,
                        animationSpec = tween(durationMillis = 300)
                    )
                    trophyOffsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
                    )

                    delay(90)
                    textVisible = true
                    delay(autoDismissMillis)

                    contentAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 320)
                    )
                    overlayAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 320)
                    )

                    textVisible = false
                    internalVisible = false
                    onDismiss()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha.value))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = trophyOffsetX.value
                            alpha = contentAlpha.value
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                                    ),
                                    shape = CircleShape
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                .padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Trofeo desbloqueado",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = textVisible,
                            enter = fadeIn(animationSpec = tween(durationMillis = 260)) +
                                slideInVertically(
                                    animationSpec = tween(durationMillis = 260),
                                    initialOffsetY = { it / 3 }
                                ),
                            exit = fadeOut(animationSpec = tween(durationMillis = 180))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "NUEVO TROFEO",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.75f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = badgeLabel,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.75f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
