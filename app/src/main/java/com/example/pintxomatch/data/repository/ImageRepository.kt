package com.example.pintxomatch.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

object ImageRepository {
    private const val CLOUD_NAME = "dm99kc8ky"
    private const val UPLOAD_PRESET = "pintxomatch"
    private const val MAX_DIMENSION_PX = 1600
    private const val TARGET_MAX_BYTES = 700 * 1024
    private const val INITIAL_JPEG_QUALITY = 82
    private const val MIN_JPEG_QUALITY = 55
    private const val DELETE_TOKEN_WINDOW_MINUTES = 10L

    data class ImageUploadResult(
        val secureUrl: String,
        val publicId: String?,
        val deleteToken: String?,
        val uploadedAtMillis: Long
    )

    data class ImageUploadAttempt(
        val result: ImageUploadResult?,
        val errorMessage: String? = null
    )

    private data class UploadPayload(
        val bytes: ByteArray,
        val contentType: String,
        val fileExtension: String
    )

    suspend fun uploadImage(context: Context, uri: Uri, folder: String = "pintxomatch"): String? {
        return uploadImageResult(context, uri, folder)?.secureUrl
    }

    suspend fun uploadImageAttempt(
        context: Context,
        uri: Uri,
        folder: String = "pintxomatch",
        preferredPublicId: String? = null,
        requireOverwriteWhenPreferredPublicId: Boolean = false
    ): ImageUploadAttempt {
        val payload = withContext(Dispatchers.IO) {
            optimizeImageForUpload(context, uri)
        } ?: return ImageUploadAttempt(result = null, errorMessage = "No se pudo leer la imagen seleccionada")

        return uploadBytes(
            payload = payload,
            folder = folder,
            preferredPublicId = preferredPublicId,
            requireOverwriteWhenPreferredPublicId = requireOverwriteWhenPreferredPublicId
        )
    }

    suspend fun uploadImageResult(
        context: Context,
        uri: Uri,
        folder: String = "pintxomatch",
        preferredPublicId: String? = null,
        requireOverwriteWhenPreferredPublicId: Boolean = false
    ): ImageUploadResult? {
        return uploadImageAttempt(
            context = context,
            uri = uri,
            folder = folder,
            preferredPublicId = preferredPublicId,
            requireOverwriteWhenPreferredPublicId = requireOverwriteWhenPreferredPublicId
        ).result
    }

    private fun optimizeImageForUpload(context: Context, uri: Uri): UploadPayload? {
        return try {
            val originalPayload = createOriginalPayload(context, uri) ?: return null
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(
                originalPayload.bytes,
                0,
                originalPayload.bytes.size,
                boundsOptions
            )

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return originalPayload
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = boundsOptions.outWidth,
                    height = boundsOptions.outHeight,
                    reqWidth = MAX_DIMENSION_PX,
                    reqHeight = MAX_DIMENSION_PX
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = BitmapFactory.decodeByteArray(
                originalPayload.bytes,
                0,
                originalPayload.bytes.size,
                decodeOptions
            ) ?: return originalPayload

            val resizedBitmap = resizeIfNeeded(decodedBitmap, MAX_DIMENSION_PX)
            if (resizedBitmap !== decodedBitmap) {
                decodedBitmap.recycle()
            }

            val compressedBytes = compressBitmapToJpeg(resizedBitmap).also {
                resizedBitmap.recycle()
            }
            UploadPayload(
                bytes = compressedBytes,
                contentType = "image/jpeg",
                fileExtension = "jpg"
            )
        } catch (_: Exception) {
            // Keep upload flow working even if optimization fails for a specific image.
            createOriginalPayload(context, uri)
        }
    }

