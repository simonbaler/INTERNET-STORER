package com.example.domain.model

import java.text.DecimalFormat

sealed class VaultError(val userMessage: String) : Exception(userMessage) {
    class StorageUnavailable(message: String = "Local storage is temporarily inaccessible on this device.") : VaultError(message)
    class StorageLimitReached(val limitBytes: Long, message: String = "InternetStorer storage limit reached. Adjust limit in settings or remove unused files.") : VaultError(message)
    class StorageFull(val requiredBytes: Long, val freeBytes: Long, message: String = "Insufficient storage space. Required: $requiredBytes bytes, Available: $freeBytes bytes.") : VaultError(message)
    class FileNotFound(val fileId: String, message: String = "Requested file could not be found in local vault.") : VaultError(message)
    class FileCorrupted(val fileId: String, message: String = "File integrity check failed. Cryptographic hash does not match stored content.") : VaultError(message)
    class IntegrityMismatch(val expectedHash: String, val actualHash: String, message: String = "Cryptographic integrity mismatch: expected $expectedHash, got $actualHash.") : VaultError(message)
    class InvalidManifest(val reason: String, message: String = "Invalid file manifest: $reason") : VaultError(message)
    class InvalidChunk(val chunkIndex: Int, val reason: String, message: String = "Invalid chunk #$chunkIndex: $reason") : VaultError(message)
    class InterruptedTransfer(val transferId: String, message: String = "Transfer $transferId was interrupted.") : VaultError(message)
    class MissingSourceFile(val path: String, message: String = "Source file missing at: $path") : VaultError(message)
    class MissingDestination(val path: String, message: String = "Destination target missing or invalid: $path") : VaultError(message)
    class EncryptionFailure(message: String = "Could not encrypt local data using Keystore.") : VaultError(message)
    class DecryptionFailure(message: String = "Could not decrypt local data. Corrupted payload or invalid key.") : VaultError(message)
    class InvalidFileName(val fileName: String, message: String = "Invalid file name: contains forbidden characters or is empty.") : VaultError(message)
    class PathTraversalAttempt(val path: String, message: String = "Security violation: Path traversal detected and prevented.") : VaultError(message)
    class DatabaseFailure(message: String = "Local database write failed.") : VaultError(message)
    class OperationAlreadyProcessing(val opId: String, message: String = "Operation is already in processing state.") : VaultError(message)
    class OperationCancelled(val opId: String, message: String = "Operation was cancelled.") : VaultError(message)
    class UnknownLocalError(message: String = "An unexpected local error occurred.") : VaultError(message)
}

enum class StorageHealth {
    HEALTHY,
    NEAR_LIMIT,
    LIMIT_REACHED,
    DEVICE_STORAGE_CRITICAL
}

data class StorageBreakdown(
    val usedBytes: Long = 0L,
    val reservedBytes: Long = 2L * 1024L * 1024L * 1024L, // 2 GB default
    val availableBytes: Long = 2L * 1024L * 1024L * 1024L,
    val deviceTotalBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val health: StorageHealth = StorageHealth.HEALTHY,
    val fileCount: Int = 0
) {
    val usagePercentage: Float
        get() = if (reservedBytes > 0) {
            (usedBytes.toFloat() / reservedBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedUsed: String
        get() = formatBytes(usedBytes)

    val formattedReserved: String
        get() = formatBytes(reservedBytes)

    val formattedAvailable: String
        get() = formatBytes(availableBytes)

    val formattedDeviceFree: String
        get() = formatBytes(deviceFreeBytes)
}

enum class FileCategory(val displayName: String, val iconName: String) {
    ALL("All Files", "folder"),
    DOCUMENTS("Documents", "description"),
    MEDIA("Media", "image"),
    ARCHIVES("Archives", "inventory_2"),
    SECURE_PAYLOADS("Encrypted", "lock"),
    PINNED("Pinned", "push_pin");

    companion object {
        fun fromMimeType(mimeType: String, isEncrypted: Boolean): FileCategory {
            if (isEncrypted) return SECURE_PAYLOADS
            return when {
                mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/") -> MEDIA
                mimeType.contains("pdf") || mimeType.contains("text") || mimeType.contains("document") || mimeType.contains("json") -> DOCUMENTS
                mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("gzip") || mimeType.contains("compressed") -> ARCHIVES
                else -> DOCUMENTS
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
