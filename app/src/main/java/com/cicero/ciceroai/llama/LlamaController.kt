package com.cicero.ciceroai.llama

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaController(context: Context) {
    private val appContext = context.applicationContext
    private val assetManager = ModelAssetManager(appContext)
    private val dispatcher = Dispatchers.Default
    private var session: LlamaSession? = null

    suspend fun prepareSession(
        modelFile: File,
        threadCount: Int,
        contextSize: Int
    ): LlamaSession = withContext(dispatcher) {
        session
            ?.takeIf { it.modelFile.absolutePath == modelFile.absolutePath && it.modelFile.exists() }
            ?.let { return@withContext it }
        session?.let { LlamaBridge.nativeRelease(it.handle) }

        val handle = LlamaBridge.nativeInit(modelFile.absolutePath, threadCount, contextSize)
        return@withContext LlamaSession(handle, modelFile).also { session = it }
    }

    suspend fun prepareSessionFromAsset(
        assetName: String,
        threadCount: Int,
        contextSize: Int
    ): LlamaSession {
        val modelFile = assetManager.copyModelIfNeeded(assetName)
        return prepareSession(modelFile, threadCount, contextSize)
    }

    suspend fun runInference(prompt: String, maxTokens: Int): String = withContext(dispatcher) {
        val currentSession = session ?: error("Model belum siap. Panggil prepareSession() terlebih dahulu.")
        LlamaBridge.nativeCompletion(currentSession.handle, prompt, maxTokens)
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
    val modelFile: File
)
