package com.cicero.ciceroai.llama

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelAssetManager(private val context: Context) {
    private val dispatcher = Dispatchers.IO

    suspend fun copyModelIfNeeded(assetName: String): File = withContext(dispatcher) {
        val targetDir = File(context.filesDir, "models").apply { mkdirs() }
        val targetFile = File(targetDir, assetName)
        if (!targetFile.exists()) {
            val available = context.assets.list("models")?.toSet().orEmpty()
            require(assetName in available) {
                "Model $assetName tidak ditemukan di assets/models"
            }

            context.assets.open("models/$assetName").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        targetFile
    }

    suspend fun listBundledModels(): List<String> = withContext(dispatcher) {
        context.assets.list("models")?.sorted()?.toList().orEmpty()
    }

    suspend fun downloadModel(url: String, fileName: String): File = withContext(dispatcher) {
        require(url.startsWith("http", ignoreCase = true)) {
            "URL harus menggunakan skema HTTP atau HTTPS"
        }

        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val safeName = File(fileName).name.ifBlank { throw IOException("Nama berkas tidak valid") }
        val targetFile = File(modelsDir, safeName)
        val tempPrefix = safeName.substringBefore('.').takeIf { it.length >= 3 } ?: "model"
        val tempFile = File.createTempFile(tempPrefix, ".download", modelsDir)

        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as? HttpURLConnection
                ?: throw IOException("Tidak dapat membuka koneksi HTTP/HTTPS")

            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Gagal mengunduh model. Kode respons: $code")
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            targetFile
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }
}
