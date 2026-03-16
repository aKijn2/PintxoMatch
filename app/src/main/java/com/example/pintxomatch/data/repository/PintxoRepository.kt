package com.example.pintxomatch.data.repository

import android.content.Context
import android.net.Uri
import com.example.pintxomatch.data.model.Pintxo
import com.example.pintxomatch.data.model.LeaderboardPintxo
import com.example.pintxomatch.data.model.LeaderboardUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class PintxoMutationResult(
    val isSuccess: Boolean,
    val userMessage: String,
    val cloudinaryCleanupQueued: Boolean = false
)

class PintxoRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val pintxosCollection = firestore.collection("Pintxos")
    private val cloudinaryCleanupCollection = firestore.collection("CloudinaryDeletionQueue")

    suspend fun updateUserPintxo(
        context: Context,
        pintxoId: String,
        userUid: String,
        nombrePintxo: String,
        nombreBar: String,
        ubicacion: String,
        precio: String,
        newImageUri: Uri?
    ): PintxoMutationResult {
        return try {
            val document = pintxosCollection.document(pintxoId).get().await()
            if (!document.exists()) {
                return PintxoMutationResult(false, "El pintxo ya no existe")
            }

            if (document.getString("uploaderUid") != userUid) {
                return PintxoMutationResult(false, "No tienes permiso para editar este pintxo")
            }

            val previousImageUrl = document.getString("imageUrl")
            val previousPublicId = document.getString("imagePublicId")
                ?: ImageRepository.extractPublicIdFromUrl(previousImageUrl)
            val previousDeleteToken = document.getString("imageDeleteToken")
            val previousDeleteTokenCreatedAt = document.getLong("imageDeleteTokenCreatedAt")

            val uploadResult = if (newImageUri != null) {
                val uploadAttempt = ImageRepository.uploadImageAttempt(
                    context = context,
                    uri = newImageUri,
                    preferredPublicId = previousPublicId,
                    requireOverwriteWhenPreferredPublicId = false
                )
                uploadAttempt.result
                    ?: return PintxoMutationResult(
                        false,
                        uploadAttempt.errorMessage ?: "Error al subir la nueva imagen"
                    )
            } else {
                null
            }

            val imageReplacedInPlace = uploadResult != null
                && !previousPublicId.isNullOrBlank()
                && uploadResult.publicId == previousPublicId

            val updates = mutableMapOf<String, Any>(
                "nombre" to nombrePintxo,
                "bar" to nombreBar,
                "ubicacion" to ubicacion,
                "precio" to (precio.toDoubleOrNull() ?: 0.0)
            )

            uploadResult?.let { uploaded ->
                updates["imageUrl"] = uploaded.secureUrl
                uploaded.publicId?.let { updates["imagePublicId"] = it }
                uploaded.deleteToken?.let { updates["imageDeleteToken"] = it }
                updates["imageDeleteTokenCreatedAt"] = uploaded.uploadedAtMillis
            }

            pintxosCollection.document(pintxoId).update(updates).await()

            val cleanupQueued = if (uploadResult != null && !imageReplacedInPlace) {
                cleanupReplacedImage(
                    pintxoId = pintxoId,
                    imageUrl = previousImageUrl,
                    publicId = previousPublicId,
                    deleteToken = previousDeleteToken,
                    deleteTokenCreatedAt = previousDeleteTokenCreatedAt
                )
            } else {
                false
            }

            val successMessage = if (cleanupQueued) {
                "Pintxo actualizado. Borrado de imagen anterior en cola"
            } else {
                "Pintxo actualizado"
            }

            PintxoMutationResult(true, successMessage, cleanupQueued)
        } catch (e: Exception) {
            PintxoMutationResult(false, e.localizedMessage ?: "Error al actualizar el pintxo")
        }
    }

    suspend fun deleteUserPintxo(
        pintxoId: String,
        userUid: String
    ): PintxoMutationResult {
        return try {
            val document = pintxosCollection.document(pintxoId).get().await()
            if (!document.exists()) {
                return PintxoMutationResult(false, "El pintxo ya no existe")
            }

            if (document.getString("uploaderUid") != userUid) {
                return PintxoMutationResult(false, "No tienes permiso para borrar este pintxo")
            }

            val imageUrl = document.getString("imageUrl")
            val publicId = document.getString("imagePublicId")
                ?: ImageRepository.extractPublicIdFromUrl(imageUrl)
            val deleteToken = document.getString("imageDeleteToken")
            val deleteTokenCreatedAt = document.getLong("imageDeleteTokenCreatedAt")

            pintxosCollection.document(pintxoId).delete().await()

            val cleanupQueued = queueOrDeleteCloudinaryImage(
                pintxoId = pintxoId,
                imageUrl = imageUrl,
                publicId = publicId,
                deleteToken = deleteToken,
                deleteTokenCreatedAt = deleteTokenCreatedAt,
                requestedByUid = userUid,
                reason = "pintxo_deleted"
            )

            PintxoMutationResult(true, "Pintxo borrado", cleanupQueued)
        } catch (e: Exception) {
            PintxoMutationResult(false, e.localizedMessage ?: "Error al borrar el pintxo")
        }
    }

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
                    imageUrl = doc.getString("imageUrl") ?: "",
                    uploaderUid = doc.getString("uploaderUid") ?: "",
                    uploaderDisplayName = doc.getString("uploaderDisplayName") ?: "Usuario Anónimo",
                    uploaderPhotoUrl = doc.getString("uploaderPhotoUrl") ?: ""
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
                    userRating = ratings[uid] ?: 0,
                    uploaderUid = doc.getString("uploaderUid") ?: "",
                    uploaderDisplayName = doc.getString("uploaderDisplayName") ?: "Usuario Anónimo",
                    uploaderPhotoUrl = doc.getString("uploaderPhotoUrl") ?: ""
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

            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val currentPhotoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString().orEmpty()
            val currentDisplayName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

            val users = grouped
                .filterKeys { it.isNotBlank() }
                .map { (uid, docs) ->
                    val first = docs.firstOrNull()
                    val uploaderName = first?.getString("uploaderDisplayName")
                        ?.takeIf { it.isNotBlank() }
                        ?: if (uid == currentUid && currentDisplayName.isNotBlank()) currentDisplayName
                        else first?.getString("uploaderEmail")?.substringBefore("@")
                        ?: "Usuario"
                    val photoUrl = first?.getString("uploaderPhotoUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: if (uid == currentUid) currentPhotoUrl else ""

                    // Enrich existing documents that are missing the photo or display name
                    if (uid == currentUid) {
                        val needsUpdate = (first?.getString("uploaderPhotoUrl").isNullOrBlank() && currentPhotoUrl.isNotBlank())
                            || (first?.getString("uploaderDisplayName").isNullOrBlank() && currentDisplayName.isNotBlank())
                        if (needsUpdate) {
                            val update = mutableMapOf<String, Any>()
                            if (currentPhotoUrl.isNotBlank()) update["uploaderPhotoUrl"] = currentPhotoUrl
                            if (currentDisplayName.isNotBlank()) update["uploaderDisplayName"] = currentDisplayName
                            docs.forEach { doc ->
                                pintxosCollection.document(doc.id).update(update)
                            }
                        }
                    }

                    LeaderboardUser(
                        uid = uid,
                        displayName = uploaderName,
                        totalUploads = docs.size,
                        profileImageUrl = photoUrl
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

    private suspend fun cleanupReplacedImage(
        pintxoId: String,
        imageUrl: String?,
        publicId: String?,
        deleteToken: String?,
        deleteTokenCreatedAt: Long?
    ): Boolean {
        if (imageUrl.isNullOrBlank() && publicId.isNullOrBlank()) return false

        return queueOrDeleteCloudinaryImage(
            pintxoId = pintxoId,
            imageUrl = imageUrl,
            publicId = publicId,
            deleteToken = deleteToken,
            deleteTokenCreatedAt = deleteTokenCreatedAt,
            requestedByUid = null,
            reason = "pintxo_image_replaced"
        )
    }

    private suspend fun queueOrDeleteCloudinaryImage(
        pintxoId: String,
        imageUrl: String?,
        publicId: String?,
        deleteToken: String?,
        deleteTokenCreatedAt: Long?,
        requestedByUid: String?,
        reason: String
    ): Boolean {
        val canDeleteByToken = !deleteToken.isNullOrBlank()
        if (canDeleteByToken) {
            val deleted = ImageRepository.deleteImageByToken(deleteToken!!)
            if (deleted) {
                return false
            }
        }

        if (publicId.isNullOrBlank() && imageUrl.isNullOrBlank()) {
            return false
        }

        val queueEntry = hashMapOf<String, Any>(
            "pintxoId" to pintxoId,
            "reason" to reason,
            "status" to "pending",
            "requestedAt" to FieldValue.serverTimestamp()
        )
        if (!imageUrl.isNullOrBlank()) queueEntry["imageUrl"] = imageUrl
        if (!publicId.isNullOrBlank()) queueEntry["publicId"] = publicId
        if (!requestedByUid.isNullOrBlank()) queueEntry["requestedByUid"] = requestedByUid

        cloudinaryCleanupCollection.add(queueEntry).await()
        return true
    }
}
