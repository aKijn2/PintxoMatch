package com.example.pintxomatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.navigation.AppNavigation
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.components.PintxoCard
import com.example.pintxomatch.ui.theme.PintxoMatchTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            PintxoMatchTheme(darkTheme = false) {
                AppNavigation()
            }
        }
    }
}

private data class RatingUpdateResult(
    val averageRating: Double,
    val ratingCount: Int,
    val userRating: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSwipeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToChatList: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToNearby: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val realtimeDb = com.google.firebase.database.FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
    var pintxosFirebase by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val seenIds = remember { mutableStateSetOf<String>() }
    var waitingPintxoId by remember { mutableStateOf<String?>(null) }
    var waitingSecondsLeft by remember { mutableStateOf(0) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val firestore = FirebaseFirestore.getInstance()

    fun notify(message: String) {
        alertMessage = message
    }

    fun extractRatings(snapshot: DocumentSnapshot): Map<String, Int> {
        val rawRatings = snapshot.get("ratings") as? Map<*, *> ?: return emptyMap()
        return rawRatings.entries.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val rating = (value as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
            uid to rating
        }.toMap()
    }

    fun mapDocumentToPintxo(snapshot: DocumentSnapshot, currentUid: String?): Pintxo {
        val ratings = extractRatings(snapshot)
        val ratingCount = snapshot.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: ratings.size
        val ratingTotal = snapshot.getDouble("ratingTotal") ?: ratings.values.sumOf { it.toDouble() }
        val averageRating = if (ratingCount > 0) {
            (snapshot.getDouble("averageRating") ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)
        } else {
            0.0
        }

        return Pintxo(
            id = snapshot.id,
            name = snapshot.getString("nombre") ?: "Sin nombre",
            barName = snapshot.getString("bar") ?: "Bar desconocido",
            location = snapshot.getString("ubicacion") ?: "Gipuzkoa",
            price = snapshot.getDouble("precio") ?: 0.0,
            imageUrl = snapshot.getString("imageUrl") ?: "",
            averageRating = averageRating,
            ratingCount = ratingCount,
            userRating = currentUid?.let { ratings[it] } ?: 0
        )
    }

    fun reloadPintxos() {
        isLoading = true
        val currentUid = auth.currentUser?.uid
        firestore.collection("Pintxos")
            .orderBy(FieldPath.documentId())
            .get()
            .addOnSuccessListener { result ->
                val listaNueva = result.map { doc -> mapDocumentToPintxo(doc, currentUid) }
                    .filter { it.id !in seenIds }
                pintxosFirebase = listaNueva
                isLoading = false
                isRefreshing = false
            }
            .addOnFailureListener {
                isLoading = false
                isRefreshing = false
                notify("Error cargando pintxos")
            }
    }

    fun submitRating(pintxo: Pintxo, stars: Int) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            notify("Inicia sesión para valorar")
            return
        }

        val newRating = stars.coerceIn(1, 5)
        val docRef = firestore.collection("Pintxos").document(pintxo.id)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val existingRatings = extractRatings(snapshot)
            val previousRating = existingRatings[uid] ?: 0
            val baseCount = snapshot.getLong("ratingCount")?.toInt()?.coerceAtLeast(0)
                ?: existingRatings.size
            val baseTotal = snapshot.getDouble("ratingTotal")
                ?: existingRatings.values.sumOf { it.toDouble() }

            val ratingCount = if (previousRating == 0) baseCount + 1 else baseCount
            val ratingTotal = if (previousRating == 0) {
                baseTotal + newRating.toDouble()
            } else {
                baseTotal - previousRating.toDouble() + newRating.toDouble()
            }
            val averageRating = if (ratingCount > 0) ratingTotal / ratingCount else 0.0

            transaction.update(
                docRef,
                mapOf(
                    "ratings.$uid" to newRating,
                    "ratingCount" to ratingCount,
                    "ratingTotal" to ratingTotal,
                    "averageRating" to averageRating
                )
            )

            RatingUpdateResult(
                averageRating = averageRating,
                ratingCount = ratingCount,
                userRating = newRating
            )
        }.addOnSuccessListener { updatedRating ->
            pintxosFirebase = pintxosFirebase.map { currentPintxo ->
                if (currentPintxo.id == pintxo.id) {
                    currentPintxo.copy(
                        averageRating = updatedRating.averageRating,
                        ratingCount = updatedRating.ratingCount,
                        userRating = updatedRating.userRating
                    )
                } else {
                    currentPintxo
                }
            }
            notify("Valoración guardada")
        }.addOnFailureListener {
            notify("No se pudo guardar la valoración")
        }
    }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    DisposableEffect(waitingPintxoId, auth.currentUser?.uid) {
        val currentUid = auth.currentUser?.uid
        val targetPintxoId = waitingPintxoId

        if (currentUid.isNullOrBlank() || targetPintxoId.isNullOrBlank()) {
            onDispose { }
        } else {
            val query = realtimeDb.getReference("chats")
                .orderByChild("pintxoId")
                .equalTo(targetPintxoId)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val myExistingChat = snapshot.children
                        .filter { chat ->
                            chat.child("participants").child(currentUid).getValue(Boolean::class.java) == true
                        }
                        .maxByOrNull { chat -> chat.child("updatedAt").getValue(Long::class.java) ?: 0L }

                    val chatId = myExistingChat?.key
                    if (!chatId.isNullOrBlank()) {
                        waitingPintxoId = null
                        onNavigateToChat(chatId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            query.addValueEventListener(listener)
            onDispose { query.removeEventListener(listener) }
        }
    }

    LaunchedEffect(waitingPintxoId) {
        val targetPintxoId = waitingPintxoId
        if (targetPintxoId.isNullOrBlank()) {
            waitingSecondsLeft = 0
            return@LaunchedEffect
        }

        waitingSecondsLeft = 10
        repeat(10) {
            delay(1000)
            if (waitingPintxoId != targetPintxoId) return@LaunchedEffect
            waitingSecondsLeft = (waitingSecondsLeft - 1).coerceAtLeast(0)
        }

        if (waitingPintxoId == targetPintxoId) {
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                realtimeDb.getReference("waitingByPintxo")
                    .child(targetPintxoId)
                    .child(uid)
                    .removeValue()
            }
            waitingPintxoId = null
            waitingSecondsLeft = 0
            notify("Tiempo agotado. Te sacamos de la lista de espera.")
            reloadPintxos()
        }
    }

    fun currentDisplayName(): String {
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Usuario"
    }

    fun startWaitingTransaction(
        waitingRef: com.google.firebase.database.DatabaseReference,
        chatsRef: com.google.firebase.database.DatabaseReference,
        uid: String,
        myName: String,
        pintxo: Pintxo,
        onNavigateToChat: (String) -> Unit
    ) {
        var matchedUid: String? = null
        var matchedName: String? = null
        var shouldWait = false

        waitingRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val allWaiters = currentData.children.toList()
                val candidate = allWaiters.firstOrNull { it.key != uid }

                if (candidate != null) {
                    matchedUid = candidate.key
                    matchedName = candidate.child("displayName").getValue(String::class.java) ?: "Usuario"
                    currentData.child(uid).value = null
                    currentData.child(candidate.key!!).value = null
                    shouldWait = false
                } else {
                    currentData.child(uid).child("displayName").value = myName
                    currentData.child(uid).child("timestamp").value = System.currentTimeMillis()
                    shouldWait = true
                }

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: com.google.firebase.database.DataSnapshot?
            ) {
                if (error != null || !committed) {
                    notify("No se pudo crear el match")
                    return
                }

                val otherUid = matchedUid
                if (otherUid == null || shouldWait) {
                    chatsRef.orderByChild("pintxoId").equalTo(pintxo.id).get()
                        .addOnSuccessListener { checkSnapshot ->
                            val myExistingChat = checkSnapshot.children
                                .filter { chat ->
                                    chat.child("participants").child(uid).getValue(Boolean::class.java) == true
                                }
                                .maxByOrNull { chat -> chat.child("updatedAt").getValue(Long::class.java) ?: 0L }

                            val myExistingChatId = myExistingChat?.key
                            if (!myExistingChatId.isNullOrBlank()) {
                                waitingPintxoId = null
                                waitingSecondsLeft = 0
                                onNavigateToChat(myExistingChatId)
                            } else {
                                waitingPintxoId = pintxo.id
                                notify("Esperando pareja para ${pintxo.name}")
                            }
                        }
                        .addOnFailureListener {
                            waitingPintxoId = pintxo.id
                            notify("Esperando pareja para ${pintxo.name}")
                        }
                    return
                }

                val otherName = matchedName ?: "Usuario"
                val pair = listOf(uid, otherUid).sorted()
                val chatId = "${pintxo.id}_${pair[0]}_${pair[1]}"

                val updates = mapOf<String, Any>(
                    "participants/$uid" to true,
                    "participants/$otherUid" to true,
                    "participantNames/$uid" to myName,
                    "participantNames/$otherUid" to otherName,
                    "pintxoId" to pintxo.id,
                    "pintxoName" to pintxo.name,
                    "updatedAt" to System.currentTimeMillis()
                )

                chatsRef.child(chatId).updateChildren(updates)
                    .addOnSuccessListener {
                        waitingPintxoId = null
                        waitingSecondsLeft = 0
                        onNavigateToChat(chatId)
                    }
                    .addOnFailureListener {
                        notify("No se pudo abrir el chat")
                    }
            }
        })
    }

    fun handlePrivateMatch(pintxo: Pintxo) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            notify("Sesión no válida")
            return
        }

        if (!waitingPintxoId.isNullOrBlank()) {
            notify("Ya estás esperando pareja")
            return
        }

        val waitingRef = realtimeDb.getReference("waitingByPintxo").child(pintxo.id)
        val chatsRef = realtimeDb.getReference("chats")
        val myName = currentDisplayName()

        chatsRef.orderByChild("pintxoId").equalTo(pintxo.id).get()
            .addOnSuccessListener { chatsSnapshot ->
                val existingChat = chatsSnapshot.children
                    .filter { chat ->
                        chat.child("participants").child(uid).getValue(Boolean::class.java) == true
                    }
                    .maxByOrNull { chat -> chat.child("updatedAt").getValue(Long::class.java) ?: 0L }

                val existingChatId = existingChat?.key
                if (!existingChatId.isNullOrBlank()) {
                    waitingPintxoId = null
                    waitingSecondsLeft = 0
                    onNavigateToChat(existingChatId)
                    return@addOnSuccessListener
                }

                val joinableChat = chatsSnapshot.children
                    .firstOrNull { chat ->
                        val participantsNode = chat.child("participants")
                        val participantCount = participantsNode.childrenCount
                        val iAmInChat = participantsNode.child(uid).getValue(Boolean::class.java) == true
                        participantCount == 1L && !iAmInChat
                    }

                val joinableChatId = joinableChat?.key
                if (!joinableChatId.isNullOrBlank()) {
                    val joinUpdates = mapOf<String, Any>(
                        "participants/$uid" to true,
                        "participantNames/$uid" to myName,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    chatsRef.child(joinableChatId).updateChildren(joinUpdates)
                        .addOnSuccessListener {
                            waitingPintxoId = null
                            waitingSecondsLeft = 0
                            onNavigateToChat(joinableChatId)
                        }
                        .addOnFailureListener {
                            notify("No se pudo unir al chat")
                        }
                    return@addOnSuccessListener
                }

                startWaitingTransaction(
                    waitingRef = waitingRef,
                    chatsRef = chatsRef,
                    uid = uid,
                    myName = myName,
                    pintxo = pintxo,
                    onNavigateToChat = onNavigateToChat
                )
            }
            .addOnFailureListener {
                startWaitingTransaction(
                    waitingRef = waitingRef,
                    chatsRef = chatsRef,
                    uid = uid,
                    myName = myName,
                    pintxo = pintxo,
                    onNavigateToChat = onNavigateToChat
                )
            }
    }

    // Descarga inicial de pintxos
    LaunchedEffect(Unit) {
        reloadPintxos()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PintxoMatch",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.Person, "Perfil") }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateToUpload) {
                            Icon(Icons.Default.Add, "Subir", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToLeaderboard) {
                            Icon(Icons.Default.Star, "Ranking", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToNearby) {
                            Icon(Icons.Default.Place, "Cerca de ti", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToChatList) {
                            Icon(Icons.Default.Send, "Chats", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = if (!waitingPintxoId.isNullOrBlank()) {
                            "Buscando pareja... ${waitingSecondsLeft}s"
                        } else {
                            "Desliza ← para pasar · → para hacer match"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading && !isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pintxosFirebase.isEmpty()) {
                // Pull-to-refresh on empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            seenIds.clear()
                            reloadPintxos()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay pintxos nuevos.\nDesliza abajo para recargar",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // Pull-to-refresh wrapping the card stack
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        seenIds.clear()
                        reloadPintxos()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                    if (pintxosFirebase.size > 1) {
                        PintxoCard(pintxo = pintxosFirebase[1])
                    }

                    val topPintxo = pintxosFirebase[0]
                    key(topPintxo.id) {
                        val offsetX = remember { Animatable(0f) }
                        val offsetY = remember { Animatable(0f) }
                        val coroutineScope = rememberCoroutineScope()
                        val springSpec = spring<Float>(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                                    .graphicsLayer {
                                        rotationZ = offsetX.value / 20f
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    if (!waitingPintxoId.isNullOrBlank()) {
                                                        launch { offsetX.animateTo(0f, springSpec) }
                                                        launch { offsetY.animateTo(0f, springSpec) }
                                                        notify("Ya estás esperando pareja")
                                                        return@launch
                                                    }

                                                    if (offsetX.value > 300) {
                                                        offsetX.animateTo(1000f)
                                                        seenIds.add(topPintxo.id)
                                                        handlePrivateMatch(topPintxo)
                                                        pintxosFirebase = pintxosFirebase.drop(1)
                                                    } else if (offsetX.value < -300) {
                                                        offsetX.animateTo(-1000f)
                                                        seenIds.add(topPintxo.id)
                                                        pintxosFirebase = pintxosFirebase.drop(1)
                                                    } else {
                                                        launch { offsetX.animateTo(0f, springSpec) }
                                                        launch { offsetY.animateTo(0f, springSpec) }
                                                    }
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (!waitingPintxoId.isNullOrBlank()) return@detectDragGestures
                                                coroutineScope.launch {
                                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                PintxoCard(
                                    pintxo = topPintxo,
                                    onRatePintxo = { stars -> submitRating(topPintxo, stars) }
                                )
                            }
                        }
                    }
                } // Box card stack
                } // PullToRefreshBox
            }
            
        }
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box
}

