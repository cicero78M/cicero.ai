package com.cicero.ciceroai.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore(name = "cicero_settings")

object SettingsPreferencesKeys {
    val MODEL_PATH: Preferences.Key<String> = stringPreferencesKey("model_path")
    val PRESET: Preferences.Key<String> = stringPreferencesKey("preset_setting")
    val MODEL: Preferences.Key<String> = stringPreferencesKey("model_setting")
    val RUNTIME: Preferences.Key<String> = stringPreferencesKey("runtime_setting")
    val SAMPLING: Preferences.Key<String> = stringPreferencesKey("sampling_setting")
    val PROMPT_PERSONA: Preferences.Key<String> = stringPreferencesKey("prompt_persona_setting")
    val MEMORY: Preferences.Key<String> = stringPreferencesKey("memory_setting")
    val CODING_WORKSPACE: Preferences.Key<String> = stringPreferencesKey("coding_workspace_setting")
    val PRIVACY: Preferences.Key<String> = stringPreferencesKey("privacy_setting")
    val STORAGE: Preferences.Key<String> = stringPreferencesKey("storage_setting")
    val DIAGNOSTICS: Preferences.Key<String> = stringPreferencesKey("diagnostics_setting")
}
