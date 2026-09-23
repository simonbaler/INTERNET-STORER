package com.example.data.repository

import com.example.core.database.LocalFile
import com.example.core.database.LocalFileDao
import com.example.core.database.StorageRecord
import com.example.core.database.StorageRecordDao
import com.example.core.datastore.PreferencesManager
import com.example.core.offline.EventTypes
import com.example.core.offline.LocalEventBus
import com.example.core.storage.LocalStorageManager
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.VaultError
import com.example.domain.repository.LocalVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

class LocalVaultRepositoryImpl(
    private val localFileDao: LocalFileDao,
    private val storageRecordDao: StorageRecordDao,
    private val storageManager: LocalStorageManager,
    private val preferencesManager: PreferencesManager,
    private val eventBus: LocalEventBus
) : LocalVaultRepository {

    override fun getAllFiles(): Flow<List<LocalFile>> = localFileDao.getAllFiles()

    override fun getPinnedFiles(): Flow<List<LocalFile>> = localFileDao.getPinnedFiles()

    override fun getFileById(id: String): Flow<LocalFile?> = localFileDao.getFileById(id)

    override suspend fun getFileByIdOnce(id: String): LocalFile? = localFileDao.getFileByIdOnce(id)

    override suspend fun importFile(
        displayName: String,
        inputStream: InputStream,
        mimeType: String,
        encrypt: Boolean
    ): Result<LocalFile> = withContext(Dispatchers.IO) {
        try {
            val reservedLimitBytes = parseStoragePreferenceToBytes(
                preferencesManager.safeStoragePreference.first()
            )

            val importResult = storageManager.importStream(
                displayName = displayName,
                inputStream = inputStream,
                mimeType = mimeType,
                encrypt = encrypt,
                reservedLimitBytes = reservedLimitBytes
            )

            val now = System.currentTimeMillis()
            val fileEntity = LocalFile(
                id = UUID.randomUUID().toString(),
                displayName = importResult.displayName,
                relativePath = importResult.relativePath,
                mimeType = mimeType,
                sizeBytes = importResult.sizeBytes,
                createdAt = now,
                updatedAt = now,
                contentHash = importResult.contentHash,
                encryptionVersion = importResult.encryptionVersion,
                status = "STORED",
                isPinned = false,
                lastAccessedAt = now
            )

            localFileDao.insert(fileEntity)

            // Update storage record
            val currentUsage = storageManager.calculateVaultUsageBytes()
            storageRecordDao.insertOrUpdate(
                StorageRecord(
                    id = "VAULT_FILES",
                    category = "Local Vault",
                    bytesUsed = currentUsage,
                    bytesReserved = reservedLimitBytes,
                    updatedAt = now
                )
            )

            eventBus.emit(EventTypes.FILE_IMPORTED, fileEntity.id)
            Result.success(fileEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openFileInputStream(fileId: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val file = localFileDao.getFileByIdOnce(fileId) ?: throw VaultError.FileNotFound(fileId)
            localFileDao.updateLastAccessed(fileId, System.currentTimeMillis())
            val stream = storageManager.openStream(file.relativePath, file.encryptionVersion > 0)
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readTextFile(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val streamResult = openFileInputStream(fileId)
            val stream = streamResult.getOrThrow()
            val content = stream.use { isStream ->
                BufferedReader(InputStreamReader(isStream)).use { it.readText() }
            }
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun togglePin(fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = localFileDao.getFileByIdOnce(fileId) ?: throw VaultError.FileNotFound(fileId)
            val newPinned = !file.isPinned
            localFileDao.updatePinStatus(fileId, newPinned)
            eventBus.emit(EventTypes.FILE_UPDATED, fileId)
            Result.success(newPinned)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = localFileDao.getFileByIdOnce(fileId) ?: throw VaultError.FileNotFound(fileId)
            storageManager.deleteFile(file.relativePath)
            localFileDao.deleteById(fileId)

            val newUsage = storageManager.calculateVaultUsageBytes()
            val reservedLimit = parseStoragePreferenceToBytes(
                preferencesManager.safeStoragePreference.first()
            )
            storageRecordDao.updateBytesUsed("VAULT_FILES", newUsage)

            eventBus.emit(EventTypes.FILE_DELETED, fileId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyFileIntegrity(fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = localFileDao.getFileByIdOnce(fileId) ?: throw VaultError.FileNotFound(fileId)
            val isValid = storageManager.verifyIntegrity(file.relativePath, file.contentHash)
            if (!isValid) {
                localFileDao.updateStatus(fileId, "CORRUPTED")
            } else {
                localFileDao.updateStatus(fileId, "STORED")
                eventBus.emit(EventTypes.FILE_INTEGRITY_VERIFIED, fileId)
            }
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getStorageBreakdown(): Flow<StorageBreakdown> =
        combine(
            preferencesManager.safeStoragePreference,
            localFileDao.getFileCount(),
            storageRecordDao.getRecordByIdFlow("VAULT_FILES")
        ) { pref, count, _ ->
            val reservedBytes = parseStoragePreferenceToBytes(pref)
            storageManager.getStorageBreakdown(
                reservedLimitBytes = reservedBytes,
                storedFileCount = count
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun cleanTempFiles(): Int = withContext(Dispatchers.IO) {
        val count = storageManager.cleanStaleTempFiles()
        if (count > 0) {
            eventBus.emit(EventTypes.TEMP_STORAGE_CLEANED, "Cleaned $count temp files")
        }
        count
    }

    private fun parseStoragePreferenceToBytes(pref: String): Long {
        return when {
            pref.contains("500 MB") -> 500L * 1024L * 1024L
            pref.contains("1 GB") -> 1L * 1024L * 1024L * 1024L
            pref.contains("2 GB") -> 2L * 1024L * 1024L * 1024L
            pref.contains("5 GB") -> 5L * 1024L * 1024L * 1024L
            pref.contains("10 GB") -> 10L * 1024L * 1024L * 1024L
            else -> 2L * 1024L * 1024L * 1024L
        }
    }
}
