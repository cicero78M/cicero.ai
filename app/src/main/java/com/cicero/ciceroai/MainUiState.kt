package com.cicero.ciceroai

data class MainUiState(
    val modelStatus: String,
    val downloadProgressVisible: Boolean,
    val downloadProgressIndeterminate: Boolean,
    val downloadProgressValue: Int,
    val downloadProgressLabel: String,
    val isDownloadButtonEnabled: Boolean,
    val isRunButtonEnabled: Boolean,
    val promptError: String?,
    val outputText: String,
    val logMessages: List<String>
)
