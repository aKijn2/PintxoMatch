package com.example.pintxomatch.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.pintxomatch.ui.components.ModernTopToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONException

private data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

private data class NearbyRestaurant(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int
)

private data class RestaurantMapSelection(
    val restaurantId: String,
    val centerMapOnSelection: Boolean = false
)

private class NearbyRestaurantsException(message: String) : IOException(message)

private class GestureAwareMapView(context: Context) : MapView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyRestaurantsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val nearbyResultsCache = remember { mutableMapOf<String, List<NearbyRestaurant>>() }

    var selectedRadius by remember { mutableIntStateOf(1000) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var restaurants by remember { mutableStateOf<List<NearbyRestaurant>>(emptyList()) }
    var selectedRestaurant by remember { mutableStateOf<NearbyRestaurant?>(null) }
    var restaurantMapSelection by remember { mutableStateOf<RestaurantMapSelection?>(null) }
    var isLocationLoading by remember { mutableStateOf(false) }
    var isPlacesLoading by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var isMapMaximized by remember { mutableStateOf(false) }

    BackHandler(enabled = isMapMaximized) {
        isMapMaximized = false
    }

    val availableCategories = remember(restaurants) {
        listOf("Todos") + restaurants
            .map { normalizeRestaurantCategory(it.category) }
            .distinct()
            .sorted()
    }
    val filteredRestaurants = remember(restaurants, selectedCategory) {
        restaurants.filter { restaurant ->
            selectedCategory == "Todos" ||
                normalizeRestaurantCategory(restaurant.category) == selectedCategory
        }
    }
    val featuredRestaurants = remember(filteredRestaurants) {
        filteredRestaurants.take(3)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            requestCurrentLocation(
                context = context,
                locationManager = locationManager,
                onLoadingChanged = { isLocationLoading = it },
                onLocationFound = { userLocation = it },
                onError = { alertMessage = it }
            )
        } else {
            alertMessage = "Necesitamos tu ubicación para buscar restaurantes cercanos"
        }
    }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            if (it.contains("left the composition", ignoreCase = true)) {
                alertMessage = null
                return@let
            }
            delay(3000)
            alertMessage = null
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            requestCurrentLocation(
                context = context,
                locationManager = locationManager,
                onLoadingChanged = { isLocationLoading = it },
                onLocationFound = { userLocation = it },
                onError = { alertMessage = it }
            )
        }
    }

    LaunchedEffect(userLocation, selectedRadius) {
        val currentLocation = userLocation ?: return@LaunchedEffect
        val cacheKey = buildNearbyCacheKey(currentLocation, selectedRadius)
        nearbyResultsCache[cacheKey]?.let { cachedRestaurants ->
            restaurants = cachedRestaurants
            return@LaunchedEffect
        }

        isPlacesLoading = true
        try {
            val fetched = fetchNearbyRestaurants(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                radiusMeters = selectedRadius
            )
            restaurants = fetched
            nearbyResultsCache[cacheKey] = fetched
            if (selectedRestaurant != null) {
                selectedRestaurant = fetched.firstOrNull { it.id == selectedRestaurant?.id }
            }
        } catch (_: CancellationException) {
            return@LaunchedEffect
        } catch (throwable: Throwable) {
            restaurants = emptyList()
            selectedRestaurant = null
            alertMessage = throwable.message ?: "No se pudieron cargar los restaurantes cercanos"
        } finally {
            isPlacesLoading = false
        }
    }

    LaunchedEffect(selectedCategory, restaurants) {
        if (selectedRestaurant != null && filteredRestaurants.none { it.id == selectedRestaurant?.id }) {
            selectedRestaurant = null
            restaurantMapSelection = null
        }
    }

    Scaffold(
        topBar = {
            if (!isMapMaximized) {
                TopAppBar(
                    title = { Text("Cerca de ti") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (hasLocationPermission) {
                                    requestCurrentLocation(
                                        context = context,
                                        locationManager = locationManager,
                                        onLoadingChanged = { isLocationLoading = it },
                                        onLocationFound = { userLocation = it },
                                        onError = { alertMessage = it }
                                    )
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isMapMaximized) PaddingValues(0.dp) else padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isMapMaximized) {
                MapStageCard(
                    modifier = Modifier.fillMaxSize(),
                    isMaximized = true,
                    onToggleMaximize = { isMapMaximized = false },
                    userLocation = userLocation,
                    restaurants = filteredRestaurants,
                    totalResults = filteredRestaurants.size,
                    radiusLabel = formatRadiusLabel(selectedRadius),
                    selectedRestaurant = selectedRestaurant,
                    selectedRestaurantId = restaurantMapSelection?.restaurantId,
                    centerOnRestaurantId = restaurantMapSelection?.takeIf { it.centerMapOnSelection }?.restaurantId,
                    isLoading = isLocationLoading || isPlacesLoading,
                    onRestaurantSelected = { restaurant ->
                        selectedRestaurant = restaurant
                        restaurantMapSelection = RestaurantMapSelection(
                            restaurantId = restaurant.id,
                            centerMapOnSelection = false
                        )
                    },
                    onCenteredSelectionHandled = {
                        restaurantMapSelection = restaurantMapSelection?.copy(centerMapOnSelection = false)
                    },
                    onSelectOnMap = { restaurant ->
                        selectedRestaurant = restaurant
                        restaurantMapSelection = RestaurantMapSelection(
                            restaurantId = restaurant.id,
                            centerMapOnSelection = true
                        )
                    },
                    onOpenDirections = { restaurant ->
                        selectedRestaurant = restaurant
                        openDirections(
                            context = context,
                            restaurant = restaurant,
                            userLocation = userLocation
                        )
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!hasLocationPermission) {
                    DiscoveryControlPanel(
                        userLocation = userLocation,
                        selectedRadius = selectedRadius,
                        selectedCategory = selectedCategory,
                        totalResults = filteredRestaurants.size,
                        isLocationLoading = isLocationLoading,
                        availableCategories = availableCategories,
                        onRadiusSelected = { selectedRadius = it },
                        onCategorySelected = { selectedCategory = it },
                        onLocateMe = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )

                    PermissionCard(
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                } else {
                    DiscoveryControlPanel(
                        userLocation = userLocation,
                        selectedRadius = selectedRadius,
                        isLocationLoading = isLocationLoading,
                        selectedCategory = selectedCategory,
                        totalResults = filteredRestaurants.size,
                        availableCategories = availableCategories,
                        onRadiusSelected = { selectedRadius = it },
                        onCategorySelected = { selectedCategory = it },
                        onLocateMe = {
                            requestCurrentLocation(
                                context = context,
                                locationManager = locationManager,
                                onLoadingChanged = { isLocationLoading = it },
                                onLocationFound = { userLocation = it },
                                onError = { alertMessage = it }
                            )
                        }
                    )

                    MapStageCard(
                        modifier = Modifier.fillMaxWidth(),
                        isMaximized = false,
                        onToggleMaximize = { isMapMaximized = true },
                        userLocation = userLocation,
                        restaurants = filteredRestaurants,
                        totalResults = filteredRestaurants.size,
                        radiusLabel = formatRadiusLabel(selectedRadius),
                        selectedRestaurant = selectedRestaurant,
                        selectedRestaurantId = restaurantMapSelection?.restaurantId,
                        centerOnRestaurantId = restaurantMapSelection?.takeIf { it.centerMapOnSelection }?.restaurantId,
                        isLoading = isLocationLoading || isPlacesLoading,
                        onRestaurantSelected = { restaurant ->
                            selectedRestaurant = restaurant
                            restaurantMapSelection = RestaurantMapSelection(
                                restaurantId = restaurant.id,
                                centerMapOnSelection = false
                            )
                        },
                        onCenteredSelectionHandled = {
                            restaurantMapSelection = restaurantMapSelection?.copy(centerMapOnSelection = false)
                        },
                        onSelectOnMap = { restaurant ->
                            selectedRestaurant = restaurant
                            restaurantMapSelection = RestaurantMapSelection(
                                restaurantId = restaurant.id,
                                centerMapOnSelection = true
                            )
                        },
                        onOpenDirections = { restaurant ->
                            selectedRestaurant = restaurant
                            openDirections(
                                context = context,
                                restaurant = restaurant,
                                userLocation = userLocation
                            )
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        when {
                            isPlacesLoading && filteredRestaurants.isEmpty() -> {
                                LoadingCard(message = "Buscando restaurantes cerca de ti...")
                            }

                            filteredRestaurants.isEmpty() -> {
                                EmptyPlacesCard()
                            }

                            else -> {
                                NearbyBrowserPanel(
                                    featuredRestaurants = featuredRestaurants,
                                    selectedRestaurantId = selectedRestaurant?.id,
                                    totalCount = filteredRestaurants.size,
                                    onSelect = { restaurant ->
                                        selectedRestaurant = restaurant
                                        restaurantMapSelection = RestaurantMapSelection(
                                            restaurantId = restaurant.id,
                                            centerMapOnSelection = true
                                        )
                                    },
                                    onOpenDirections = { restaurant ->
                                        selectedRestaurant = restaurant
                                        openDirections(
                                            context = context,
                                            restaurant = restaurant,
                                            userLocation = userLocation
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } // Close Column
            } // Close else branch of isMapMaximized

            ModernTopToast(
                message = alertMessage,
                onDismiss = { alertMessage = null },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Activa tu ubicación",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Sin permiso de ubicación no podemos centrar el mapa ni encontrar locales cercanos de forma útil.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRequestPermission) {
                Text("Permitir ubicación")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryControlPanel(
    userLocation: GeoPoint?,
    selectedRadius: Int,
    isLocationLoading: Boolean,
    selectedCategory: String,
    totalResults: Int,
    availableCategories: List<String>,
    onRadiusSelected: (Int) -> Unit,
    onCategorySelected: (String) -> Unit,
    onLocateMe: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EXPLORA TU ZONA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "¿Dónde vamos hoy?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 32.sp
                    )
                    Text(
                        text = if (userLocation == null) {
                            "Activa tu ubicación para encontrar los mejores pintxos cerca de ti."
                        } else {
                            "Hemos encontrado $totalResults lugares fantásticos en un radio de ${formatRadiusLabel(selectedRadius)}."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                    shadowElevation = 4.dp
                ) {
                    if (isLocationLoading) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else {
                        IconButton(onClick = onLocateMe, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Actualizar",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Radius Selection
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Distancia máxima",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 3000).forEach { radius ->
                        val isSelected = selectedRadius == radius
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onRadiusSelected(radius) }
                        ) {
                            Text(
                                text = formatRadiusLabel(radius),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                RestaurantFilterRow(
                    categories = availableCategories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
            }
        }
    }
}

@Composable
private fun MapStageCard(
    modifier: Modifier = Modifier,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    userLocation: GeoPoint?,
    restaurants: List<NearbyRestaurant>,
    totalResults: Int,
    radiusLabel: String,
    selectedRestaurant: NearbyRestaurant?,
    selectedRestaurantId: String?,
    centerOnRestaurantId: String?,
    isLoading: Boolean,
    onRestaurantSelected: (NearbyRestaurant) -> Unit,
    onCenteredSelectionHandled: () -> Unit,
    onSelectOnMap: (NearbyRestaurant) -> Unit,
    onOpenDirections: (NearbyRestaurant) -> Unit
) {
    val context = LocalContext.current
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val mapSignature = remember(userLocation, restaurants, selectedRestaurantId, centerOnRestaurantId) {
        buildString {
            append(userLocation?.latitude)
            append('|')
            append(userLocation?.longitude)
            append('|')
            append(selectedRestaurantId)
            append('|')
            append(centerOnRestaurantId)
            append('|')
            restaurants.forEach { restaurant ->
                append(restaurant.id)
                append(':')
                append(restaurant.latitude)
                append(':')
                append(restaurant.longitude)
                append(';')
            }
        }
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (isMaximized) 0.dp else 32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .then(if (isMaximized) Modifier.systemBarsPadding() else Modifier)
                .padding(if (isMaximized) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TU MAPA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A un simple vistazo",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        lineHeight = 28.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$totalResults ${if (totalResults == 1) "lugar" else "lugares"}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.clickable(onClick = onToggleMaximize)
                ) {
                    Icon(
                        imageVector = if (isMaximized) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isMaximized) "Minimizar" else "Ampliar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
            }

            if (userLocation == null || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(340.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val mapView = remember {
                    Configuration.getInstance().userAgentValue = context.packageName
                    GestureAwareMapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.5)
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                    }
                }

                DisposableEffect(mapView) {
                    onDispose {
                        mapView.onDetach()
                    }
                }

                Card(
                    modifier = if (isMaximized) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(340.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { mapView },
                            update = { osmMap ->
                                if (osmMap.getTag() == mapSignature) {
                                    return@AndroidView
                                }

                                osmMap.setTag(mapSignature)
                                osmMap.overlays.clear()
                                val mapSelectedRestaurant = restaurants.firstOrNull { it.id == selectedRestaurantId }
                                val centeredRestaurant = restaurants.firstOrNull { it.id == centerOnRestaurantId }
                                val centerTarget = centeredRestaurant ?: mapSelectedRestaurant
                                osmMap.controller.setCenter(
                                    OsmGeoPoint(
                                        centerTarget?.latitude ?: userLocation.latitude,
                                        centerTarget?.longitude ?: userLocation.longitude
                                    )
                                )
                                if (centeredRestaurant != null) {
                                    osmMap.controller.animateTo(
                                        OsmGeoPoint(centeredRestaurant.latitude, centeredRestaurant.longitude)
                                    )
                                    onCenteredSelectionHandled()
                                }

                                val userHalo = Polygon().apply {
                                    points = Polygon.pointsAsCircle(
                                        OsmGeoPoint(userLocation.latitude, userLocation.longitude),
                                        85.0
                                    )
                                    fillColor = android.graphics.Color.argb(90, 211, 47, 47)
                                    strokeColor = android.graphics.Color.rgb(211, 47, 47)
                                    strokeWidth = 2f
                                    title = "Estás aquí"
                                }
                                osmMap.overlays.add(userHalo)

                                val userCore = Polygon().apply {
                                    points = Polygon.pointsAsCircle(
                                        OsmGeoPoint(userLocation.latitude, userLocation.longitude),
                                        22.0
                                    )
                                    fillColor = android.graphics.Color.argb(240, 211, 47, 47)
                                    strokeColor = android.graphics.Color.WHITE
                                    strokeWidth = 5f
                                    title = "Estás aquí"
                                }
                                osmMap.overlays.add(userCore)

                                restaurants.forEach { restaurant ->
                                    val marker = Marker(osmMap).apply {
                                        position = OsmGeoPoint(restaurant.latitude, restaurant.longitude)
                                        title = restaurant.name
                                        snippet = buildString {
                                            append(restaurant.category)
                                            if (restaurant.address.isNotBlank()) {
                                                append("\n")
                                                append(restaurant.address)
                                            }
                                            append("\n")
                                            append(formatDistance(restaurant.distanceMeters))
                                        }
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        icon = buildRestaurantMarkerDrawable(
                                            context = context,
                                            restaurant = restaurant,
                                            isSelected = restaurant.id == mapSelectedRestaurant?.id,
                                            primaryColor = primaryColorArgb
                                        )
                                        setOnMarkerClickListener { clickedMarker, _ ->
                                            clickedMarker.showInfoWindow()
                                            onRestaurantSelected(restaurant)
                                            true
                                        }
                                    }
                                    osmMap.overlays.add(marker)
                                }

                                osmMap.invalidate()
                            },
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "Zona: $radiusLabel",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isMaximized) {
                            selectedRestaurant?.let { restaurant ->
                                MiniSelectedRestaurantOverlay(
                                    restaurant = restaurant,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                                        .fillMaxWidth(),
                                    onSelectOnMap = { onSelectOnMap(restaurant) },
                                    onOpenDirections = { onOpenDirections(restaurant) }
                                )
                            }
                        }
                    }
                }

                if (!isMaximized) {
                    selectedRestaurant?.let { restaurant ->
                        MiniSelectedRestaurantOverlay(
                            restaurant = restaurant,
                            modifier = Modifier.fillMaxWidth(),
                            onSelectOnMap = { onSelectOnMap(restaurant) },
                            onOpenDirections = { onOpenDirections(restaurant) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(message)
        }
    }
}

@Composable
private fun EmptyPlacesCard() {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Nada cerca por ahora", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                text = "Prueba a cambiar el filtro, ampliar el radio o actualizar tu ubicación para buscar otra vez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RestaurantFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onCategorySelected(category) }
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MiniSelectedRestaurantOverlay(
    restaurant: NearbyRestaurant,
    modifier: Modifier = Modifier,
    onSelectOnMap: () -> Unit,
    onOpenDirections: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Visto en el mapa",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = restaurant.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${normalizeRestaurantCategory(restaurant.category)} · ${formatDistance(restaurant.distanceMeters)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onSelectOnMap,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Centrar", fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.Button(
                    onClick = onOpenDirections,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ruta", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FeaturedRestaurantCarousel(
    restaurants: List<NearbyRestaurant>,
    selectedRestaurantId: String?,
    onSelect: (NearbyRestaurant) -> Unit,
    onOpenDirections: (NearbyRestaurant) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp) // Added padding so shadows aren't cut off
    ) {
        items(restaurants) { restaurant ->
            FeaturedRestaurantCard(
                restaurant = restaurant,
                isSelected = selectedRestaurantId == restaurant.id,
                onSelect = { onSelect(restaurant) },
                onOpenDirections = { onOpenDirections(restaurant) }
            )
        }
    }
}

@Composable
private fun NearbyBrowserPanel(
    featuredRestaurants: List<NearbyRestaurant>,
    selectedRestaurantId: String?,
    totalCount: Int,
    onSelect: (NearbyRestaurant) -> Unit,
    onOpenDirections: (NearbyRestaurant) -> Unit
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DESTACADOS",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Los mejores de la zona",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    text = "Desliza para ver más opciones en tus cercanías.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (featuredRestaurants.isNotEmpty()) {
                FeaturedRestaurantCarousel(
                    restaurants = featuredRestaurants,
                    selectedRestaurantId = selectedRestaurantId,
                    onSelect = onSelect,
                    onOpenDirections = onOpenDirections
                )
            }
        }
    }
}

@Composable
private fun FeaturedRestaurantCard(
    restaurant: NearbyRestaurant,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenDirections: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 12.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = normalizeRestaurantCategory(restaurant.category).uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = formatDistance(restaurant.distanceMeters),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = restaurant.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = restaurant.address.ifBlank { "Un rincón para descubrir en tu zona" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isSelected) {
                    FilledTonalButton(
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ver", fontWeight = FontWeight.Bold)
                    }
                }
                androidx.compose.material3.Button(
                    onClick = onOpenDirections,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ir ahora", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SwipeHintPill() {
    val transition = rememberInfiniteTransition(label = "swipeHint")
    val alpha = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintAlpha"
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    ) {
        Text(
            text = "Desliza ->",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha.value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SelectedRestaurantCard(
    restaurant: NearbyRestaurant,
    onSelectOnMap: () -> Unit,
    onOpenDirections: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tu elección ahora mismo",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        text = restaurant.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = restaurant.address.ifBlank { restaurant.category },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = formatDistance(restaurant.distanceMeters),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(text = normalizeRestaurantCategory(restaurant.category))
                SummaryPill(text = "Seleccionado")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onOpenDirections) {
                    Text("Abrir ruta")
                }
                TextButton(onClick = onSelectOnMap) {
                    Text("Centrar mapa")
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun buildRestaurantMarkerDrawable(
    context: Context,
    restaurant: NearbyRestaurant,
    isSelected: Boolean,
    primaryColor: Int
): android.graphics.drawable.Drawable {
    val markerSizePx = if (isSelected) dpToPx(context, 44) else dpToPx(context, 36)
    val strokeWidthPx = if (isSelected) dpToPx(context, 6) else dpToPx(context, 3)

    val background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setSize(markerSizePx, markerSizePx)
        setColor(if (isSelected) primaryColor else android.graphics.Color.WHITE)
        setStroke(strokeWidthPx, primaryColor)
    }

    val iconResId = when (normalizeRestaurantCategory(restaurant.category)) {
        "Restaurante", "Restaurantes" -> com.example.pintxomatch.R.drawable.ic_map_restaurant
        "Bar", "Bares" -> com.example.pintxomatch.R.drawable.ic_map_bar
        "Pub", "Pubs" -> com.example.pintxomatch.R.drawable.ic_map_pub
        "Cafetería", "Cafeterías" -> com.example.pintxomatch.R.drawable.ic_map_cafe
        "Comida Rápida" -> com.example.pintxomatch.R.drawable.ic_map_fastfood
        else -> null
    }

    if (iconResId != null) {
        val iconDrawable = ContextCompat.getDrawable(context, iconResId)?.mutate()
        if (iconDrawable != null) {
            iconDrawable.setTint(if (isSelected) android.graphics.Color.WHITE else primaryColor)
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(background, iconDrawable))
            val padding = if (isSelected) dpToPx(context, 10) else dpToPx(context, 8)
            layerDrawable.setLayerInset(1, padding, padding, padding, padding)
            return layerDrawable
        }
    }

    return background
}

private fun openDirections(
    context: Context,
    restaurant: NearbyRestaurant,
    userLocation: GeoPoint?
) {
    val destination = "${restaurant.latitude},${restaurant.longitude}"
    val origin = userLocation?.let { "${it.latitude},${it.longitude}" }

    val googleMapsUri = Uri.parse(
        buildString {
            append("google.navigation:q=")
            append(destination)
            append("&mode=w")
        }
    )

    val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri).apply {
        `package` = "com.google.android.apps.maps"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val browserUri = Uri.parse(
        buildString {
            append("https://www.google.com/maps/dir/?api=1")
            if (origin != null) {
                append("&origin=")
                append(Uri.encode(origin))
            }
            append("&destination=")
            append(Uri.encode(destination))
            append("&travelmode=walking")
        }
    )

    val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleMapsIntent)
        } else {
            context.startActivity(browserIntent)
        }
    } catch (_: ActivityNotFoundException) {
    }
}

private fun dpToPx(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density).roundToInt()
}

private fun formatRadiusLabel(radiusMeters: Int): String {
    return if (radiusMeters >= 1000) {
        String.format(Locale.US, "%.0f km", radiusMeters / 1000f)
    } else {
        "$radiusMeters m"
    }
}

private fun normalizeRestaurantCategory(category: String): String {
    val normalized = category.trim().replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    }

    return if (normalized.isBlank()) "Otros" else normalized
}

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    onLoadingChanged: (Boolean) -> Unit,
    onLocationFound: (GeoPoint) -> Unit,
    onError: (String) -> Unit
) {
    if (!context.hasLocationPermission()) {
        onError("Activa el permiso de ubicación")
        return
    }

    onLoadingChanged(true)
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    val lastKnownLocation = providers
        .filter { provider -> locationManager.isProviderEnabled(provider) }
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { location -> location.time }

    if (lastKnownLocation != null) {
        onLoadingChanged(false)
        onLocationFound(GeoPoint(lastKnownLocation.latitude, lastKnownLocation.longitude))
        return
    }

    val enabledProvider = providers.firstOrNull { provider -> locationManager.isProviderEnabled(provider) }
    if (enabledProvider == null) {
        onLoadingChanged(false)
        onError("Activa el GPS o la ubicación de red para buscar sitios cercanos")
        return
    }

    val cancellationSignal = CancellationSignal()
    LocationManagerCompat.getCurrentLocation(
        locationManager,
        enabledProvider,
        cancellationSignal,
        ContextCompat.getMainExecutor(context)
    ) { currentLocation: Location? ->
        onLoadingChanged(false)
        if (currentLocation != null) {
            onLocationFound(GeoPoint(currentLocation.latitude, currentLocation.longitude))
        } else {
            onError("No pudimos determinar tu ubicación actual")
        }
    }
}

private suspend fun fetchNearbyRestaurants(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int
): List<NearbyRestaurant> = withContext(Dispatchers.IO) {

    // API Key de Geoapify (Obtenida satisfactoriamente)
    val apiKey = "7e67ac55037f48288c5074eed9d3a424" 

    val categories = "catering.restaurant,catering.cafe,catering.bar,catering.pub"
    val filter = "circle:$longitude,$latitude,$radiusMeters"
    val url = "https://api.geoapify.com/v2/places?categories=$categories&filter=$filter&limit=25&apiKey=$apiKey"

    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 5000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "FoodViewX-App/1.0 (contacto@foodviewx.com) PintxoMatch")
    }

    try {
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            throw NearbyRestaurantsException("Error $statusCode de Geoapify: El servicio rechazó la consulta.")
        }

        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw NearbyRestaurantsException("Respuesta ilegible del servidor de lugares.")
        }

        val features = json.getJSONArray("features")
        val results = mutableListOf<NearbyRestaurant>()

        for (index in 0 until features.length()) {
            val feature = features.getJSONObject(index)
            val properties = feature.optJSONObject("properties") ?: continue
            
            val name = properties.optString("name")
            if (name.isBlank()) continue

            val lat = properties.optDouble("lat", Double.NaN)
            val lon = properties.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            // Calculamos la distancia exacta desde el móvil garantizando precisión
            val distanceArray = FloatArray(1)
            Location.distanceBetween(latitude, longitude, lat, lon, distanceArray)
            val computedDistance = distanceArray[0].toInt()

            val address = properties.optString("address_line2", properties.optString("street"))
            
            // Mapeo fácil de categorías a español analizando toda la lista
            val categoriesArray = properties.optJSONArray("categories")
            val categoriesStr = categoriesArray?.toString() ?: ""
            val categoryLabel = when {
                categoriesStr.contains("pub", ignoreCase = true) -> "Pub"
                categoriesStr.contains("bar", ignoreCase = true) -> "Bar"
                categoriesStr.contains("cafe", ignoreCase = true) -> "Cafetería"
                categoriesStr.contains("restaurant", ignoreCase = true) -> "Restaurante"
                else -> "Restaurante"
            }

            results.add(
                NearbyRestaurant(
                    id = properties.optString("place_id", "geoapify_$index"),
                    name = name,
                    category = categoryLabel,
                    address = address,
                    latitude = lat,
                    longitude = lon,
                    distanceMeters = computedDistance
                )
            )
        }

        results
            .distinctBy { it.id }
            .sortedBy { it.distanceMeters }

    } catch (exception: Exception) {
        val msg = exception.message.orEmpty()
        if (exception is java.net.SocketTimeoutException) {
            throw NearbyRestaurantsException("El nuevo servidor de mapas está tardando también. Revisa tu conexión.")
        } else {
            throw NearbyRestaurantsException(exception.message ?: "Error al contactar con la API de lugares rápidos.")
        }
    } finally {
        connection.disconnect()
    }
}

private fun formatDistance(distanceMeters: Int): String {
    return if (distanceMeters >= 1000) {
        String.format(Locale.US, "%.1f km", distanceMeters / 1000f)
    } else {
        "$distanceMeters m"
    }
}

private fun formatCoordinate(value: Double): String {
    return String.format(Locale.US, "%.4f", value)
}

private fun buildNearbyCacheKey(
    userLocation: GeoPoint,
    radiusMeters: Int
): String {
    val latitudeBucket = (userLocation.latitude * 1000).roundToInt()
    val longitudeBucket = (userLocation.longitude * 1000).roundToInt()
    return "$radiusMeters:$latitudeBucket:$longitudeBucket"
}

