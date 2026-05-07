package com.example.pintxomatch.ui.navigation

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
import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.ui.auth.LoginScreen
import com.example.pintxomatch.ui.feed.HomeReviewScreen
import com.example.pintxomatch.ui.leaderboard.LeaderboardScreen
import com.example.pintxomatch.ui.map.NearbyRestaurantsScreen
import com.example.pintxomatch.ui.pintxo.EditPintxoScreen
import com.example.pintxomatch.ui.pintxo.UploadPintxoScreen
import com.example.pintxomatch.ui.profile.UserPintxosScreen
import com.example.pintxomatch.ui.profile.UserProfileScreen
import com.example.pintxomatch.ui.reviews.ReviewsScreen
import com.example.pintxomatch.ui.settings.SettingsScreen
import com.example.pintxomatch.ui.support.SupportChatScreen
import com.example.pintxomatch.ui.support.SupportInboxScreen
import com.example.pintxomatch.utils.ADMIN_UID
import com.example.pintxomatch.utils.PINTXO_MATCH_RTDB_URL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val auth = FirebaseAuth.getInstance()
    val startScreen = "home"

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid
        if (uid == ADMIN_UID) {
            FirebaseDatabase.getInstance(PINTXO_MATCH_RTDB_URL)
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
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            val currentUid = auth.currentUser?.uid
            HomeReviewScreen(
                isAdmin = currentUid == ADMIN_UID,
                onNavigateToProfile = {
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("profile")
                },
                onNavigateToUpload = {
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("upload")
                },
                onNavigateToReviews = {
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("reviews")
                },
                onNavigateToLeaderboard = { navController.navigate("leaderboard") },
                onNavigateToNearby = { navController.navigate("nearby_restaurants") },
                onNavigateToSupport = {
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("support")
                },
                onNavigateToSupportInbox = { navController.navigate("support_inbox") },
                onNavigateToSettings = {
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("settings")
                },
                onNavigateToPublicProfile = { uid ->
                    if (auth.currentUser == null) navController.navigate("login")
                    else navController.navigate("public_profile/$uid")
                },
                onRequireAuth = { navController.navigate("login") }
            )
        }

        composable("profile") {
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                UserProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUserPintxos = { navController.navigate("user_pintxos") }
                )
            }
        }

        composable("user_pintxos") {
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                UserPintxosScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { pintxoId -> navController.navigate("edit_pintxo/$pintxoId") }
                )
            }
        }

        composable("edit_pintxo/{pintxoId}") { backStackEntry ->
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                val pintxoId = backStackEntry.arguments?.getString("pintxoId") ?: return@composable
                EditPintxoScreen(
                    pintxoId = pintxoId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable("upload") {
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                UploadPintxoScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        composable("reviews") {
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                ReviewsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        composable("support") {
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                val currentUid = auth.currentUser?.uid
                SupportChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isAdmin = currentUid == ADMIN_UID
                )
            }
        }

        composable("support_inbox") {
            val currentUid = auth.currentUser?.uid
            if (currentUid == ADMIN_UID) {
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
            val currentUid = auth.currentUser?.uid
            if (currentUid == ADMIN_UID) {
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
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProfile = { navController.navigate("profile") },
                    onNavigateToSupport = { navController.navigate("support") },
                    onLogout = {
                        AuthRepository.signOut()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("public_profile/{uid}") { backStackEntry ->
            if (auth.currentUser == null) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            } else {
                val uid = backStackEntry.arguments?.getString("uid") ?: return@composable
                UserProfileScreen(
                    profileUid = uid,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUserPintxos = { navController.navigate("user_pintxos") }
                )
            }
        }
    }
}
