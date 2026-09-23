package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Explorer",
    val avatarId: String = "heart_rose",
    val preferredLanguage: String = "English",
    val storagePreference: String = "Balanced (2 GB)",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "Soft Romantic",
    val animationIntensity: String = "Smooth",
    val forcedOfflineMode: Boolean = false,
    val lastBackupTimestamp: Long = 0L
)

@Entity(
    tableName = "local_activity",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class LocalActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val category: String = "SYSTEM",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_state")
data class OfflineState(
    @PrimaryKey val id: Int = 1,
    val readinessScore: Int = 100,
    val lastCalculated: Long = System.currentTimeMillis(),
    val networkState: String = "ONLINE",
    val storageUsedBytes: Long = 1024L * 1024L * 35L, // initial local cache: ~35 MB
    val isKeystoreReady: Boolean = true
)

@Entity(
    tableName = "offline_operations",
    indices = [
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["nextAttemptAt"])
    ]
)
data class OfflineOperation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val operationType: String,
    val payloadReference: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextAttemptAt: Long? = null,
    val priority: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Entity(
    tableName = "local_events",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["processed"]),
        Index(value = ["createdAt"])
    ]
)
data class LocalEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val payloadReference: String? = null,
    val processed: Boolean = false,
    val priority: Int = 0
)

@Entity(
    tableName = "local_files",
    indices = [
        Index(value = ["status"]),
        Index(value = ["isPinned"]),
        Index(value = ["createdAt"]),
        Index(value = ["contentHash"])
    ]
)
data class LocalFile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val contentHash: String,
    val encryptionVersion: Int = 0, // 0 = unencrypted, 1 = AES-GCM
    val status: String = "STORED", // STORED, CORRUPTED, DELETED
    val isPinned: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val referenceCount: Int = 1,
    val version: Int = 1,
    val originalName: String = displayName
)

@Entity(tableName = "storage_records")
data class StorageRecord(
    @PrimaryKey val id: String,
    val category: String,
    val bytesUsed: Long = 0L,
    val bytesReserved: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "file_manifests",
    indices = [
        Index(value = ["contentHash"])
    ]
)
data class FileManifestEntity(
    @PrimaryKey val fileId: String,
    val contentHash: String,
    val sizeBytes: Long,
    val mimeType: String,
    val filename: String,
    val version: Int = 1,
    val encryptionMetadata: String = "NONE",
    val chunkSize: Int = 64 * 1024,
    val totalChunks: Int = 1,
    val manifestJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "file_chunks",
    primaryKeys = ["fileId", "chunkIndex"],
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["chunkHash"])
    ]
)
data class FileChunkEntity(
    val fileId: String,
    val chunkIndex: Int,
    val offset: Long,
    val length: Int,
    val chunkHash: String,
    val status: String = "VERIFIED",
    val verifiedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["state"])
    ]
)
data class TransferEntity(
    @PrimaryKey val transferId: String = UUID.randomUUID().toString(),
    val fileId: String,
    val filename: String = "",
    val direction: String = "LOCAL_RECONSTRUCTION",
    val state: String = "CREATED",
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
)

@Entity(
    tableName = "transfer_chunks",
    primaryKeys = ["transferId", "chunkIndex"],
    indices = [
        Index(value = ["transferId"]),
        Index(value = ["status"])
    ]
)
data class TransferChunkEntity(
    val transferId: String,
    val fileId: String,
    val chunkIndex: Int,
    val offset: Long,
    val length: Int,
    val chunkHash: String,
    val status: String = "PENDING", // PENDING, PROCESSING, VERIFIED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null
)

@Entity(
    tableName = "file_versions",
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["fileId", "versionNumber"])
    ]
)
data class FileVersionEntity(
    @PrimaryKey val versionId: String = UUID.randomUUID().toString(),
    val fileId: String,
    val versionNumber: Int = 1,
    val parentVersionId: String? = null,
    val contentHash: String,
    val sizeBytes: Long,
    val modifiedAt: Long = System.currentTimeMillis(),
    val changeDescription: String = "Initial version"
)
