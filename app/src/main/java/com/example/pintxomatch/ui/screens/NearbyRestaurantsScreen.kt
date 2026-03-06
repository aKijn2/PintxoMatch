package com.example.pintxomatch.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

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

private class NearbyRestaurantsException(message: String) : IOException(message)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyRestaurantsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedRadius by remember { mutableIntStateOf(10000) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var restaurants by remember { mutableStateOf<List<NearbyRestaurant>>(emptyList()) }
    var isLocationLoading by remember { mutableStateOf(false) }
    var isPlacesLoading by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

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
        isPlacesLoading = true
        runCatching {
            val primaryResults = fetchNearbyRestaurants(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                radiusMeters = selectedRadius
            )
            if (primaryResults.isNotEmpty() || selectedRadius >= 25000) {
                primaryResults
            } else {
                fetchNearbyRestaurants(
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    radiusMeters = 25000
                )
            }
        }.onSuccess { fetched ->
            restaurants = fetched
            if (fetched.isNotEmpty() && selectedRadius < 25000 && fetched.none { it.distanceMeters <= selectedRadius }) {
                alertMessage = "No había sitios en tu radio inicial. Ampliamos la búsqueda hasta 25 km."
            }
        }.onFailure { throwable ->
            restaurants = emptyList()
            alertMessage = throwable.message ?: "No se pudieron cargar los restaurantes cercanos"
        }
        isPlacesLoading = false
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
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
                                listOf(1000, 5000, 10000, 25000).forEach { radius ->
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
                }

                if (!hasLocationPermission) {
                    item {
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
                    }
                } else {
                    item {
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
                    }

                    item {
                        MapSectionCard(
                            userLocation = userLocation,
                            restaurants = restaurants,
                            isLoading = isLocationLoading || isPlacesLoading
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Locales cercanos",
                            subtitle = "Ordenados por distancia desde tu ubicación"
                        )
                    }

                    if (isPlacesLoading && restaurants.isEmpty()) {
                        item {
                            LoadingCard(message = "Buscando restaurantes cerca de ti...")
                        }
                    } else if (restaurants.isEmpty()) {
                        item {
                            EmptyPlacesCard()
                        }
                    } else {
                        items(restaurants) { restaurant ->
                            NearbyRestaurantCard(restaurant = restaurant)
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
                modifier = Modifier.weight(1f),
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
    isLoading: Boolean
) {
    val context = LocalContext.current
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
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.5)
                    }
                }

                DisposableEffect(mapView) {
                    onDispose {
                        mapView.onDetach()
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    factory = { mapView },
                    update = { osmMap ->
                        osmMap.overlays.clear()
                        osmMap.controller.setCenter(
                            OsmGeoPoint(userLocation.latitude, userLocation.longitude)
                        )

                        val userMarker = Marker(osmMap).apply {
                            position = OsmGeoPoint(userLocation.latitude, userLocation.longitude)
                            title = "Estás aquí"
                            snippet = "Tu ubicación actual"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        osmMap.overlays.add(userMarker)

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
                text = "Prueba a ampliar el radio o actualiza tu ubicación para buscar otra vez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NearbyRestaurantCard(restaurant: NearbyRestaurant) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
                        fontSize = 17.sp
                    )
                    Text(
                        text = restaurant.category,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (restaurant.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = restaurant.address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = formatDistance(restaurant.distanceMeters),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
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
        [out:json][timeout:25];
        (
                    node["amenity"~"restaurant|bar|pub|cafe"](around:$radiusMeters,$latitude,$longitude);
                    way["amenity"~"restaurant|bar|pub|cafe"](around:$radiusMeters,$latitude,$longitude);
                    relation["amenity"~"restaurant|bar|pub|cafe"](around:$radiusMeters,$latitude,$longitude);
        );
        out center;
    """.trimIndent()

    val response = requestOverpass(query)
    val json = JSONObject(response)
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
                connectTimeout = 15000
                readTimeout = 20000
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
                else -> "No pudimos consultar restaurantes cercanos ahora mismo."
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

