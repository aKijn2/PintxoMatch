package com.example.pintxomatch.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pintxomatch.data.model.pintxo.Pintxo
import com.example.pintxomatch.ui.feed.FeedUiState
import com.example.pintxomatch.ui.feed.PintxoViewModel
import java.util.Locale

@Composable
fun MainFeedScreen( // Renamed to reflect the new feed style
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: PintxoViewModel = viewModel()
) {
    val feedState by viewModel.feedState.collectAsState()

    Scaffold(
        containerColor = Color.Black // TikTok feeds always look best on true black
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            
            when (val state = feedState) {
                is FeedUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                }
                is FeedUiState.Error -> {
                    Text(state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                }
                is FeedUiState.Success -> {
                    if (state.pintxos.isEmpty()) {
                        Text("¡No hay más pintxos!", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    } else {
                        // The Magic TikTok Pager
                        val pagerState = rememberPagerState(pageCount = { state.pintxos.size })
                        
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val pintxo = state.pintxos[page]
                            FullScreenFoodItem(
                                pintxo = pintxo,
                                onLikeClick = { viewModel.onSwipe(pintxo.id) }, // You can rename this ViewModel function later
                                onChatClick = { onNavigateToChat(pintxo.id) }
                            )
                        }
                    }
                }
            }

            // Top Navigation Overlay (Floating above the feed)
            TopFloatingHeader(
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToUpload = onNavigateToUpload
            )
        }
    }
}

@Composable
fun FullScreenFoodItem(
    pintxo: Pintxo,
    onLikeClick: () -> Unit,
    onChatClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Full Bleed Image (Soon to be Video)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(pintxo.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = pintxo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Heavy bottom gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Bottom Left: Context & Text
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.8f) // Take up 80% width, leave right side for buttons
                .padding(start = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = "@${pintxo.uploaderUid}", // Displaying UID as placeholder for username
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pintxo.name.uppercase(Locale.getDefault()),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${pintxo.barName} · ${pintxo.location}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }
        }

        // Bottom Right: Action Column (TikTok Style)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Uploader Avatar
            val hasPhoto = pintxo.uploaderPhotoUrl.isNotBlank()
            AsyncImage(
                model = if (hasPhoto) pintxo.uploaderPhotoUrl else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80",
                contentDescription = "Profile",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )

            // Like Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onLikeClick) {
                    Icon(Icons.Default.Favorite, contentDescription = "Like", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Text("Like", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Chat/Comment Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onChatClick) {
                    Icon(Icons.Default.Send, contentDescription = "Chat", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text("Chat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun TopFloatingHeader(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 20.dp, end = 20.dp), // Pushed down slightly for status bars
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile
        IconButton(onClick = onNavigateToProfile) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        // Center Branding
        Text(
            text = "Food View X",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 1.sp
        )

        // Upload
        IconButton(onClick = onNavigateToUpload) {
            Icon(Icons.Default.Add, contentDescription = "Upload", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}