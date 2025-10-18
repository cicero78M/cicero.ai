package com.cicero.ciceroai

import com.cicero.ciceroai.settings.PresetOption

enum class MainPage {
    HOME,
    DOWNLOAD,
    SETTINGS
}

data class MainUiState(
    val currentPage: MainPage,
    val modelStatus: String,
    val downloadProgressVisible: Boolean,
    val downloadProgressIndeterminate: Boolean,
    val downloadProgressValue: Int,
    val downloadProgressPercentText: String,
    val downloadProgressDataText: String,
    val isDownloadButtonEnabled: Boolean,
    val isRunButtonEnabled: Boolean,
    val promptError: String?,
    val outputText: String,
    val logMessages: List<String>,
    val downloadedModels: List<String>,
    val selectedModelName: String?,
    val standardModels: List<StandardModelInfo>,
    val selectedStandardModelIndex: Int,
    val selectedPreset: PresetOption,
    val modelSetting: String,
    val runtimeSetting: String,
    val samplingSetting: String,
    val promptPersonaSetting: String,
    val memorySetting: String,
    val codingWorkspaceSetting: String,
    val privacySetting: String,
    val storageSetting: String,
    val diagnosticsSetting: String,
    val contextSize: Int,
    val nGpuLayers: Int,
    val batchSize: Int,
    val temperature: Float,
    val topP: Float,
    val isVulkanAvailable: Boolean? = null
)

data class StandardModelInfo(
    val name: String,
    val sizeLabel: String,
    val description: String,
    val downloadUrl: String,
    val repositoryLinks: List<RepositoryLink>
)

data class RepositoryLink(
    val label: String,
    val url: String
)
