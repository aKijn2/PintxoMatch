package com.example.pintxomatch.data.repository.auth

import com.example.pintxomatch.data.repository.user.UserRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

object AuthRepository {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun signOut() {
        val uid = currentUserId
        if (!uid.isNullOrBlank()) {
            runBlocking {
                try {
                    val token = FirebaseMessaging.getInstance().token.await()
                    UserRepository().removeDeviceToken(uid, token)
                } catch (_: Exception) {
                }
            }
        }
        auth.signOut()
    }
    
    // Auth login/register methods remain task-based or handled in UI 
    // until we fully implement ViewModel with coroutine await() 
    // to avoid adding new library dependencies mid-refactor.
}
