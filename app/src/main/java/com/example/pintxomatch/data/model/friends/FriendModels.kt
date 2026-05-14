package com.example.pintxomatch.data.model.friends

enum class PresenceStatus {
    ONLINE,
    BUSY,
    INVISIBLE;

    companion object {
        fun fromStorage(value: String?): PresenceStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ONLINE
        }
    }
}

enum class FriendRelationshipStatus {
    SELF,
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIENDS
}

data class FriendListItem(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val presenceStatus: PresenceStatus,
    val friendsSince: Long
)

data class FriendRequestItem(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val sentAt: Long
)
