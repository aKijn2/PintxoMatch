package com.example.pintxomatch.notifications

import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.user.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PintxoFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = AuthRepository.currentUserId ?: return
        serviceScope.launch {
            UserRepository().saveDeviceToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Nuevo mensaje"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Tienes un mensaje nuevo"
        val chatId = message.data["chatId"]

        NotificationHelper.showChatNotification(
            context = applicationContext,
            title = title,
            body = body,
            chatId = chatId
        )
    }
}
