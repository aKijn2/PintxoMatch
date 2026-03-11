package com.example.pintxomatch.data.repository

import com.example.pintxomatch.data.model.ProfileComment
import com.example.pintxomatch.data.model.LeaderboardUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("Users")

    suspend fun getFriendsCount(uid: String): Int {
        return try {
            val friends = firestore.collection("Users").document(uid)
                .collection("Friends").get().await()
            friends.size()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun areCommentsEnabled(uid: String): Boolean {
        return try {
            val doc = firestore.collection("Users").document(uid).get().await()
            doc.getBoolean("commentsEnabled") ?: true // Default to true if not set
        } catch (e: Exception) {
            true
        }
    }

    suspend fun updateCommentsEnabled(uid: String, enabled: Boolean): Boolean {
        return try {
            firestore.collection("Users").document(uid).update("commentsEnabled", enabled).await()
            true
        } catch (e: Exception) {
            // If the document doesn't exist yet, creating it with the field
            try {
                firestore.collection("Users").document(uid).set(hashMapOf("commentsEnabled" to enabled)).await()
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    suspend fun getPublicProfile(uid: String): LeaderboardUser? {
        // By default, LeaderboardUser serves well as a public profile model 
        // We calculate uploads dynamically from Pintxos collection as done in Leaderboard
        return try {
            val pintxos = firestore.collection("Pintxos").whereEqualTo("uploaderUid", uid).get().await()
            val doc = pintxos.documents.firstOrNull()
            
            val displayName = doc?.getString("uploaderDisplayName") ?: "Usuario"
            val photoUrl = doc?.getString("uploaderPhotoUrl") ?: ""

            LeaderboardUser(
                uid = uid,
                displayName = displayName,
                totalUploads = pintxos.size(),
                profileImageUrl = photoUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addFriend(currentUserId: String, targetUserId: String): Boolean {
        return try {
            // Guardamos la amistad de forma unidireccional o bidireccional
            // Por simplicidad en este MVP, añadimos a targetUserid a la subcolección de amigos
            val friendData = hashMapOf(
                "friendId" to targetUserId,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("Users").document(currentUserId)
                .collection("Friends").document(targetUserId)
                .set(friendData).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isFriend(currentUserId: String, targetUserId: String): Boolean {
        return try {
            val doc = firestore.collection("Users").document(currentUserId)
                .collection("Friends").document(targetUserId)
                .get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeFriend(currentUserId: String, targetUserId: String): Boolean {
        return try {
            firestore.collection("Users").document(currentUserId)
                .collection("Friends").document(targetUserId)
                .delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun leaveComment(comment: ProfileComment): Boolean {
        return try {
            val validComment = hashMapOf(
                "senderId" to comment.senderId,
                "senderName" to comment.senderName,
                "senderPhotoUrl" to comment.senderPhotoUrl,
                "receiverId" to comment.receiverId,
                "text" to comment.text,
                "timestamp" to comment.timestamp
            )
            firestore.collection("Users").document(comment.receiverId)
                .collection("Comments")
                .add(validComment).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteComment(receiverId: String, commentId: String): Boolean {
        return try {
            firestore.collection("Users").document(receiverId)
                .collection("Comments").document(commentId)
                .delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getProfileComments(uid: String): List<ProfileComment> {
        return try {
            val result = firestore.collection("Users").document(uid)
                .collection("Comments")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().await()

            result.documents.map { doc ->
                ProfileComment(
                    id = doc.id,
                    senderId = doc.getString("senderId") ?: "",
                    senderName = doc.getString("senderName") ?: "Anónimo",
                    senderPhotoUrl = doc.getString("senderPhotoUrl") ?: "",
                    receiverId = doc.getString("receiverId") ?: "",
                    text = doc.getString("text") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
