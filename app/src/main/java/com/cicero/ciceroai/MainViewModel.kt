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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = LlamaController(application)
    private val preferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val standardModelOptions = listOf(
        StandardModelInfo(
            name = "Llama-3.2-3B Instruct",
            sizeLabel = "~2 GB (Q4) hingga ~3.4 GB (Q8)",
            description = "Versi instruct dari Llama 3.2 dengan banyak varian quant yang cocok untuk perangkat Android.",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "bartowski/Llama-3.2-3B-Instruct-GGUF",
                    url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF"
                ),
                RepositoryLink(
                    label = "hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF",
                    url = "https://huggingface.co/hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF"
                )
            )
        ),
        StandardModelInfo(
            name = "Llama-3.2-1B Instruct",
            sizeLabel = "< 1 GB hingga ~1.3 GB tergantung quant",
            description = "Varian Llama 3.2 yang lebih ringan untuk perangkat dengan resource terbatas.",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "bartowski/Llama-3.2-1B-Instruct-GGUF",
                    url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF"
                )
            )
        ),
        StandardModelInfo(
            name = "Phi-3 Mini 4K Instruct",
            sizeLabel = "~2–4 GB tergantung quant",
            description = "Model ringan dengan kemampuan instruksi dan reasoning yang solid.",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "microsoft/Phi-3-mini-4k-instruct-gguf",
                    url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf"
                ),
                RepositoryLink(
                    label = "LiteLLMs/Phi-3-mini-4k-instruct-GGUF",
                    url = "https://huggingface.co/LiteLLMs/Phi-3-mini-4k-instruct-GGUF"
                )
            )
        ),
        StandardModelInfo(
            name = "Phi-4 Mini Instruct",
            sizeLabel = "~2–3 GB",
            description = "Versi terbaru/eksperimental dari keluarga Phi dengan peningkatan kemampuan.",
            downloadUrl = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "unsloth/Phi-4-mini-instruct-GGUF",
                    url = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF"
                ),
                RepositoryLink(
                    label = "tensorblock/Phi-4-mini-instruct-GGUF",
                    url = "https://huggingface.co/tensorblock/Phi-4-mini-instruct-GGUF"
                ),
                RepositoryLink(
                    label = "lmstudio-community/Phi-4-mini-instruct-GGUF",
                    url = "https://huggingface.co/lmstudio-community/Phi-4-mini-instruct-GGUF"
                )
            )
        ),
        StandardModelInfo(
            name = "Dolphin3.0 Llama3.2-3B",
            sizeLabel = "~2 GB (Q4) dengan varian lebih besar tersedia",
            description = "Fine-tune dari Llama 3.2 untuk eksperimen alternatif bila varian utama kurang cocok.",
            downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Llama3.2-3B-GGUF/resolve/main/Dolphin3.0-Llama3.2-3B-Q4_K_M.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "bartowski/Dolphin3.0-Llama3.2-3B-GGUF",
                    url = "https://huggingface.co/bartowski/Dolphin3.0-Llama3.2-3B-GGUF"
                )
            )
        ),
        StandardModelInfo(
            name = "Hermes-3 Llama3.2-3B",
            sizeLabel = "~3B params",
            description = "Varian Llama 3.2 dengan peningkatan reasoning dan kemampuan agen.",
            downloadUrl = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF/resolve/main/Hermes-3-Llama-3.2-3B.Q4_K_M.gguf?download=true",
            repositoryLinks = listOf(
                RepositoryLink(
                    label = "NousResearch/Hermes-3-Llama-3.2-3B-GGUF",
                    url = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF"
                )
            )
        )
    )

    private val context: Application
        get() = getApplication()

    private val defaultPresetSetting = context.getString(R.string.settings_preset_default)
    private val defaultModelSetting = context.getString(R.string.settings_model_default)
    private val defaultRuntimeSetting = context.getString(R.string.settings_runtime_default)
    private val defaultSamplingSetting = context.getString(R.string.settings_sampling_default)
    private val defaultPromptPersonaSetting = context.getString(R.string.settings_prompt_persona_default)
    private val defaultMemorySetting = context.getString(R.string.settings_memory_default)
    private val defaultCodingWorkspaceSetting = context.getString(R.string.settings_coding_workspace_default)
    private val defaultPrivacySetting = context.getString(R.string.settings_privacy_default)
    private val defaultStorageSetting = context.getString(R.string.settings_storage_default)
    private val defaultDiagnosticsSetting = context.getString(R.string.settings_diagnostics_default)

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentPage = MainPage.HOME,
            modelStatus = context.getString(R.string.model_status_download_prompt),
            downloadProgressVisible = false,
            downloadProgressIndeterminate = true,
            downloadProgressValue = 0,
            downloadProgressPercentText = context.getString(R.string.download_progress_percent_placeholder),
            downloadProgressDataText = context.getString(R.string.download_progress_data_placeholder),
            isDownloadButtonEnabled = true,
            isRunButtonEnabled = false,
            promptError = null,
            outputText = context.getString(R.string.inference_placeholder),
            logMessages = emptyList(),
            downloadedModels = emptyList(),
            selectedModelName = loadSavedModelName(),
            standardModels = standardModelOptions,
            selectedStandardModelIndex = 0,
            presetSetting = loadPresetSetting(),
            modelSetting = loadModelSetting(),
            runtimeSetting = loadRuntimeSetting(),
            samplingSetting = loadSamplingSetting(),
            promptPersonaSetting = loadPromptPersonaSetting(),
            memorySetting = loadMemorySetting(),
            codingWorkspaceSetting = loadCodingWorkspaceSetting(),
            privacySetting = loadPrivacySetting(),
            storageSetting = loadStorageSetting(),
            diagnosticsSetting = loadDiagnosticsSetting()
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
        viewModelScope.launch {
            controller.inferenceProgress.collect { token ->
                val printableToken = token.replace("\n", "\\n")
                appendLog(context.getString(R.string.log_inference_progress, printableToken))
            }
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

    fun onStandardModelSelected(index: Int) {
        if (standardModelOptions.isEmpty()) {
            return
        }
        val safeIndex = index.coerceIn(0, standardModelOptions.lastIndex)
        if (safeIndex == _uiState.value.selectedStandardModelIndex) {
            return
        }
        _uiState.update { state -> state.copy(selectedStandardModelIndex = safeIndex) }
    }

    fun onStandardModelDownloadRequested() {
        if (!_uiState.value.isDownloadButtonEnabled) {
            return
        }
        if (standardModelOptions.isEmpty()) {
            return
        }
        val safeIndex = _uiState.value.selectedStandardModelIndex.coerceIn(0, standardModelOptions.lastIndex)
        val model = standardModelOptions.getOrNull(safeIndex) ?: return
        startDownload(model.downloadUrl)
    }

    fun onManualDownloadRequested(url: String) {
        if (!_uiState.value.isDownloadButtonEnabled) {
            return
        }
        val sanitized = url.trim()
        if (sanitized.isEmpty()) {
            _uiState.update { state ->
                state.copy(modelStatus = context.getString(R.string.model_status_manual_url_missing))
            }
            return
        }
        startDownload(sanitized)
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
            } catch (error: CancellationException) {
                throw error
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

    fun onModelSelected(modelName: String) {
        val currentSelection = _uiState.value.selectedModelName
        if (currentSelection == modelName) {
            val savedName = loadSavedModelName()
            if (savedName == modelName) {
                return
            }
        }

        val modelFile = File(File(context.filesDir, "models"), modelName)
        if (!modelFile.exists()) {
            _uiState.update { state ->
                state.copy(
                    modelStatus = context.getString(R.string.model_status_no_file),
                    selectedModelName = null,
                    isRunButtonEnabled = false
                )
            }
            clearModelReference()
            return
        }

        saveModelReference(modelFile)
        prepareModel(modelFile)
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

    fun onPresetSettingChanged(value: String) {
        persistSetting(KEY_PRESET_SETTING, value) { state ->
            if (state.presetSetting == value) state else state.copy(presetSetting = value)
        }
    }

    fun onModelSettingChanged(value: String) {
        persistSetting(KEY_MODEL_SETTING, value) { state ->
            if (state.modelSetting == value) state else state.copy(modelSetting = value)
        }
    }

    fun onRuntimeSettingChanged(value: String) {
        persistSetting(KEY_RUNTIME_SETTING, value) { state ->
            if (state.runtimeSetting == value) state else state.copy(runtimeSetting = value)
        }
    }

    fun onSamplingSettingChanged(value: String) {
        persistSetting(KEY_SAMPLING_SETTING, value) { state ->
            if (state.samplingSetting == value) state else state.copy(samplingSetting = value)
        }
    }

    fun onPromptPersonaSettingChanged(value: String) {
        persistSetting(KEY_PROMPT_PERSONA_SETTING, value) { state ->
            if (state.promptPersonaSetting == value) state else state.copy(promptPersonaSetting = value)
        }
    }

    fun onMemorySettingChanged(value: String) {
        persistSetting(KEY_MEMORY_SETTING, value) { state ->
            if (state.memorySetting == value) state else state.copy(memorySetting = value)
        }
    }

    fun onCodingWorkspaceSettingChanged(value: String) {
        persistSetting(KEY_CODING_WORKSPACE_SETTING, value) { state ->
            if (state.codingWorkspaceSetting == value) state else state.copy(codingWorkspaceSetting = value)
        }
    }

    fun onPrivacySettingChanged(value: String) {
        persistSetting(KEY_PRIVACY_SETTING, value) { state ->
            if (state.privacySetting == value) state else state.copy(privacySetting = value)
        }
    }

    fun onStorageSettingChanged(value: String) {
        persistSetting(KEY_STORAGE_SETTING, value) { state ->
            if (state.storageSetting == value) state else state.copy(storageSetting = value)
        }
    }

    fun onDiagnosticsSettingChanged(value: String) {
        persistSetting(KEY_DIAGNOSTICS_SETTING, value) { state ->
            if (state.diagnosticsSetting == value) state else state.copy(diagnosticsSetting = value)
        }
    }

    private fun persistSetting(key: String, value: String, reducer: (MainUiState) -> MainUiState) {
        preferences.edit()
            .putString(key, value)
            .apply()
        _uiState.update(reducer)
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
                    downloadProgressPercentText = context.getString(R.string.download_progress_percent_placeholder),
                    downloadProgressDataText = context.getString(R.string.download_progress_data_placeholder)
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
            } catch (error: CancellationException) {
                throw error
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
                        downloadProgressPercentText = context.getString(R.string.download_progress_percent_placeholder),
                        downloadProgressDataText = context.getString(R.string.download_progress_data_placeholder)
                    )
                }
            }
        }
    }

    private fun updateDownloadProgress(downloaded: Long, total: Long?) {
        val percentText: String
        val dataText: String
        val progressValue: Int
        val isIndeterminate: Boolean

        if (total != null && total > 0L) {
            val progress = ((downloaded.toDouble() / total) * 100).coerceIn(0.0, 100.0)
            progressValue = progress.roundToInt()
            isIndeterminate = false
            percentText = context.getString(R.string.download_progress_percent_value, progressValue)
            dataText = context.getString(
                R.string.download_progress_data_with_total,
                formatFileSize(downloaded),
                formatFileSize(total)
            )
        } else {
            progressValue = 0
            isIndeterminate = true
            percentText = context.getString(R.string.download_progress_percent_placeholder)
            dataText = context.getString(
                R.string.download_progress_data_without_total,
                formatFileSize(downloaded)
            )
        }

        _uiState.update { state ->
            state.copy(
                downloadProgressVisible = true,
                downloadProgressIndeterminate = isIndeterminate,
                downloadProgressValue = progressValue,
                downloadProgressPercentText = percentText,
                downloadProgressDataText = dataText
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
            } catch (error: CancellationException) {
                throw error
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

    private fun loadSavedModelName(): String? {
        val path = preferences.getString(KEY_MODEL_PATH, null) ?: return null
        val file = File(path)
        return if (file.exists()) {
            file.name
        } else {
            removeModelPreference()
            null
        }
    }

    private fun loadSavedModelFile(): File? {
        val path = preferences.getString(KEY_MODEL_PATH, null) ?: return null
        val file = File(path)
        return if (file.exists()) {
            file
        } else {
            removeModelPreference()
            null
        }
    }

    private fun saveModelReference(file: File) {
        preferences.edit()
            .putString(KEY_MODEL_PATH, file.absolutePath)
            .apply()
        _uiState.update { state -> state.copy(selectedModelName = file.name) }
    }

    private fun clearModelReference() {
        removeModelPreference()
        _uiState.update { state -> state.copy(selectedModelName = null) }
        refreshDownloadedModels()
    }

    private fun removeModelPreference() {
        preferences.edit()
            .remove(KEY_MODEL_PATH)
            .apply()
    }

    private fun refreshDownloadedModels() {
        val modelsDir = File(context.filesDir, "models")
        val names = modelsDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
        val savedName = loadSavedModelName()
        val currentSelection = _uiState.value.selectedModelName
        val selectedName = when {
            savedName != null && names.contains(savedName) -> savedName
            currentSelection != null && names.contains(currentSelection) -> currentSelection
            else -> null
        }
        _uiState.update { state ->
            state.copy(
                downloadedModels = names,
                selectedModelName = selectedName
            )
        }
    }

    private fun loadPresetSetting(): String = loadSetting(KEY_PRESET_SETTING, defaultPresetSetting)

    private fun loadModelSetting(): String = loadSetting(KEY_MODEL_SETTING, defaultModelSetting)

    private fun loadRuntimeSetting(): String = loadSetting(KEY_RUNTIME_SETTING, defaultRuntimeSetting)

    private fun loadSamplingSetting(): String = loadSetting(KEY_SAMPLING_SETTING, defaultSamplingSetting)

    private fun loadPromptPersonaSetting(): String =
        loadSetting(KEY_PROMPT_PERSONA_SETTING, defaultPromptPersonaSetting)

    private fun loadMemorySetting(): String = loadSetting(KEY_MEMORY_SETTING, defaultMemorySetting)

    private fun loadCodingWorkspaceSetting(): String =
        loadSetting(KEY_CODING_WORKSPACE_SETTING, defaultCodingWorkspaceSetting)

    private fun loadPrivacySetting(): String = loadSetting(KEY_PRIVACY_SETTING, defaultPrivacySetting)

    private fun loadStorageSetting(): String = loadSetting(KEY_STORAGE_SETTING, defaultStorageSetting)

    private fun loadDiagnosticsSetting(): String =
        loadSetting(KEY_DIAGNOSTICS_SETTING, defaultDiagnosticsSetting)

    private fun loadSetting(key: String, defaultValue: String): String =
        preferences.getString(key, defaultValue) ?: defaultValue

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
        private const val KEY_PRESET_SETTING = "preset_setting"
        private const val KEY_MODEL_SETTING = "model_setting"
        private const val KEY_RUNTIME_SETTING = "runtime_setting"
        private const val KEY_SAMPLING_SETTING = "sampling_setting"
        private const val KEY_PROMPT_PERSONA_SETTING = "prompt_persona_setting"
        private const val KEY_MEMORY_SETTING = "memory_setting"
        private const val KEY_CODING_WORKSPACE_SETTING = "coding_workspace_setting"
        private const val KEY_PRIVACY_SETTING = "privacy_setting"
        private const val KEY_STORAGE_SETTING = "storage_setting"
        private const val KEY_DIAGNOSTICS_SETTING = "diagnostics_setting"
        private const val MODEL_URL = "https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    }
}
