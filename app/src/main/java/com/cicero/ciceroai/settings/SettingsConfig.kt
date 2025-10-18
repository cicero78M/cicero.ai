package com.cicero.ciceroai.settings

data class SettingsConfig(
    val modelPath: String?,
    val preset: PresetOption,
    val model: String,
    val runtime: String,
    val sampling: String,
    val promptPersona: String,
    val memory: String,
    val codingWorkspace: String,
    val privacy: String,
    val storage: String,
    val diagnostics: String
)