    private fun createOriginalPayload(context: Context, uri: Uri): UploadPayload? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        return UploadPayload(
            bytes = bytes,
            contentType = mimeType,
            fileExtension = mimeTypeToExtension(mimeType)
        )
    }

    private fun mimeTypeToExtension(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "bin"
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        var quality = INITIAL_JPEG_QUALITY
        var result = ByteArray(0)

        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            result = out.toByteArray()
            quality -= 7
        } while (result.size > TARGET_MAX_BYTES && quality >= MIN_JPEG_QUALITY)

        return result
    }

    fun extractPublicIdFromUrl(imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null

        return try {
            val uploadMarker = "/image/upload/"
            val markerIndex = imageUrl.indexOf(uploadMarker)
            if (markerIndex == -1) return null

            val pathAfterUpload = imageUrl.substring(markerIndex + uploadMarker.length)
            val segments = pathAfterUpload.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null

            val publicIdSegments = if (segments.first().startsWith("v") && segments.first().drop(1).all(Char::isDigit)) {
                segments.drop(1)
            } else {
                segments
            }

            if (publicIdSegments.isEmpty()) return null

            val rawPublicId = publicIdSegments.joinToString("/")
            URLDecoder.decode(rawPublicId.substringBeforeLast('.', rawPublicId), Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }

    fun isDeleteTokenFresh(uploadedAtMillis: Long?): Boolean {
        if (uploadedAtMillis == null || uploadedAtMillis <= 0L) return false
        val ageMillis = System.currentTimeMillis() - uploadedAtMillis
        return ageMillis in 0 until TimeUnit.MINUTES.toMillis(DELETE_TOKEN_WINDOW_MINUTES)
    }

    suspend fun deleteImageByToken(deleteToken: String): Boolean = withContext(Dispatchers.IO) {
        if (deleteToken.isBlank()) return@withContext false

        try {
            val connection = (URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/delete_by_token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { writer ->
                writer.write("token=$deleteToken")
                writer.flush()
            }

            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun uploadBytes(
        payload: UploadPayload,
        folder: String,
        preferredPublicId: String?,
        requireOverwriteWhenPreferredPublicId: Boolean
    ): ImageUploadAttempt = withContext(Dispatchers.IO) {
        fun tryUploadSequence(targetPublicId: String?): ImageUploadAttempt {
            val firstAttempt = uploadBytesInternal(
                payload = payload,
                folder = folder,
                includeDeleteToken = true,
                preferredPublicId = targetPublicId
            )
            if (firstAttempt.result != null) {
                return firstAttempt
            }

            val secondAttempt = uploadBytesInternal(
                payload = payload,
                folder = folder,
                includeDeleteToken = false,
                preferredPublicId = targetPublicId
            )
            if (secondAttempt.result != null) {
                return secondAttempt
            }

            val finalMessage = secondAttempt.errorMessage ?: firstAttempt.errorMessage ?: "Error al subir la imagen"
            return ImageUploadAttempt(result = null, errorMessage = finalMessage)
        }

        // First try replacing existing image in-place when a previous Cloudinary public_id is available.
        if (!preferredPublicId.isNullOrBlank()) {
            val overwriteAttempt = tryUploadSequence(preferredPublicId)
            if (overwriteAttempt.result != null) {
                return@withContext overwriteAttempt
            }

            if (requireOverwriteWhenPreferredPublicId) {
                return@withContext overwriteAttempt
            }
        }

        // Fallback to regular upload if overwrite is not allowed by preset/policy.
        tryUploadSequence(null)
    }

    private fun uploadBytesInternal(
        payload: UploadPayload,
        folder: String,
        includeDeleteToken: Boolean,
        preferredPublicId: String?
    ): ImageUploadAttempt {
        val fileName = "gallery_${System.currentTimeMillis()}.${payload.fileExtension}"
        return try {
            val boundary = "Boundary-${UUID.randomUUID()}"
            val url = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { out ->
                fun writeTextPart(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.writeBytes(value)
                    out.writeBytes("\r\n")
                }

                writeTextPart("upload_preset", UPLOAD_PRESET)
                if (preferredPublicId.isNullOrBlank()) {
                    writeTextPart("folder", folder)
                } else {
                    writeTextPart("public_id", preferredPublicId)
                    writeTextPart("overwrite", "true")
                    writeTextPart("invalidate", "true")
                }
                if (includeDeleteToken) {
                    writeTextPart("return_delete_token", "true")
                }

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                out.writeBytes("Content-Type: ${payload.contentType}\r\n\r\n")
                out.write(payload.bytes)
                out.writeBytes("\r\n")
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            }

            if (responseCode !in 200..299 || response.isNullOrBlank()) {
                ImageUploadAttempt(
                    result = null,
                    errorMessage = buildUploadErrorMessage(responseCode, response, includeDeleteToken)
                )
            } else {
                val payload = JSONObject(response)
                val secureUrl = payload.optString("secure_url")
                if (secureUrl.isBlank()) {
                    ImageUploadAttempt(result = null, errorMessage = "Cloudinary no devolvió secure_url")
                } else {
                    ImageUploadAttempt(
                        result = ImageUploadResult(
                            secureUrl = secureUrl,
                            publicId = payload.optString("public_id").ifBlank { extractPublicIdFromUrl(secureUrl) },
                            deleteToken = payload.optString("delete_token").ifBlank { null },
                            uploadedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ImageUploadAttempt(result = null, errorMessage = e.localizedMessage ?: "Error de red al subir la imagen")
        }
    }

    private fun buildUploadErrorMessage(
        responseCode: Int,
        response: String?,
        includeDeleteToken: Boolean
    ): String {
        if (!response.isNullOrBlank()) {
            try {
                val payload = JSONObject(response)
                val errorMessage = payload.optJSONObject("error")?.optString("message").orEmpty()
                if (errorMessage.isNotBlank()) {
                    return if (includeDeleteToken) {
                        "Cloudinary: $errorMessage"
                    } else {
                        "Cloudinary: $errorMessage"
                    }
                }
            } catch (_: Exception) {
            }
        }

        return "Error al subir la imagen (HTTP $responseCode)"
    }
}
