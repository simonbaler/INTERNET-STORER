package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileOnce(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getAppSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getAppSettingsOnce(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettings)
}

@Dao
interface LocalActivityDao {
    @Query("SELECT * FROM local_activity ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentActivities(limit: Int = 20): Flow<List<LocalActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: LocalActivity)

    @Query("DELETE FROM local_activity")
    suspend fun clearAll()
}

@Dao
interface OfflineStateDao {
    @Query("SELECT * FROM offline_state WHERE id = 1 LIMIT 1")
    fun getOfflineState(): Flow<OfflineState?>

    @Query("SELECT * FROM offline_state WHERE id = 1 LIMIT 1")
    suspend fun getOfflineStateOnce(): OfflineState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: OfflineState)
}

@Dao
interface LocalFileDao {
    @Query("SELECT * FROM local_files WHERE status != 'DELETED' ORDER BY isPinned DESC, createdAt DESC")
    fun getAllFiles(): Flow<List<LocalFile>>

    @Query("SELECT * FROM local_files WHERE status != 'DELETED' AND isPinned = 1 ORDER BY createdAt DESC")
    fun getPinnedFiles(): Flow<List<LocalFile>>

    @Query("SELECT * FROM local_files WHERE id = :id LIMIT 1")
    fun getFileById(id: String): Flow<LocalFile?>

    @Query("SELECT * FROM local_files WHERE id = :id LIMIT 1")
    suspend fun getFileByIdOnce(id: String): LocalFile?

    @Query("SELECT * FROM local_files WHERE relativePath = :relativePath AND status != 'DELETED' LIMIT 1")
    suspend fun getFileByRelativePath(relativePath: String): LocalFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: LocalFile)

    @Update
    suspend fun update(file: LocalFile)

    @Query("UPDATE local_files SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_files SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_files SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM local_files WHERE status != 'DELETED'")
    fun getTotalBytesUsed(): Flow<Long>

    @Query("SELECT COUNT(*) FROM local_files WHERE status != 'DELETED'")
    fun getFileCount(): Flow<Int>

    @Query("SELECT * FROM local_files WHERE contentHash = :hash AND status != 'DELETED'")
    suspend fun getFilesByHash(hash: String): List<LocalFile>

    @Query("SELECT * FROM local_files WHERE status != 'DELETED'")
    suspend fun getAllStoredFilesOnce(): List<LocalFile>

    @Query("UPDATE local_files SET referenceCount = referenceCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementReferenceCount(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_files SET referenceCount = CASE WHEN referenceCount > 0 THEN referenceCount - 1 ELSE 0 END, updatedAt = :updatedAt WHERE id = :id")
    suspend fun decrementReferenceCount(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM local_files WHERE relativePath = :relativePath AND status != 'DELETED'")
    suspend fun getActiveFilesByPath(relativePath: String): List<LocalFile>

    @Query("UPDATE local_files SET referenceCount = :refCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReferenceCount(id: String, refCount: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT referenceCount FROM local_files WHERE id = :id")
    suspend fun getReferenceCount(id: String): Int?
}

@Dao
interface OfflineOperationDao {
    @Query("SELECT * FROM offline_operations ORDER BY createdAt DESC")
    fun getAllOperations(): Flow<List<OfflineOperation>>

