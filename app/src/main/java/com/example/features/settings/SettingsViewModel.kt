package com.example.features.settings

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.connectivity.ConnectivityInfo
import com.example.core.database.AppSettings
import com.example.core.database.UserProfile
import com.example.core.security.SecurityStatus
import com.example.data.AppContainer
import com.example.domain.usecase.OfflineReadinessResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentTheme: String = "Soft Romantic",
    val animationIntensity: String = "Smooth",
    val connectivityInfo: ConnectivityInfo = ConnectivityInfo(),
    val isForcedOffline: Boolean = false,
    val securityStatus: SecurityStatus? = null,
    val readiness: OfflineReadinessResult? = null,
    val userProfile: UserProfile? = null,
    val localStorageUsedBytes: Long = 1024L * 1024L * 42L,
    val storagePreference: String = "Balanced (2 GB)",
    val freeDeviceStorageBytes: Long = 0L,
    val totalDeviceStorageBytes: Long = 0L
)

class SettingsViewModel(
    private val appContainer: AppContainer,
    private val context: Context
) : ViewModel() {

    private data class PrefsBundle(
        val theme: String,
        val animation: String,
        val forcedOffline: Boolean,
        val storagePref: String
    )

    private val prefsFlow = combine(
        appContainer.preferencesManager.themeMode,
        appContainer.preferencesManager.animationIntensity,
        appContainer.preferencesManager.isForcedOffline,
        appContainer.preferencesManager.safeStoragePreference
    ) { theme, animation, forcedOffline, storagePref ->
        PrefsBundle(theme, animation, forcedOffline, storagePref)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsFlow,
        appContainer.connectivityObserver.observe(),
        appContainer.userProfileRepository.getUserProfile(),
        appContainer.localVaultRepository.getStorageBreakdown()
    ) { prefs, realConn, profile, breakdown ->
        val effectiveConn = if (prefs.forcedOffline) {
            ConnectivityInfo(
                status = com.example.core.connectivity.ConnectivityStatus.OFFLINE,
                networkType = com.example.core.connectivity.NetworkType.NONE
            )
        } else {
            realConn
        }

        val secStatus = appContainer.securityManager.getSecurityStatus()
        val readiness = appContainer.calculateOfflineReadinessUseCase(
            profile = profile,
            securityStatus = secStatus,
            connectivityInfo = effectiveConn
        )

        SettingsUiState(
            currentTheme = prefs.theme,
            animationIntensity = prefs.animation,
            connectivityInfo = effectiveConn,
            isForcedOffline = prefs.forcedOffline,
            securityStatus = secStatus,
            readiness = readiness,
            userProfile = profile,
            localStorageUsedBytes = breakdown.usedBytes,
            storagePreference = prefs.storagePref,
            freeDeviceStorageBytes = breakdown.deviceFreeBytes,
            totalDeviceStorageBytes = breakdown.deviceTotalBytes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setTheme(theme: String) {
        viewModelScope.launch {
            appContainer.preferencesManager.setThemeMode(theme)
            appContainer.settingsRepository.saveSettings(
                AppSettings(themeMode = theme)
            )
        }
    }

    fun setAnimationIntensity(intensity: String) {
        viewModelScope.launch {
            appContainer.preferencesManager.setAnimationIntensity(intensity)
        }
    }

    fun toggleForcedOffline() {
        viewModelScope.launch {
            val current = appContainer.preferencesManager.isForcedOffline.first()
            appContainer.preferencesManager.setForcedOffline(!current)
        }
    }

    fun updateStoragePreference(preference: String) {
        viewModelScope.launch {
            appContainer.preferencesManager.setStoragePreference(preference)
        }
    }

    fun cleanTempStorage() {
        viewModelScope.launch {
            appContainer.localVaultRepository.cleanTempFiles()
        }
    }

    fun resetAppData(onResetComplete: () -> Unit) {
        viewModelScope.launch {
            appContainer.preferencesManager.resetAll()
            appContainer.activityRepository.clearActivities()
            onResetComplete()
        }
    }
}
