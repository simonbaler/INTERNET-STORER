package com.example.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class SplashNavTarget {
    object Idle : SplashNavTarget()
    object Onboarding : SplashNavTarget()
    object Main : SplashNavTarget()
}

class SplashViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val _statusText = MutableStateFlow("Preparing your digital world...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _navTarget = MutableStateFlow<SplashNavTarget>(SplashNavTarget.Idle)
    val navTarget: StateFlow<SplashNavTarget> = _navTarget.asStateFlow()

    init {
        startBootSequence()
    }

    private fun startBootSequence() {
        viewModelScope.launch {
            try {
                // Step 1: Initialize local security layer (Android Keystore)
                _statusText.value = "Securing local digital vault..."
                appContainer.securityManager.initialize()

                // Step 2: Initialize local database & check profile
                _statusText.value = "Loading continuity resources..."
                val profile = appContainer.userProfileRepository.getUserProfileOnce()

                // Step 3: Record local boot activity safely
                try {
                    appContainer.activityRepository.recordActivity(
                        title = "Local Autonomous Boot",
                        description = "Zero-network initialization complete; keystore active",
                        category = "SYSTEM"
                    )
                } catch (_: Exception) {
                    // Non-fatal if activity log table is busy
                }

                // Step 4: Check if onboarding is completed
                val isOnboardingDone = appContainer.preferencesManager.isOnboardingCompleted.first()
                _statusText.value = "You're ready. ♡"

                if (isOnboardingDone && profile != null) {
                    _navTarget.value = SplashNavTarget.Main
                } else {
                    _navTarget.value = SplashNavTarget.Onboarding
                }
            } catch (_: Exception) {
                // Guaranteed fallback: never hang the splash screen on any initialization exception
                _statusText.value = "Starting offline environment..."
                _navTarget.value = SplashNavTarget.Onboarding
            }
        }
    }
}
