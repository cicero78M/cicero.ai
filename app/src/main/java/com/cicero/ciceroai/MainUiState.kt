package com.cicero.ciceroai

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
    val downloadProgressLabel: String,
    val isDownloadButtonEnabled: Boolean,
    val isRunButtonEnabled: Boolean,
    val promptError: String?,
    val outputText: String,
    val logMessages: List<String>,
    val downloadedModels: List<String>,
    val selectedModelName: String?,
    val engineSetting: String,
    val promptTemplateSetting: String
)
