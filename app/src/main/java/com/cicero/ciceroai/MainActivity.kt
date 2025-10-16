package com.cicero.ciceroai

import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.cicero.ciceroai.llama.LlamaController
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: LlamaController
    private var prepareJob: Job? = null
    private var downloadJob: Job? = null

    private val preferences by lazy {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = LlamaController(this)

        binding.runButton.isEnabled = false

        binding.modelDownloadButton.setOnClickListener {
            val savedModel = loadSavedModelFile()
            if (savedModel != null) {
                prepareModel(savedModel)
            } else {
                startDownload(MODEL_URL)
            }
        }

        binding.runButton.setOnClickListener {
            val prompt = binding.promptInput.text.toString().trim()
            if (prompt.isEmpty()) {
                binding.promptInput.error = getString(R.string.prompt_hint)
                return@setOnClickListener
            }

            binding.promptInput.error = null

            lifecycleScope.launch {
                binding.runButton.isEnabled = false
                binding.outputView.text = getString(R.string.inference_placeholder)
                try {
                    val result = controller.runInference(prompt, maxTokens = 256)
                    binding.outputView.text = result
                } catch (error: Exception) {
                    val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                    binding.outputView.text = message
                } finally {
                    binding.runButton.isEnabled = true
                }
            }
        }

        val savedModel = loadSavedModelFile()
        if (savedModel != null) {
            prepareModel(savedModel)
        } else {
            binding.modelStatus.text = getString(R.string.model_status_download_prompt)
        }
    }

    private fun loadSavedModelFile(): File? {
        val path = preferences.getString(KEY_MODEL_PATH, null) ?: return null
        val file = File(path)
        return if (file.exists()) {
            file
        } else {
            clearModelReference()
            null
        }
    }

    private fun startDownload(url: String) {
        val fileName = resolveFileName(url)
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            binding.modelDownloadButton.isEnabled = false
            val wasInferenceEnabled = binding.runButton.isEnabled
            binding.runButton.isEnabled = false
            binding.modelStatus.text = getString(R.string.model_status_downloading)
            binding.downloadProgressIndicator.isIndeterminate = true
            binding.downloadProgressIndicator.progress = 0
            binding.downloadProgressIndicator.isVisible = true
            binding.downloadProgressLabel.isVisible = true
            binding.downloadProgressLabel.text = getString(R.string.download_progress_placeholder)

            try {
                val modelFile = controller.downloadModel(url, fileName) { downloaded, total ->
                    withContext(Dispatchers.Main) {
                        updateDownloadProgress(downloaded, total)
                    }
                }
                saveModelReference(modelFile)
                binding.modelStatus.text = getString(R.string.model_status_download_success, modelFile.name)
                prepareModel(modelFile)
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                binding.modelStatus.text = getString(R.string.model_status_download_failed, message)
                binding.runButton.isEnabled = wasInferenceEnabled
                clearModelReference()
            } finally {
                binding.modelDownloadButton.isEnabled = true
                binding.downloadProgressIndicator.isVisible = false
                binding.downloadProgressLabel.isVisible = false
            }
        }
    }

    private fun updateDownloadProgress(downloaded: Long, total: Long?) {
        if (!binding.downloadProgressIndicator.isVisible) {
            binding.downloadProgressIndicator.isVisible = true
        }
        binding.downloadProgressIndicator.isIndeterminate = total == null
        if (total != null && total > 0L) {
            val progress = ((downloaded.toDouble() / total) * 100).coerceIn(0.0, 100.0)
            val progressInt = progress.roundToInt()
            binding.downloadProgressIndicator.progress = progressInt
            val downloadedLabel = formatFileSize(downloaded)
            val totalLabel = formatFileSize(total)
            binding.downloadProgressLabel.text = getString(
                R.string.download_progress_with_total,
                progressInt,
                downloadedLabel,
                totalLabel
            )
        } else {
            binding.downloadProgressIndicator.progress = 0
            binding.downloadProgressLabel.text = getString(
                R.string.download_progress_without_total,
                formatFileSize(downloaded)
            )
        }
        if (!binding.downloadProgressLabel.isVisible) {
            binding.downloadProgressLabel.isVisible = true
        }
    }

    private fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

    private fun prepareModel(file: File) {
        if (!file.exists()) {
            binding.modelStatus.text = getString(R.string.model_status_no_file)
            binding.runButton.isEnabled = false
            clearModelReference()
            return
        }

        prepareJob?.cancel()
        prepareJob = lifecycleScope.launch {
            binding.modelStatus.text = getString(R.string.model_status_loading, file.name)
            binding.runButton.isEnabled = false
            try {
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                controller.prepareSession(
                    modelFile = file,
                    threadCount = threads,
                    contextSize = 2_048
                )
                binding.modelStatus.text = getString(R.string.model_status_ready, file.name)
                binding.runButton.isEnabled = true
                binding.outputView.text = getString(R.string.inference_placeholder)
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                binding.modelStatus.text = message
                binding.runButton.isEnabled = false
            }
        }
    }

    private fun saveModelReference(file: File) {
        preferences.edit()
            .putString(KEY_MODEL_PATH, file.absolutePath)
            .apply()
    }

    private fun clearModelReference() {
        preferences.edit()
            .remove(KEY_MODEL_PATH)
            .apply()
    }

    private fun resolveFileName(url: String): String {
        val lastSegment = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
        return lastSegment ?: "model-${System.currentTimeMillis()}"
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        downloadJob?.cancel()
        controller.release()
        super.onDestroy()
    }

    companion object {
        private const val PREF_NAME = "cicero_model_storage"
        private const val KEY_MODEL_PATH = "model_path"
        private const val MODEL_URL = "https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    }
}
