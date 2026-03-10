package com.example.pintxomatch.data.repository

import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.data.model.LeaderboardPintxo
import com.example.pintxomatch.data.model.LeaderboardUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PintxoRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val pintxosCollection = firestore.collection("Pintxos")

    suspend fun getFeedPintxos(): List<Pintxo> {
        return try {
            val result = pintxosCollection.get().await()
            result.documents.map { doc ->
                Pintxo(
                    id = doc.id,
                    name = doc.getString("nombre") ?: "",
                    barName = doc.getString("bar") ?: "",
                    location = doc.getString("ubicacion") ?: "",
                    price = doc.getDouble("precio") ?: 0.0,
                    imageUrl = doc.getString("imageUrl") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserPintxos(uid: String): List<Pintxo> {
        return try {
            val result = pintxosCollection.whereEqualTo("uploaderUid", uid).get().await()
            result.documents.map { doc ->
                val ratingsRaw = doc.get("ratings") as? Map<*, *> ?: emptyMap<Any, Any>()
                val ratings = ratingsRaw.entries.mapNotNull { (k, v) ->
                    val userUid = k as? String ?: return@mapNotNull null
                    val rating = (v as? Number)?.toInt()?.coerceIn(1, 5) ?: return@mapNotNull null
                    userUid to rating
                }.toMap()
                val ratingCount = doc.getLong("ratingCount")?.toInt()?.coerceAtLeast(0) ?: ratings.size
                val ratingTotal = doc.getDouble("ratingTotal") ?: ratings.values.sumOf { it.toDouble() }
                val averageRating = if (ratingCount > 0) {
                    (doc.getDouble("averageRating") ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)
                } else 0.0

                Pintxo(
                    id = doc.id,
                    name = doc.getString("nombre") ?: "",
                    barName = doc.getString("bar") ?: "",
                    location = doc.getString("ubicacion") ?: "",
                    price = doc.getDouble("precio") ?: 0.0,
                    imageUrl = doc.getString("imageUrl") ?: "",
                    averageRating = averageRating,
                    ratingCount = ratingCount,
                    userRating = ratings[uid] ?: 0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLeaderboardData(): Pair<List<LeaderboardUser>, List<LeaderboardPintxo>> {
        return try {
            val result = pintxosCollection.get().await()
            
            val grouped = result.documents.groupBy { doc ->
                doc.getString("uploaderUid").orEmpty()
            }

            val users = grouped
                .filterKeys { it.isNotBlank() }
                .map { (uid, docs) ->
                    val first = docs.firstOrNull()
                    val uploaderName = first?.getString("uploaderDisplayName")
                        ?: first?.getString("uploaderEmail")?.substringBefore("@")
                        ?: "Usuario"

                    LeaderboardUser(
                        uid = uid,
                        displayName = uploaderName,
                        totalUploads = docs.size
                    )
                }
                .sortedByDescending { it.totalUploads }

            val topRatedPintxos = result.documents.mapNotNull { doc ->
                val rawRatings = doc.get("ratings") as? Map<*, *>
                val fallbackRatings = rawRatings?.values
                    ?.mapNotNull { (it as? Number)?.toInt()?.coerceIn(1, 5) }
                    .orEmpty()
                val ratingCount = doc.getLong("ratingCount")?.toInt()?.coerceAtLeast(0)
                    ?: fallbackRatings.size
                if (ratingCount <= 0) {
                    return@mapNotNull null
                }

                val ratingTotal = doc.getDouble("ratingTotal")
                    ?: fallbackRatings.sumOf { it.toDouble() }
                val averageRating = (doc.getDouble("averageRating")
                    ?: (ratingTotal / ratingCount)).coerceIn(0.0, 5.0)

                LeaderboardPintxo(
                    id = doc.id,
                    name = doc.getString("nombre") ?: "Sin nombre",
                    barName = doc.getString("bar") ?: "Bar desconocido",
                    averageRating = averageRating,
                    ratingCount = ratingCount
                )
            }.sortedWith(
                compareByDescending<LeaderboardPintxo> { it.averageRating }
                    .thenByDescending { it.ratingCount }
                    .thenBy { it.name.lowercase() }
            )

            Pair(users, topRatedPintxos)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }
}
