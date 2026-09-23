package com.example.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.UserProfile
import com.example.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "Explorer",
    val selectedAvatar: String = "heart_rose",
    val preferredLanguage: String = "English",
    val storagePreference: String = "Balanced (2 GB)",
    val isSaved: Boolean = false
)

class ProfileViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val availableAvatars = listOf(
        "heart_rose" to "Rose Heart",
        "star_soft" to "Gentle Star",
        "spark_lavender" to "Cosmic Spark",
        "shield_warm" to "Safe Haven",
        "feather_peace" to "Calm Wave"
    )

    val storageOptions = listOf(
        "Compact (500 MB)",
        "Balanced (2 GB)",
        "Extended (5 GB)",
        "Full Archive (10 GB)"
    )

    val languages = listOf("English", "Español", "Français", "Deutsch", "日本語")

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val existing = appContainer.userProfileRepository.getUserProfileOnce()
            if (existing != null) {
                _uiState.value = ProfileUiState(
                    displayName = existing.displayName,
                    selectedAvatar = existing.avatarId,
                    preferredLanguage = existing.preferredLanguage,
                    storagePreference = existing.storagePreference
                )
            }
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun selectAvatar(avatarId: String) {
        _uiState.value = _uiState.value.copy(selectedAvatar = avatarId)
    }

    fun selectLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(preferredLanguage = lang)
    }

    fun selectStoragePreference(pref: String) {
        _uiState.value = _uiState.value.copy(storagePreference = pref)
    }

    fun saveProfile(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val profile = UserProfile(
                id = 1,
                displayName = state.displayName.ifBlank { "Explorer" },
                avatarId = state.selectedAvatar,
                preferredLanguage = state.preferredLanguage,
                storagePreference = state.storagePreference,
                createdAt = System.currentTimeMillis()
            )
            appContainer.userProfileRepository.saveUserProfile(profile)
            appContainer.preferencesManager.setLanguage(state.preferredLanguage)
            appContainer.activityRepository.recordActivity(
                title = "Local Profile Created",
                description = "Identity '${profile.displayName}' active locally with zero cloud upload",
                category = "PROFILE"
            )
            _uiState.value = _uiState.value.copy(isSaved = true)
            onSuccess()
        }
    }
}
