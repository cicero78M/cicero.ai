package com.cicero.ciceroai

import android.app.Application
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cicero.ciceroai.llama.LlamaController
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = LlamaController(application)
    private val preferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val context: Application
        get() = getApplication()

    private val defaultEngineName = context.getString(R.string.settings_engine_default)
    private val defaultPromptTemplate = context.getString(R.string.settings_prompt_default)

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentPage = MainPage.HOME,
            modelStatus = context.getString(R.string.model_status_download_prompt),
            downloadProgressVisible = false,
            downloadProgressIndeterminate = true,
            downloadProgressValue = 0,
            downloadProgressLabel = context.getString(R.string.download_progress_placeholder),
            isDownloadButtonEnabled = true,
            isRunButtonEnabled = false,
            promptError = null,
            outputText = context.getString(R.string.inference_placeholder),
            logMessages = emptyList(),
            downloadedModels = emptyList(),
            engineSetting = loadEngineSetting(),
            promptTemplateSetting = loadPromptTemplate()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var prepareJob: Job? = null
    private var downloadJob: Job? = null

    init {
        refreshDownloadedModels()
        val savedModel = loadSavedModelFile()
        if (savedModel != null) {
            prepareModel(savedModel)
        }
    }

    fun onDownloadButtonClicked() {
        val savedModel = loadSavedModelFile()
        if (savedModel != null) {
            prepareModel(savedModel)
        } else {
            startDownload(MODEL_URL)
        }
    }

    fun runInference(prompt: String) {
        val sanitizedPrompt = prompt.trim()
        if (sanitizedPrompt.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    promptError = context.getString(R.string.prompt_hint),
                    logMessages = state.logMessages + context.getString(R.string.log_inference_prompt_empty)
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    promptError = null,
                    isRunButtonEnabled = false,
                    outputText = context.getString(R.string.inference_placeholder),
                    logMessages = listOf(
                        context.getString(R.string.log_inference_started),
                        context.getString(R.string.log_inference_preparing_prompt)
                    )
                )
            }

            try {
                appendLog(context.getString(R.string.log_inference_requesting_completion, 256))
                val result = controller.runInference(sanitizedPrompt, maxTokens = 256)
                appendLog(context.getString(R.string.log_inference_success))
                _uiState.update { state ->
                    state.copy(outputText = result)
                }
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                appendLog(context.getString(R.string.log_inference_error, message))
                _uiState.update { state ->
                    state.copy(outputText = message)
                }
            } finally {
                _uiState.update { state ->
                    state.copy(isRunButtonEnabled = true)
                }
            }
        }
    }

    private fun appendLog(message: String) {
        _uiState.update { state ->
            state.copy(logMessages = state.logMessages + message)
        }
    }

    fun onPromptTextChanged() {
        if (_uiState.value.promptError != null) {
            _uiState.update { state -> state.copy(promptError = null) }
        }
    }

    fun showHomePage() {
        _uiState.update { state -> state.copy(currentPage = MainPage.HOME) }
    }

    fun showDownloadPage() {
        refreshDownloadedModels()
        _uiState.update { state -> state.copy(currentPage = MainPage.DOWNLOAD) }
    }

    fun showSettingsPage() {
        _uiState.update { state -> state.copy(currentPage = MainPage.SETTINGS) }
    }

    fun onEngineSettingChanged(value: String) {
        preferences.edit()
            .putString(KEY_ENGINE_NAME, value)
            .apply()
        _uiState.update { state ->
            if (state.engineSetting == value) state else state.copy(engineSetting = value)
        }
    }

    fun onPromptTemplateChanged(value: String) {
        preferences.edit()
            .putString(KEY_PROMPT_TEMPLATE, value)
            .apply()
        _uiState.update { state ->
            if (state.promptTemplateSetting == value) state else state.copy(promptTemplateSetting = value)
        }
    }

    private fun startDownload(url: String) {
        val fileName = resolveFileName(url)
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val previousRunState = _uiState.value.isRunButtonEnabled
            _uiState.update { state ->
                state.copy(
                    isDownloadButtonEnabled = false,
                    isRunButtonEnabled = false,
                    modelStatus = context.getString(R.string.model_status_downloading),
                    downloadProgressVisible = true,
                    downloadProgressIndeterminate = true,
                    downloadProgressValue = 0,
                    downloadProgressLabel = context.getString(R.string.download_progress_placeholder)
                )
            }

            try {
                val modelFile = controller.downloadModel(url, fileName) { downloaded, total ->
                    updateDownloadProgress(downloaded, total)
                }
                saveModelReference(modelFile)
                _uiState.update { state ->
                    state.copy(
                        modelStatus = context.getString(R.string.model_status_download_success, modelFile.name)
                    )
                }
                refreshDownloadedModels()
                prepareModel(modelFile)
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                _uiState.update { state ->
                    state.copy(
                        modelStatus = context.getString(R.string.model_status_download_failed, message),
                        isRunButtonEnabled = previousRunState
                    )
                }
                clearModelReference()
            } finally {
                _uiState.update { state ->
                    state.copy(
                        isDownloadButtonEnabled = true,
                        downloadProgressVisible = false,
                        downloadProgressIndeterminate = true,
                        downloadProgressValue = 0,
                        downloadProgressLabel = context.getString(R.string.download_progress_placeholder)
                    )
                }
            }
        }
    }

    private fun updateDownloadProgress(downloaded: Long, total: Long?) {
        val progressLabel: String
        val progressValue: Int
        val isIndeterminate: Boolean

        if (total != null && total > 0L) {
            val progress = ((downloaded.toDouble() / total) * 100).coerceIn(0.0, 100.0)
            progressValue = progress.roundToInt()
            isIndeterminate = false
            progressLabel = context.getString(
                R.string.download_progress_with_total,
                progressValue,
                formatFileSize(downloaded),
                formatFileSize(total)
            )
        } else {
            progressValue = 0
            isIndeterminate = true
            progressLabel = context.getString(
                R.string.download_progress_without_total,
                formatFileSize(downloaded)
            )
        }

        _uiState.update { state ->
            state.copy(
                downloadProgressVisible = true,
                downloadProgressIndeterminate = isIndeterminate,
                downloadProgressValue = progressValue,
                downloadProgressLabel = progressLabel
            )
        }
    }

    private fun prepareModel(file: File) {
        if (!file.exists()) {
            _uiState.update { state ->
                state.copy(
                    modelStatus = context.getString(R.string.model_status_no_file),
                    isRunButtonEnabled = false
                )
            }
            clearModelReference()
            return
        }

        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    modelStatus = context.getString(R.string.model_status_loading, file.name),
                    isRunButtonEnabled = false
                )
            }

            try {
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                controller.prepareSession(
                    modelFile = file,
                    threadCount = threads,
                    contextSize = 2_048
                )
                _uiState.update { state ->
                    state.copy(
                        modelStatus = context.getString(R.string.model_status_ready, file.name),
                        isRunButtonEnabled = true,
                        outputText = context.getString(R.string.inference_placeholder)
                    )
                }
            } catch (error: Exception) {
                val message = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
                _uiState.update { state ->
                    state.copy(
                        modelStatus = message,
                        isRunButtonEnabled = false
                    )
                }
            }
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

    private fun saveModelReference(file: File) {
        preferences.edit()
            .putString(KEY_MODEL_PATH, file.absolutePath)
            .apply()
    }

    private fun clearModelReference() {
        preferences.edit()
            .remove(KEY_MODEL_PATH)
            .apply()
        refreshDownloadedModels()
    }

    private fun refreshDownloadedModels() {
        val modelsDir = File(context.filesDir, "models")
        val names = modelsDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
        _uiState.update { state -> state.copy(downloadedModels = names) }
    }

    private fun loadEngineSetting(): String =
        preferences.getString(KEY_ENGINE_NAME, defaultEngineName) ?: defaultEngineName

    private fun loadPromptTemplate(): String =
        preferences.getString(KEY_PROMPT_TEMPLATE, defaultPromptTemplate) ?: defaultPromptTemplate

    private fun resolveFileName(url: String): String {
        val lastSegment = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
        return lastSegment ?: "model-${System.currentTimeMillis()}"
    }

    private fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    override fun onCleared() {
        prepareJob?.cancel()
        downloadJob?.cancel()
        controller.release()
        super.onCleared()
    }

    companion object {
        private const val PREF_NAME = "cicero_model_storage"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_ENGINE_NAME = "engine_name"
        private const val KEY_PROMPT_TEMPLATE = "prompt_template"
        private const val MODEL_URL = "https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    }
}
