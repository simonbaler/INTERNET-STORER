package com.example.domain.repository

import com.example.core.database.LocalFile
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.StorageIntegrityReport
import com.example.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTransfers(): Flow<List<TransferRecord>>
    fun getActiveTransfers(): Flow<List<TransferRecord>>
    fun getTransfer(transferId: String): Flow<TransferRecord?>
    suspend fun getTransferOnce(transferId: String): TransferRecord?

    suspend fun prepareOutgoingTransfer(fileId: String, chunkSize: Int = 64 * 1024): TransferRecord
    suspend fun initializeReconstruction(manifest: FileManifest, expectedChunkHashes: List<String>? = null): TransferRecord
    suspend fun writeChunk(transferId: String, chunkIndex: Int, chunkData: ByteArray, expectedHash: String): Boolean
    suspend fun finalizeReconstruction(transferId: String): LocalFile

    suspend fun pauseTransfer(transferId: String): Boolean
    suspend fun resumeTransfer(transferId: String): Boolean
    suspend fun cancelTransfer(transferId: String): Boolean
    suspend fun retryTransfer(transferId: String): Boolean

    fun getChunksForTransfer(transferId: String): Flow<List<FileChunk>>
    suspend fun getManifest(fileId: String): FileManifest?

    suspend fun runIntegrityCheck(): StorageIntegrityReport
    suspend fun recoverOrphanFile(relativePath: String): LocalFile
    suspend fun deduplicateFile(existingFileId: String, newName: String): LocalFile
    suspend fun safeDeleteFile(fileId: String): Boolean
}
