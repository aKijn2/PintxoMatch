package com.example.pintxomatch.data.repository

import com.example.pintxomatch.data.model.GamificationActionType
import com.example.pintxomatch.data.model.UserGamification
import com.example.pintxomatch.data.model.WeeklyChallenge
import com.example.pintxomatch.data.model.WeeklyChallengeProgress
import com.example.pintxomatch.data.model.toUserGamification
import com.example.pintxomatch.data.model.toWeeklyChallengeOrNull
import com.example.pintxomatch.domain.gamification.GamificationRules
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

private const val USERS_COLLECTION = "Users"
private const val WEEKLY_CHALLENGES_COLLECTION = "WeeklyChallenges"
private const val USER_WEEKLY_PROGRESS_COLLECTION = "WeeklyChallengeProgress"
private const val MILLIS_IN_WEEK = 7L * 24L * 60L * 60L * 1000L

data class GamificationSnapshot(
    val user: UserGamification,
    val activeChallenges: List<WeeklyChallengeProgress>
)

data class AwardXpResult(
    val xp: Int,
    val currentStreak: Int,
    val unlockedBadges: List<String>
)

class GamificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val usersCollection = firestore.collection(USERS_COLLECTION)
    private val challengesCollection = firestore.collection(WEEKLY_CHALLENGES_COLLECTION)

    suspend fun upsertDefaultWeeklyChallengesForCurrentWeek(now: Long = System.currentTimeMillis()) {
        val weekId = buildWeekId(now)
        val startsAt = now
        val endsAt = now + MILLIS_IN_WEEK

        val defaults = listOf(
            mapOf(
                "id" to "$weekId-rate-3",
                "title" to "Valora 3 pintxos",
                "description" to "Deja tu rating en 3 pintxos esta semana",
                "actionType" to GamificationActionType.RATE_PINTXO.firestoreKey,
                "targetCount" to 3,
                "badgeId" to "badge_${weekId}_critic",
                "weekId" to weekId,
                "startsAt" to startsAt,
                "endsAt" to endsAt,
                "isActive" to true
            ),
            mapOf(
                "id" to "$weekId-upload-1",
                "title" to "Sube 1 pintxo",
                "description" to "Comparte una nueva recomendacion esta semana",
                "actionType" to GamificationActionType.UPLOAD_PINTXO.firestoreKey,
                "targetCount" to 1,
                "badgeId" to "badge_${weekId}_creator",
                "weekId" to weekId,
                "startsAt" to startsAt,
                "endsAt" to endsAt,
                "isActive" to true
            )
        )

        defaults.forEach { challenge ->
            val challengeId = challenge.getValue("id") as String
            val payload = challenge.toMutableMap().apply { remove("id") }
            challengesCollection.document(challengeId).set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun getGamificationState(uid: String): GamificationSnapshot {
        if (uid.isBlank()) {
            return GamificationSnapshot(UserGamification(), emptyList())
        }

        val now = System.currentTimeMillis()
        val activeChallenges = getActiveWeeklyChallenges(now)
        val userRef = usersCollection.document(uid)
        val userSnapshot = userRef.get().await()
        val userGamification = userSnapshot.toUserGamification()

        val progressCollection = userRef.collection(USER_WEEKLY_PROGRESS_COLLECTION)
        val progress = activeChallenges.map { challenge ->
            val progressDoc = progressCollection.document(challenge.id).get().await()
            val progressCount = progressDoc.getLong("progressCount")?.toInt()?.coerceAtLeast(0) ?: 0
            val completed = progressDoc.getBoolean("completed") ?: false

            WeeklyChallengeProgress(
                challengeId = challenge.id,
                title = challenge.title,
                description = challenge.description,
                badgeId = challenge.badgeId,
                targetCount = challenge.targetCount,
                progressCount = progressCount,
                completed = completed
            )
        }

        return GamificationSnapshot(
            user = userGamification,
            activeChallenges = progress
        )
    }

    suspend fun awardXpForAction(uid: String, actionType: GamificationActionType): AwardXpResult {
        if (uid.isBlank()) return AwardXpResult(0, 0, emptyList())

        val now = System.currentTimeMillis()
        val matchingChallenges = getActiveWeeklyChallenges(now)
            .filter { it.actionType == actionType }

        val userRef = usersCollection.document(uid)
        val progressCollection = userRef.collection(USER_WEEKLY_PROGRESS_COLLECTION)

        return firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            val currentGamification = userSnapshot.toUserGamification()

            val updatedXp = currentGamification.xp + actionType.xpReward
            val updatedStreak = GamificationRules.calculateUpdatedStreak(
                previousStreak = currentGamification.currentStreak,
                previousLastActionTimestamp = currentGamification.lastActionTimestamp,
                actionTimestamp = now
            )

            val mutableBadges = currentGamification.badges.toMutableSet()
            val unlockedBadges = mutableListOf<String>()

            matchingChallenges.forEach { challenge ->
                val progressRef = progressCollection.document(challenge.id)
                val progressSnapshot = transaction.get(progressRef)

                val alreadyCompleted = progressSnapshot.getBoolean("completed") == true
                val previousProgress = progressSnapshot.getLong("progressCount")
                    ?.toInt()
                    ?.coerceAtLeast(0)
                    ?: 0

                val nextProgress = if (alreadyCompleted) {
                    previousProgress
                } else {
                    (previousProgress + 1).coerceAtMost(challenge.targetCount)
                }

                val completedNow = !alreadyCompleted && nextProgress >= challenge.targetCount
                val finalCompleted = alreadyCompleted || completedNow

                val progressPayload = hashMapOf<String, Any>(
                    "challengeId" to challenge.id,
                    "title" to challenge.title,
                    "description" to challenge.description,
                    "badgeId" to challenge.badgeId,
                    "targetCount" to challenge.targetCount,
                    "progressCount" to nextProgress,
                    "completed" to finalCompleted,
                    "weekId" to challenge.weekId,
                    "lastUpdatedAt" to now
                )
                if (completedNow) {
                    progressPayload["completedAt"] = now
                }

                transaction.set(progressRef, progressPayload, SetOptions.merge())

                if (completedNow && mutableBadges.add(challenge.badgeId)) {
                    unlockedBadges.add(challenge.badgeId)
                }
            }

            val userPayload = hashMapOf<String, Any>(
                "xp" to updatedXp,
                "currentStreak" to updatedStreak,
                "lastActionTimestamp" to now
            )

            if (mutableBadges != currentGamification.badges.toSet()) {
                userPayload["badges"] = mutableBadges.toList()
            }

            transaction.set(userRef, userPayload, SetOptions.merge())

            AwardXpResult(
                xp = updatedXp,
                currentStreak = updatedStreak,
                unlockedBadges = unlockedBadges
            )
        }.await()
    }

    private suspend fun getActiveWeeklyChallenges(now: Long): List<WeeklyChallenge> {
        val snapshot = challengesCollection
            .whereEqualTo("isActive", true)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toWeeklyChallengeOrNull() }
            .filter { challenge ->
                now in challenge.startsAt..challenge.endsAt
            }
            .sortedBy { it.targetCount }
    }

    private fun buildWeekId(now: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }
}
