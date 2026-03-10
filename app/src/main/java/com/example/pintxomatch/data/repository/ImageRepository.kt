package com.example.pintxomatch.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ImageRepository {
    private const val CLOUD_NAME = "dm99kc8ky"
    private const val UPLOAD_PRESET = "pintxomatch"

    suspend fun uploadImage(context: Context, uri: Uri, folder: String = "pintxomatch"): String? {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return null

        return uploadBytes(bytes, folder)
    }

    private suspend fun uploadBytes(bytes: ByteArray, folder: String): String? = withContext(Dispatchers.IO) {
        val fileName = "gallery_${System.currentTimeMillis()}.jpg"
        try {
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
                writeTextPart("folder", folder)

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
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
                null
            } else {
                JSONObject(response).optString("secure_url")
            }
        } catch (_: Exception) {
            null
        }
    }
}
