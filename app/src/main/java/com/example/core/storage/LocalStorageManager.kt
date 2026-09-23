package com.example.core.storage

import android.content.Context
import android.os.StatFs
import com.example.core.security.SecurityManager
import com.example.domain.model.FileCategory
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.StorageHealth
import com.example.domain.model.VaultError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

class LocalStorageManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    private val writeMutex = Mutex()

    val vaultRoot: File
        get() = File(context.filesDir, "internet_storer_vault").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    val tmpDir: File
        get() = File(vaultRoot, ".tmp").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    init {
        // Ensure subdirectories exist
        ensureDirectoryStructure()
    }

    private fun ensureDirectoryStructure() {
        val categories = listOf("documents", "media", "archives", "secure_payloads", ".tmp")
        for (cat in categories) {
            val dir = File(vaultRoot, cat)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    /**
     * Sanitizes file name and prevents path traversal attacks.
     */
    fun sanitizeFileName(fileName: String): String {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) {
            throw VaultError.InvalidFileName(fileName, "File name cannot be empty")
        }
        if (trimmed.contains('\u0000') || trimmed.contains('\r') || trimmed.contains('\n') ||
            trimmed.contains("..") || trimmed.contains('/') || trimmed.contains('\\')) {
            throw VaultError.PathTraversalAttempt(fileName, "Path traversal sequence detected in file name")
        }
        // Keep alphanumeric, dots, underscores, dashes, and spaces
        val clean = trimmed.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        return clean.ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    /**
     * Resolves a relative path safely inside vaultRoot.
     * Prevents any escape outside vaultRoot.
     */
    fun resolveVaultFile(relativePath: String): File {
        if (relativePath.contains('\u0000') || relativePath.contains('\r') || relativePath.contains('\n')) {
            throw VaultError.PathTraversalAttempt(relativePath, "Forbidden control characters detected in relative path")
        }
        val target = File(vaultRoot, relativePath)
        val canonicalRoot = vaultRoot.canonicalFile
        val canonicalTarget = target.canonicalFile

        val rootPath = canonicalRoot.path.removeSuffix(File.separator) + File.separator
        val targetPath = canonicalTarget.path
        if (!targetPath.startsWith(rootPath) && targetPath != canonicalRoot.path) {
            throw VaultError.PathTraversalAttempt(relativePath, "Target path resolves outside vault root")
        }
        return canonicalTarget
    }

    /**
     * Imports a file from an InputStream into the vault using atomic staging.
     * Calculates SHA-256 streaming hash and enforces storage limits.
     */
    suspend fun importStream(
        displayName: String,
        inputStream: InputStream,
        mimeType: String,
        encrypt: Boolean,
        reservedLimitBytes: Long
    ): StorageImportResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val cleanName = sanitizeFileName(displayName)
            val category = FileCategory.fromMimeType(mimeType, encrypt)
            val categorySubdir = when (category) {
                FileCategory.DOCUMENTS -> "documents"
                FileCategory.MEDIA -> "media"
                FileCategory.ARCHIVES -> "archives"
                FileCategory.SECURE_PAYLOADS -> "secure_payloads"
                else -> "documents"
            }

            val currentUsage = calculateVaultUsageBytes()
            val deviceFreeBytes = try {
                val statFs = StatFs(context.filesDir.path)
                if (statFs.blockCountLong > 0) statFs.availableBytes else Long.MAX_VALUE
            } catch (_: Exception) {
                Long.MAX_VALUE
            }

            if (deviceFreeBytes < 50L * 1024L * 1024L) {
                throw VaultError.StorageUnavailable("Device free storage is critically low (${deviceFreeBytes / 1024 / 1024} MB free).")
            }

            // Staging file in .tmp
            val stageId = UUID.randomUUID().toString()
            val stageFile = File(tmpDir, "stage_$stageId.tmp")

            var totalBytesWritten = 0L

            try {
                stageFile.outputStream().use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    if (encrypt) {
                        // For encrypted local payload: encrypt stream
                        val key = securityManager.getOrCreateSecretKey()
                        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                        val iv = cipher.iv

                        // Write IV prefix to file (12 bytes)
                        fos.write(iv)
                        totalBytesWritten += iv.size

                        val cos = javax.crypto.CipherOutputStream(fos, cipher)
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            cos.write(buffer, 0, bytesRead)
                            totalBytesWritten += bytesRead

                            if (currentUsage + totalBytesWritten > reservedLimitBytes) {
                                throw VaultError.StorageLimitReached(reservedLimitBytes)
                            }
                        }
                        cos.flush()
                        cos.close()
                    } else {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalBytesWritten += bytesRead

                            if (currentUsage + totalBytesWritten > reservedLimitBytes) {
                                throw VaultError.StorageLimitReached(reservedLimitBytes)
                            }
                        }
                        fos.flush()
                    }
                }

                // Streaming SHA-256 calculation directly on staged on-disk file
                val digest = MessageDigest.getInstance("SHA-256")
                stageFile.inputStream().use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                val contentHash = digest.digest().joinToString("") { "%02x".format(it) }

                // Target path inside category directory
                val uniqueTargetName = "${System.currentTimeMillis()}_$cleanName"
                val relativePath = "$categorySubdir/$uniqueTargetName"
                val targetFile = resolveVaultFile(relativePath)
                targetFile.parentFile?.mkdirs()

                // Atomic rename/move into final location
                val success = stageFile.renameTo(targetFile)
                if (!success && stageFile.exists()) {
                    // Fallback copy if rename across mount fails
                    stageFile.copyTo(targetFile, overwrite = true)
                    stageFile.delete()
                }

                val finalSize = targetFile.length()

                StorageImportResult(
                    displayName = cleanName,
                    relativePath = relativePath,
                    sizeBytes = finalSize,
                    contentHash = contentHash,
                    encryptionVersion = if (encrypt) 1 else 0
                )
            } catch (e: Exception) {
                if (stageFile.exists()) {
                    stageFile.delete()
                }
                throw e
            }
        }
    }

    /**
     * Reads a file from the vault as an InputStream.
     * If encrypted, automatically wraps in decrypting CipherInputStream.
     */
    suspend fun openStream(
        relativePath: String,
        isEncrypted: Boolean
    ): InputStream = withContext(Dispatchers.IO) {
        val file = resolveVaultFile(relativePath)
        if (!file.exists()) {
            throw VaultError.FileNotFound(relativePath)
        }

        val fis = FileInputStream(file)
        if (!isEncrypted) {
            return@withContext fis
        }

        try {
            // Read 12-byte IV prefix
            val iv = ByteArray(12)
            val read = fis.read(iv)
            if (read < 12) {
                fis.close()
                throw VaultError.DecryptionFailure("Encrypted file payload too short for IV")
            }

            val key = securityManager.getOrCreateSecretKey()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)

            return@withContext javax.crypto.CipherInputStream(fis, cipher)
        } catch (e: Exception) {
            fis.close()
            throw VaultError.DecryptionFailure(e.message ?: "Failed to initialize decryption stream")
        }
    }

    /**
     * Verifies that the file exists and its SHA-256 hash matches the expected hash.
     */
    suspend fun verifyIntegrity(relativePath: String, expectedHash: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveVaultFile(relativePath)
        if (!file.exists()) return@withContext false

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
        calculatedHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Deletes a file safely from disk.
     */
    suspend fun deleteFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveVaultFile(relativePath)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    /**
     * Cleans up stale temporary files in the .tmp folder.
     */
    suspend fun cleanStaleTempFiles(olderThanMs: Long = 3600_000L): Int = withContext(Dispatchers.IO) {
        var count = 0
        val threshold = System.currentTimeMillis() - olderThanMs
        tmpDir.listFiles()?.forEach { f ->
            if (f.lastModified() < threshold || f.name.endsWith(".tmp")) {
                if (f.delete()) count++
            }
        }
        count
    }

    /**
     * Calculates actual disk space used by vault files.
     */
    suspend fun calculateVaultUsageBytes(): Long = withContext(Dispatchers.IO) {
        calculateDirSize(vaultRoot)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    /**
     * Obtains real device and vault storage breakdown.
     */
    suspend fun getStorageBreakdown(
        reservedLimitBytes: Long,
        storedFileCount: Int
    ): StorageBreakdown = withContext(Dispatchers.IO) {
        val usedBytes = calculateVaultUsageBytes()
        val (deviceTotalBytes, deviceFreeBytes) = try {
            val statFs = StatFs(context.filesDir.path)
            if (statFs.blockCountLong > 0) {
                Pair(statFs.totalBytes, statFs.availableBytes)
            } else {
                Pair(64L * 1024L * 1024L * 1024L, 32L * 1024L * 1024L * 1024L)
            }
        } catch (_: Exception) {
            Pair(64L * 1024L * 1024L * 1024L, 32L * 1024L * 1024L * 1024L)
        }

        val availableInLimit = (reservedLimitBytes - usedBytes).coerceAtLeast(0L)
        val availableBytes = minOf(availableInLimit, deviceFreeBytes)

        val health = when {
            deviceFreeBytes < 100L * 1024L * 1024L -> StorageHealth.DEVICE_STORAGE_CRITICAL
            usedBytes >= reservedLimitBytes -> StorageHealth.LIMIT_REACHED
            usedBytes >= (reservedLimitBytes * 0.8f).toLong() -> StorageHealth.NEAR_LIMIT
            else -> StorageHealth.HEALTHY
        }

        StorageBreakdown(
            usedBytes = usedBytes,
            reservedBytes = reservedLimitBytes,
            availableBytes = availableBytes,
            deviceTotalBytes = deviceTotalBytes,
            deviceFreeBytes = deviceFreeBytes,
            health = health,
            fileCount = storedFileCount
        )
    }

    fun createTempFile(prefix: String, suffix: String = ".tmp"): File {
        return File.createTempFile(prefix, suffix, tmpDir)
    }

    suspend fun atomicMove(source: File, targetRelativePath: String): File = withContext(Dispatchers.IO) {
        val targetFile = resolveVaultFile(targetRelativePath)
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val success = source.renameTo(targetFile)
        if (!success && source.exists()) {
            // Fallback to copy and delete if cross-filesystem move occurs
            source.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            source.delete()
        }
        targetFile
    }

    suspend fun listAllVaultFiles(): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.name == ".tmp") return@forEach
                if (file.isDirectory) {
                    scan(file)
                } else if (file.isFile) {
                    results.add(file)
                }
            }
        }
        scan(vaultRoot)
        results
    }

    fun getRelativePath(file: File): String {
        val rootPath = vaultRoot.canonicalPath.removeSuffix(File.separator) + File.separator
        val filePath = file.canonicalPath
        return if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length)
        } else {
            file.name
        }
    }
}

data class StorageImportResult(
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val contentHash: String,
    val encryptionVersion: Int
)
