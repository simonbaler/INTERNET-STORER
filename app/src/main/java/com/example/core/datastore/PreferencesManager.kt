package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "internet_storer_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ANIMATION_INTENSITY = stringPreferencesKey("animation_intensity")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_LAST_CONNECTIVITY = stringPreferencesKey("last_connectivity")
        val KEY_FORCED_OFFLINE = booleanPreferencesKey("forced_offline")
        val KEY_STORAGE_PREFERENCE = stringPreferencesKey("storage_preference")
    }

    private val safeData: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val isOnboardingCompleted: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val themeMode: Flow<String> = safeData.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "Soft Romantic"
    }

    val animationIntensity: Flow<String> = safeData.map { preferences ->
        preferences[KEY_ANIMATION_INTENSITY] ?: "Smooth"
    }

    val languagePreference: Flow<String> = safeData.map { preferences ->
        preferences[KEY_LANGUAGE] ?: "English"
    }

    val lastKnownConnectivity: Flow<String> = safeData.map { preferences ->
        preferences[KEY_LAST_CONNECTIVITY] ?: "UNKNOWN"
    }

    val isForcedOffline: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_FORCED_OFFLINE] ?: false
    }

    val safeStoragePreference: Flow<String> = safeData.map { preferences ->
        preferences[KEY_STORAGE_PREFERENCE] ?: "Balanced (2 GB)"
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setAnimationIntensity(intensity: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ANIMATION_INTENSITY] = intensity
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = language
        }
    }

    suspend fun setLastKnownConnectivity(status: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_CONNECTIVITY] = status
        }
    }

    suspend fun setForcedOffline(forced: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FORCED_OFFLINE] = forced
        }
    }

    suspend fun setStoragePreference(preference: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STORAGE_PREFERENCE] = preference
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
