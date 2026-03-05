package com.example.pintxomatch

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pintxomatch.ui.components.PintxoCard
import com.example.pintxomatch.ui.screens.ChatListScreen
import com.example.pintxomatch.ui.screens.ChatScreen
import com.example.pintxomatch.ui.screens.LoginScreen
import com.example.pintxomatch.ui.screens.UploadPintxoScreen
import com.example.pintxomatch.ui.screens.UserProfileScreen
import com.example.pintxomatch.ui.theme.PintxoMatchTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                            onNavigateToChatList = { navController.navigate("chat_list") }
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
    onNavigateToChatList: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var pintxosFirebase by remember { mutableStateOf<List<Pintxo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun currentDisplayName(): String {
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Usuario"
    }

    fun handlePrivateMatch(pintxo: Pintxo) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(context, "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }

        val db = com.google.firebase.database.FirebaseDatabase
            .getInstance("https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app")
        val waitingRef = db.getReference("waitingByPintxo").child(pintxo.id)
        val chatsRef = db.getReference("chats")
        val myName = currentDisplayName()

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
                    Toast.makeText(context, "No se pudo crear el match", Toast.LENGTH_SHORT).show()
                    return
                }

                val otherUid = matchedUid
                if (otherUid == null || shouldWait) {
                    Toast.makeText(
                        context,
                        "Esperando pareja para ${pintxo.name}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                        onNavigateToChat(chatId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show()
                    }
            }
        })
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
                Log.e("Firebase", "Error cargando pintxos", it)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PintxoMatch 🍢", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = onNavigateToUpload) { Icon(Icons.Default.Add, "Subir", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onNavigateToChatList) { Icon(Icons.Default.Send, "Chats", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.Person, "Perfil") }
                }
            )
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

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingActionButton(onClick = { pintxosFirebase = pintxosFirebase.drop(1) }, containerColor = Color.White, contentColor = Color.Red) {
                        Icon(Icons.Default.Close, "Paso", modifier = Modifier.size(36.dp))
                    }
                    FloatingActionButton(
                        onClick = {
                            val topPintxo = pintxosFirebase.firstOrNull() ?: return@FloatingActionButton
                            handlePrivateMatch(topPintxo)
                            pintxosFirebase = pintxosFirebase.drop(1)
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFF4CAF50)
                    ) {
                        Icon(Icons.Default.Favorite, "Match", modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}