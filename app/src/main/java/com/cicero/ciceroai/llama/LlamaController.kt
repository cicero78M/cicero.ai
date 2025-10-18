package com.cicero.ciceroai.llama

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class LlamaController(context: Context) {
    private val appContext = context.applicationContext
    private val assetManager = ModelAssetManager(appContext)
    private val dispatcher = Dispatchers.Default
    private var session: LlamaSession? = null
    private val _inferenceProgress = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val inferenceProgress: SharedFlow<String> = _inferenceProgress.asSharedFlow()

    suspend fun prepareSession(
        modelFile: File,
        runtimeConfig: RuntimeConfig
    ): LlamaSession = withContext(dispatcher) {
        session
            ?.takeIf {
                it.modelFile.absolutePath == modelFile.absolutePath &&
                    it.modelFile.exists() &&
                    it.runtimeConfig == runtimeConfig
            }
            ?.let { return@withContext it }
        session?.let {
            LlamaBridge.nativeRelease(it.handle)
            session = null
        }

        val sanitizedConfig = runtimeConfig.sanitized()
        val handle = LlamaBridge.nativeInit(modelFile.absolutePath, sanitizedConfig)
        val newSession = LlamaSession(handle, modelFile, sanitizedConfig)
        session = newSession
        return@withContext newSession
    }

    suspend fun prepareSessionFromAsset(
        assetName: String,
        runtimeConfig: RuntimeConfig
    ): LlamaSession {
        val modelFile = assetManager.copyModelIfNeeded(assetName)
        return prepareSession(modelFile, runtimeConfig)
    }

    suspend fun runInference(
        prompt: String,
        samplingConfig: SamplingConfig
    ): String = withContext(dispatcher) {
        val currentSession =
            session ?: error("Model belum siap. Panggil prepareSession() terlebih dahulu.")
        LlamaBridge.nativeCompletionWithProgress(
            currentSession.handle,
            prompt,
            samplingConfig
        ) { token ->
            _inferenceProgress.tryEmit(token)
        }
    }

    suspend fun listBundledModels(): List<String> = assetManager.listBundledModels()

    suspend fun downloadModel(
        url: String,
        fileName: String,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): File = assetManager.downloadModel(url, fileName, onProgress)

    fun release() {
        session?.let {
            LlamaBridge.nativeRelease(it.handle)
        }
        session = null
    }
}

data class LlamaSession(
    val handle: Long,
    val modelFile: File,
    val runtimeConfig: RuntimeConfig
)
