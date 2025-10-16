package com.cicero.ciceroai.llama

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
}
