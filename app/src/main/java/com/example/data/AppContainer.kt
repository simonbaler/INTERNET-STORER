package com.example.data

import android.content.Context
import com.example.core.connectivity.ConnectivityObserver
import com.example.core.connectivity.NetworkConnectivityObserver
import com.example.core.database.AppDatabase
import com.example.core.datastore.PreferencesManager
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.security.SecurityManager
import com.example.core.offline.LocalEventBus
import com.example.core.offline.OfflineOperationEngine
import com.example.core.storage.LocalStorageManager
import com.example.data.repository.ActivityRepositoryImpl
import com.example.data.repository.LocalVaultRepositoryImpl
import com.example.data.repository.OfflineOperationRepositoryImpl
import com.example.data.repository.OfflineStateRepositoryImpl
import com.example.data.repository.SettingsRepositoryImpl
import com.example.data.repository.UserProfileRepositoryImpl
import com.example.domain.repository.ActivityRepository
import com.example.domain.repository.LocalVaultRepository
import com.example.domain.repository.OfflineOperationRepository
import com.example.domain.repository.OfflineStateRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.repository.UserProfileRepository
import com.example.domain.usecase.CalculateOfflineReadinessUseCase

interface AppContainer {
    val database: AppDatabase
    val preferencesManager: PreferencesManager
    val connectivityObserver: ConnectivityObserver
    val securityManager: SecurityManager
    val storageManager: LocalStorageManager
    val localEventBus: LocalEventBus
    val offlineOperationEngine: OfflineOperationEngine
    val localVaultRepository: LocalVaultRepository
    val offlineOperationRepository: OfflineOperationRepository
    val userProfileRepository: UserProfileRepository
    val settingsRepository: SettingsRepository
    val activityRepository: ActivityRepository
    val offlineStateRepository: OfflineStateRepository
    val calculateOfflineReadinessUseCase: CalculateOfflineReadinessUseCase
    val chunkEngine: com.example.core.storage.ChunkEngine
    val manifestManager: com.example.core.storage.FileManifestManager
    val integrityEngine: com.example.core.storage.StorageIntegrityEngine
    val transferEngine: com.example.core.transfer.TransferEngine
    val transferRepository: com.example.domain.repository.TransferRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override val connectivityObserver: ConnectivityObserver by lazy {
        NetworkConnectivityObserver(context)
    }

    override val securityManager: SecurityManager by lazy {
        AndroidKeystoreSecurityManager()
    }

    override val storageManager: LocalStorageManager by lazy {
        LocalStorageManager(context, securityManager)
    }

    override val localEventBus: LocalEventBus by lazy {
        LocalEventBus(database.localEventDao())
    }

    override val offlineOperationEngine: OfflineOperationEngine by lazy {
        OfflineOperationEngine(
            operationDao = database.offlineOperationDao(),
            localFileDao = database.localFileDao(),
            storageRecordDao = database.storageRecordDao(),
            storageManager = storageManager,
            eventBus = localEventBus
        )
    }

    override val localVaultRepository: LocalVaultRepository by lazy {
        LocalVaultRepositoryImpl(
            localFileDao = database.localFileDao(),
            storageRecordDao = database.storageRecordDao(),
            storageManager = storageManager,
            preferencesManager = preferencesManager,
            eventBus = localEventBus
        )
    }

    override val offlineOperationRepository: OfflineOperationRepository by lazy {
        OfflineOperationRepositoryImpl(offlineOperationEngine)
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        UserProfileRepositoryImpl(database.userProfileDao())
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database.appSettingsDao())
    }

    override val activityRepository: ActivityRepository by lazy {
        ActivityRepositoryImpl(database.localActivityDao())
    }

    override val offlineStateRepository: OfflineStateRepository by lazy {
        OfflineStateRepositoryImpl(database.offlineStateDao())
    }

    override val calculateOfflineReadinessUseCase: CalculateOfflineReadinessUseCase by lazy {
        CalculateOfflineReadinessUseCase()
    }

    override val chunkEngine: com.example.core.storage.ChunkEngine by lazy {
        com.example.core.storage.ChunkEngine()
    }

    override val manifestManager: com.example.core.storage.FileManifestManager by lazy {
        com.example.core.storage.FileManifestManager()
    }

    override val integrityEngine: com.example.core.storage.StorageIntegrityEngine by lazy {
        com.example.core.storage.StorageIntegrityEngine(
            database = database,
            localStorageManager = storageManager,
            chunkEngine = chunkEngine
        )
    }

    override val transferEngine: com.example.core.transfer.TransferEngine by lazy {
        com.example.core.transfer.TransferEngine(
            database = database,
            localStorageManager = storageManager,
            chunkEngine = chunkEngine,
            manifestManager = manifestManager
        )
    }

    override val transferRepository: com.example.domain.repository.TransferRepository by lazy {
        com.example.data.repository.TransferRepositoryImpl(
            database = database,
            transferEngine = transferEngine,
            integrityEngine = integrityEngine,
            manifestManager = manifestManager
        )
    }
}
