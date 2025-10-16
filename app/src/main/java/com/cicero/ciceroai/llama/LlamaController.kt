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
        assetName: String,
        threadCount: Int,
        contextSize: Int
    ): LlamaSession = withContext(dispatcher) {
        session?.takeIf { it.assetName == assetName }?.let { return@withContext it }
        session?.let { LlamaBridge.nativeRelease(it.handle) }

        val modelFile = assetManager.copyModelIfNeeded(assetName)
        val handle = LlamaBridge.nativeInit(modelFile.absolutePath, threadCount, contextSize)
        return@withContext LlamaSession(handle, assetName, modelFile).also { session = it }
    }

    suspend fun runInference(prompt: String, maxTokens: Int): String = withContext(dispatcher) {
        val currentSession = session ?: error("Model belum siap. Panggil prepareSession() terlebih dahulu.")
        LlamaBridge.nativeCompletion(currentSession.handle, prompt, maxTokens)
    }

    suspend fun listBundledModels(): List<String> = assetManager.listBundledModels()

    fun release() {
        session?.let {
            LlamaBridge.nativeRelease(it.handle)
        }
        session = null
    }
}

data class LlamaSession(
    val handle: Long,
    val assetName: String,
    val modelFile: File
)
