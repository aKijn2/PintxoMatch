package com.example.pintxomatch.data.repository.gamification

import com.example.pintxomatch.data.model.gamification.GamificationActionType
import com.example.pintxomatch.data.model.gamification.UserGamification
import com.example.pintxomatch.data.model.gamification.WeeklyChallenge
import com.example.pintxomatch.data.model.gamification.WeeklyChallengeProgress
import com.example.pintxomatch.data.model.gamification.toUserGamification
import com.example.pintxomatch.data.model.gamification.toWeeklyChallengeOrNull
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

interface GamificationGateway {
    suspend fun upsertDefaultWeeklyChallengesForCurrentWeek(now: Long = System.currentTimeMillis())
    suspend fun getGamificationState(uid: String): GamificationSnapshot
    suspend fun awardXpForAction(uid: String, actionType: GamificationActionType): AwardXpResult
}

internal data class ChallengeProgressState(
    val progressCount: Int = 0,
    val completed: Boolean = false
)

internal data class ChallengeProgressResult(
    val challenge: WeeklyChallenge,
    val progressCount: Int,
    val completed: Boolean,
    val completedNow: Boolean
)

internal data class AwardComputation(
    val updatedUser: UserGamification,
    val progressByChallenge: Map<String, ChallengeProgressResult>,
    val unlockedBadges: List<String>
)

internal fun computeAwardComputation(
    currentGamification: UserGamification,
    matchingChallenges: List<WeeklyChallenge>,
    previousProgressByChallenge: Map<String, ChallengeProgressState>,
    actionType: GamificationActionType,
    now: Long
): AwardComputation {
    val updatedXp = currentGamification.xp + actionType.xpReward
    val updatedStreak = GamificationRules.calculateUpdatedStreak(
        previousStreak = currentGamification.currentStreak,
        previousLastActionTimestamp = currentGamification.lastActionTimestamp,
        actionTimestamp = now
    )

    val mutableBadges = currentGamification.badges.toMutableSet()
    val unlockedBadges = mutableListOf<String>()
    val progressResults = linkedMapOf<String, ChallengeProgressResult>()

    matchingChallenges.forEach { challenge ->
        val previous = previousProgressByChallenge[challenge.id] ?: ChallengeProgressState()
        val nextProgress = if (previous.completed) {
            previous.progressCount
        } else {
            (previous.progressCount + 1).coerceAtMost(challenge.targetCount)
        }

        val completedNow = !previous.completed && nextProgress >= challenge.targetCount
        val finalCompleted = previous.completed || completedNow

        if (completedNow && mutableBadges.add(challenge.badgeId)) {
            unlockedBadges.add(challenge.badgeId)
        }

        progressResults[challenge.id] = ChallengeProgressResult(
            challenge = challenge,
            progressCount = nextProgress,
            completed = finalCompleted,
            completedNow = completedNow
        )
    }

    return AwardComputation(
        updatedUser = UserGamification(
            xp = updatedXp,
            currentStreak = updatedStreak,
            lastActionTimestamp = now,
            badges = mutableBadges.toList()
        ),
        progressByChallenge = progressResults,
        unlockedBadges = unlockedBadges
    )
}

class GamificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : GamificationGateway {

    private val usersCollection = firestore.collection(USERS_COLLECTION)
    private val challengesCollection = firestore.collection(WEEKLY_CHALLENGES_COLLECTION)

    override suspend fun upsertDefaultWeeklyChallengesForCurrentWeek(now: Long) {
        val weekId = buildWeekId(now)
        val startsAt = startOfWeekMillis(now)
        val endsAt = startsAt + MILLIS_IN_WEEK - 1L

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

    override suspend fun getGamificationState(uid: String): GamificationSnapshot {
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

    override suspend fun awardXpForAction(uid: String, actionType: GamificationActionType): AwardXpResult {
        if (uid.isBlank()) return AwardXpResult(0, 0, emptyList())

        val now = System.currentTimeMillis()
        val matchingChallenges = getActiveWeeklyChallenges(now)
            .filter { it.actionType == actionType }

        val userRef = usersCollection.document(uid)
        val progressCollection = userRef.collection(USER_WEEKLY_PROGRESS_COLLECTION)

        return firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            val currentGamification = userSnapshot.toUserGamification()

            val previousProgress = matchingChallenges.associate { challenge ->
                val progressRef = progressCollection.document(challenge.id)
                val progressSnapshot = transaction.get(progressRef)
                val state = ChallengeProgressState(
                    progressCount = progressSnapshot.getLong("progressCount")?.toInt()?.coerceAtLeast(0) ?: 0,
                    completed = progressSnapshot.getBoolean("completed") == true
                )
                challenge.id to state
            }

            val computation = computeAwardComputation(
                currentGamification = currentGamification,
                matchingChallenges = matchingChallenges,
                previousProgressByChallenge = previousProgress,
                actionType = actionType,
                now = now
            )

            matchingChallenges.forEach { challenge ->
                val progressRef = progressCollection.document(challenge.id)
                val progressResult = computation.progressByChallenge[challenge.id] ?: return@forEach

                val progressPayload = hashMapOf<String, Any>(
                    "challengeId" to challenge.id,
                    "title" to challenge.title,
                    "description" to challenge.description,
                    "badgeId" to challenge.badgeId,
                    "targetCount" to challenge.targetCount,
                    "progressCount" to progressResult.progressCount,
                    "completed" to progressResult.completed,
                    "weekId" to challenge.weekId,
                    "lastUpdatedAt" to now
                )
                if (progressResult.completedNow) {
                    progressPayload["completedAt"] = now
                }

                transaction.set(progressRef, progressPayload, SetOptions.merge())
            }

            val userPayload = hashMapOf<String, Any>(
                "xp" to computation.updatedUser.xp,
                "currentStreak" to computation.updatedUser.currentStreak,
                "lastActionTimestamp" to now
            )

            if (computation.updatedUser.badges.toSet() != currentGamification.badges.toSet()) {
                userPayload["badges"] = computation.updatedUser.badges
            }

            transaction.set(userRef, userPayload, SetOptions.merge())

            AwardXpResult(
                xp = computation.updatedUser.xp,
                currentStreak = computation.updatedUser.currentStreak,
                unlockedBadges = computation.unlockedBadges
            )
        }.await()
    }

    private suspend fun getActiveWeeklyChallenges(now: Long): List<WeeklyChallenge> {
        val currentWeekId = buildWeekId(now)
        val snapshot = challengesCollection
            .whereEqualTo("isActive", true)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toWeeklyChallengeOrNull() }
            .filter { challenge ->
                challenge.weekId == currentWeekId && now in challenge.startsAt..challenge.endsAt
            }
            .sortedBy { it.targetCount }
    }

    private fun buildWeekId(now: Long): String {
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = now
        }
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    private fun startOfWeekMillis(now: Long): Long {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
