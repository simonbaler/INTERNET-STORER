package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.core.database.OfflineOperation
import com.example.core.datastore.PreferencesManager
import com.example.core.offline.LocalEventBus
import com.example.core.offline.OfflineOperationEngine
import com.example.core.offline.OperationTypes
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.storage.LocalStorageManager
import com.example.data.repository.LocalVaultRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Phase02OfflineVaultTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var storageManager: LocalStorageManager
    private lateinit var eventBus: LocalEventBus
    private lateinit var engine: OfflineOperationEngine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var vaultRepository: LocalVaultRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = AndroidKeystoreSecurityManager()
        storageManager = LocalStorageManager(context, securityManager)
        eventBus = LocalEventBus(database.localEventDao())
        engine = OfflineOperationEngine(
            operationDao = database.offlineOperationDao(),
            localFileDao = database.localFileDao(),
            storageRecordDao = database.storageRecordDao(),
            storageManager = storageManager,
            eventBus = eventBus
        )
        preferencesManager = PreferencesManager(context)
        vaultRepository = LocalVaultRepositoryImpl(
            localFileDao = database.localFileDao(),
            storageRecordDao = database.storageRecordDao(),
            storageManager = storageManager,
            preferencesManager = preferencesManager,
            eventBus = eventBus
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "vault").deleteRecursively()
        File(context.filesDir, ".tmp").deleteRecursively()
    }

    @Test
    fun testLocalStorageManagerDirectFileWritingAndVerification() = runBlocking {
        val testContent = "Offline continuity data payload for InternetStorer 3.0"
        val inputStream = ByteArrayInputStream(testContent.toByteArray(Charsets.UTF_8))

        val writtenFile = storageManager.importStream(
            displayName = "protocol_spec.txt",
            inputStream = inputStream,
            mimeType = "text/plain",
            encrypt = false,
            reservedLimitBytes = 1024L * 1024L * 500L
        )

        assertNotNull(writtenFile)
        assertTrue(writtenFile.relativePath.isNotEmpty())
        assertTrue(writtenFile.sizeBytes > 0)
        assertTrue(writtenFile.contentHash.isNotEmpty())

        // Verify SHA-256 validation
        val isValid = storageManager.verifyIntegrity(writtenFile.relativePath, writtenFile.contentHash)
        assertTrue("File hash must verify correctly against on-disk file", isValid)

        // Read stream back
        val readStream = storageManager.openStream(writtenFile.relativePath, isEncrypted = false)
        assertNotNull(readStream)
        val readText = readStream.bufferedReader().use { it.readText() }
        assertEquals(testContent, readText)

        // Verify deletion
        val deleted = storageManager.deleteFile(writtenFile.relativePath)
        assertTrue(deleted)
    }

    @Test
    fun testOfflineOperationEngineStateTransitionsAndRecovery() = runBlocking {
        // Enqueue operation
        val op = engine.enqueueOperation(
            operationType = OperationTypes.CLEANUP_TEMP,
            payloadReference = null,
            priority = 10
        )

        assertNotNull(op)
        assertEquals("PENDING", op.status)

        // Process pending
        val processedCount = engine.processPendingOperations(maxBatch = 1)
        assertEquals(1, processedCount)

        val updatedOp = database.offlineOperationDao().getOperationById(op.id)
        assertNotNull(updatedOp)
        assertEquals("COMPLETED", updatedOp?.status)

        // Test recovery of interrupted/stale operation
        val now = System.currentTimeMillis()
        val staleOp = OfflineOperation(
            id = "stale-op-1",
            operationType = OperationTypes.RECALCULATE_STORAGE,
            status = "PROCESSING",
            createdAt = now - 60000,
            updatedAt = now - 60000,
            lastAttemptAt = now - 60000,
            retryCount = 0
        )
        database.offlineOperationDao().insert(staleOp)

        val recovered = engine.recoverStaleOperations(timeoutMs = 10000L)
        assertEquals(1, recovered)

        val recoveredOp = database.offlineOperationDao().getOperationById("stale-op-1")
        assertEquals("PENDING", recoveredOp?.status)
    }

    @Test
    fun testLocalVaultRepositoryImportAndStorageAccounting() = runBlocking {
        val docText = "Emergency Survival & Mesh Networking Field Guide"
        val stream = ByteArrayInputStream(docText.toByteArray(Charsets.UTF_8))

        val result = vaultRepository.importFile(
            displayName = "mesh_guide.txt",
            inputStream = stream,
            mimeType = "text/plain",
            encrypt = false
        )

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("mesh_guide.txt", file.displayName)
        assertFalse(file.isPinned)

        // Read text content back
        val content = vaultRepository.readTextFile(file.id).getOrNull()
        assertEquals(docText, content)

        // Pin file
        vaultRepository.togglePin(file.id)
        val files = vaultRepository.getAllFiles().first()
        val pinnedFile = files.find { it.id == file.id }
        assertTrue(pinnedFile?.isPinned == true)

        // Storage breakdown check
        val breakdown = vaultRepository.getStorageBreakdown().first()
        assertTrue(breakdown.usedBytes > 0)

        // Delete file and ensure storage accounting is updated
        val deleteResult = vaultRepository.deleteFile(file.id)
        assertTrue(deleteResult.isSuccess)

        val updatedFiles = vaultRepository.getAllFiles().first()
        assertTrue(updatedFiles.isEmpty())
    }
}
