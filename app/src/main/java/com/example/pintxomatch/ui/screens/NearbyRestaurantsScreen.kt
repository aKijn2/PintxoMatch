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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.78f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SIGUE LA RONDA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Encuentra un sitio que apetezca de verdad",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = if (userLocation == null) {
                            "Activa o refresca tu ubicación para ver sitios cercanos bien ordenados sobre el mapa."
                        } else {
                            "Tienes $totalResults sitios en ${formatRadiusLabel(selectedRadius)} y el filtro actual es $selectedCategory."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isLocationLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    IconButton(onClick = onLocateMe) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Actualizar ubicación")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill(text = if (userLocation == null) "Ubicación pendiente" else "Ubicación lista")
                SummaryPill(text = formatRadiusLabel(selectedRadius))
                if (totalResults > 0) {
                    SummaryPill(text = "$totalResults sitios")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500, 1000, 3000).forEach { radius ->
                    FilterChip(
                        selected = selectedRadius == radius,
                        onClick = { onRadiusSelected(radius) },
                        label = { Text(formatRadiusLabel(radius)) }
                    )
                }
            }

            RestaurantFilterRow(
                categories = availableCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected
            )
        }
    }
}

@Composable
private fun MapStageCard(
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Mapa activo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Mira la zona y decide sin salir de la vista principal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill(text = radiusLabel)
                    SummaryPill(text = "$totalResults sitios")
                }
            }

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
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryPill(text = "Tu zona")
                            if (selectedRestaurant != null) {
                                SummaryPill(text = "1 elegido")
                            }
                        }
                    }
                }

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Seleccionado",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = restaurant.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${normalizeRestaurantCategory(restaurant.category)} · ${formatDistance(restaurant.distanceMeters)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onSelectOnMap) {
                    Text("Centrar")
                }
                FilledTonalButton(onClick = onOpenDirections) {
                    Text("Ruta")
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        RoundedCornerShape(999.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Destacados cerca de ti",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Desliza las tarjetas para descubrir más opciones sin perder el foco.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill(text = "$totalCount sitios")
                SwipeHintPill()
            }

            if (featuredRestaurants.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            .width(232.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(text = normalizeRestaurantCategory(restaurant.category))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = formatDistance(restaurant.distanceMeters),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = restaurant.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = restaurant.address.ifBlank { "Selecciónalo para verlo mejor en el mapa y abrir la ruta" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSelect) {
                    Text(if (isSelected) "En mapa" else "Ver")
                }
                TextButton(onClick = onOpenDirections) {
                    Text("Ir")
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

