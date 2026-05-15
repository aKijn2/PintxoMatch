package com.example.pintxomatch.notifications

import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.user.UserRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object PushTokenManager {
    suspend fun syncCurrentUserToken() {
        val uid = AuthRepository.currentUserId ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        UserRepository().saveDeviceToken(uid, token)
    }

    suspend fun removeCurrentUserToken() {
        val uid = AuthRepository.currentUserId ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        UserRepository().removeDeviceToken(uid, token)
    }
}
