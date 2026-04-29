package com.example.pintxomatch.data.repository.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.pintxomatch.BuildConfig
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
import java.net.URLEncoder
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
    private const val PROVIDER_LOCAL = "local"

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

        if (isUsingLocalProvider()) {
            return withContext(Dispatchers.IO) { uploadToLocalServer(payload) }
        }

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
            if (markerIndex == -1) {
                val lastSegment = imageUrl.substringAfterLast('/').substringBefore('?')
                if (lastSegment.isBlank()) return null
                return lastSegment.substringBeforeLast('.', lastSegment).takeIf { it.isNotBlank() }
            }

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

    fun normalizeImageUrlForCurrentProvider(imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return imageUrl
        if (!isUsingLocalProvider()) return imageUrl

        val rawUrl = imageUrl.trim()
        val base = BuildConfig.LOCAL_IMAGE_BASE_URL.trimEnd('/')

        // Local image-server URLs always expose files under /uploads.
        // Rewriting by suffix keeps old localhost/10.0.2.2/trycloudflare hosts working
        // after switching to a new tunnel domain.
        val uploadsPathIndex = rawUrl.indexOf("/uploads/", ignoreCase = true)
        if (uploadsPathIndex >= 0) {
            val suffix = rawUrl.substring(uploadsPathIndex)
            return "$base$suffix"
        }

        val legacyPrefixes = listOf(
            "http://localhost:8080",
            "https://localhost:8080",
            "http://10.0.2.2:8080",
            "https://10.0.2.2:8080"
        )

        val matchedPrefix = legacyPrefixes.firstOrNull { rawUrl.startsWith(it, ignoreCase = true) }
            ?: return imageUrl

        val suffix = rawUrl.substring(matchedPrefix.length)
        return if (suffix.isBlank()) base else "$base$suffix"
    }

    fun isDeleteTokenFresh(uploadedAtMillis: Long?): Boolean {
        if (uploadedAtMillis == null || uploadedAtMillis <= 0L) return false
        val ageMillis = System.currentTimeMillis() - uploadedAtMillis
        return ageMillis in 0 until TimeUnit.MINUTES.toMillis(DELETE_TOKEN_WINDOW_MINUTES)
    }

    suspend fun deleteImageByToken(deleteToken: String): Boolean = withContext(Dispatchers.IO) {
        if (deleteToken.isBlank()) return@withContext false

        if (isUsingLocalProvider()) {
            return@withContext deleteLocalImageById(deleteToken)
        }

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

    private fun isUsingLocalProvider(): Boolean {
        return BuildConfig.IMAGE_PROVIDER.equals(PROVIDER_LOCAL, ignoreCase = true)
    }

    private fun uploadToLocalServer(payload: UploadPayload): ImageUploadAttempt {
        return try {
            val boundary = "Boundary-${UUID.randomUUID()}"
            val baseUrl = BuildConfig.LOCAL_IMAGE_BASE_URL.trimEnd('/')
            val endpoint = URL("$baseUrl/api/images")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (BuildConfig.LOCAL_IMAGE_API_KEY.isNotBlank()) {
                    setRequestProperty("x-api-key", BuildConfig.LOCAL_IMAGE_API_KEY)
                }
            }

            val fileName = "gallery_${System.currentTimeMillis()}.${payload.fileExtension}"
            DataOutputStream(connection.outputStream).use { out ->
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
                ImageUploadAttempt(result = null, errorMessage = buildLocalUploadErrorMessage(responseCode, response))
            } else {
                val json = JSONObject(response)
                val imageUrl = json.optString("url")
                val imageId = json.optString("imageId").ifBlank { extractPublicIdFromUrl(imageUrl) }

                if (imageUrl.isBlank()) {
                    ImageUploadAttempt(result = null, errorMessage = "Servidor local no devolvió URL")
                } else {
                    ImageUploadAttempt(
                        result = ImageUploadResult(
                            secureUrl = imageUrl,
                            publicId = imageId,
                            deleteToken = imageId,
                            uploadedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ImageUploadAttempt(result = null, errorMessage = e.localizedMessage ?: "Error subiendo al servidor local")
        }
    }

    private fun buildLocalUploadErrorMessage(responseCode: Int, response: String?): String {
        if (!response.isNullOrBlank()) {
            try {
                val json = JSONObject(response)
                val message = json.optString("error")
                if (message.isNotBlank()) {
                    return "Servidor local: $message"
                }
            } catch (_: Exception) {
            }
        }
        return "Servidor local: error subiendo imagen (HTTP $responseCode)"
    }

    private fun deleteLocalImageById(imageId: String): Boolean {
        return try {
            val baseUrl = BuildConfig.LOCAL_IMAGE_BASE_URL.trimEnd('/')
            val encodedId = URLEncoder.encode(imageId, Charsets.UTF_8.name())
            val endpoint = URL("$baseUrl/api/images/$encodedId")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                if (BuildConfig.LOCAL_IMAGE_API_KEY.isNotBlank()) {
                    setRequestProperty("x-api-key", BuildConfig.LOCAL_IMAGE_API_KEY)
                }
            }

            val responseCode = connection.responseCode
            responseCode in 200..299 || responseCode == 404
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
