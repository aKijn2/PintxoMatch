package com.example.pintxomatch.data.repository.chat

import com.example.pintxomatch.data.model.chat.ChatMessage
import com.example.pintxomatch.domain.model.chat.ChatListItem
import com.example.pintxomatch.domain.model.support.SupportThreadItem
import com.example.pintxomatch.utils.PINTXO_MATCH_RTDB_URL
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val database = FirebaseDatabase.getInstance(PINTXO_MATCH_RTDB_URL)
    private val chatsRef = database.getReference("chats")
    private val supportChatsRef = database.getReference("support_chats")
    private val userInboxRef = database.getReference("user_inbox")

    data class IncomingChatAlert(
        val chatId: String,
        val title: String,
        val body: String,
        val senderId: String,
        val timestamp: Long,
        val route: String
    )

    fun getUserChatsFlow(currentUid: String): Flow<List<ChatListItem>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<ChatListItem>()
                snapshot.children.forEach { chatSnapshot ->
                    val chatId = chatSnapshot.key ?: return@forEach
                    val isParticipant = chatSnapshot.child("participants").child(currentUid)
                        .getValue(Boolean::class.java) == true
                    if (!isParticipant) return@forEach

                    val messagesSnapshot = chatSnapshot.child("messages")
                    val parsedMessages = mutableListOf<ChatMessage>()
                    messagesSnapshot.children.forEach { msgSnapshot ->
                        msgSnapshot.getValue(ChatMessage::class.java)?.let { parsedMessages.add(it) }
                    }

                    val lastMsg = parsedMessages.maxByOrNull { it.timestamp }
                    val chatType = chatSnapshot.child("chatType").getValue(String::class.java).orEmpty()
                    val pintxoName = chatSnapshot.child("pintxoName").getValue(String::class.java) ?: "Chat de pintxo"
                    val otherUid = chatSnapshot.child("participants").children
                        .mapNotNull { it.key }
                        .firstOrNull { it != currentUid }
                    val otherName = if (otherUid != null) {
                        chatSnapshot.child("participantNames").child(otherUid).getValue(String::class.java)
                    } else {
                        null
                    }
                    val title = if (chatType == "friend_direct") {
                        otherName ?: "Amigo"
                    } else if (otherName.isNullOrBlank()) {
                        pintxoName
                    } else {
                        "$pintxoName · $otherName"
                    }

                    newList.add(
                        ChatListItem(
                            chatId = chatId,
                            title = title,
                            lastMessage = lastMsg?.text ?: "Sin mensajes todavia",
                            lastTimestamp = lastMsg?.timestamp ?: 0L,
                            messageCount = parsedMessages.size,
                            lastSenderId = lastMsg?.senderId.orEmpty()
                        )
                    )
                }
                trySend(newList.sortedByDescending { it.lastTimestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        chatsRef.addValueEventListener(listener)
        awaitClose { chatsRef.removeEventListener(listener) }
    }

    suspend fun deleteChat(chatId: String) {
        chatsRef.child(chatId).removeValue().await()
    }

    suspend fun createOrGetDirectChat(
        currentUid: String,
        currentDisplayName: String,
        currentPhotoUrl: String,
        targetUid: String,
        targetDisplayName: String,
        targetPhotoUrl: String
    ): String {
        val chatKey = directChatKey(currentUid, targetUid)
        val directRef = chatsRef.child(chatKey)
        val existing = directRef.get().await()
        if (existing.exists()) {
            return chatKey
        }

        directRef.setValue(
            mapOf(
                "chatType" to "friend_direct",
                "chatKey" to chatKey,
                "pintxoName" to "Chat directo",
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "participants" to mapOf(
                    currentUid to true,
                    targetUid to true
                ),
                "participantNames" to mapOf(
                    currentUid to currentDisplayName,
                    targetUid to targetDisplayName
                ),
                "participantPhotos" to mapOf(
                    currentUid to currentPhotoUrl,
                    targetUid to targetPhotoUrl
                )
            )
        ).await()

        return chatKey
    }

    fun getChatMessagesFlow(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val ref = chatsRef.child(chatId).child("messages")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(ChatMessage::class.java) }
                trySend(list.sortedBy { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getChatParticipantPhotosFlow(chatId: String): Flow<Map<String, String>> = callbackFlow {
        val ref = chatsRef.child(chatId).child("participantPhotos")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.mapNotNull {
                    val uid = it.key
                    val url = it.getValue(String::class.java)
                    if (uid.isNullOrBlank() || url.isNullOrBlank()) null else uid to url
                }.toMap()
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getIncomingChatAlertsFlow(currentUid: String): Flow<List<IncomingChatAlert>> = callbackFlow {
        val ref = userInboxRef.child(currentUid).child("chatAlerts")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = snapshot.children.mapNotNull { node ->
                    val chatId = node.key ?: return@mapNotNull null
                    val title = node.child("title").getValue(String::class.java).orEmpty()
                    val body = node.child("body").getValue(String::class.java).orEmpty()
                    val senderId = node.child("senderId").getValue(String::class.java).orEmpty()
                    val timestamp = node.child("timestamp").getValue(Long::class.java) ?: 0L
                    IncomingChatAlert(
                        chatId = chatId,
                        title = title,
                        body = body,
                        senderId = senderId,
                        timestamp = timestamp,
                        route = node.child("route").getValue(String::class.java).orEmpty()
                    )
                }.sortedByDescending { it.timestamp }
                trySend(alerts)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateChatMetadataAndValidateAccess(
        chatId: String,
        currentUid: String,
        displayName: String,
        photoUrl: String
    ): Triple<Boolean, String, Boolean> {
        val chatSnapshot = chatsRef.child(chatId).get().await()
        val participantSnapshot = chatSnapshot.child("participants").child(currentUid)
        var hasAccess = participantSnapshot.getValue(Boolean::class.java) == true

        if (!hasAccess) {
            val messagesSnapshot = chatSnapshot.child("messages")
            val wroteInThisChat = messagesSnapshot.children.any {
                it.child("senderId").getValue(String::class.java) == currentUid
            }
            hasAccess = wroteInThisChat
        }

        if (hasAccess) {
            val metadataUpdates = mapOf<String, Any>(
                "participants/$currentUid" to true,
                "participantNames/$currentUid" to displayName,
                "updatedAt" to System.currentTimeMillis()
            )
            chatsRef.child(chatId).updateChildren(metadataUpdates).await()
            if (photoUrl.isNotBlank()) {
                chatsRef.child(chatId).child("participantPhotos").child(currentUid).setValue(photoUrl).await()
            }

            val chatType = chatSnapshot.child("chatType").getValue(String::class.java).orEmpty()
            val chatTitle = if (chatType == "friend_direct") {
                chatSnapshot.child("participants").children
                    .mapNotNull { it.key }
                    .firstOrNull { it != currentUid }
                    ?.let { otherUid ->
                        chatSnapshot.child("participantNames").child(otherUid).getValue(String::class.java)
                    } ?: "Amigo"
            } else {
                chatSnapshot.child("pintxoName").getValue(String::class.java) ?: "Chat privado"
            }
            return Triple(true, chatTitle, true)
        }

        return Triple(false, "Chat privado", false)
    }

    suspend fun sendMessage(chatId: String, currentUid: String, senderName: String, senderPhoto: String, text: String) {
        val chatSnapshot = chatsRef.child(chatId).get().await()
        val canWrite = chatSnapshot.exists() &&
            (chatSnapshot.child("participants").child(currentUid).getValue(Boolean::class.java) == true)
        if (!canWrite) throw Exception("No tienes permiso o el chat fue cerrado.")

        val msg = ChatMessage(
            senderId = currentUid,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        chatsRef.child(chatId).child("messages").push().setValue(msg).await()
        chatsRef.child(chatId).child("updatedAt").setValue(System.currentTimeMillis()).await()
        chatsRef.child(chatId).child("participantNames").child(currentUid).setValue(senderName).await()
        if (senderPhoto.isNotBlank()) {
            chatsRef.child(chatId).child("participantPhotos").child(currentUid).setValue(senderPhoto).await()
        }

        val recipients = chatSnapshot.child("participants").children
            .mapNotNull { it.key }
            .filter { it != currentUid }

        recipients.forEach { recipientUid ->
            val recipientTitle = if (
                chatSnapshot.child("chatType").getValue(String::class.java).orEmpty() == "friend_direct"
            ) {
                senderName
            } else {
                chatSnapshot.child("pintxoName").getValue(String::class.java) ?: "Nuevo mensaje"
            }

            userInboxRef.child(recipientUid).child("chatAlerts").child(chatId).setValue(
                mapOf(
                    "title" to recipientTitle,
                    "body" to text,
                    "senderId" to currentUid,
                    "timestamp" to System.currentTimeMillis(),
                    "route" to "chat/$chatId"
                )
            ).await()
        }
    }

    suspend fun cleanupChatIfEmptyAndLeave(chatId: String) {
        val messagesSnapshot = chatsRef.child(chatId).child("messages").get().await()
        if (!messagesSnapshot.exists() || messagesSnapshot.childrenCount == 0L) {
            val participantsSnapshot = chatsRef.child(chatId).child("participants").get().await()
            if (participantsSnapshot.childrenCount <= 1L) {
                chatsRef.child(chatId).removeValue().await()
            }
        }
    }

    fun getSupportThreadsFlow(): Flow<List<SupportThreadItem>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = snapshot.children.mapNotNull { thread ->
                    val id = thread.key ?: return@mapNotNull null
                    val meta = thread.child("meta")
                    SupportThreadItem(
                        threadId = id,
                        userName = meta.child("userName").getValue(String::class.java) ?: "Usuario",
                        ticketTitle = meta.child("ticketTitle").getValue(String::class.java).orEmpty(),
                        userEmail = meta.child("userEmail").getValue(String::class.java) ?: "",
                        lastMessage = meta.child("lastMessage").getValue(String::class.java) ?: "Sin mensajes",
                        status = meta.child("status").getValue(String::class.java) ?: "open",
                        updatedAt = meta.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.updatedAt }
                trySend(loaded)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        supportChatsRef.addValueEventListener(listener)
        awaitClose { supportChatsRef.removeEventListener(listener) }
    }

    fun getSupportMessagesFlow(threadId: String): Flow<Map<String, ChatMessage>> = callbackFlow {
        val ref = supportChatsRef.child(threadId).child("messages")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.mapNotNull { node ->
                    val id = node.key ?: return@mapNotNull null
                    val msg = node.getValue(ChatMessage::class.java) ?: return@mapNotNull null
                    id to msg
                }.toMap()
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getSupportTicketStatusFlow(threadId: String): Flow<String> = callbackFlow {
        val ref = supportChatsRef.child(threadId).child("meta").child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "open"
                trySend(status)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun hasSupportTicket(threadId: String): Boolean {
        if (threadId.isBlank()) return false
        val metaSnapshot = supportChatsRef.child(threadId).child("meta").get().await()
        if (!metaSnapshot.exists()) return false

        val ticketTitle = metaSnapshot.child("ticketTitle").getValue(String::class.java).orEmpty().trim()
        return ticketTitle.isNotBlank()
    }

    fun getSupportTicketTitleFlow(threadId: String): Flow<String> = callbackFlow {
        val ref = supportChatsRef.child(threadId).child("meta").child("ticketTitle")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val title = snapshot.getValue(String::class.java).orEmpty()
                trySend(title)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun ensureSupportTicketTitle(threadId: String, title: String) {
        val normalized = title.trim()
        if (normalized.isBlank()) return

        val metaRef = supportChatsRef.child(threadId).child("meta")
        val current = metaRef.child("ticketTitle").get().await().getValue(String::class.java).orEmpty()
        if (current.isBlank()) {
            metaRef.updateChildren(
                mapOf<String, Any>(
                    "ticketTitle" to normalized,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    suspend fun sendSupportMessage(threadId: String, currentUid: String, email: String, senderName: String, text: String) {
        val msg = ChatMessage(
            senderId = currentUid,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        supportChatsRef.child(threadId).child("messages").push().setValue(msg).await()
        supportChatsRef.child(threadId).child("meta").updateChildren(
            mapOf<String, Any?>(
                "userUid" to threadId,
                "userEmail" to email,
                "userName" to senderName,
                "lastMessage" to text,
                "updatedAt" to System.currentTimeMillis(),
                "status" to "open",
                "resolvedBy" to null,
                "resolvedAt" to null
            )
        ).await()

        val supportTitle = if (threadId == currentUid) {
            "Soporte"
        } else {
            senderName
        }

        userInboxRef.child(threadId).child("chatAlerts").child("support_$threadId").setValue(
            mapOf(
                "title" to supportTitle,
                "body" to text,
                "senderId" to currentUid,
                "timestamp" to System.currentTimeMillis(),
                "route" to "support"
            )
        ).await()
    }

    suspend fun updateSupportTicketStatus(threadId: String, resolved: Boolean, resolverUid: String) {
        supportChatsRef.child(threadId).child("meta").updateChildren(
            mapOf<String, Any?>(
                "status" to if (resolved) "resolved" else "open",
                "updatedAt" to System.currentTimeMillis(),
                "resolvedBy" to if (resolved) resolverUid else null,
                "resolvedAt" to if (resolved) System.currentTimeMillis() else null
            )
        ).await()
    }

    suspend fun deleteSupportTicket(threadId: String) {
        supportChatsRef.child(threadId).removeValue().await()
    }

    suspend fun deleteSupportMessage(threadId: String, messageId: String) {
        supportChatsRef.child(threadId).child("messages").child(messageId).removeValue().await()
    }

    private fun directChatKey(firstUid: String, secondUid: String): String {
        return listOf(firstUid, secondUid).sorted().joinToString("_")
    }
}
