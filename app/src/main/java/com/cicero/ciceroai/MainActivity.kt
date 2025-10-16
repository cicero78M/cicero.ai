package com.cicero.ciceroai

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.cicero.ciceroai.llama.LlamaController
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        binding.modelUrlInput.setText(preferences.getString(KEY_LAST_URL, ""))

        binding.downloadButton.setOnClickListener {
            val url = binding.modelUrlInput.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                binding.modelUrlInput.error = getString(R.string.model_url_hint)
            } else {
                startDownload(url)
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
            binding.modelUrlInput.error = null
            binding.downloadButton.isEnabled = false
            binding.modelUrlInput.isEnabled = false
            val wasInferenceEnabled = binding.runButton.isEnabled
            binding.runButton.isEnabled = false
            binding.modelStatus.text = getString(R.string.model_status_downloading)

            try {
                val modelFile = controller.downloadModel(url, fileName)
                saveModelReference(modelFile, url)
                binding.modelStatus.text = getString(R.string.model_status_download_success, modelFile.name)
                prepareModel(modelFile)
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                binding.modelStatus.text = getString(R.string.model_status_download_failed, message)
                binding.modelUrlInput.error = message
                binding.runButton.isEnabled = wasInferenceEnabled
                clearModelReference()
            } finally {
                binding.downloadButton.isEnabled = true
                binding.modelUrlInput.isEnabled = true
            }
        }
    }

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

    private fun saveModelReference(file: File, url: String) {
        preferences.edit()
            .putString(KEY_MODEL_PATH, file.absolutePath)
            .putString(KEY_LAST_URL, url)
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
        private const val KEY_LAST_URL = "last_url"
    }
}
