package com.example.pintxomatch.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pintxomatch.ui.screens.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

private const val ADMIN_UID = "3rR1Cwqv2Ccvyw9OU6s8Oxu1AJV2"
private const val RTDB_URL = "https://pintxomatch-default-rtdb.europe-west1.firebasedatabase.app"

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val auth = FirebaseAuth.getInstance()
    val startScreen = if (auth.currentUser == null) "login" else "home"

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid
        if (uid == ADMIN_UID) {
            FirebaseDatabase.getInstance(RTDB_URL)
                .getReference("admins")
                .child(uid)
                .setValue(true)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startScreen,
        enterTransition = { fadeIn(animationSpec = tween(260)) + slideInHorizontally { it / 4 } },
        exitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally { -it / 4 } },
        popEnterTransition = { fadeIn(animationSpec = tween(260)) + slideInHorizontally { -it / 4 } },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally { it / 4 } }
    ) {

        composable("login") {
            LoginScreen(onLoginSuccess = {
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }

        composable("home") {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isAdmin = currentUid == ADMIN_UID

            HomeReviewScreen(
                isAdmin = isAdmin,
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToUpload = { navController.navigate("upload") },
                onNavigateToReviews = { navController.navigate("reviews") },
                onNavigateToLeaderboard = { navController.navigate("leaderboard") },
                onNavigateToNearby = { navController.navigate("nearby_restaurants") },
                onNavigateToSupport = { navController.navigate("support") },
                onNavigateToSupportInbox = { navController.navigate("support_inbox") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("profile") {
            UserProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserPintxos = { navController.navigate("user_pintxos") }
            )
        }

        composable("user_pintxos") {
            UserPintxosScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { pintxoId -> navController.navigate("edit_pintxo/$pintxoId") }
            )
        }

        composable("edit_pintxo/{pintxoId}") { backStackEntry ->
            val pintxoId = backStackEntry.arguments?.getString("pintxoId") ?: return@composable
            EditPintxoScreen(
                pintxoId = pintxoId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("upload") {
            UploadPintxoScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("reviews") {
            ReviewsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("support") {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isAdmin = currentUid == ADMIN_UID
            SupportChatScreen(
                onNavigateBack = { navController.popBackStack() },
                isAdmin = isAdmin
            )
        }

        composable("support_inbox") {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isAdmin = currentUid == ADMIN_UID
            if (isAdmin) {
                SupportInboxScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenThread = { threadId -> navController.navigate("support_thread/$threadId") }
                )
            } else {
                SupportChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isAdmin = false
                )
            }
        }

        composable("support_thread/{threadId}") { backStackEntry ->
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isAdmin = currentUid == ADMIN_UID
            if (isAdmin) {
                val threadId = backStackEntry.arguments?.getString("threadId")
                SupportChatScreen(
                    threadId = threadId,
                    isAdmin = true,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                SupportChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isAdmin = false
                )
            }
        }

        composable("leaderboard") {
            LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("nearby_restaurants") {
            NearbyRestaurantsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSupport = { navController.navigate("support") },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
