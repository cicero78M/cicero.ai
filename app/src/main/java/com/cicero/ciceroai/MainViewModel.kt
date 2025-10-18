package com.cicero.ciceroai

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cicero.ciceroai.llama.LlamaController
import com.cicero.ciceroai.llama.LlamaSettingsParser
import com.cicero.ciceroai.settings.PresetOption
import com.cicero.ciceroai.settings.PresetValues
import com.cicero.ciceroai.settings.SettingsConfig
import com.cicero.ciceroai.settings.SettingsRepository
import com.cicero.ciceroai.settings.settingsDataStore
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = LlamaController(application)
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

    private val presetValues: Map<PresetOption, PresetValues> = mapOf(
        PresetOption.BATTERY_SAVER to PresetValues(
            model = context.getString(R.string.settings_model_battery_saver),
            runtime = context.getString(R.string.settings_runtime_battery_saver),
            sampling = context.getString(R.string.settings_sampling_battery_saver),
            promptPersona = context.getString(R.string.settings_prompt_persona_battery_saver),
            memory = context.getString(R.string.settings_memory_battery_saver),
            codingWorkspace = context.getString(R.string.settings_coding_workspace_battery_saver),
            privacy = context.getString(R.string.settings_privacy_battery_saver),
            storage = context.getString(R.string.settings_storage_battery_saver),
            diagnostics = context.getString(R.string.settings_diagnostics_battery_saver),
            contextSize = 1_024,
            nGpuLayers = 0,
            batchSize = 8,
            temperature = 0.6f,
            topP = 0.8f
        ),
        PresetOption.BALANCED to PresetValues(
            model = context.getString(R.string.settings_model_default),
            runtime = context.getString(R.string.settings_runtime_default),
            sampling = context.getString(R.string.settings_sampling_default),
            promptPersona = context.getString(R.string.settings_prompt_persona_default),
            memory = context.getString(R.string.settings_memory_default),
            codingWorkspace = context.getString(R.string.settings_coding_workspace_default),
            privacy = context.getString(R.string.settings_privacy_default),
            storage = context.getString(R.string.settings_storage_default),
            diagnostics = context.getString(R.string.settings_diagnostics_default),
            contextSize = 2_048,
            nGpuLayers = 2,
            batchSize = 16,
            temperature = 0.7f,
            topP = 0.9f
        ),
        PresetOption.TURBO to PresetValues(
            model = context.getString(R.string.settings_model_turbo),
            runtime = context.getString(R.string.settings_runtime_turbo),
            sampling = context.getString(R.string.settings_sampling_turbo),
            promptPersona = context.getString(R.string.settings_prompt_persona_turbo),
            memory = context.getString(R.string.settings_memory_turbo),
            codingWorkspace = context.getString(R.string.settings_coding_workspace_turbo),
            privacy = context.getString(R.string.settings_privacy_turbo),
            storage = context.getString(R.string.settings_storage_turbo),
            diagnostics = context.getString(R.string.settings_diagnostics_turbo),
            contextSize = 4_096,
            nGpuLayers = 4,
            batchSize = 32,
            temperature = 0.9f,
            topP = 0.95f
        )
    )

    private val defaultPresetOption = PresetOption.BALANCED
    private val defaultPresetValues = presetValues.getValue(defaultPresetOption)

    private val defaultSettingsConfig = SettingsConfig(
        modelPath = null,
        preset = defaultPresetOption,
        model = defaultPresetValues.model,
        runtime = defaultPresetValues.runtime,
        sampling = defaultPresetValues.sampling,
        promptPersona = defaultPresetValues.promptPersona,
        memory = defaultPresetValues.memory,
        codingWorkspace = defaultPresetValues.codingWorkspace,
        privacy = defaultPresetValues.privacy,
        storage = defaultPresetValues.storage,
        diagnostics = defaultPresetValues.diagnostics,
        contextSize = defaultPresetValues.contextSize,
        nGpuLayers = defaultPresetValues.nGpuLayers,
        batchSize = defaultPresetValues.batchSize,
        temperature = defaultPresetValues.temperature,
        topP = defaultPresetValues.topP
    )

    private val settingsRepository = SettingsRepository(context.settingsDataStore, defaultSettingsConfig)

    private var latestSettingsConfig: SettingsConfig = sanitizeConfig(
        runBlocking { settingsRepository.settingsFlow.first() }
    )

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
            selectedModelName = getSavedModelName(),
            standardModels = standardModelOptions,
            selectedStandardModelIndex = 0,
            selectedPreset = latestSettingsConfig.preset,
            modelSetting = latestSettingsConfig.model,
            runtimeSetting = latestSettingsConfig.runtime,
            samplingSetting = latestSettingsConfig.sampling,
            promptPersonaSetting = latestSettingsConfig.promptPersona,
            memorySetting = latestSettingsConfig.memory,
            codingWorkspaceSetting = latestSettingsConfig.codingWorkspace,
            privacySetting = latestSettingsConfig.privacy,
            storageSetting = latestSettingsConfig.storage,
            diagnosticsSetting = latestSettingsConfig.diagnostics,
            contextSize = latestSettingsConfig.contextSize,
            nGpuLayers = latestSettingsConfig.nGpuLayers,
            batchSize = latestSettingsConfig.batchSize,
            temperature = latestSettingsConfig.temperature,
            topP = latestSettingsConfig.topP,
            isVulkanAvailable = controller.isVulkanAvailable()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var prepareJob: Job? = null
    private var downloadJob: Job? = null

    init {
        refreshDownloadedModels()
        val savedModel = getSavedModelFile()
        if (savedModel != null) {
            prepareModel(savedModel)
        }
        viewModelScope.launch {
            controller.inferenceProgress.collect { token ->
                val printableToken = token.replace("\n", "\\n")
                appendLog(context.getString(R.string.log_inference_progress, printableToken))
            }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { config ->
                val sanitizedConfig = sanitizeConfig(config)
                latestSettingsConfig = sanitizedConfig
                _uiState.update { state ->
                    state.copy(
                        selectedPreset = sanitizedConfig.preset,
                        modelSetting = sanitizedConfig.model,
                        runtimeSetting = sanitizedConfig.runtime,
                        samplingSetting = sanitizedConfig.sampling,
                        promptPersonaSetting = sanitizedConfig.promptPersona,
                        memorySetting = sanitizedConfig.memory,
                        codingWorkspaceSetting = sanitizedConfig.codingWorkspace,
                        privacySetting = sanitizedConfig.privacy,
                        storageSetting = sanitizedConfig.storage,
                        diagnosticsSetting = sanitizedConfig.diagnostics,
                        contextSize = sanitizedConfig.contextSize,
                        nGpuLayers = sanitizedConfig.nGpuLayers,
                        batchSize = sanitizedConfig.batchSize,
                        temperature = sanitizedConfig.temperature,
                        topP = sanitizedConfig.topP
                    )
                }
                refreshDownloadedModels()
            }
        }
    }

    fun onDownloadButtonClicked() {
        val savedModel = getSavedModelFile()
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
                val contextSize = latestSettingsConfig.contextSize.coerceAtLeast(0)
                val baseSamplingConfig = LlamaSettingsParser.parseSamplingConfig(
                    latestSettingsConfig.sampling,
                    defaultMaxTokens = deriveDefaultMaxTokens(contextSize)
                )
                val sanitizedSamplingConfig = baseSamplingConfig.copy(
                    temperature = latestSettingsConfig.temperature,
                    topP = latestSettingsConfig.topP
                ).sanitized()
                val tokenBudget = computeTokenBudget(
                    prompt = sanitizedPrompt,
                    contextSize = contextSize,
                    configuredMaxTokens = sanitizedSamplingConfig.maxTokens
                )
                val samplingConfig = sanitizedSamplingConfig.copy(maxTokens = tokenBudget.maxTokens)
                appendLog(
                    context.getString(
                        R.string.log_inference_requesting_completion,
                        tokenBudget.maxTokens,
                        tokenBudget.remainingTokens,
                        contextSize
                    )
                )
                val result = controller.runInference(sanitizedPrompt, samplingConfig)
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
            val savedName = getSavedModelName()
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

    fun onPresetSelected(preset: PresetOption) {
        if (latestSettingsConfig.preset == preset) {
            return
        }
        if (preset == PresetOption.CUSTOM) {
            latestSettingsConfig = latestSettingsConfig.copy(preset = preset)
            viewModelScope.launch { settingsRepository.updatePreset(preset) }
            _uiState.update { state -> state.copy(selectedPreset = preset) }
            return
        }
        val values = presetValues[preset] ?: return
        latestSettingsConfig = latestSettingsConfig.copy(
            preset = preset,
            model = values.model,
            runtime = values.runtime,
            sampling = values.sampling,
            promptPersona = values.promptPersona,
            memory = values.memory,
            codingWorkspace = values.codingWorkspace,
            privacy = values.privacy,
            storage = values.storage,
            diagnostics = values.diagnostics,
            contextSize = values.contextSize,
            nGpuLayers = values.nGpuLayers,
            batchSize = values.batchSize,
            temperature = values.temperature,
            topP = values.topP
        )
        viewModelScope.launch { settingsRepository.applyPreset(preset, values) }
        _uiState.update { state ->
            state.copy(
                selectedPreset = preset,
                modelSetting = values.model,
                runtimeSetting = values.runtime,
                samplingSetting = values.sampling,
                promptPersonaSetting = values.promptPersona,
                memorySetting = values.memory,
                codingWorkspaceSetting = values.codingWorkspace,
                privacySetting = values.privacy,
                storageSetting = values.storage,
                diagnosticsSetting = values.diagnostics,
                contextSize = values.contextSize,
                nGpuLayers = values.nGpuLayers,
                batchSize = values.batchSize,
                temperature = values.temperature,
                topP = values.topP
            )
        }
    }

    fun onModelSettingChanged(value: String) {
        if (latestSettingsConfig.model == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(model = value)
        viewModelScope.launch { settingsRepository.updateModel(value) }
        _uiState.update { state ->
            if (state.modelSetting == value) state else state.copy(modelSetting = value)
        }
    }

    fun onRuntimeSettingChanged(value: String) {
        if (latestSettingsConfig.runtime == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(runtime = value)
        viewModelScope.launch { settingsRepository.updateRuntime(value) }
        _uiState.update { state ->
            if (state.runtimeSetting == value) state else state.copy(runtimeSetting = value)
        }
    }

    fun onSamplingSettingChanged(value: String) {
        if (latestSettingsConfig.sampling == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(sampling = value)
        viewModelScope.launch { settingsRepository.updateSampling(value) }
        _uiState.update { state ->
            if (state.samplingSetting == value) state else state.copy(samplingSetting = value)
        }
    }

    fun onPromptPersonaSettingChanged(value: String) {
        if (latestSettingsConfig.promptPersona == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(promptPersona = value)
        viewModelScope.launch { settingsRepository.updatePromptPersona(value) }
        _uiState.update { state ->
            if (state.promptPersonaSetting == value) state else state.copy(promptPersonaSetting = value)
        }
    }

    fun onMemorySettingChanged(value: String) {
        if (latestSettingsConfig.memory == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(memory = value)
        viewModelScope.launch { settingsRepository.updateMemory(value) }
        _uiState.update { state ->
            if (state.memorySetting == value) state else state.copy(memorySetting = value)
        }
    }

    fun onCodingWorkspaceSettingChanged(value: String) {
        if (latestSettingsConfig.codingWorkspace == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(codingWorkspace = value)
        viewModelScope.launch { settingsRepository.updateCodingWorkspace(value) }
        _uiState.update { state ->
            if (state.codingWorkspaceSetting == value) state else state.copy(codingWorkspaceSetting = value)
        }
    }

    fun onPrivacySettingChanged(value: String) {
        if (latestSettingsConfig.privacy == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(privacy = value)
        viewModelScope.launch { settingsRepository.updatePrivacy(value) }
        _uiState.update { state ->
            if (state.privacySetting == value) state else state.copy(privacySetting = value)
        }
    }

    fun onStorageSettingChanged(value: String) {
        if (latestSettingsConfig.storage == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(storage = value)
        viewModelScope.launch { settingsRepository.updateStorage(value) }
        _uiState.update { state ->
            if (state.storageSetting == value) state else state.copy(storageSetting = value)
        }
    }

    fun onDiagnosticsSettingChanged(value: String) {
        if (latestSettingsConfig.diagnostics == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(diagnostics = value)
        viewModelScope.launch { settingsRepository.updateDiagnostics(value) }
        _uiState.update { state ->
            if (state.diagnosticsSetting == value) state else state.copy(diagnosticsSetting = value)
        }
    }

    fun onContextSizeChanged(value: Int) {
        if (latestSettingsConfig.contextSize == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(contextSize = value)
        viewModelScope.launch { settingsRepository.updateContextSize(value) }
        _uiState.update { state -> state.copy(contextSize = value) }
    }

    fun onGpuLayersChanged(value: Int) {
        if (latestSettingsConfig.nGpuLayers == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(nGpuLayers = value)
        viewModelScope.launch { settingsRepository.updateGpuLayers(value) }
        _uiState.update { state -> state.copy(nGpuLayers = value) }
    }

    fun onBatchSizeChanged(value: Int) {
        if (latestSettingsConfig.batchSize == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(batchSize = value)
        viewModelScope.launch { settingsRepository.updateBatchSize(value) }
        _uiState.update { state -> state.copy(batchSize = value) }
    }

    fun onTemperatureChanged(value: Float) {
        if (latestSettingsConfig.temperature == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(temperature = value)
        viewModelScope.launch { settingsRepository.updateTemperature(value) }
        _uiState.update { state -> state.copy(temperature = value) }
    }

    fun onTopPChanged(value: Float) {
        if (latestSettingsConfig.topP == value) {
            return
        }
        ensureCustomPreset()
        latestSettingsConfig = latestSettingsConfig.copy(topP = value)
        viewModelScope.launch { settingsRepository.updateTopP(value) }
        _uiState.update { state -> state.copy(topP = value) }
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
                val modelFile = controller.downloadModel(
                    url,
                    fileName,
                    onProgress = { downloaded, total ->
                        updateDownloadProgress(downloaded, total)
                    },
                    onStatus = { message ->
                        _uiState.update { state ->
                            state.copy(modelStatus = message)
                        }
                    }
                )
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
                val baseRuntimeConfig = LlamaSettingsParser.parseRuntimeConfig(
                    latestSettingsConfig.runtime,
                    fallbackThreads = threads,
                    fallbackContext = latestSettingsConfig.contextSize
                )
                val runtimeConfig = baseRuntimeConfig.copy(
                    contextSize = latestSettingsConfig.contextSize,
                    batchSize = latestSettingsConfig.batchSize.takeIf { it > 0 },
                    nGpuLayers = latestSettingsConfig.nGpuLayers
                ).sanitized()
                controller.prepareSession(
                    modelFile = file,
                    runtimeConfig = runtimeConfig
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

    private fun saveModelReference(file: File) {
        latestSettingsConfig = latestSettingsConfig.copy(modelPath = file.absolutePath)
        viewModelScope.launch { settingsRepository.updateModelPath(file.absolutePath) }
        _uiState.update { state -> state.copy(selectedModelName = file.name) }
    }

    private fun clearModelReference() {
        latestSettingsConfig = latestSettingsConfig.copy(modelPath = null)
        viewModelScope.launch { settingsRepository.clearModelPath() }
        _uiState.update { state -> state.copy(selectedModelName = null) }
        refreshDownloadedModels()
    }

    private fun refreshDownloadedModels() {
        val modelsDir = File(context.filesDir, "models")
        val names = modelsDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
        val savedFile = latestSettingsConfig.modelPath?.let { File(it) }
        val savedName = if (savedFile != null && savedFile.exists()) {
            savedFile.name
        } else {
            if (savedFile != null) {
                latestSettingsConfig = latestSettingsConfig.copy(modelPath = null)
                viewModelScope.launch { settingsRepository.clearModelPath() }
            }
            null
        }
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

    private fun getSavedModelFile(): File? {
        val path = latestSettingsConfig.modelPath ?: return null
        val file = File(path)
        return if (file.exists()) {
            file
        } else {
            latestSettingsConfig = latestSettingsConfig.copy(modelPath = null)
            viewModelScope.launch { settingsRepository.clearModelPath() }
            null
        }
    }

    private fun getSavedModelName(): String? = getSavedModelFile()?.name

    private fun ensureCustomPreset() {
        if (latestSettingsConfig.preset == PresetOption.CUSTOM) {
            return
        }
        latestSettingsConfig = latestSettingsConfig.copy(preset = PresetOption.CUSTOM)
        viewModelScope.launch { settingsRepository.updatePreset(PresetOption.CUSTOM) }
        _uiState.update { state ->
            if (state.selectedPreset == PresetOption.CUSTOM) {
                state
            } else {
                state.copy(selectedPreset = PresetOption.CUSTOM)
            }
        }
    }

    private fun sanitizeConfig(config: SettingsConfig): SettingsConfig {
        val path = config.modelPath ?: return config
        val file = File(path)
        return if (file.exists()) {
            config
        } else {
            viewModelScope.launch { settingsRepository.clearModelPath() }
            config.copy(modelPath = null)
        }
    }

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
        private const val MODEL_URL = "https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    }
}
