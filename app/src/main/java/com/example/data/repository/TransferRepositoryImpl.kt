package com.example.data.repository

import com.example.core.database.AppDatabase
import com.example.core.database.LocalFile
import com.example.core.database.TransferChunkEntity
import com.example.core.database.TransferEntity
import com.example.core.storage.FileManifestManager
import com.example.core.storage.StorageIntegrityEngine
import com.example.core.transfer.TransferEngine
import com.example.domain.model.ChunkState
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.StorageIntegrityReport
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferRecord
import com.example.domain.model.TransferState
import com.example.domain.model.VaultError
import com.example.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransferRepositoryImpl(
    private val database: AppDatabase,
    private val transferEngine: TransferEngine,
    private val integrityEngine: StorageIntegrityEngine,
    private val manifestManager: FileManifestManager
) : TransferRepository {

    override fun getAllTransfers(): Flow<List<TransferRecord>> {
        return database.transferDao().getAllTransfers().map { list ->
            list.map { mapToRecord(it) }
        }
    }

    override fun getActiveTransfers(): Flow<List<TransferRecord>> {
        return database.transferDao().getActiveTransfers().map { list ->
            list.map { mapToRecord(it) }
        }
    }

    override fun getTransfer(transferId: String): Flow<TransferRecord?> {
        return database.transferDao().getTransferById(transferId).map { entity ->
            entity?.let { mapToRecord(it) }
        }
    }

    override suspend fun getTransferOnce(transferId: String): TransferRecord? {
        val entity = database.transferDao().getTransferByIdOnce(transferId) ?: return null
        return mapToRecord(entity)
    }

    override suspend fun prepareOutgoingTransfer(fileId: String, chunkSize: Int): TransferRecord {
        val localFile = database.localFileDao().getFileByIdOnce(fileId)
            ?: throw VaultError.FileNotFound(fileId)
        return transferEngine.prepareOutgoingTransfer(localFile, chunkSize)
    }

    override suspend fun initializeReconstruction(
        manifest: FileManifest,
        expectedChunkHashes: List<String>?
    ): TransferRecord {
        return transferEngine.initializeReconstruction(manifest, expectedChunkHashes)
    }

    override suspend fun writeChunk(
        transferId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        expectedHash: String
    ): Boolean {
        return transferEngine.writeAndVerifyChunk(transferId, chunkIndex, chunkData, expectedHash)
    }

    override suspend fun finalizeReconstruction(transferId: String): LocalFile {
        return transferEngine.finalizeReconstruction(transferId)
    }

    override suspend fun pauseTransfer(transferId: String): Boolean {
        return transferEngine.pauseTransfer(transferId)
    }

    override suspend fun resumeTransfer(transferId: String): Boolean {
        return transferEngine.resumeTransfer(transferId)
    }

    override suspend fun cancelTransfer(transferId: String): Boolean {
        return transferEngine.cancelTransfer(transferId)
    }

    override suspend fun retryTransfer(transferId: String): Boolean {
        return transferEngine.resumeTransfer(transferId)
    }

    override fun getChunksForTransfer(transferId: String): Flow<List<FileChunk>> {
        return database.transferChunkDao().getChunksForTransfer(transferId).map { list ->
            list.map { mapChunkToDomain(it) }
        }
    }

    override suspend fun getManifest(fileId: String): FileManifest? {
        val entity = database.fileManifestDao().getManifestOnce(fileId) ?: return null
        return manifestManager.deserialize(entity.manifestJson)
    }

    override suspend fun runIntegrityCheck(): StorageIntegrityReport {
        return integrityEngine.runIntegrityCheck()
    }

    override suspend fun recoverOrphanFile(relativePath: String): LocalFile {
        return integrityEngine.recoverOrphanFile(relativePath)
    }

    override suspend fun deduplicateFile(existingFileId: String, newName: String): LocalFile {
        val existing = database.localFileDao().getFileByIdOnce(existingFileId)
            ?: throw VaultError.FileNotFound(existingFileId)
        return integrityEngine.linkDeduplicatedFile(existing, newName)
    }

    override suspend fun safeDeleteFile(fileId: String): Boolean {
        return integrityEngine.safeDeleteFile(fileId)
    }

    private fun mapToRecord(entity: TransferEntity): TransferRecord {
        return TransferRecord(
            transferId = entity.transferId,
            fileId = entity.fileId,
            filename = entity.filename,
            direction = try { TransferDirection.valueOf(entity.direction) } catch (_: Exception) { TransferDirection.LOCAL_RECONSTRUCTION },
            state = try { TransferState.valueOf(entity.state) } catch (_: Exception) { TransferState.FAILED },
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

    private fun mapChunkToDomain(entity: TransferChunkEntity): FileChunk {
        return FileChunk(
            transferId = entity.transferId,
            fileId = entity.fileId,
            chunkIndex = entity.chunkIndex,
            offset = entity.offset,
            length = entity.length,
            chunkHash = entity.chunkHash,
            status = try { ChunkState.valueOf(entity.status) } catch (_: Exception) { ChunkState.PENDING },
            createdAt = entity.createdAt,
            verifiedAt = entity.verifiedAt
        )
    }
}
