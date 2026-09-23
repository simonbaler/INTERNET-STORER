package com.example.domain.model

import java.text.DecimalFormat

data class FileIdentity(
    val fileId: String,
    val contentHash: String,
    val mimeType: String,
    val originalFilename: String,
    val sanitizedFilename: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val encryptionState: String = "NONE", // NONE, AES_256_GCM
    val storageLocation: String,
    val versionNumber: Int = 1,
    val lifecycleState: String = "ACTIVE", // ACTIVE, ARCHIVED, CORRUPTED, DELETED
    val referenceCount: Int = 1
)

data class FileManifest(
    val fileId: String,
    val contentHash: String,
    val sizeBytes: Long,
    val mimeType: String,
    val filename: String,
    val version: Int = 1,
    val encryptionMetadata: String = "NONE",
    val chunkSize: Int = 64 * 1024, // 64 KB default safe chunk
    val totalChunks: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1
)

enum class ChunkState {
    PENDING,
    PROCESSING,
    VERIFIED,
    FAILED
}

data class FileChunk(
    val transferId: String,
    val fileId: String,
    val chunkIndex: Int,
    val offset: Long,
    val length: Int,
    val chunkHash: String,
    val status: ChunkState = ChunkState.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null
)

enum class TransferState {
    CREATED,
    PREPARING,
    TRANSFERRING,
    PAUSED,
    INTERRUPTED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == CANCELLED

    fun canTransitionTo(next: TransferState): Boolean = when (this) {
        CREATED -> next == PREPARING || next == CANCELLED
        PREPARING -> next == TRANSFERRING || next == FAILED || next == CANCELLED
        TRANSFERRING -> next == PAUSED || next == INTERRUPTED || next == VERIFYING || next == FAILED || next == CANCELLED
        PAUSED -> next == TRANSFERRING || next == CANCELLED
        INTERRUPTED -> next == PREPARING || next == TRANSFERRING || next == CANCELLED
        VERIFYING -> next == COMPLETED || next == FAILED
        FAILED -> next == PREPARING || next == TRANSFERRING || next == CANCELLED // via retry
        COMPLETED -> false
        CANCELLED -> false
    }
}

enum class TransferDirection {
    INCOMING,
    OUTGOING,
    LOCAL_RECONSTRUCTION
}

data class TransferRecord(
    val transferId: String,
    val fileId: String,
    val filename: String = "",
    val direction: TransferDirection = TransferDirection.LOCAL_RECONSTRUCTION,
    val state: TransferState = TransferState.CREATED,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val totalChunks: Int = 0,
    val verifiedChunks: Int = 0,
    val chunkSize: Int = 64 * 1024,
    val contentHash: String = "",
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progress: Float
        get() = if (totalChunks > 0) {
            (verifiedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
        } else if (totalBytes > 0) {
            (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedTotalSize: String
        get() = formatBytes(totalBytes)

    val formattedTransferredSize: String
        get() = formatBytes(transferredBytes)
}

data class FileVersion(
    val versionId: String,
    val fileId: String,
    val versionNumber: Int,
    val parentVersionId: String? = null,
    val contentHash: String,
    val sizeBytes: Long,
    val modifiedAt: Long = System.currentTimeMillis(),
    val changeDescription: String = "Initial import"
)

enum class IntegrityIssueType {
    MISSING_FILE,
    HASH_MISMATCH,
    ORPHAN_METADATA,
    ORPHAN_FILE
}

data class IntegrityIssue(
    val type: IntegrityIssueType,
    val fileId: String?,
    val path: String,
    val description: String,
    val recoverable: Boolean = true
)

data class StorageIntegrityReport(
    val totalFilesChecked: Int = 0,
    val validFiles: Int = 0,
    val missingFiles: Int = 0,
    val corruptedFiles: Int = 0,
    val orphanFiles: Int = 0,
    val orphanMetadataRecords: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val issues: List<IntegrityIssue> = emptyList()
) {
    val isClean: Boolean
        get() = missingFiles == 0 && corruptedFiles == 0 && orphanFiles == 0 && orphanMetadataRecords == 0
}
