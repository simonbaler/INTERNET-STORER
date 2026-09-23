package com.example.core.transfer

import androidx.room.withTransaction
import com.example.core.database.AppDatabase
import com.example.core.database.FileChunkEntity
import com.example.core.database.FileManifestEntity
import com.example.core.database.LocalFile
import com.example.core.database.TransferChunkEntity
import com.example.core.database.TransferEntity
import com.example.core.storage.ChunkEngine
import com.example.core.storage.FileManifestManager
import com.example.core.storage.LocalStorageManager
import com.example.domain.model.ChunkState
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferRecord
import com.example.domain.model.TransferState
import com.example.domain.model.VaultError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class TransferEngine(
    private val database: AppDatabase,
    private val localStorageManager: LocalStorageManager,
    private val chunkEngine: ChunkEngine,
    private val manifestManager: FileManifestManager
) {

    private val engineMutex = Mutex()

    /**
     * Creates a new outgoing preparation transfer from an existing local file.
     * Computes manifest and registers chunks.
     */
    suspend fun prepareOutgoingTransfer(
        localFile: LocalFile,
        chunkSize: Int = FileManifestManager.DEFAULT_CHUNK_SIZE
    ): TransferRecord = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val file = localStorageManager.resolveVaultFile(localFile.relativePath)
            if (!file.exists()) {
                throw VaultError.MissingSourceFile(file.absolutePath)
            }

            val transferId = UUID.randomUUID().toString()
            val totalChunks = manifestManager.calculateTotalChunks(file.length(), chunkSize)

            val manifest = FileManifest(
                fileId = localFile.id,
                contentHash = localFile.contentHash,
                sizeBytes = file.length(),
                mimeType = localFile.mimeType,
                filename = localFile.displayName,
                version = localFile.version,
                encryptionMetadata = if (localFile.encryptionVersion > 0) "AES_256_GCM" else "NONE",
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )

            val serializedManifest = manifestManager.serialize(manifest)

            val transfer = TransferEntity(
                transferId = transferId,
                fileId = localFile.id,
                filename = localFile.displayName,
                direction = "OUTGOING",
                state = "CREATED",
                totalBytes = file.length(),
                transferredBytes = 0L,
                totalChunks = totalChunks,
                verifiedChunks = 0,
                chunkSize = chunkSize,
                contentHash = localFile.contentHash,
                errorMessage = null,
                retryCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Split file into chunks
            val fileChunks = chunkEngine.createChunksForFile(file, localFile.id, transferId, chunkSize)

            val manifestEntity = FileManifestEntity(
                fileId = localFile.id,
                contentHash = localFile.contentHash,
                sizeBytes = file.length(),
                mimeType = localFile.mimeType,
                filename = localFile.displayName,
                version = localFile.version,
                encryptionMetadata = manifest.encryptionMetadata,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                manifestJson = serializedManifest,
                createdAt = manifest.createdAt,
                modifiedAt = manifest.modifiedAt
            )

            val transferChunkEntities = fileChunks.map { c ->
                TransferChunkEntity(
                    transferId = transferId,
                    fileId = localFile.id,
                    chunkIndex = c.chunkIndex,
                    offset = c.offset,
                    length = c.length,
                    chunkHash = c.chunkHash,
                    status = "VERIFIED",
                    createdAt = c.createdAt,
                    verifiedAt = c.verifiedAt
                )
            }

            val fileChunkEntities = fileChunks.map { c ->
                FileChunkEntity(
                    fileId = localFile.id,
                    chunkIndex = c.chunkIndex,
                    offset = c.offset,
                    length = c.length,
                    chunkHash = c.chunkHash,
                    status = "VERIFIED",
                    verifiedAt = c.verifiedAt ?: System.currentTimeMillis()
                )
            }

            // Room persistence
            database.transferDao().insertOrUpdate(transfer)

            // Transition from CREATED to PREPARING
            database.transferDao().transitionState(transferId, "CREATED", "PREPARING")

            database.fileManifestDao().insertOrUpdate(manifestEntity)
            database.transferChunkDao().insertChunks(transferChunkEntities)
            database.fileChunkDao().insertChunks(fileChunkEntities)

            // Transition from PREPARING to TRANSFERRING
            database.transferDao().transitionState(transferId, "PREPARING", "TRANSFERRING")
            database.transferDao().updateTransferProgress(transferId, file.length(), totalChunks)

            mapToRecord(transfer.copy(state = "TRANSFERRING", verifiedChunks = totalChunks, transferredBytes = file.length()))
        }
    }

    /**
     * Initializes an incoming/reconstruction transfer based on a valid FileManifest.
     */
    suspend fun initializeReconstruction(
        manifest: FileManifest,
        expectedChunkHashes: List<String>? = null
    ): TransferRecord = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            manifestManager.validate(manifest)

            val transferId = UUID.randomUUID().toString()
            val totalChunks = manifest.totalChunks

            val transferEntity = TransferEntity(
                transferId = transferId,
                fileId = manifest.fileId,
                filename = manifest.filename,
                direction = "LOCAL_RECONSTRUCTION",
                state = "CREATED",
                totalBytes = manifest.sizeBytes,
                transferredBytes = 0L,
                totalChunks = totalChunks,
                verifiedChunks = 0,
                chunkSize = manifest.chunkSize,
                contentHash = manifest.contentHash,
                errorMessage = null,
                retryCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Prepare chunk descriptors
            val chunkEntities = (0 until totalChunks).map { index ->
                val offset = index.toLong() * manifest.chunkSize
                val length = minOf(manifest.chunkSize.toLong(), manifest.sizeBytes - offset).toInt().coerceAtLeast(0)
                val expectedHash = expectedChunkHashes?.getOrNull(index) ?: ""

                TransferChunkEntity(
                    transferId = transferId,
                    fileId = manifest.fileId,
                    chunkIndex = index,
                    offset = offset,
                    length = length,
                    chunkHash = expectedHash,
                    status = "PENDING",
                    createdAt = System.currentTimeMillis(),
                    verifiedAt = null
                )
            }

            val manifestEntity = FileManifestEntity(
                fileId = manifest.fileId,
                contentHash = manifest.contentHash,
                sizeBytes = manifest.sizeBytes,
                mimeType = manifest.mimeType,
                filename = manifest.filename,
                version = manifest.version,
                encryptionMetadata = manifest.encryptionMetadata,
                chunkSize = manifest.chunkSize,
                totalChunks = manifest.totalChunks,
                manifestJson = manifestManager.serialize(manifest),
                createdAt = manifest.createdAt,
                modifiedAt = manifest.modifiedAt
            )

            database.transferDao().insertOrUpdate(transferEntity)

            // Transition from CREATED to PREPARING
            database.transferDao().transitionState(transferId, "CREATED", "PREPARING")

            database.fileManifestDao().insertOrUpdate(manifestEntity)
            database.transferChunkDao().insertChunks(chunkEntities)

            // Ensure staging file exists
            val stagingFile = getStagingFile(transferId)
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            stagingFile.createNewFile()

            // Transition from PREPARING to TRANSFERRING
            database.transferDao().transitionState(transferId, "PREPARING", "TRANSFERRING")

            mapToRecord(transferEntity.copy(state = "TRANSFERRING"))
        }
    }

    /**
     * Writes and verifies an individual chunk into the reconstruction staging file.
     * Prevents duplicate chunk processing by concurrent workers.
     */
    suspend fun writeAndVerifyChunk(
        transferId: String,
        chunkIndex: Int,
        chunkBytes: ByteArray,
        expectedHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        val transferDao = database.transferDao()
        val chunkDao = database.transferChunkDao()

        val transfer = transferDao.getTransferByIdOnce(transferId)
            ?: throw VaultError.FileNotFound(transferId, "Transfer record not found")

        val currentState = TransferState.valueOf(transfer.state)
        if (currentState != TransferState.TRANSFERRING) {
            throw VaultError.InterruptedTransfer(transferId, "Cannot write chunk to a ${transfer.state} transfer")
        }

        val chunk = chunkDao.getChunk(transferId, chunkIndex)
            ?: throw VaultError.InvalidChunk(chunkIndex, "Chunk record not found for transfer $transferId")

        // If chunk is already verified, skip duplicate write
        if (chunk.status == "VERIFIED") {
            return@withContext true
        }

        // Atomically claim chunk for processing
        val claimed = chunkDao.claimChunkForProcessing(transferId, chunkIndex)
        if (claimed == 0) {
            // Already claimed by another concurrent worker or already verified
            val currentChunk = chunkDao.getChunk(transferId, chunkIndex)
            return@withContext currentChunk?.status == "VERIFIED"
        }

        val stagingFile = getStagingFile(transferId)
        val hashToVerify = if (chunk.chunkHash.isNotBlank()) chunk.chunkHash else expectedHash

        try {
            chunkEngine.writeChunkToTarget(
                targetFile = stagingFile,
                offset = chunk.offset,
                chunkBytes = chunkBytes,
                expectedHash = hashToVerify,
                chunkIndex = chunkIndex
            )

            // Mark chunk as verified
            val now = System.currentTimeMillis()
            chunkDao.updateChunkStatus(transferId, chunkIndex, "VERIFIED", now)

            // Update transfer progress using exact sum of verified chunk lengths
            val verifiedCount = chunkDao.getVerifiedChunkCount(transferId)
            val transferredBytes = chunkDao.getSumVerifiedChunkLengths(transferId)
            transferDao.updateTransferProgress(transferId, transferredBytes, verifiedCount)

            true
        } catch (e: Exception) {
            chunkDao.updateChunkStatus(transferId, chunkIndex, "FAILED")
            transferDao.markFailed(transferId, e.message ?: "Failed writing chunk $chunkIndex")
            throw e
        }
    }

    /**
     * Atomically finalizes an incoming reconstruction once all chunks are verified.
     * Full streaming SHA-256 check performed before atomic move and metadata commit.
     */
    suspend fun finalizeReconstruction(transferId: String): LocalFile = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val transferDao = database.transferDao()
            val chunkDao = database.transferChunkDao()
            val manifestDao = database.fileManifestDao()
            val fileDao = database.localFileDao()

            val transfer = transferDao.getTransferByIdOnce(transferId)
                ?: throw VaultError.FileNotFound(transferId, "Transfer $transferId not found")

            // Atomic state transition: TRANSFERRING -> VERIFYING
            val transitioned = transferDao.transitionState(transferId, "TRANSFERRING", "VERIFYING")
            if (transitioned == 0) {
                if (transfer.state == "COMPLETED") {
                    val existing = fileDao.getFileByIdOnce(transfer.fileId)
                    if (existing != null) return@withLock existing
                }
                throw VaultError.InvalidTransferState(
                    transfer.state,
                    "VERIFYING",
                    "Cannot finalize transfer $transferId from state ${transfer.state}"
                )
            }

            val manifestEntity = manifestDao.getManifestOnce(transfer.fileId)
                ?: throw VaultError.InvalidManifest("No manifest found for file ${transfer.fileId}")

            val manifest = manifestManager.deserialize(manifestEntity.manifestJson)

            // Verify all chunks are marked VERIFIED
            val verifiedCount = chunkDao.getVerifiedChunkCount(transferId)
            if (verifiedCount != transfer.totalChunks) {
                transferDao.transitionState(transferId, "VERIFYING", "FAILED")
                transferDao.markFailed(transferId, "Reconstruction incomplete: $verifiedCount / ${transfer.totalChunks} chunks verified")
                throw VaultError.InvalidChunk(
                    verifiedCount,
                    "Reconstruction incomplete: $verifiedCount / ${transfer.totalChunks} chunks verified"
                )
            }

            val stagingFile = getStagingFile(transferId)
            if (!stagingFile.exists()) {
                transferDao.transitionState(transferId, "VERIFYING", "FAILED")
                transferDao.markFailed(transferId, "Staging file missing for reconstruction")
                throw VaultError.MissingSourceFile(stagingFile.absolutePath)
            }

            // 1. Verify size
            if (stagingFile.length() != manifest.sizeBytes) {
                transferDao.transitionState(transferId, "VERIFYING", "FAILED")
                transferDao.markFailed(transferId, "Final size mismatch: expected ${manifest.sizeBytes}, got ${stagingFile.length()}")
                throw VaultError.IntegrityMismatch(
                    expectedHash = "size:${manifest.sizeBytes}",
                    actualHash = "size:${stagingFile.length()}",
                    message = "File size mismatch on finalized reconstruction"
                )
            }

            // 2. Verify whole-file SHA-256 via streaming 8 KB read
            val finalHash = chunkEngine.calculateStreamingFileHash(stagingFile)
            if (!finalHash.equals(manifest.contentHash, ignoreCase = true)) {
                transferDao.transitionState(transferId, "VERIFYING", "FAILED")
                transferDao.markFailed(transferId, "Final SHA-256 integrity mismatch: expected ${manifest.contentHash}, got $finalHash")
                throw VaultError.IntegrityMismatch(
                    expectedHash = manifest.contentHash,
                    actualHash = finalHash
                )
            }

            // 3. Determine target vault directory based on MIME / encryption
            val isEncrypted = manifest.encryptionMetadata != "NONE"
            val categorySubdir = when {
                isEncrypted -> "secure_payloads"
                manifest.mimeType.startsWith("image/") || manifest.mimeType.startsWith("video/") || manifest.mimeType.startsWith("audio/") -> "media"
                manifest.mimeType.contains("zip") || manifest.mimeType.contains("tar") || manifest.mimeType.contains("compressed") -> "archives"
                else -> "documents"
            }

            val sanitizedName = localStorageManager.sanitizeFileName(manifest.filename)
            val finalRelativePath = "$categorySubdir/${manifest.fileId}_$sanitizedName"

            // 4. Atomically move staged file into final vault location
            val targetFile = localStorageManager.atomicMove(stagingFile, finalRelativePath)

            // 5. Commit Room metadata in transaction
            val now = System.currentTimeMillis()
            val localFile = LocalFile(
                id = manifest.fileId,
                displayName = sanitizedName,
                originalName = manifest.filename,
                relativePath = finalRelativePath,
                mimeType = manifest.mimeType,
                sizeBytes = targetFile.length(),
                createdAt = manifest.createdAt,
                updatedAt = now,
                contentHash = finalHash,
                encryptionVersion = if (isEncrypted) 1 else 0,
                status = "STORED",
                isPinned = false,
                lastAccessedAt = now,
                referenceCount = 1,
                version = manifest.version
            )

            fileDao.insert(localFile)
            database.fileVersionDao().insertVersion(
                com.example.core.database.FileVersionEntity(
                    versionId = UUID.randomUUID().toString(),
                    fileId = manifest.fileId,
                    versionNumber = manifest.version,
                    contentHash = finalHash,
                    sizeBytes = targetFile.length(),
                    modifiedAt = now,
                    changeDescription = "Reconstructed from verified chunks"
                )
            )

            // Atomic state transition: VERIFYING -> COMPLETED
            transferDao.transitionState(transferId, "VERIFYING", "COMPLETED")
            transferDao.markCompleted(transferId, now)

            localFile
        }
    }

    /**
     * Pauses an active transfer.
     */
    suspend fun pauseTransfer(transferId: String): Boolean = withContext(Dispatchers.IO) {
        val transferDao = database.transferDao()
        val updated = transferDao.transitionState(transferId, "TRANSFERRING", TransferState.PAUSED.name)
        updated > 0
    }

    /**
     * Resumes a paused or interrupted transfer.
     */
    suspend fun resumeTransfer(transferId: String): Boolean = withContext(Dispatchers.IO) {
        val transferDao = database.transferDao()
        val updated = transferDao.transitionStateFromAllowed(
            transferId,
            listOf("PAUSED", "INTERRUPTED", "FAILED"),
            TransferState.TRANSFERRING.name
        )
        updated > 0
    }

    /**
     * Cancels an active or paused transfer and deletes its temporary staging artifacts.
     */
    suspend fun cancelTransfer(transferId: String): Boolean = withContext(Dispatchers.IO) {
        val transferDao = database.transferDao()
        val updated = transferDao.transitionStateFromAllowed(
            transferId,
            listOf("CREATED", "PREPARING", "TRANSFERRING", "PAUSED", "INTERRUPTED", "FAILED"),
            TransferState.CANCELLED.name
        )
        if (updated > 0) {
            val stagingFile = getStagingFile(transferId)
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            true
        } else {
            false
        }
    }

    /**
     * Recovers from process death or sudden app termination.
     * Any transfer left in PREPARING or TRANSFERRING is moved to INTERRUPTED.
     */
    suspend fun recoverInterruptedTransfers(): Int = withContext(Dispatchers.IO) {
        val transferDao = database.transferDao()
        val inFlightTransfers = transferDao.getResumableTransfers()
        var recovered = 0
        for (t in inFlightTransfers) {
            if (t.state == "TRANSFERRING" || t.state == "PREPARING") {
                val updated = transferDao.transitionStateFromAllowed(
                    t.transferId,
                    listOf("PREPARING", "TRANSFERRING"),
                    TransferState.INTERRUPTED.name
                )
                if (updated > 0) recovered++
            }
        }

        // Clean up abandoned staging files older than 24 hours
        try {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            localStorageManager.tmpDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("reconstruct_") && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}

        recovered
    }

    private fun getStagingFile(transferId: String): File {
        return File(localStorageManager.tmpDir, "reconstruct_$transferId.tmp")
    }

    private fun mapToRecord(entity: TransferEntity): TransferRecord {
        return TransferRecord(
            transferId = entity.transferId,
            fileId = entity.fileId,
            filename = entity.filename,
            direction = TransferDirection.valueOf(entity.direction),
            state = TransferState.valueOf(entity.state),
            totalBytes = entity.totalBytes,
            transferredBytes = entity.transferredBytes,
            totalChunks = entity.totalChunks,
            verifiedChunks = entity.verifiedChunks,
            chunkSize = entity.chunkSize,
            contentHash = entity.contentHash,
            errorMessage = entity.errorMessage,
            retryCount = entity.retryCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            completedAt = entity.completedAt
        )
    }
}
