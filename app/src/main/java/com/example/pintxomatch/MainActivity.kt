package com.example.pintxomatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.example.pintxomatch.ui.components.PintxoCard
import com.example.pintxomatch.ui.screens.ChatListScreen
import com.example.pintxomatch.ui.screens.ChatScreen
import com.example.pintxomatch.ui.screens.LeaderboardScreen
import com.example.pintxomatch.ui.screens.LoginScreen
import com.example.pintxomatch.ui.screens.UploadPintxoScreen
import com.example.pintxomatch.ui.screens.UserProfileScreen
import com.example.pintxomatch.ui.theme.PintxoMatchTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
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
                // 1. EL PORTERO: Comprobamos si el usuario ya está logueado
                val auth = FirebaseAuth.getInstance()
                val startScreen = if (auth.currentUser == null) "login" else "home"

                val navController = rememberNavController()

                // 2. NAVHOST ÚNICO: Gestiona todas las pantallas
                NavHost(navController = navController, startDestination = startScreen) {

                    // Pantalla de Login / Registro
                    composable("login") {
                        LoginScreen(onLoginSuccess = {
                            navController.navigate("home") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }

                    // Pantalla Principal (Swipe)
                    composable("home") {
                        MainSwipeScreen(
                            onNavigateToProfile = { navController.navigate("profile") },
                            onNavigateToUpload = { navController.navigate("upload") },
                            onNavigateToChat = { chatId -> navController.navigate("chat/$chatId") },
                            onNavigateToChatList = { navController.navigate("chat_list") },
                            onNavigateToLeaderboard = { navController.navigate("leaderboard") }
                        )
                    }

                    // Pantalla de Perfil (Con cierre de sesión corregido)
                    composable("profile") {
                        UserProfileScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Pantalla de Subida (Para añadir tortillas de Villabona)
                    composable("upload") {
                        UploadPintxoScreen(onNavigateBack = { navController.popBackStack() })
                    }

                    // Dentro de tu NavHost { ... }
                    composable("chat/{chatId}") { backStackEntry ->
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: "pintxo_general"
                        ChatScreen(
                            chatId = chatId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("chat_list") {
                        ChatListScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenChat = { chatId -> navController.navigate("chat/$chatId") }
                        )
                    }

                    composable("leaderboard") {
                        LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSwipeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToChatList: () -> Unit,
    onNavigateToLeaderboard: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val realtimeDb = com.google.firebase.database.FirebaseDatabase
        .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
    var pintxosFirebase by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var waitingPintxoId by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    fun notify(message: String) {
        alertMessage = message
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

    // Descarga de datos de Firestore en tiempo real
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Pintxos")
            .orderBy(FieldPath.documentId())
            .get()
            .addOnSuccessListener { result ->
                val listaNueva = result.map { doc ->
                    Pintxo(
                        id = doc.id,
                        name = doc.getString("nombre") ?: "Sin nombre",
                        barName = doc.getString("bar") ?: "Bar desconocido",
                        location = doc.getString("ubicacion") ?: "Gipuzkoa",
                        price = doc.getDouble("precio") ?: 0.0,
                        imageUrl = doc.getString("imageUrl") ?: "" // Cargamos el link de internet
                    )
                }
                pintxosFirebase = listaNueva
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                notify("Error cargando pintxos")
            }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PintxoMatch 🍢",
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
                        IconButton(onClick = onNavigateToChatList) {
                            Icon(Icons.Default.Send, "Chats", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = "Desliza ← para pasar · → para hacer match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pintxosFirebase.isEmpty()) {
                Text("No hay pintxos en la nube. ¡Sé el primero!", modifier = Modifier.align(Alignment.Center))
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                    if (pintxosFirebase.size > 1) {
                        PintxoCard(pintxo = pintxosFirebase[1])
                    }

                    val topPintxo = pintxosFirebase[0]
                    key(topPintxo.id) {
                        val offsetX = remember { Animatable(0f) }
                        val offsetY = remember { Animatable(0f) }
                        val coroutineScope = rememberCoroutineScope()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                                .graphicsLayer { rotationZ = offsetX.value / 20f }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            coroutineScope.launch {
                                                if (offsetX.value > 300) {
                                                    offsetX.animateTo(1000f)
                                                    handlePrivateMatch(topPintxo)
                                                    pintxosFirebase = pintxosFirebase.drop(1)
                                                } else if (offsetX.value < -300) {
                                                    offsetX.animateTo(-1000f)
                                                    pintxosFirebase = pintxosFirebase.drop(1)
                                                } else {
                                                    offsetX.animateTo(0f)
                                                    offsetY.animateTo(0f)
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            coroutineScope.launch {
                                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                                offsetY.snapTo(offsetY.value + dragAmount.y)
                                            }
                                        }
                                    )
                                }
                        ) {
                            PintxoCard(pintxo = topPintxo)
                        }
                    }
                }
            }
        }
    }
}