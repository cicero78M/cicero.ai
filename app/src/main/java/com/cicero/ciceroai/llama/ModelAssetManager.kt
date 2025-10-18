package com.cicero.ciceroai.llama

import android.content.Context
import com.cicero.ciceroai.R
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.DEFAULT_BUFFER_SIZE

class ModelAssetManager(
    private val context: Context,
    timeoutConfig: TimeoutConfig = TimeoutConfig()
) {
    private val dispatcher = Dispatchers.IO
    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(timeoutConfig.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutConfig.readTimeoutMinutes, TimeUnit.MINUTES)
        .writeTimeout(timeoutConfig.writeTimeoutMinutes, TimeUnit.MINUTES)
        .callTimeout(timeoutConfig.callTimeoutMinutes, TimeUnit.MINUTES)
        .build()

    companion object {
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val USER_AGENT = "CiceroAI-ModelDownloader/1.0"
        private const val ACCEPT_HEADER = "application/octet-stream, */*"
        private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_READ_TIMEOUT_MINUTES = 15L
        private const val DEFAULT_WRITE_TIMEOUT_MINUTES = 15L
        private const val DEFAULT_CALL_TIMEOUT_MINUTES = 20L
        private const val MAX_RETRY_AFTER_SECONDS = 600L
        private const val INITIAL_BACKOFF_MILLIS = 1_000L
        private const val MAX_BACKOFF_MILLIS = 60_000L
        private val RETRYABLE_STATUS_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }

    data class TimeoutConfig(
        val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
        val readTimeoutMinutes: Long = DEFAULT_READ_TIMEOUT_MINUTES,
        val writeTimeoutMinutes: Long = DEFAULT_WRITE_TIMEOUT_MINUTES,
        val callTimeoutMinutes: Long = DEFAULT_CALL_TIMEOUT_MINUTES
    )

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

    suspend fun downloadModel(
        url: String,
        fileName: String,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        onStatus: suspend (message: String) -> Unit = {}
    ): File = withContext(dispatcher) {
        require(url.startsWith("http", ignoreCase = true)) {
            "URL harus menggunakan skema HTTP atau HTTPS"
        }

        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val safeName = File(fileName).name.ifBlank { throw IOException("Nama berkas tidak valid") }
        val targetFile = File(modelsDir, safeName)
        val tempPrefix = safeName.substringBefore('.').takeIf { it.length >= 3 } ?: "model"
        var attempt = 0
        var waitCount = 0
        var lastError: IOException? = null

        onStatus(context.getString(R.string.model_status_downloading))

        attemptLoop@ while (attempt < MAX_DOWNLOAD_ATTEMPTS) {
            val tempFile = File.createTempFile(tempPrefix, ".download", modelsDir)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", ACCEPT_HEADER)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in RETRYABLE_STATUS_CODES) {
                            val retryAfterSeconds = parseRetryAfterSeconds(response.header("Retry-After"))
                            val delayMillis = computeRetryDelayMillis(retryAfterSeconds, waitCount)
                            waitCount++
                            tempFile.delete()

                            if (delayMillis > 0L) {
                                val formattedDuration = formatDelayMessage(delayMillis)
                                onStatus(
                                    context.getString(
                                        R.string.model_status_download_waiting_retry,
                                        formattedDuration
                                    )
                                )
                                delay(delayMillis)
                                onStatus(context.getString(R.string.model_status_downloading))
                            }

                            continue@attemptLoop
                        }

                        throw IOException(
                            "Gagal mengunduh model. Kode respons: ${response.code}"
                        )
                    }

                    val body = response.body ?: throw IOException("Respons tidak memiliki konten")
                    val totalBytes = body.contentLength().takeIf { it > 0L }
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, totalBytes)
                            }
                            output.flush()

                            if (totalBytes != null && downloaded != totalBytes) {
                                throw IOException(
                                    "Unduhan terputus. Hanya menerima $downloaded dari $totalBytes byte"
                                )
                            }
                        }
                    }
                    waitCount = 0
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                return@withContext targetFile
            } catch (error: IOException) {
                tempFile.delete()
                lastError = error
                attempt++
                waitCount = 0
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) {
                    throw error
                }
            }
        }

        throw lastError ?: IOException("Unduhan gagal tanpa pengecualian yang diketahui")
    }

    private fun parseRetryAfterSeconds(headerValue: String?): Long? {
        val trimmed = headerValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.toLongOrNull()?.coerceAtLeast(0L)
    }

    private fun computeRetryDelayMillis(retryAfterSeconds: Long?, waitCount: Int): Long {
        val fromHeader = retryAfterSeconds
            ?.coerceAtMost(MAX_RETRY_AFTER_SECONDS)
            ?.takeIf { it > 0L }
        if (fromHeader != null) {
            return TimeUnit.SECONDS.toMillis(fromHeader)
        }

        val exponent = waitCount.coerceAtMost(10)
        val backoff = INITIAL_BACKOFF_MILLIS * (1L shl exponent)
        return backoff.coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    private fun formatDelayMessage(delayMillis: Long): String {
        val totalSeconds = (delayMillis / 1000L).coerceAtLeast(1L)
        val minutes = (totalSeconds / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        return when {
            minutes > 0 && seconds > 0 -> {
                val minutesText = context.resources.getQuantityString(
                    R.plurals.download_retry_minutes,
                    minutes,
                    minutes
                )
                val secondsText = context.resources.getQuantityString(
                    R.plurals.download_retry_seconds,
                    seconds,
                    seconds
                )
                context.getString(
                    R.string.download_retry_minutes_seconds,
                    minutesText,
                    secondsText
                )
            }

            minutes > 0 -> context.resources.getQuantityString(
                R.plurals.download_retry_minutes,
                minutes,
                minutes
            )

            else -> context.resources.getQuantityString(
                R.plurals.download_retry_seconds,
                seconds,
                seconds
            )
        }
    }
}
