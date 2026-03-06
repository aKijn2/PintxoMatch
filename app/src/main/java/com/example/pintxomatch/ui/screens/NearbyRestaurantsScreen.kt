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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.pintxomatch.ui.components.AppSnackbarHost
import kotlinx.coroutines.Dispatchers
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
    val snackbarHostState = remember { SnackbarHostState() }
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
    var showAllRestaurants by remember { mutableStateOf(false) }

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
    val visibleRestaurants = remember(filteredRestaurants, showAllRestaurants) {
        if (showAllRestaurants) filteredRestaurants else filteredRestaurants.take(6)
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
            snackbarHostState.showSnackbar(it)
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
        runCatching {
            fetchNearbyRestaurants(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                radiusMeters = selectedRadius
            )
        }.onSuccess { fetched ->
            restaurants = fetched
            nearbyResultsCache[cacheKey] = fetched
            if (selectedRestaurant != null) {
                selectedRestaurant = fetched.firstOrNull { it.id == selectedRestaurant?.id }
            }
        }.onFailure { throwable ->
            restaurants = emptyList()
            selectedRestaurant = null
            alertMessage = throwable.message ?: "No se pudieron cargar los restaurantes cercanos"
        }
        isPlacesLoading = false
    }

    LaunchedEffect(selectedCategory, restaurants) {
        showAllRestaurants = false
        if (selectedRestaurant != null && filteredRestaurants.none { it.id == selectedRestaurant?.id }) {
            selectedRestaurant = null
            restaurantMapSelection = null
        }
    }

    Scaffold(
        topBar = {
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Descubre dónde seguir el plan",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Usamos tu ubicación para enseñarte restaurantes, bares y cafeterías cercanas directamente sobre el mapa.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500, 1000, 3000).forEach { radius ->
                                FilterChip(
                                    selected = selectedRadius == radius,
                                    onClick = { selectedRadius = radius },
                                    label = {
                                        Text(
                                            if (radius >= 1000) {
                                                String.format(Locale.US, "%.0f km", radius / 1000f)
                                            } else {
                                                "${radius} m"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (!hasLocationPermission) {
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
                    LocationSummaryCard(
                        userLocation = userLocation,
                        radiusMeters = selectedRadius,
                        isLocationLoading = isLocationLoading,
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

                    MapSectionCard(
                        userLocation = userLocation,
                        restaurants = filteredRestaurants,
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
                        }
                    )

                    selectedRestaurant?.let { restaurant ->
                        SelectedRestaurantCard(
                            restaurant = restaurant,
                            onOpenDirections = {
                                openDirections(
                                    context = context,
                                    restaurant = restaurant,
                                    userLocation = userLocation
                                )
                            }
                        )
                    }

                    SectionHeader(
                        title = "Locales cercanos",
                        subtitle = if (selectedCategory == "Todos") {
                            "Ordenados por distancia desde tu ubicación"
                        } else {
                            "Filtrados por $selectedCategory"
                        }
                    )

                    RestaurantFilterRow(
                        categories = availableCategories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )

                    if (filteredRestaurants.isNotEmpty()) {
                        ResultSummaryCard(
                            visibleCount = visibleRestaurants.size,
                            totalCount = filteredRestaurants.size,
                            isExpanded = showAllRestaurants,
                            onToggleExpanded = {
                                showAllRestaurants = !showAllRestaurants
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        when {
                            isPlacesLoading && filteredRestaurants.isEmpty() -> {
                                LoadingCard(message = "Buscando restaurantes cerca de ti...")
                            }

                            filteredRestaurants.isEmpty() -> {
                                EmptyPlacesCard()
                            }

                            else -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    visibleRestaurants.forEach { restaurant ->
                                        NearbyRestaurantCard(
                                            restaurant = restaurant,
                                            isSelected = selectedRestaurant?.id == restaurant.id,
                                            onSelect = {
                                                selectedRestaurant = restaurant
                                                restaurantMapSelection = RestaurantMapSelection(
                                                    restaurantId = restaurant.id,
                                                    centerMapOnSelection = true
                                                )
                                            },
                                            onOpenDirections = {
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
                    }
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
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

@Composable
private fun LocationSummaryCard(
    userLocation: GeoPoint?,
    radiusMeters: Int,
    isLocationLoading: Boolean,
    onLocateMe: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = if (userLocation == null) "Buscando ubicación" else "Ubicación detectada",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (userLocation == null) {
                            "Radio activo: ${radiusMeters} m"
                        } else {
                            "${formatCoordinate(userLocation.latitude)}, ${formatCoordinate(userLocation.longitude)} · ${radiusMeters} m"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (isLocationLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else {
                IconButton(onClick = onLocateMe) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Actualizar ubicación")
                }
            }
        }
    }
}

@Composable
private fun MapSectionCard(
    userLocation: GeoPoint?,
    restaurants: List<NearbyRestaurant>,
    selectedRestaurantId: String?,
    centerOnRestaurantId: String?,
    isLoading: Boolean,
    onRestaurantSelected: (NearbyRestaurant) -> Unit,
    onCenteredSelectionHandled: () -> Unit
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Mapa de la zona",
                subtitle = "Tu posición y los sitios cercanos en una sola vista"
            )

            if (userLocation == null || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { mapView },
                        update = { osmMap ->
                            if (osmMap.getTag() == mapSignature) {
                                return@AndroidView
                            }

                            osmMap.setTag(mapSignature)
                            osmMap.overlays.clear()
                            val selectedRestaurant = restaurants.firstOrNull { it.id == selectedRestaurantId }
                            val centeredRestaurant = restaurants.firstOrNull { it.id == centerOnRestaurantId }
                            val centerTarget = centeredRestaurant ?: selectedRestaurant
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
                                        isSelected = restaurant.id == selectedRestaurant?.id,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestaurantFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun ResultSummaryCard(
    visibleCount: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isExpanded || totalCount <= visibleCount) {
                    "$totalCount locales visibles"
                } else {
                    "Mostrando $visibleCount de $totalCount"
                },
                fontWeight = FontWeight.SemiBold
            )

            if (totalCount > 6) {
                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Ver menos" else "Ver todos")
                }
            }
        }
    }
}

@Composable
private fun SelectedRestaurantCard(
    restaurant: NearbyRestaurant,
    onOpenDirections: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                CategoryChip(text = restaurant.category)
                Text(
                    text = "Restaurante seleccionado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = onOpenDirections) {
                Text("Como llegar en Maps")
            }
        }
    }
}

@Composable
private fun NearbyRestaurantCard(
    restaurant: NearbyRestaurant,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenDirections: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.76f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            text = restaurant.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        CategoryChip(text = restaurant.category)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = formatDistance(restaurant.distanceMeters),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (restaurant.address.isNotBlank()) {
                Text(
                    text = restaurant.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSelect) {
                    Text(if (isSelected) "Seleccionado" else "Ver en el mapa")
                }

                TextButton(onClick = onOpenDirections) {
                    Text("Como llegar")
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
    isSelected: Boolean,
    primaryColor: Int
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        val markerSizePx = if (isSelected) dpToPx(context, 44) else dpToPx(context, 36)
        val strokeWidthPx = if (isSelected) dpToPx(context, 6) else dpToPx(context, 3)
        setSize(
            markerSizePx,
            markerSizePx
        )
        setColor(if (isSelected) primaryColor else android.graphics.Color.WHITE)
        setStroke(strokeWidthPx, primaryColor)
    }
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
    val query = """
        [out:json][timeout:12];
        nwr["amenity"~"restaurant|bar|pub|cafe"]["name"](around:$radiusMeters,$latitude,$longitude);
        out tags center qt;
    """.trimIndent()

    val response = requestOverpass(query)
    val normalizedResponse = response.trimStart()
    if (!normalizedResponse.startsWith("{")) {
        val preview = normalizedResponse.take(120).replace('\n', ' ')
        throw NearbyRestaurantsException(
            if (preview.isBlank()) {
                "El servicio de restaurantes devolvió una respuesta vacía"
            } else {
                "El servicio de restaurantes devolvió una respuesta no válida: $preview"
            }
        )
    }

    val json = try {
        JSONObject(normalizedResponse)
    } catch (exception: JSONException) {
        throw NearbyRestaurantsException("No pudimos interpretar la respuesta del servicio de restaurantes")
    }
    val elements = json.getJSONArray("elements")
    val results = mutableListOf<NearbyRestaurant>()

    for (index in 0 until elements.length()) {
        val element = elements.getJSONObject(index)
        val tags = element.optJSONObject("tags") ?: continue
        val name = tags.optString("name")
        if (name.isBlank()) continue
        val lat = when {
            element.has("lat") -> element.optDouble("lat")
            element.has("center") -> element.getJSONObject("center").optDouble("lat")
            else -> Double.NaN
        }
        val lon = when {
            element.has("lon") -> element.optDouble("lon")
            element.has("center") -> element.getJSONObject("center").optDouble("lon")
            else -> Double.NaN
        }

        if (lat.isNaN() || lon.isNaN()) continue

        val distance = FloatArray(1)
        Location.distanceBetween(latitude, longitude, lat, lon, distance)
        val addressParts = listOf(
            tags.optString("addr:street"),
            tags.optString("addr:housenumber")
        ).filter { it.isNotBlank() }

        val category = when (tags.optString("amenity")) {
            "restaurant" -> "Restaurante"
            "bar" -> "Bar"
            "pub" -> "Pub"
            "cafe" -> "Cafetería"
            else -> "Local"
        }

        results.add(
            NearbyRestaurant(
                id = "${element.optString("type")}_${element.optLong("id")}",
                name = name,
                category = category,
                address = addressParts.joinToString(" "),
                latitude = lat,
                longitude = lon,
                distanceMeters = distance[0].toInt()
            )
        )
    }

    results
        .distinctBy { it.id }
        .sortedBy { it.distanceMeters }
        .take(20)
}

private fun requestOverpass(query: String): String {
    val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    var lastError: String? = null
    val requestBody = "data=${URLEncoder.encode(query, Charsets.UTF_8.name())}"

    for (endpoint in endpoints) {
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PintxoMatch/1.0")
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (statusCode in 200..299 && body.isNotBlank()) {
                return body
            }

            lastError = when (statusCode) {
                429 -> "El servicio de mapas está saturado ahora mismo. Prueba otra vez en unos segundos."
                in 500..599 -> "El servicio de restaurantes cercanos no responde bien en este momento."
                else -> {
                    val preview = body.trim().take(120).replace('\n', ' ')
                    if (preview.isBlank()) {
                        "No pudimos consultar restaurantes cercanos ahora mismo."
                    } else {
                        "Respuesta no válida del servicio de restaurantes: $preview"
                    }
                }
            }
        } catch (exception: Exception) {
            lastError = exception.message ?: "Fallo de red consultando locales cercanos"
        }
    }

    throw NearbyRestaurantsException(lastError ?: "No pudimos consultar restaurantes cercanos ahora mismo")
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

