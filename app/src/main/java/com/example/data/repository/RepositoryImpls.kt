package com.example.data.repository

import com.example.core.database.AppSettings
import com.example.core.database.AppSettingsDao
import com.example.core.database.LocalActivity
import com.example.core.database.LocalActivityDao
import com.example.core.database.OfflineState
import com.example.core.database.OfflineStateDao
import com.example.core.database.UserProfile
import com.example.core.database.UserProfileDao
import com.example.domain.repository.ActivityRepository
import com.example.domain.repository.OfflineStateRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow

class UserProfileRepositoryImpl(
    private val dao: UserProfileDao
) : UserProfileRepository {
    override fun getUserProfile(): Flow<UserProfile?> = dao.getUserProfile()
    override suspend fun getUserProfileOnce(): UserProfile? = dao.getUserProfileOnce()
    override suspend fun saveUserProfile(profile: UserProfile) = dao.insertOrUpdate(profile)
}

class SettingsRepositoryImpl(
    private val dao: AppSettingsDao
) : SettingsRepository {
    override fun getSettings(): Flow<AppSettings?> = dao.getAppSettings()
    override suspend fun getSettingsOnce(): AppSettings? = dao.getAppSettingsOnce()
    override suspend fun saveSettings(settings: AppSettings) = dao.insertOrUpdate(settings)

    override suspend fun setForcedOffline(forced: Boolean) {
        val current = dao.getAppSettingsOnce() ?: AppSettings()
        dao.insertOrUpdate(current.copy(forcedOfflineMode = forced))
    }
}

class ActivityRepositoryImpl(
    private val dao: LocalActivityDao
) : ActivityRepository {
    override fun getRecentActivities(limit: Int): Flow<List<LocalActivity>> =
        dao.getRecentActivities(limit)

    override suspend fun recordActivity(title: String, description: String, category: String) {
        dao.insert(
            LocalActivity(
                title = title,
                description = description,
                category = category,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearActivities() = dao.clearAll()
}

class OfflineStateRepositoryImpl(
    private val dao: OfflineStateDao
) : OfflineStateRepository {
    override fun getOfflineState(): Flow<OfflineState?> = dao.getOfflineState()
    override suspend fun getOfflineStateOnce(): OfflineState? = dao.getOfflineStateOnce()
    override suspend fun updateOfflineState(state: OfflineState) = dao.insertOrUpdate(state)
}
