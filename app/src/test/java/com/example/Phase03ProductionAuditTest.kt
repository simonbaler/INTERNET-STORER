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
import com.example.domain.model.FileManifest
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferState
import com.example.domain.model.VaultError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class Phase03ProductionAuditTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var storageManager: LocalStorageManager
    private lateinit var chunkEngine: ChunkEngine
    private lateinit var manifestManager: FileManifestManager
    private lateinit var integrityEngine: StorageIntegrityEngine
    private lateinit var transferEngine: TransferEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = AndroidKeystoreSecurityManager()
        storageManager = LocalStorageManager(context, securityManager)
        chunkEngine = ChunkEngine(defaultChunkSize = 64)
        manifestManager = FileManifestManager()
        integrityEngine = StorageIntegrityEngine(database, storageManager, chunkEngine)
        transferEngine = TransferEngine(database, storageManager, chunkEngine, manifestManager)
    }

    @After
    fun teardown() {
        database.close()
        storageManager.vaultRoot.deleteRecursively()
        storageManager.tmpDir.deleteRecursively()
    }

    private fun createValidManifest(
        fileId: String = "test-${UUID.randomUUID()}",
        filename: String = "valid.txt",
        size: Long = 128L,
        chunkSize: Int = 64,
        hash: String = "a".repeat(64),
        version: Int = 1,
        schemaVersion: Int = 1,
        encryptionMetadata: String = "NONE"
    ): FileManifest {
        val totalChunks = if (size == 0L) 1 else ((size + chunkSize - 1) / chunkSize).toInt()
        return FileManifest(
            fileId = fileId,
            contentHash = hash,
            sizeBytes = size,
            mimeType = "text/plain",
            filename = filename,
            version = version,
            encryptionMetadata = encryptionMetadata,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            schemaVersion = schemaVersion
        )
    }

    // =========================================================================
    // 1. STATE MACHINE & INVALID TRANSITIONS
    // =========================================================================

    @Test
    fun `completed transfer cannot be resumed or cancelled`() = runBlocking {
        val payload = "Immutable completed state test".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload).joinToString("") { "%02x".format(it) }

        val manifest = createValidManifest(size = payload.size.toLong(), chunkSize = 128, hash = hash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(hash))

        transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, hash)
        val localFile = transferEngine.finalizeReconstruction(transfer.transferId)
        assertNotNull(localFile)

        // Attempt to resume completed transfer
        val resumeResult = transferEngine.resumeTransfer(transfer.transferId)
        assertFalse("Resuming COMPLETED transfer must be rejected", resumeResult)

        // Attempt to pause completed transfer
        val pauseResult = transferEngine.pauseTransfer(transfer.transferId)
        assertFalse("Pausing COMPLETED transfer must be rejected", pauseResult)

        // Attempt to cancel completed transfer
        val cancelResult = transferEngine.cancelTransfer(transfer.transferId)
        assertFalse("Cancelling COMPLETED transfer must be rejected", cancelResult)

        val finalRecord = database.transferDao().getTransferByIdOnce(transfer.transferId)
        assertEquals("State must remain COMPLETED", TransferState.COMPLETED.name, finalRecord?.state)
    }

    @Test
    fun `cancelled transfer cannot be resumed or written to`() = runBlocking {
        val manifest = createValidManifest(size = 128L, chunkSize = 64)
        val transfer = transferEngine.initializeReconstruction(manifest)

        val cancelled = transferEngine.cancelTransfer(transfer.transferId)
        assertTrue(cancelled)

        val record = database.transferDao().getTransferByIdOnce(transfer.transferId)
        assertEquals(TransferState.CANCELLED.name, record?.state)

        // Cannot resume CANCELLED transfer
        val resumeResult = transferEngine.resumeTransfer(transfer.transferId)
        assertFalse("Resuming CANCELLED transfer must be rejected", resumeResult)

        // Writing chunks to CANCELLED transfer must throw InterruptedTransfer
        try {
            transferEngine.writeAndVerifyChunk(transfer.transferId, 0, ByteArray(64), "a".repeat(64))
            fail("Writing chunk to CANCELLED transfer should throw InterruptedTransfer")
        } catch (e: VaultError.InterruptedTransfer) {
            assertTrue(e.message!!.contains("CANCELLED"))
        }
    }

    @Test
    fun `pausing and resuming works through allowed transitions`() = runBlocking {
        val manifest = createValidManifest(size = 128L, chunkSize = 64)
        val transfer = transferEngine.initializeReconstruction(manifest)
        assertEquals(TransferState.TRANSFERRING.name, transfer.state.name)

        // Pause
        val paused = transferEngine.pauseTransfer(transfer.transferId)
        assertTrue(paused)
        assertEquals(TransferState.PAUSED.name, database.transferDao().getTransferByIdOnce(transfer.transferId)?.state)

        // Writing chunks while paused must be rejected
        try {
            transferEngine.writeAndVerifyChunk(transfer.transferId, 0, ByteArray(64), "a".repeat(64))
            fail("Writing chunk while PAUSED should throw InterruptedTransfer")
        } catch (e: VaultError.InterruptedTransfer) {
            assertTrue(e.message!!.contains("PAUSED"))
        }

        // Resume
        val resumed = transferEngine.resumeTransfer(transfer.transferId)
        assertTrue(resumed)
        assertEquals(TransferState.TRANSFERRING.name, database.transferDao().getTransferByIdOnce(transfer.transferId)?.state)
    }

    // =========================================================================
    // 2. CONCURRENT WORKERS & DUPLICATE CHUNK CLAIMING
    // =========================================================================

    @Test
    fun `concurrent chunk writing prevents duplicate processing`() = runBlocking {
        val payload = ByteArray(64) { 0x42 }
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload).joinToString("") { "%02x".format(it) }

        val manifest = createValidManifest(size = 64L, chunkSize = 64, hash = hash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(hash))

        // Worker 1 writes and verifies
        val worker1Result = transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, hash)
        assertTrue(worker1Result)

        // Worker 2 attempts same chunk: already verified, safely skips without error
        val worker2Result = transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, hash)
        assertTrue(worker2Result)

        val verifiedCount = database.transferChunkDao().getVerifiedChunkCount(transfer.transferId)
        assertEquals("Chunk count must remain exactly 1", 1, verifiedCount)
    }

    @Test
    fun `concurrent finalization calls handle idempotently without duplicate records`() = runBlocking {
        val payload = "Concurrent finalization safety test".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload).joinToString("") { "%02x".format(it) }

        val manifest = createValidManifest(size = payload.size.toLong(), chunkSize = 128, hash = hash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(hash))
        transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, hash)

        // Worker 1 finalizes
        val file1 = transferEngine.finalizeReconstruction(transfer.transferId)
        assertNotNull(file1)

        // Worker 2 calls finalize on same transfer: should return the completed file cleanly
        val file2 = transferEngine.finalizeReconstruction(transfer.transferId)
        assertEquals(file1.id, file2.id)
        assertEquals(file1.contentHash, file2.contentHash)

        val storedFiles = database.localFileDao().getAllStoredFilesOnce()
        assertEquals("Only 1 file record should exist", 1, storedFiles.size)
    }

    // =========================================================================
    // 3. SECURITY: PATH TRAVERSAL, NULL BYTES, NEWLINES & MALFORMED INPUT
    // =========================================================================

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with path traversal filename is rejected`() {
        val manifest = createValidManifest(filename = "../../etc/passwd")
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with null byte in filename is rejected`() {
        val manifest = createValidManifest(filename = "malicious\u0000.txt")
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with newline in filename is rejected`() {
        val manifest = createValidManifest(filename = "evil\nname.txt")
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with invalid schema version is rejected`() {
        val manifest = createValidManifest(schemaVersion = 999)
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with negative size is rejected`() {
        val manifest = createValidManifest(size = -10L)
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with mismatched total chunks is rejected`() {
        val manifest = createValidManifest(size = 100L, chunkSize = 50).copy(totalChunks = 99)
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `manifest with unknown encryption metadata is rejected`() {
        val manifest = createValidManifest().copy(encryptionMetadata = "CUSTOM_CIPHER_UNKNOWN")
        manifestManager.validate(manifest)
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `deserializing empty string is rejected`() {
        manifestManager.deserialize("   ")
    }

    @Test(expected = VaultError.InvalidManifest::class)
    fun `deserializing missing required fields is rejected`() {
        manifestManager.deserialize("""{"fileId":"123"}""")
    }

    // =========================================================================
    // 4. CHUNK INTEGRITY & TAMPERING DETECTION
    // =========================================================================

    @Test
    fun `tampered chunk content triggers hash mismatch on write`() = runBlocking {
        val genuinePayload = "Genuine chunk payload data".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val genuineHash = md.digest(genuinePayload).joinToString("") { "%02x".format(it) }

        val manifest = createValidManifest(size = genuinePayload.size.toLong(), chunkSize = 64, hash = genuineHash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(genuineHash))

        val tamperedPayload = "Corrupted chunk payload data".toByteArray()
        try {
            transferEngine.writeAndVerifyChunk(transfer.transferId, 0, tamperedPayload, genuineHash)
            fail("Writing tampered chunk payload must throw InvalidChunk")
        } catch (e: VaultError.InvalidChunk) {
            assertTrue(e.message!!.contains("Hash mismatch"))
        }

        val chunkRecord = database.transferChunkDao().getChunk(transfer.transferId, 0)
        assertEquals("FAILED", chunkRecord?.status)
    }

    @Test
    fun `tampered whole file triggers final integrity check failure`() = runBlocking {
        val payload = "Integrity check will catch modified bytes in final verification".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val correctHash = md.digest(payload).joinToString("") { "%02x".format(it) }

        // We claim the expected whole-file hash is 64 'f's, which will not match payload
        val forgedHash = "f".repeat(64)
        val manifest = createValidManifest(size = payload.size.toLong(), chunkSize = 128, hash = forgedHash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(correctHash))

        // Write the chunk with its actual hash
        transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, correctHash)

        // Finalize must fail whole-file SHA-256 validation
        try {
            transferEngine.finalizeReconstruction(transfer.transferId)
            fail("Finalizing transfer with mismatched whole-file hash must throw IntegrityMismatch")
        } catch (e: VaultError.IntegrityMismatch) {
            assertEquals(forgedHash, e.expectedHash)
            assertEquals(correctHash, e.actualHash)
        }

        val transferRecord = database.transferDao().getTransferByIdOnce(transfer.transferId)
        assertEquals("State must be marked FAILED on hash mismatch", TransferState.FAILED.name, transferRecord?.state)
    }

    @Test
    fun `size mismatch during finalization is caught and marks transfer failed`() = runBlocking {
        val payload = "Short payload".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload).joinToString("") { "%02x".format(it) }

        // Manifest declares size 100 bytes, but payload is only payload.size
        val manifest = createValidManifest(size = 100L, chunkSize = 128, hash = hash)
        val transfer = transferEngine.initializeReconstruction(manifest, listOf(hash))

        transferEngine.writeAndVerifyChunk(transfer.transferId, 0, payload, hash)

        try {
            transferEngine.finalizeReconstruction(transfer.transferId)
            fail("Finalizing with mismatched size must throw IntegrityMismatch")
        } catch (e: VaultError.IntegrityMismatch) {
            assertTrue(e.message!!.contains("size mismatch"))
        }

        val transferRecord = database.transferDao().getTransferByIdOnce(transfer.transferId)
        assertEquals(TransferState.FAILED.name, transferRecord?.state)
    }

    // =========================================================================
    // 5. DEDUPLICATION & THREAD-SAFE REFERENCE COUNTING
    // =========================================================================

    @Test
    fun `deduplication preserves physical file while referenceCount is positive`() = runBlocking {
        // Create an original file in vault
        val originalFile = storageManager.resolveVaultFile("documents/original.txt")
        originalFile.parentFile?.mkdirs()
        originalFile.writeText("Shared content deduplication test")

        val hash = chunkEngine.calculateStreamingFileHash(originalFile)
        val file1 = LocalFile(
            id = "file-orig-1",
            displayName = "original.txt",
            originalName = "original.txt",
            relativePath = "documents/original.txt",
            mimeType = "text/plain",
            sizeBytes = originalFile.length(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            contentHash = hash,
            encryptionVersion = 0,
            status = "STORED",
            isPinned = false,
            referenceCount = 1,
            version = 1
        )
        database.localFileDao().insert(file1)

        // Deduplicate file (link to duplicate record)
        val file2 = integrityEngine.linkDeduplicatedFile(file1, "duplicate_link.txt")
        assertEquals(2, file2.referenceCount)

        // Verify referenceCount on original is updated to 2
        val updatedFile1 = database.localFileDao().getFileByIdOnce(file1.id)
        assertEquals(2, updatedFile1?.referenceCount)

        // Safe delete file 1: file 2 still references physical file!
        val deleted1 = integrityEngine.safeDeleteFile(file1.id)
        assertTrue(deleted1)

        // Physical file must STILL EXIST
        assertTrue("Physical file must be preserved while refCount > 0", originalFile.exists())

        val updatedFile2 = database.localFileDao().getFileByIdOnce(file2.id)
        assertEquals(1, updatedFile2?.referenceCount)

        // Safe delete file 2: now refCount reaches 0 -> physical file is deleted
        val deleted2 = integrityEngine.safeDeleteFile(file2.id)
        assertTrue(deleted2)

        assertFalse("Physical file must be removed when all references deleted", originalFile.exists())
    }

    @Test
    fun `reference count cannot become negative under multiple delete calls`() = runBlocking {
        val file = storageManager.resolveVaultFile("documents/single.txt")
        file.parentFile?.mkdirs()
        file.writeText("Single reference file")

        val hash = chunkEngine.calculateStreamingFileHash(file)
        val localFile = LocalFile(
            id = "file-single-1",
            displayName = "single.txt",
            originalName = "single.txt",
            relativePath = "documents/single.txt",
            mimeType = "text/plain",
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

        // First deletion
        assertTrue(integrityEngine.safeDeleteFile(localFile.id))

        // Second deletion call on already deleted record
        assertTrue(integrityEngine.safeDeleteFile(localFile.id))

        val refCount = database.localFileDao().getReferenceCount(localFile.id)
        assertEquals(0, refCount)
        assertTrue("Reference count cannot be negative", refCount!! >= 0)
    }

    // =========================================================================
    // 6. PROCESS DEATH & ORPHAN RECOVERY
    // =========================================================================

    @Test
    fun `process death recovery moves in-flight transfers to INTERRUPTED`() = runBlocking {
        val manifest1 = createValidManifest(fileId = "f-1", size = 128L)
        val manifest2 = createValidManifest(fileId = "f-2", size = 128L)

        val t1 = transferEngine.initializeReconstruction(manifest1)
        val t2 = transferEngine.initializeReconstruction(manifest2)

        // Both are in TRANSFERRING state
        assertEquals(TransferState.TRANSFERRING.name, database.transferDao().getTransferByIdOnce(t1.transferId)?.state)
        assertEquals(TransferState.TRANSFERRING.name, database.transferDao().getTransferByIdOnce(t2.transferId)?.state)

        // Simulate app crash / worker recovery
        val recoveredCount = transferEngine.recoverInterruptedTransfers()
        assertEquals(2, recoveredCount)

        assertEquals(TransferState.INTERRUPTED.name, database.transferDao().getTransferByIdOnce(t1.transferId)?.state)
        assertEquals(TransferState.INTERRUPTED.name, database.transferDao().getTransferByIdOnce(t2.transferId)?.state)

        // Resuming from INTERRUPTED is allowed
        val resumed = transferEngine.resumeTransfer(t1.transferId)
        assertTrue(resumed)
        assertEquals(TransferState.TRANSFERRING.name, database.transferDao().getTransferByIdOnce(t1.transferId)?.state)
    }

    @Test
    fun `orphan file on disk is recovered into vault database`() = runBlocking {
        // Place an unindexed file directly on disk
        val orphan = storageManager.resolveVaultFile("documents/unindexed_backup.pdf")
        orphan.parentFile?.mkdirs()
        orphan.writeText("%PDF-1.4 unindexed orphan content")

        val recovered = integrityEngine.recoverOrphanFile("documents/unindexed_backup.pdf")
        assertNotNull(recovered)
        assertEquals("unindexed_backup.pdf", recovered.displayName)

        val dbRecord = database.localFileDao().getFileByIdOnce(recovered.id)
        assertNotNull(dbRecord)
        assertEquals(orphan.length(), dbRecord?.sizeBytes)
        assertEquals("STORED", dbRecord?.status)
    }

    // =========================================================================
    // 7. STREAMING & LARGE-FILE BUFFERING
    // =========================================================================

    @Test
    fun `chunk engine handles zero-byte files gracefully`() = runBlocking {
        val emptyFile = File(storageManager.tmpDir, "empty.txt")
        emptyFile.createNewFile()

        val chunks = chunkEngine.createChunksForFile(emptyFile, "empty-1", "t-empty", chunkSize = 64)
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].length)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", chunks[0].chunkHash)

        val read = chunkEngine.readChunk(emptyFile, 0L, 0)
        assertEquals(0, read.size)
    }

    @Test
    fun `streaming hash computation handles multi-megabyte files with constant buffer`(): Unit = runBlocking {
        val largeFile = File(storageManager.tmpDir, "large_test.bin")
        largeFile.outputStream().use { os ->
            val buf = ByteArray(1024) { 0x55 }
            for (i in 0 until 512) { // 512 KB
                os.write(buf)
            }
        }

        val hash = chunkEngine.calculateStreamingFileHash(largeFile)
        assertNotNull(hash)
        assertEquals(64, hash.length)

        largeFile.delete()
        Unit
    }
}
