package com.example.core.storage

import com.example.core.database.AppDatabase
import com.example.core.database.LocalFile
import com.example.domain.model.IntegrityIssue
import com.example.domain.model.IntegrityIssueType
import com.example.domain.model.StorageIntegrityReport
import com.example.domain.model.VaultError
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class StorageIntegrityEngine(
    private val database: AppDatabase,
    private val localStorageManager: LocalStorageManager,
    private val chunkEngine: ChunkEngine
) {

    private val engineMutex = Mutex()

    /**
     * Executes a thorough integrity check across Room database metadata and on-disk files.
     * Never silently deletes user data or evidence.
     */
    suspend fun runIntegrityCheck(): StorageIntegrityReport = withContext(Dispatchers.IO) {
        val fileDao = database.localFileDao()
        val allStoredFiles = fileDao.getAllStoredFilesOnce()
        val onDiskFiles = localStorageManager.listAllVaultFiles()

        val issues = mutableListOf<IntegrityIssue>()
        var validFiles = 0
        var missingFiles = 0
        var corruptedFiles = 0
        var orphanMetadataCount = 0

        val mappedDiskPaths = mutableSetOf<String>()

        // 1. Validate every database record against physical disk
        for (localFile in allStoredFiles) {
            val file = try {
                localStorageManager.resolveVaultFile(localFile.relativePath)
            } catch (e: Exception) {
                null
            }

            if (file == null || !file.exists()) {
                missingFiles++
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.MISSING_FILE,
                        fileId = localFile.id,
                        path = localFile.relativePath,
                        description = "Database record exists for '${localFile.displayName}', but physical file is missing from vault.",
                        recoverable = true
                    )
                )
            } else {
                mappedDiskPaths.add(file.canonicalPath)

                // Verify content SHA-256 hash using streaming read
                val actualHash = try {
                    chunkEngine.calculateStreamingFileHash(file)
                } catch (e: Exception) {
                    ""
                }

                if (!actualHash.equals(localFile.contentHash, ignoreCase = true)) {
                    corruptedFiles++
                    // Mark file status as CORRUPTED in database so UI knows, but preserve file evidence on disk
                    fileDao.updateStatus(localFile.id, "CORRUPTED")
                    issues.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.HASH_MISMATCH,
                            fileId = localFile.id,
                            path = localFile.relativePath,
                            description = "Cryptographic integrity failure for '${localFile.displayName}': recorded hash was ${localFile.contentHash}, but on-disk hash is $actualHash.",
                            recoverable = true
                        )
                    )
                } else {
                    validFiles++
                }
            }
        }

        // 2. Detect orphan files on disk that have no Room metadata
        var orphanFiles = 0
        for (diskFile in onDiskFiles) {
            if (!mappedDiskPaths.contains(diskFile.canonicalPath)) {
                orphanFiles++
                val relPath = localStorageManager.getRelativePath(diskFile)
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.ORPHAN_FILE,
                        fileId = null,
                        path = relPath,
                        description = "Orphan file found on disk with no database record: $relPath (${diskFile.length()} bytes).",
                        recoverable = true
                    )
                )
            }
        }

        StorageIntegrityReport(
            totalFilesChecked = allStoredFiles.size,
            validFiles = validFiles,
            missingFiles = missingFiles,
            corruptedFiles = corruptedFiles,
            orphanFiles = orphanFiles,
            orphanMetadataRecords = orphanMetadataCount,
            timestamp = System.currentTimeMillis(),
            issues = issues
        )
    }

    /**
     * Checks if identical content exists in the vault for deduplication.
     */
    suspend fun findExistingFileByHash(contentHash: String): LocalFile? = withContext(Dispatchers.IO) {
        val matches = database.localFileDao().getFilesByHash(contentHash)
        matches.firstOrNull { it.status == "STORED" }
    }

    /**
     * Links a new file record to existing on-disk content without duplicating physical bytes.
     * Increments the reference count of the original file.
     */
    suspend fun linkDeduplicatedFile(
        existingFile: LocalFile,
        newDisplayName: String
    ): LocalFile = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            database.withTransaction {
                val fileDao = database.localFileDao()
                val sanitizedName = localStorageManager.sanitizeFileName(newDisplayName)

                val activeBefore = fileDao.getActiveFilesByPath(existingFile.relativePath)
                val newRefCount = activeBefore.size + 1

                val duplicateRecord = LocalFile(
                    id = UUID.randomUUID().toString(),
                    displayName = sanitizedName,
                    originalName = newDisplayName,
                    relativePath = existingFile.relativePath, // Shares same physical file safely
                    mimeType = existingFile.mimeType,
                    sizeBytes = existingFile.sizeBytes,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    contentHash = existingFile.contentHash,
                    encryptionVersion = existingFile.encryptionVersion,
                    status = "STORED",
                    isPinned = false,
                    referenceCount = newRefCount,
                    version = 1
                )
                fileDao.insert(duplicateRecord)

                // Update all active references sharing this physical file
                for (ref in activeBefore) {
                    fileDao.setReferenceCount(ref.id, newRefCount)
                }

                duplicateRecord
            }
        }
    }

    /**
     * Safe deletion respecting reference counting.
     * Deletes the physical file only when no other references remain.
     * Guaranteed non-negative reference counts and thread-safe.
     */
    suspend fun safeDeleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            database.withTransaction {
                val fileDao = database.localFileDao()
                val fileRecord = fileDao.getFileByIdOnce(fileId) ?: return@withTransaction false
                if (fileRecord.status == "DELETED") return@withTransaction true

                // Mark this record as DELETED and set its reference count to 0
                fileDao.updateStatus(fileId, "DELETED")
                fileDao.setReferenceCount(fileId, 0)

                // Find all active records pointing to the same relativePath
                val activeRemaining = fileDao.getActiveFilesByPath(fileRecord.relativePath)
                val remainingCount = activeRemaining.size

                if (remainingCount == 0) {
                    // No references remain; safely delete physical file from disk
                    try {
                        localStorageManager.deleteFile(fileRecord.relativePath)
                    } catch (_: Exception) {}
                } else {
                    // Synchronize reference count on all remaining active records
                    for (ref in activeRemaining) {
                        fileDao.setReferenceCount(ref.id, remainingCount)
                    }
                }
                true
            }
        }
    }

    /**
     * Recovers or registers an orphan file found on disk by calculating its hash
     * and adding it to the vault database.
     */
    suspend fun recoverOrphanFile(relativePath: String): LocalFile = withContext(Dispatchers.IO) {
        val file = localStorageManager.resolveVaultFile(relativePath)
        if (!file.exists()) {
            throw VaultError.FileNotFound(relativePath, "Cannot recover non-existent file: $relativePath")
        }

        val hash = chunkEngine.calculateStreamingFileHash(file)
        val fileId = UUID.randomUUID().toString()
        val localFile = LocalFile(
            id = fileId,
            displayName = file.name,
            originalName = file.name,
            relativePath = relativePath,
            mimeType = "application/octet-stream",
            sizeBytes = file.length(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            contentHash = hash,
            encryptionVersion = 0,
            status = "STORED",
            isPinned = false,
            referenceCount = 1,
            version = 1
        )

        database.localFileDao().insert(localFile)
        localFile
    }
}
