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
        return SettingsConfig(
            modelPath = preferences[SettingsPreferencesKeys.MODEL_PATH]
                ?: defaultConfig.modelPath,
            preset = preferences[SettingsPreferencesKeys.PRESET]
                ?: defaultConfig.preset,
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
                ?: defaultConfig.diagnostics
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

    suspend fun updatePreset(value: String) {
        updatePreference(SettingsPreferencesKeys.PRESET, value)
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

    private suspend fun updatePreference(key: Preferences.Key<String>, value: String) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
