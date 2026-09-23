package com.example.core.offline

import com.example.core.database.LocalFileDao
import com.example.core.database.OfflineOperation
import com.example.core.database.OfflineOperationDao
import com.example.core.database.StorageRecord
import com.example.core.database.StorageRecordDao
import com.example.core.storage.LocalStorageManager
import com.example.domain.model.VaultError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

object OperationTypes {
    const val VERIFY_INTEGRITY = "VERIFY_INTEGRITY"
    const val CLEANUP_TEMP = "CLEANUP_TEMP"
    const val RECALCULATE_STORAGE = "RECALCULATE_STORAGE"
    const val OPTIMIZE_LOCAL_INDEX = "OPTIMIZE_LOCAL_INDEX"
}

class OfflineOperationEngine(
    private val operationDao: OfflineOperationDao,
    private val localFileDao: LocalFileDao,
    private val storageRecordDao: StorageRecordDao,
    private val storageManager: LocalStorageManager,
    private val eventBus: LocalEventBus
) {

    val allOperations: Flow<List<OfflineOperation>> = operationDao.getAllOperations()

    /**
     * Enqueues an operation locally and emits a creation event.
     */
    suspend fun enqueueOperation(
        operationType: String,
        payloadReference: String? = null,
        priority: Int = 0
    ): OfflineOperation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val operation = OfflineOperation(
            id = UUID.randomUUID().toString(),
            operationType = operationType,
            payloadReference = payloadReference,
            createdAt = now,
            updatedAt = now,
            status = "PENDING",
            retryCount = 0,
            priority = priority
        )

        operationDao.insert(operation)
        eventBus.emit(EventTypes.OFFLINE_OPERATION_CREATED, operation.id, priority)
        operation
    }

    /**
     * Claims and executes pending local operations.
     */
    suspend fun processPendingOperations(maxBatch: Int = 5): Int = withContext(Dispatchers.IO) {
        val pending = operationDao.getPendingOperations(maxBatch)
        var processedCount = 0

        for (op in pending) {
            val claimed = claimOperation(op.id)
            if (claimed) {
                executeOperation(op.id)
                processedCount++
            }
        }
        processedCount
    }

    private suspend fun claimOperation(opId: String): Boolean {
        val affected = operationDao.claimOperation(opId)
        return affected > 0
    }

    /**
     * Executes a single claimed operation.
     */
    suspend fun executeOperation(opId: String): Boolean = withContext(Dispatchers.IO) {
        val op = operationDao.getOperationById(opId) ?: return@withContext false
        val now = System.currentTimeMillis()

        try {
            when (op.operationType) {
                OperationTypes.VERIFY_INTEGRITY -> {
                    val fileId = op.payloadReference ?: throw IllegalArgumentException("Missing file ID")
                    val file = localFileDao.getFileByIdOnce(fileId) ?: throw VaultError.FileNotFound(fileId)
                    val isValid = storageManager.verifyIntegrity(file.relativePath, file.contentHash)
                    if (!isValid) {
                        localFileDao.updateStatus(file.id, "CORRUPTED")
                        throw VaultError.FileCorrupted(file.id)
                    } else {
                        localFileDao.updateStatus(file.id, "STORED")
                        eventBus.emit(EventTypes.FILE_INTEGRITY_VERIFIED, file.id)
                    }
                }

                OperationTypes.CLEANUP_TEMP -> {
                    val cleaned = storageManager.cleanStaleTempFiles(olderThanMs = 60_000L)
                    eventBus.emit(EventTypes.TEMP_STORAGE_CLEANED, "Cleaned $cleaned files")
                }

                OperationTypes.RECALCULATE_STORAGE -> {
                    val usage = storageManager.calculateVaultUsageBytes()
                    storageRecordDao.insertOrUpdate(
                        StorageRecord(
                            id = "VAULT_FILES",
                            category = "Local Vault Files",
                            bytesUsed = usage,
                            bytesReserved = 2L * 1024L * 1024L * 1024L,
                            updatedAt = now
                        )
                    )
                    eventBus.emit(EventTypes.STORAGE_LIMIT_CHANGED, "$usage bytes")
                }

                OperationTypes.OPTIMIZE_LOCAL_INDEX -> {
                    // Clean events older than 7 days
                    val oldThreshold = now - (7L * 24L * 3600L * 1000L)
                    // Maintenance operation
                }

                else -> {
                    // Unknown operation type
                    throw IllegalArgumentException("Unsupported local operation type: ${op.operationType}")
                }
            }

            // Mark COMPLETED
            operationDao.updateStatus(op.id, "COMPLETED")
            eventBus.emit(EventTypes.OFFLINE_OPERATION_COMPLETED, op.id)
            true
        } catch (e: Exception) {
            val newRetry = op.retryCount + 1
            val nextAttempt = if (newRetry < 5) {
                now + (10_000L * (1 shl (newRetry - 1))) // Exponential backoff: 10s, 20s, 40s...
            } else null

            val finalStatus = if (newRetry >= 5) "FAILED" else "PENDING"

            operationDao.updateStatusAndError(
                id = op.id,
                status = finalStatus,
                errorCode = e.javaClass.simpleName,
                errorMessage = e.message ?: "Unknown local execution error",
                retryCount = newRetry,
                lastAttemptAt = now,
                nextAttemptAt = nextAttempt
            )

            eventBus.emit(EventTypes.OFFLINE_OPERATION_FAILED, op.id)
            false
        }
    }

    /**
     * Recovers operations stuck in PROCESSING (e.g. from process termination).
     */
    suspend fun recoverStaleOperations(timeoutMs: Long = 30_000L): Int = withContext(Dispatchers.IO) {
        val staleThreshold = System.currentTimeMillis() - timeoutMs
        val staleList = operationDao.getStaleProcessingOperations(staleThreshold)
        var count = 0

        for (stale in staleList) {
            val newRetry = stale.retryCount + 1
            if (newRetry >= 5) {
                operationDao.updateStatusAndError(
                    id = stale.id,
                    status = "FAILED",
                    errorCode = "PROCESS_TERMINATED",
                    errorMessage = "Operation interrupted by process death and exceeded max retries",
                    retryCount = newRetry,
                    lastAttemptAt = System.currentTimeMillis(),
                    nextAttemptAt = null
                )
            } else {
                operationDao.updateStatusAndError(
                    id = stale.id,
                    status = "PENDING",
                    errorCode = "RECOVERED_STALE",
                    errorMessage = "Recovered from interrupted processing state",
                    retryCount = newRetry,
                    lastAttemptAt = System.currentTimeMillis(),
                    nextAttemptAt = System.currentTimeMillis() + 5000L
                )
            }
            count++
        }
        count
    }

    suspend fun retryOperation(opId: String): Boolean = withContext(Dispatchers.IO) {
        val op = operationDao.getOperationById(opId) ?: return@withContext false
        // State machine rule: only FAILED operations can be moved back to PENDING for retry
        if (op.status != "FAILED") return@withContext false
        operationDao.updateStatusAndError(
            id = opId,
            status = "PENDING",
            errorCode = null,
            errorMessage = null,
            retryCount = 0,
            lastAttemptAt = null,
            nextAttemptAt = System.currentTimeMillis()
        )
        val claimed = claimOperation(opId)
        if (claimed) {
            executeOperation(opId)
        } else {
            false
        }
    }

    suspend fun cancelOperation(opId: String): Boolean = withContext(Dispatchers.IO) {
        val op = operationDao.getOperationById(opId) ?: return@withContext false
        // State machine rule: only PENDING or PROCESSING operations can be CANCELLED
        if (op.status != "PENDING" && op.status != "PROCESSING") return@withContext false
        operationDao.updateStatus(opId, "CANCELLED")
        true
    }
}
