package com.example.pintxomatch.data.repository.user

import com.example.pintxomatch.data.model.friends.FriendListItem
import com.example.pintxomatch.data.model.friends.FriendRelationshipStatus
import com.example.pintxomatch.data.model.friends.FriendRequestItem
import com.example.pintxomatch.data.model.friends.PresenceStatus
import com.example.pintxomatch.data.model.leaderboard.LeaderboardUser
import com.example.pintxomatch.data.model.profile.ProfileComment
import com.example.pintxomatch.data.repository.media.ImageRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("Users")

    private fun userDoc(uid: String) = usersCollection.document(uid)

    suspend fun syncUserProfile(uid: String, displayName: String, photoUrl: String) {
        if (uid.isBlank()) return

        userDoc(uid).set(
            mapOf(
                "displayName" to displayName,
                "photoUrl" to photoUrl,
                "presenceStatus" to PresenceStatus.ONLINE.name
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun updatePresenceStatus(uid: String, status: PresenceStatus): Boolean {
        return try {
            userDoc(uid).set(
                mapOf(
                    "presenceStatus" to status.name,
                    "presenceUpdatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPresenceStatus(uid: String): PresenceStatus {
        return try {
            val doc = userDoc(uid).get().await()
            PresenceStatus.fromStorage(doc.getString("presenceStatus"))
        } catch (_: Exception) {
            PresenceStatus.ONLINE
        }
    }

    suspend fun getFriendsCount(uid: String): Int {
        return try {
            userDoc(uid).collection("Friends").get().await().size()
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getFriends(uid: String): List<FriendListItem> {
        return try {
            val docs = userDoc(uid)
                .collection("Friends")
                .orderBy("displayName", Query.Direction.ASCENDING)
                .get()
                .await()

            docs.documents.map { doc ->
                val friendUid = doc.id
                val friendProfile = userDoc(friendUid).get().await()
                FriendListItem(
                    uid = friendUid,
                    displayName = doc.getString("displayName")
                        ?: friendProfile.getString("displayName")
                        ?: "Usuario",
                    photoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(
                        doc.getString("photoUrl") ?: friendProfile.getString("photoUrl")
                    ) ?: "",
                    presenceStatus = PresenceStatus.fromStorage(friendProfile.getString("presenceStatus")),
                    friendsSince = doc.getLong("since") ?: 0L
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getIncomingFriendRequests(uid: String): List<FriendRequestItem> {
        return try {
            val docs = userDoc(uid)
                .collection("FriendRequestsIncoming")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .get()
                .await()

            docs.documents.map { doc ->
                FriendRequestItem(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: "Usuario",
                    photoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(doc.getString("photoUrl")) ?: "",
                    sentAt = doc.getLong("sentAt") ?: 0L
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getOutgoingFriendRequests(uid: String): List<FriendRequestItem> {
        return try {
            val docs = userDoc(uid)
                .collection("FriendRequestsOutgoing")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .get()
                .await()

            docs.documents.map { doc ->
                FriendRequestItem(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: "Usuario",
                    photoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(doc.getString("photoUrl")) ?: "",
                    sentAt = doc.getLong("sentAt") ?: 0L
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getFriendRelationship(currentUserId: String, targetUserId: String): FriendRelationshipStatus {
        if (currentUserId == targetUserId) return FriendRelationshipStatus.SELF

        return try {
            if (userDoc(currentUserId).collection("Friends").document(targetUserId).get().await().exists()) {
                FriendRelationshipStatus.FRIENDS
            } else if (userDoc(currentUserId).collection("FriendRequestsOutgoing").document(targetUserId).get().await().exists()) {
                FriendRelationshipStatus.OUTGOING_PENDING
            } else if (userDoc(currentUserId).collection("FriendRequestsIncoming").document(targetUserId).get().await().exists()) {
                FriendRelationshipStatus.INCOMING_PENDING
            } else {
                FriendRelationshipStatus.NONE
            }
        } catch (_: Exception) {
            FriendRelationshipStatus.NONE
        }
    }

    suspend fun sendFriendRequest(
        currentUserId: String,
        targetUserId: String,
        currentDisplayName: String,
        currentPhotoUrl: String
    ): Boolean {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) return false

        return try {
            val relationship = getFriendRelationship(currentUserId, targetUserId)
            if (relationship != FriendRelationshipStatus.NONE) return false

            syncUserProfile(currentUserId, currentDisplayName, currentPhotoUrl)
            val requesterPhoto = ImageRepository.normalizeImageUrlForCurrentProvider(currentPhotoUrl).orEmpty()
            val targetProfile = userDoc(targetUserId).get().await()
            val targetName = targetProfile.getString("displayName") ?: "Usuario"
            val targetPhoto = ImageRepository.normalizeImageUrlForCurrentProvider(targetProfile.getString("photoUrl")).orEmpty()
            val sentAt = System.currentTimeMillis()

            val batch = firestore.batch()
            batch.set(
                userDoc(currentUserId).collection("FriendRequestsOutgoing").document(targetUserId),
                mapOf(
                    "uid" to targetUserId,
                    "displayName" to targetName,
                    "photoUrl" to targetPhoto,
                    "sentAt" to sentAt
                )
            )
            batch.set(
                userDoc(targetUserId).collection("FriendRequestsIncoming").document(currentUserId),
                mapOf(
                    "uid" to currentUserId,
                    "displayName" to currentDisplayName,
                    "photoUrl" to requesterPhoto,
                    "sentAt" to sentAt
                )
            )
            batch.commit().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun acceptFriendRequest(currentUserId: String, requesterUserId: String): Boolean {
        if (currentUserId.isBlank() || requesterUserId.isBlank()) return false

        return try {
            val me = userDoc(currentUserId).get().await()
            val requester = userDoc(requesterUserId).get().await()
            val now = System.currentTimeMillis()

            val myDisplayName = me.getString("displayName") ?: "Usuario"
            val myPhotoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(me.getString("photoUrl")).orEmpty()
            val requesterDisplayName = requester.getString("displayName") ?: "Usuario"
            val requesterPhotoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(requester.getString("photoUrl")).orEmpty()

            val batch = firestore.batch()
            batch.set(
                userDoc(currentUserId).collection("Friends").document(requesterUserId),
                mapOf(
                    "friendId" to requesterUserId,
                    "displayName" to requesterDisplayName,
                    "photoUrl" to requesterPhotoUrl,
                    "since" to now
                )
            )
            batch.set(
                userDoc(requesterUserId).collection("Friends").document(currentUserId),
                mapOf(
                    "friendId" to currentUserId,
                    "displayName" to myDisplayName,
                    "photoUrl" to myPhotoUrl,
                    "since" to now
                )
            )
            batch.delete(userDoc(currentUserId).collection("FriendRequestsIncoming").document(requesterUserId))
            batch.delete(userDoc(requesterUserId).collection("FriendRequestsOutgoing").document(currentUserId))
            batch.commit().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rejectFriendRequest(currentUserId: String, requesterUserId: String): Boolean {
        if (currentUserId.isBlank() || requesterUserId.isBlank()) return false

        return try {
            val batch = firestore.batch()
            batch.delete(userDoc(currentUserId).collection("FriendRequestsIncoming").document(requesterUserId))
            batch.delete(userDoc(requesterUserId).collection("FriendRequestsOutgoing").document(currentUserId))
            batch.commit().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun cancelFriendRequest(currentUserId: String, targetUserId: String): Boolean {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return false

        return try {
            val batch = firestore.batch()
            batch.delete(userDoc(currentUserId).collection("FriendRequestsOutgoing").document(targetUserId))
            batch.delete(userDoc(targetUserId).collection("FriendRequestsIncoming").document(currentUserId))
            batch.commit().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isFriend(currentUserId: String, targetUserId: String): Boolean {
        return getFriendRelationship(currentUserId, targetUserId) == FriendRelationshipStatus.FRIENDS
    }

    suspend fun removeFriend(currentUserId: String, targetUserId: String): Boolean {
        return try {
            val batch = firestore.batch()
            batch.delete(userDoc(currentUserId).collection("Friends").document(targetUserId))
            batch.delete(userDoc(targetUserId).collection("Friends").document(currentUserId))
            batch.commit().await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun areCommentsEnabled(uid: String): Boolean {
        return try {
            val doc = userDoc(uid).get().await()
            doc.getBoolean("commentsEnabled") ?: true
        } catch (_: Exception) {
            true
        }
    }

    suspend fun updateCommentsEnabled(uid: String, enabled: Boolean): Boolean {
        return try {
            userDoc(uid).update("commentsEnabled", enabled).await()
            true
        } catch (_: Exception) {
            try {
                userDoc(uid).set(hashMapOf("commentsEnabled" to enabled)).await()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun syncUploaderProfileToPintxos(uid: String, displayName: String, photoUrl: String) {
        val updates = mutableMapOf<String, Any>()
        if (displayName.isNotBlank()) updates["uploaderDisplayName"] = displayName
        updates["uploaderPhotoUrl"] = photoUrl

        if (updates.isEmpty()) return

        val docs = firestore.collection("Pintxos")
            .whereEqualTo("uploaderUid", uid)
            .get()
            .await()

        for (doc in docs.documents) {
            doc.reference.update(updates).await()
        }
    }

    suspend fun getPublicProfile(uid: String): LeaderboardUser? {
        return try {
            val profileDoc = userDoc(uid).get().await()
            val pintxos = firestore.collection("Pintxos").whereEqualTo("uploaderUid", uid).get().await()

            val fallbackDoc = pintxos.documents.firstOrNull()
            val displayName = profileDoc.getString("displayName")
                ?: fallbackDoc?.getString("uploaderDisplayName")
                ?: "Usuario"
            val photoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(
                profileDoc.getString("photoUrl") ?: fallbackDoc?.getString("uploaderPhotoUrl")
            ) ?: ""

            LeaderboardUser(
                uid = uid,
                displayName = displayName,
                totalUploads = pintxos.size(),
                profileImageUrl = photoUrl
            )
        } catch (_: Exception) {
            null
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
            userDoc(comment.receiverId)
                .collection("Comments")
                .add(validComment).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteComment(receiverId: String, commentId: String): Boolean {
        return try {
            userDoc(receiverId)
                .collection("Comments")
                .document(commentId)
                .delete()
                .await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getProfileComments(uid: String): List<ProfileComment> {
        return try {
            val result = userDoc(uid)
                .collection("Comments")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            result.documents.map { doc ->
                ProfileComment(
                    id = doc.id,
                    senderId = doc.getString("senderId") ?: "",
                    senderName = doc.getString("senderName") ?: "Anonimo",
                    senderPhotoUrl = ImageRepository.normalizeImageUrlForCurrentProvider(doc.getString("senderPhotoUrl")) ?: "",
                    receiverId = doc.getString("receiverId") ?: "",
                    text = doc.getString("text") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
