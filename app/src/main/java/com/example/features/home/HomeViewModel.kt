package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.connectivity.ConnectivityInfo
import com.example.core.connectivity.ConnectivityObserver
import com.example.core.database.LocalActivity
import com.example.core.database.UserProfile
import com.example.core.security.SecurityManager
import com.example.data.AppContainer
import com.example.domain.usecase.OfflineReadinessResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val greeting: String = "Welcome ♡",
    val subtitle: String = "Your digital world is ready.",
    val userProfile: UserProfile? = null,
    val connectivityInfo: ConnectivityInfo = ConnectivityInfo(),
    val readiness: OfflineReadinessResult? = null,
    val recentActivities: List<LocalActivity> = emptyList(),
    val isForcedOffline: Boolean = false
)

class HomeViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        appContainer.userProfileRepository.getUserProfile(),
        appContainer.connectivityObserver.observe(),
        appContainer.activityRepository.getRecentActivities(10),
        appContainer.preferencesManager.isForcedOffline
    ) { profile, realConn, activities, forcedOffline ->
        val effectiveConn = if (forcedOffline) {
            ConnectivityInfo(
                status = com.example.core.connectivity.ConnectivityStatus.OFFLINE,
                networkType = com.example.core.connectivity.NetworkType.NONE
            )
        } else {
            realConn
        }

        val securityStatus = appContainer.securityManager.getSecurityStatus()
        val readiness = appContainer.calculateOfflineReadinessUseCase(
            profile = profile,
            securityStatus = securityStatus,
            connectivityInfo = effectiveConn
        )

        val name = profile?.displayName ?: "Explorer"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning, $name ♡"
            in 12..16 -> "Good afternoon, $name ♡"
            in 17..21 -> "Good evening, $name ♡"
            else -> "Good night, $name ♡"
        }

        val subtitle = when {
            effectiveConn.isOffline -> "You're offline, but your local world is safe."
            effectiveConn.isLimited -> "Prioritizing local resources while connection is faint."
            else -> "Everything important, always close to you."
        }

        HomeUiState(
            greeting = timeGreeting,
            subtitle = subtitle,
            userProfile = profile,
            connectivityInfo = effectiveConn,
            readiness = readiness,
            recentActivities = activities,
            isForcedOffline = forcedOffline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun toggleForcedOfflineMode() {
        viewModelScope.launch {
            val current = appContainer.preferencesManager.isForcedOffline.first()
            appContainer.preferencesManager.setForcedOffline(!current)
        }
    }
}
