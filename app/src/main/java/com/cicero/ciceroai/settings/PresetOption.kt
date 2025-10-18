package com.cicero.ciceroai.settings

import androidx.annotation.StringRes
import com.cicero.ciceroai.R

enum class PresetOption(
    val id: String,
    @StringRes val descriptionRes: Int
) {
    BATTERY_SAVER("battery_saver", R.string.settings_preset_battery_saver_description),
    BALANCED("balanced", R.string.settings_preset_balanced_description),
    TURBO("turbo", R.string.settings_preset_turbo_description),
    CUSTOM("custom", R.string.settings_preset_custom_description);

    companion object {
        fun fromId(value: String?): PresetOption {
            if (value.isNullOrBlank()) {
                return BALANCED
            }
            val normalized = value.trim().lowercase()
            return values().firstOrNull { it.id == normalized }
                ?: when (normalized) {
                    "default", "balanced", "preset default" -> BALANCED
                    "battery", "battery saver", "hemat baterai" -> BATTERY_SAVER
                    "turbo", "pro" -> TURBO
                    "custom" -> CUSTOM
                    else -> BALANCED
                }
        }
    }
}

data class PresetValues(
    val model: String,
    val runtime: String,
    val sampling: String,
    val promptPersona: String,
    val memory: String,
    val codingWorkspace: String,
    val privacy: String,
    val storage: String,
    val diagnostics: String,
    val contextSize: Int,
    val nGpuLayers: Int,
    val batchSize: Int,
    val temperature: Float,
    val topP: Float
)
