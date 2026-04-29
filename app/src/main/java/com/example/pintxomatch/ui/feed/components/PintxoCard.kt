package com.example.pintxomatch.ui.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pintxomatch.data.model.pintxo.Pintxo
import java.util.Locale

@Composable
fun PintxoCard(
    pintxo: Pintxo,
    modifier: Modifier = Modifier,
    onRatePintxo: ((Int) -> Unit)? = null,
    onUploaderClick: ((String) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
    ) {
        // Full-bleed photo
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(pintxo.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = pintxo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_alert)
        )

        // Gradient overlay (dark at bottom, transparent at top)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Price pill (top-right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Text(
                text = String.format(Locale.US, "%.2f €", pintxo.price),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        // Average Rating badge (top-left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", pintxo.averageRating),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "(${pintxo.ratingCount})",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // Bottom info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Interactive Rating (Floating above info core)
            if (onRatePintxo != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "VALORAR", 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Black, 
                            color = Color.White.copy(alpha = 0.8f), 
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..5).forEach { starIndex ->
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$starIndex",
                                    tint = if (starIndex <= pintxo.userRating) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onRatePintxo(starIndex) }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = pintxo.name.uppercase(Locale.getDefault()),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onUploaderClick?.invoke(pintxo.uploaderUid) }
                    .padding(vertical = 4.dp)
            ) {
                // Small Uploader Avatar
                val hasPhoto = pintxo.uploaderPhotoUrl.isNotBlank()
                AsyncImage(
                    model = if (hasPhoto) pintxo.uploaderPhotoUrl else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80",
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${pintxo.barName} · ${pintxo.location}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
