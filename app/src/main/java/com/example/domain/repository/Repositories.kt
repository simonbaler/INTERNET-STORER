package com.example.domain.repository

import com.example.core.database.AppSettings
import com.example.core.database.LocalActivity
import com.example.core.database.OfflineState
import com.example.core.database.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun getUserProfileOnce(): UserProfile?
    suspend fun saveUserProfile(profile: UserProfile)
}

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings?>
    suspend fun getSettingsOnce(): AppSettings?
    suspend fun saveSettings(settings: AppSettings)
    suspend fun setForcedOffline(forced: Boolean)
}

interface ActivityRepository {
    fun getRecentActivities(limit: Int = 20): Flow<List<LocalActivity>>
    suspend fun recordActivity(title: String, description: String, category: String = "SYSTEM")
    suspend fun clearActivities()
}

interface OfflineStateRepository {
    fun getOfflineState(): Flow<OfflineState?>
    suspend fun getOfflineStateOnce(): OfflineState?
    suspend fun updateOfflineState(state: OfflineState)
}
