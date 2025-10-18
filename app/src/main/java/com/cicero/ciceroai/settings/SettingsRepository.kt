package com.cicero.ciceroai.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaultConfig: SettingsConfig
) {

    val settingsFlow: Flow<SettingsConfig> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> mapPreferences(preferences) }

    private fun mapPreferences(preferences: Preferences): SettingsConfig {
        val presetValue = PresetOption.fromId(preferences[SettingsPreferencesKeys.PRESET])
        return SettingsConfig(
            modelPath = preferences[SettingsPreferencesKeys.MODEL_PATH]
                ?: defaultConfig.modelPath,
            preset = presetValue,
            model = preferences[SettingsPreferencesKeys.MODEL]
                ?: defaultConfig.model,
            runtime = preferences[SettingsPreferencesKeys.RUNTIME]
                ?: defaultConfig.runtime,
            sampling = preferences[SettingsPreferencesKeys.SAMPLING]
                ?: defaultConfig.sampling,
            promptPersona = preferences[SettingsPreferencesKeys.PROMPT_PERSONA]
                ?: defaultConfig.promptPersona,
            memory = preferences[SettingsPreferencesKeys.MEMORY]
                ?: defaultConfig.memory,
            codingWorkspace = preferences[SettingsPreferencesKeys.CODING_WORKSPACE]
                ?: defaultConfig.codingWorkspace,
            privacy = preferences[SettingsPreferencesKeys.PRIVACY]
                ?: defaultConfig.privacy,
            storage = preferences[SettingsPreferencesKeys.STORAGE]
                ?: defaultConfig.storage,
            diagnostics = preferences[SettingsPreferencesKeys.DIAGNOSTICS]
                ?: defaultConfig.diagnostics,
            contextSize = preferences[SettingsPreferencesKeys.CONTEXT_SIZE]
                ?: defaultConfig.contextSize,
            nGpuLayers = preferences[SettingsPreferencesKeys.GPU_LAYERS]
                ?: defaultConfig.nGpuLayers,
            batchSize = preferences[SettingsPreferencesKeys.BATCH_SIZE]
                ?: defaultConfig.batchSize,
            temperature = preferences[SettingsPreferencesKeys.TEMPERATURE]
                ?: defaultConfig.temperature,
            topP = preferences[SettingsPreferencesKeys.TOP_P]
                ?: defaultConfig.topP
        )
    }

    suspend fun updateModelPath(path: String?) {
        dataStore.edit { preferences ->
            if (path.isNullOrBlank()) {
                preferences.remove(SettingsPreferencesKeys.MODEL_PATH)
            } else {
                preferences[SettingsPreferencesKeys.MODEL_PATH] = path
            }
        }
    }

    suspend fun clearModelPath() {
        updateModelPath(null)
    }

    suspend fun updatePreset(preset: PresetOption) {
        updatePreference(SettingsPreferencesKeys.PRESET, preset.id)
    }

    suspend fun applyPreset(preset: PresetOption, values: PresetValues) {
        dataStore.edit { preferences ->
            preferences[SettingsPreferencesKeys.PRESET] = preset.id
            preferences[SettingsPreferencesKeys.MODEL] = values.model
            preferences[SettingsPreferencesKeys.RUNTIME] = values.runtime
            preferences[SettingsPreferencesKeys.SAMPLING] = values.sampling
            preferences[SettingsPreferencesKeys.PROMPT_PERSONA] = values.promptPersona
            preferences[SettingsPreferencesKeys.MEMORY] = values.memory
            preferences[SettingsPreferencesKeys.CODING_WORKSPACE] = values.codingWorkspace
            preferences[SettingsPreferencesKeys.PRIVACY] = values.privacy
            preferences[SettingsPreferencesKeys.STORAGE] = values.storage
            preferences[SettingsPreferencesKeys.DIAGNOSTICS] = values.diagnostics
            preferences[SettingsPreferencesKeys.CONTEXT_SIZE] = values.contextSize
            preferences[SettingsPreferencesKeys.GPU_LAYERS] = values.nGpuLayers
            preferences[SettingsPreferencesKeys.BATCH_SIZE] = values.batchSize
            preferences[SettingsPreferencesKeys.TEMPERATURE] = values.temperature
            preferences[SettingsPreferencesKeys.TOP_P] = values.topP
        }
    }

    suspend fun updateModel(value: String) {
        updatePreference(SettingsPreferencesKeys.MODEL, value)
    }

    suspend fun updateRuntime(value: String) {
        updatePreference(SettingsPreferencesKeys.RUNTIME, value)
    }

    suspend fun updateSampling(value: String) {
        updatePreference(SettingsPreferencesKeys.SAMPLING, value)
    }

    suspend fun updatePromptPersona(value: String) {
        updatePreference(SettingsPreferencesKeys.PROMPT_PERSONA, value)
    }

    suspend fun updateMemory(value: String) {
        updatePreference(SettingsPreferencesKeys.MEMORY, value)
    }

    suspend fun updateCodingWorkspace(value: String) {
        updatePreference(SettingsPreferencesKeys.CODING_WORKSPACE, value)
    }

    suspend fun updatePrivacy(value: String) {
        updatePreference(SettingsPreferencesKeys.PRIVACY, value)
    }

    suspend fun updateStorage(value: String) {
        updatePreference(SettingsPreferencesKeys.STORAGE, value)
    }

    suspend fun updateDiagnostics(value: String) {
        updatePreference(SettingsPreferencesKeys.DIAGNOSTICS, value)
    }

    suspend fun updateContextSize(value: Int) {
        updatePreference(SettingsPreferencesKeys.CONTEXT_SIZE, value)
    }

    suspend fun updateGpuLayers(value: Int) {
        updatePreference(SettingsPreferencesKeys.GPU_LAYERS, value)
    }

    suspend fun updateBatchSize(value: Int) {
        updatePreference(SettingsPreferencesKeys.BATCH_SIZE, value)
    }

    suspend fun updateTemperature(value: Float) {
        updatePreference(SettingsPreferencesKeys.TEMPERATURE, value)
    }

    suspend fun updateTopP(value: Float) {
        updatePreference(SettingsPreferencesKeys.TOP_P, value)
    }

    private suspend fun updatePreference(key: Preferences.Key<String>, value: String) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private suspend fun updatePreference(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private suspend fun updatePreference(key: Preferences.Key<Float>, value: Float) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
