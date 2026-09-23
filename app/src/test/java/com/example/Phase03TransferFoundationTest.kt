package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.core.database.LocalFile
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.storage.ChunkEngine
import com.example.core.storage.FileManifestManager
import com.example.core.storage.LocalStorageManager
import com.example.core.storage.StorageIntegrityEngine
import com.example.core.transfer.TransferEngine
import com.example.data.repository.TransferRepositoryImpl
import com.example.domain.model.ChunkState
import com.example.domain.model.FileManifest
import com.example.domain.model.IntegrityIssueType
import com.example.domain.model.TransferState
import com.example.domain.model.VaultError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class Phase03TransferFoundationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var storageManager: LocalStorageManager
    private lateinit var chunkEngine: ChunkEngine
    private lateinit var manifestManager: FileManifestManager
    private lateinit var integrityEngine: StorageIntegrityEngine
    private lateinit var transferEngine: TransferEngine
    private lateinit var transferRepository: TransferRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = AndroidKeystoreSecurityManager()
        storageManager = LocalStorageManager(context, securityManager)
        chunkEngine = ChunkEngine(defaultChunkSize = 64) // small chunk size for testing
        manifestManager = FileManifestManager()
        integrityEngine = StorageIntegrityEngine(database, storageManager, chunkEngine)
        transferEngine = TransferEngine(database, storageManager, chunkEngine, manifestManager)
        transferRepository = TransferRepositoryImpl(database, transferEngine, integrityEngine, manifestManager)
    }

    @After
    fun teardown() {
        database.close()
        storageManager.vaultRoot.deleteRecursively()
    }

    @Test
    fun testFileManifest_serializationAndValidation() {
        val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val manifest = FileManifest(
            fileId = "test-file-123",
            contentHash = hash,
            sizeBytes = 120L,
            mimeType = "text/plain",
            filename = "report.txt",
            version = 1,
            encryptionMetadata = "NONE",
            chunkSize = 64,
            totalChunks = 2,
            createdAt = 1000L,
            modifiedAt = 1000L
        )

        val json = manifestManager.serialize(manifest)
        assertTrue(json.contains("\"fileId\":\"test-file-123\""))
        assertTrue(json.contains("\"contentHash\":\"$hash\""))

        val deserialized = manifestManager.deserialize(json)
        assertEquals(manifest.fileId, deserialized.fileId)
        assertEquals(manifest.contentHash, deserialized.contentHash)
        assertEquals(manifest.sizeBytes, deserialized.sizeBytes)
        assertEquals(manifest.totalChunks, deserialized.totalChunks)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun testFileManifest_rejectsInvalidHash() {
        val manifest = FileManifest(
            fileId = "test-file-123",
            contentHash = "short_invalid_hash",
            sizeBytes = 100L,
            mimeType = "text/plain",
            filename = "bad.txt",
            version = 1,
            encryptionMetadata = "NONE",
            chunkSize = 64,
            totalChunks = 2,
            createdAt = 1000L,
            modifiedAt = 1000L
        )
        manifestManager.validate(manifest)
    }

    @Test
    fun testChunkEngine_createsDeterministicChunks() = runBlocking {
        val tempFile = File(storageManager.tmpDir, "test_source.txt")
        val content = "A".repeat(150) // 150 bytes -> with chunk size 64: 3 chunks (64, 64, 22)
        tempFile.writeText(content)

        val chunks = chunkEngine.createChunksForFile(tempFile, "f1", "t1", chunkSize = 64)
        assertEquals(3, chunks.size)
        assertEquals(0L, chunks[0].offset)
        assertEquals(64, chunks[0].length)
        assertEquals(64L, chunks[1].offset)
        assertEquals(64, chunks[1].length)
        assertEquals(128L, chunks[2].offset)
        assertEquals(22, chunks[2].length)

        assertTrue(chunks.all { it.chunkHash.length == 64 })
        assertTrue(chunks.all { it.status == ChunkState.VERIFIED })
    }

    @Test
    fun testTransferEngine_outgoingPreparation() = runBlocking {
        // Place a real file in vault
        val targetFile = storageManager.resolveVaultFile("documents/source_doc.txt")
        targetFile.parentFile?.mkdirs()
        targetFile.writeText("InternetStorer 3.0 secure local data chunk test")

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(targetFile.readBytes()).joinToString("") { "%02x".format(it) }

        val localFile = LocalFile(
            id = "file-out-1",
            displayName = "source_doc.txt",
            originalName = "source_doc.txt",
            relativePath = "documents/source_doc.txt",
            mimeType = "text/plain",
            sizeBytes = targetFile.length(),
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

        val transfer = transferRepository.prepareOutgoingTransfer("file-out-1", chunkSize = 16)
        assertEquals("file-out-1", transfer.fileId)
        assertTrue(transfer.totalChunks > 1)
        assertEquals(transfer.totalChunks, transfer.verifiedChunks)

        // Verify chunks in Room
        val chunks = transferRepository.getChunksForTransfer(transfer.transferId).first()
        assertEquals(transfer.totalChunks, chunks.size)
    }

    @Test
    fun testTransferEngine_atomicReconstruction() = runBlocking {
        val payload = "Offline local chunk reconstruction engine end-to-end test payload!".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val fullHash = md.digest(payload).joinToString("") { "%02x".format(it) }

        val chunkSize = 16
        val totalChunks = ((payload.size + chunkSize - 1) / chunkSize)

        val chunkHashes = (0 until totalChunks).map { i ->
            val offset = i * chunkSize
            val len = minOf(chunkSize, payload.size - offset)
            val chunkMd = MessageDigest.getInstance("SHA-256")
            chunkMd.update(payload, offset, len)
            chunkMd.digest().joinToString("") { "%02x".format(it) }
        }

        val manifest = FileManifest(
            fileId = "recon-file-1",
            contentHash = fullHash,
            sizeBytes = payload.size.toLong(),
            mimeType = "text/plain",
            filename = "reconstructed.txt",
            version = 1,
            encryptionMetadata = "NONE",
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        // Initialize reconstruction
        val transfer = transferRepository.initializeReconstruction(manifest, chunkHashes)
        assertEquals(TransferState.TRANSFERRING, transfer.state)

        // Write each chunk
        for (i in 0 until totalChunks) {
            val offset = i * chunkSize
            val len = minOf(chunkSize, payload.size - offset)
            val chunkSlice = payload.copyOfRange(offset, offset + len)
            val success = transferRepository.writeChunk(transfer.transferId, i, chunkSlice, chunkHashes[i])
            assertTrue(success)
        }

        // Finalize atomic reconstruction
        val resultFile = transferRepository.finalizeReconstruction(transfer.transferId)
        assertEquals("reconstructed.txt", resultFile.displayName)
        assertEquals(fullHash, resultFile.contentHash)

        // Verify physical file exists and matches
        val physicalFile = storageManager.resolveVaultFile(resultFile.relativePath)
        assertTrue(physicalFile.exists())
        assertEquals(payload.size.toLong(), physicalFile.length())
        assertEquals(fullHash, chunkEngine.calculateStreamingFileHash(physicalFile))

        // Verify transfer record marked COMPLETED
        val finalTransfer = transferRepository.getTransferOnce(transfer.transferId)
        assertNotNull(finalTransfer)
        assertEquals(TransferState.COMPLETED, finalTransfer?.state)
    }

    @Test
    fun testStorageIntegrity_detectsCorruptAndMissingFiles() = runBlocking {
        // 1. Missing file in database
        val missingFile = LocalFile(
            id = "missing-1",
            displayName = "ghost.txt",
            originalName = "ghost.txt",
            relativePath = "documents/ghost.txt",
            mimeType = "text/plain",
            sizeBytes = 100L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            contentHash = "00".repeat(32),
            encryptionVersion = 0,
            status = "STORED",
            isPinned = false,
            referenceCount = 1,
            version = 1
        )
        database.localFileDao().insert(missingFile)

        // 2. Orphan file on disk (no DB record)
        val orphan = storageManager.resolveVaultFile("documents/orphan.bin")
        orphan.parentFile?.mkdirs()
        orphan.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val report = transferRepository.runIntegrityCheck()
        assertEquals(1, report.missingFiles)
        assertEquals(1, report.orphanFiles)
        assertFalse(report.isClean)

        // Recover orphan
        val recovered = transferRepository.recoverOrphanFile("documents/orphan.bin")
        assertEquals("orphan.bin", recovered.displayName)

        val reportAfter = transferRepository.runIntegrityCheck()
        assertEquals(0, reportAfter.orphanFiles)
    }

    @Test
    fun testStorageDeduplication_referenceCounting() = runBlocking {
        // Create an initial file
        val fileOnDisk = storageManager.resolveVaultFile("documents/doc1.txt")
        fileOnDisk.parentFile?.mkdirs()
        fileOnDisk.writeText("Shared content deduplication test")
        val hash = chunkEngine.calculateStreamingFileHash(fileOnDisk)

        val primaryRecord = LocalFile(
            id = "doc1",
            displayName = "doc1.txt",
            originalName = "doc1.txt",
            relativePath = "documents/doc1.txt",
            mimeType = "text/plain",
            sizeBytes = fileOnDisk.length(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            contentHash = hash,
            encryptionVersion = 0,
            status = "STORED",
            isPinned = false,
            referenceCount = 1,
            version = 1
        )
        database.localFileDao().insert(primaryRecord)

        // Deduplicate new reference to same content
        val deduplicated = transferRepository.deduplicateFile("doc1", "doc1_copy.txt")
        assertEquals(primaryRecord.relativePath, deduplicated.relativePath)

        // Check reference count on primary
        val updatedPrimary = database.localFileDao().getFileByIdOnce("doc1")
        assertEquals(2, updatedPrimary?.referenceCount)

        // Delete the duplicate: physical file should NOT be deleted!
        transferRepository.safeDeleteFile(deduplicated.id)
        assertTrue(fileOnDisk.exists())

        // Delete primary: now all references gone, physical file should be deleted!
        transferRepository.safeDeleteFile(primaryRecord.id)
        assertFalse(fileOnDisk.exists())
    }

    @Test
    fun testTransferEngine_pauseAndResume() = runBlocking {
        val payload = "Resumable pause and resume transfer machine test".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val fullHash = md.digest(payload).joinToString("") { "%02x".format(it) }

        val manifest = FileManifest(
            fileId = "pause-test-1",
            contentHash = fullHash,
            sizeBytes = payload.size.toLong(),
            mimeType = "text/plain",
            filename = "pause_test.txt",
            version = 1,
            encryptionMetadata = "NONE",
            chunkSize = 16,
            totalChunks = 3,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        val transfer = transferRepository.initializeReconstruction(manifest)
        assertEquals(TransferState.TRANSFERRING, transfer.state)

        // Pause
        val paused = transferRepository.pauseTransfer(transfer.transferId)
        assertTrue(paused)
        var current = transferRepository.getTransferOnce(transfer.transferId)
        assertEquals(TransferState.PAUSED, current?.state)

        // Resume
        val resumed = transferRepository.resumeTransfer(transfer.transferId)
        assertTrue(resumed)
        current = transferRepository.getTransferOnce(transfer.transferId)
        assertEquals(TransferState.TRANSFERRING, current?.state)
    }

    @Test
    fun testTransferEngine_processDeathRecovery() = runBlocking {
        val manifest = FileManifest(
            fileId = "death-test-1",
            contentHash = "00".repeat(32),
            sizeBytes = 64L,
            mimeType = "text/plain",
            filename = "death_test.txt",
            version = 1,
            encryptionMetadata = "NONE",
            chunkSize = 32,
            totalChunks = 2,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        val transfer = transferRepository.initializeReconstruction(manifest)
        assertEquals(TransferState.TRANSFERRING, transfer.state)

        // Simulate app crash / process death recovery on reboot
        val recoveredCount = transferEngine.recoverInterruptedTransfers()
        assertEquals(1, recoveredCount)

        val recovered = transferRepository.getTransferOnce(transfer.transferId)
        assertEquals(TransferState.INTERRUPTED, recovered?.state)

        // Interrupted transfers are resumable
        val resumed = transferRepository.resumeTransfer(transfer.transferId)
        assertTrue(resumed)
        val afterResume = transferRepository.getTransferOnce(transfer.transferId)
        assertEquals(TransferState.TRANSFERRING, afterResume?.state)
    }
}