    @Query("SELECT * FROM offline_operations WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    fun getOperationsByStatus(status: String): Flow<List<OfflineOperation>>

    @Query("SELECT * FROM offline_operations WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun getPendingOperations(limit: Int = 10): List<OfflineOperation>

    @Query("SELECT * FROM offline_operations WHERE status = 'PROCESSING' AND updatedAt < :olderThan")
    suspend fun getStaleProcessingOperations(olderThan: Long): List<OfflineOperation>

    @Query("SELECT * FROM offline_operations WHERE id = :id LIMIT 1")
    suspend fun getOperationById(id: String): OfflineOperation?

    @Query("UPDATE offline_operations SET status = 'PROCESSING', updatedAt = :updatedAt WHERE id = :id AND status = 'PENDING'")
    suspend fun claimOperation(id: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OfflineOperation)

    @Update
    suspend fun update(operation: OfflineOperation)

    @Query("UPDATE offline_operations SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE offline_operations SET status = :status, errorCode = :errorCode, errorMessage = :errorMessage, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt, nextAttemptAt = :nextAttemptAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusAndError(
        id: String,
        status: String,
        errorCode: String?,
        errorMessage: String?,
        retryCount: Int,
        lastAttemptAt: Long?,
        nextAttemptAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM offline_operations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_operations WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}

@Dao
interface LocalEventDao {
    @Query("SELECT * FROM local_events ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<LocalEvent>>

    @Query("SELECT * FROM local_events WHERE processed = 0 ORDER BY createdAt ASC")
    suspend fun getUnprocessedEvents(): List<LocalEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LocalEvent)

    @Query("UPDATE local_events SET processed = 1 WHERE id = :id")
    suspend fun markProcessed(id: String)

    @Query("DELETE FROM local_events WHERE createdAt < :olderThan")
    suspend fun clearOldEvents(olderThan: Long)
}

@Dao
interface StorageRecordDao {
    @Query("SELECT * FROM storage_records")
    fun getAllRecords(): Flow<List<StorageRecord>>

    @Query("SELECT * FROM storage_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): StorageRecord?

    @Query("SELECT * FROM storage_records WHERE id = :id LIMIT 1")
    fun getRecordByIdFlow(id: String): Flow<StorageRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: StorageRecord)

    @Query("UPDATE storage_records SET bytesUsed = :bytesUsed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBytesUsed(id: String, bytesUsed: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface FileManifestDao {
    @Query("SELECT * FROM file_manifests WHERE fileId = :fileId LIMIT 1")
    fun getManifest(fileId: String): Flow<FileManifestEntity?>

    @Query("SELECT * FROM file_manifests WHERE fileId = :fileId LIMIT 1")
    suspend fun getManifestOnce(fileId: String): FileManifestEntity?

    @Query("SELECT * FROM file_manifests WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getManifestByHash(contentHash: String): FileManifestEntity?

    @Query("SELECT * FROM file_manifests ORDER BY createdAt DESC")
    fun getAllManifests(): Flow<List<FileManifestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(manifest: FileManifestEntity)

    @Query("DELETE FROM file_manifests WHERE fileId = :fileId")
    suspend fun delete(fileId: String)
}

@Dao
interface FileChunkDao {
    @Query("SELECT * FROM file_chunks WHERE fileId = :fileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(fileId: String): List<FileChunkEntity>

    @Query("SELECT * FROM file_chunks WHERE fileId = :fileId AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getChunk(fileId: String, chunkIndex: Int): FileChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<FileChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: FileChunkEntity)

    @Query("DELETE FROM file_chunks WHERE fileId = :fileId")
    suspend fun deleteChunksForFile(fileId: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE state NOT IN ('COMPLETED', 'CANCELLED', 'FAILED') ORDER BY createdAt DESC")
    fun getActiveTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE transferId = :transferId LIMIT 1")
    fun getTransferById(transferId: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers WHERE transferId = :transferId LIMIT 1")
    suspend fun getTransferByIdOnce(transferId: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE fileId = :fileId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestTransferForFile(fileId: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE state IN ('CREATED', 'PREPARING', 'TRANSFERRING', 'INTERRUPTED')")
    suspend fun getResumableTransfers(): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(transfer: TransferEntity)

    @Query("UPDATE transfers SET state = :state, updatedAt = :updatedAt WHERE transferId = :transferId")
    suspend fun updateTransferState(transferId: String, state: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transfers SET state = :newState, updatedAt = :updatedAt WHERE transferId = :transferId AND state = :expectedState")
    suspend fun transitionState(transferId: String, expectedState: String, newState: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE transfers SET state = :newState, updatedAt = :updatedAt WHERE transferId = :transferId AND state IN (:allowedStates)")
    suspend fun transitionStateFromAllowed(transferId: String, allowedStates: List<String>, newState: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE transfers SET transferredBytes = :transferredBytes, verifiedChunks = :verifiedChunks, updatedAt = :updatedAt WHERE transferId = :transferId")
    suspend fun updateTransferProgress(
        transferId: String,
        transferredBytes: Long,
        verifiedChunks: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE transfers SET state = 'COMPLETED', completedAt = :completedAt, updatedAt = :completedAt WHERE transferId = :transferId")
    suspend fun markCompleted(transferId: String, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transfers SET state = 'FAILED', errorMessage = :error, retryCount = retryCount + 1, updatedAt = :updatedAt WHERE transferId = :transferId")
    suspend fun markFailed(transferId: String, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transfers WHERE transferId = :transferId")
    suspend fun deleteTransfer(transferId: String)

    @Query("DELETE FROM transfers WHERE state IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearFinishedTransfers()
}

@Dao
interface TransferChunkDao {
    @Query("SELECT * FROM transfer_chunks WHERE transferId = :transferId ORDER BY chunkIndex ASC")
    fun getChunksForTransfer(transferId: String): Flow<List<TransferChunkEntity>>

    @Query("SELECT * FROM transfer_chunks WHERE transferId = :transferId ORDER BY chunkIndex ASC")
    suspend fun getChunksForTransferOnce(transferId: String): List<TransferChunkEntity>

    @Query("SELECT * FROM transfer_chunks WHERE transferId = :transferId AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getChunk(transferId: String, chunkIndex: Int): TransferChunkEntity?

    @Query("UPDATE transfer_chunks SET status = 'PROCESSING' WHERE transferId = :transferId AND chunkIndex = :chunkIndex AND status IN ('PENDING', 'FAILED')")
    suspend fun claimChunkForProcessing(transferId: String, chunkIndex: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<TransferChunkEntity>)

    @Query("UPDATE transfer_chunks SET status = :status, verifiedAt = :verifiedAt WHERE transferId = :transferId AND chunkIndex = :chunkIndex")
    suspend fun updateChunkStatus(
        transferId: String,
        chunkIndex: Int,
        status: String,
        verifiedAt: Long? = null
    )

    @Query("SELECT COUNT(*) FROM transfer_chunks WHERE transferId = :transferId AND status = 'VERIFIED'")
    suspend fun getVerifiedChunkCount(transferId: String): Int

    @Query("SELECT COALESCE(SUM(length), 0) FROM transfer_chunks WHERE transferId = :transferId AND status = 'VERIFIED'")
    suspend fun getSumVerifiedChunkLengths(transferId: String): Long

    @Query("DELETE FROM transfer_chunks WHERE transferId = :transferId")
    suspend fun deleteChunksForTransfer(transferId: String)
}

@Dao
interface FileVersionDao {
    @Query("SELECT * FROM file_versions WHERE fileId = :fileId ORDER BY versionNumber DESC")
    fun getVersionsForFile(fileId: String): Flow<List<FileVersionEntity>>

    @Query("SELECT * FROM file_versions WHERE fileId = :fileId ORDER BY versionNumber DESC")
    suspend fun getVersionsForFileOnce(fileId: String): List<FileVersionEntity>

    @Query("SELECT * FROM file_versions WHERE fileId = :fileId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatestVersion(fileId: String): FileVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: FileVersionEntity)

    @Query("DELETE FROM file_versions WHERE fileId = :fileId")
    suspend fun deleteVersionsForFile(fileId: String)
}
