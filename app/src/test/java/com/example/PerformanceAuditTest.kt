package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.core.database.LocalActivity
import com.example.core.database.LocalFile
import com.example.core.datastore.PreferencesManager
import com.example.core.offline.LocalEventBus
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.storage.LocalStorageManager
import com.example.data.repository.LocalVaultRepositoryImpl
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.StorageIntegrityReport
import com.example.domain.model.TransferRecord
import com.example.domain.repository.TransferRepository
import com.example.features.transfers.TransfersViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceAuditTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var localStorageManager: LocalStorageManager
    private lateinit var eventBus: LocalEventBus

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = AndroidKeystoreSecurityManager()
        securityManager.initialize()
        localStorageManager = LocalStorageManager(context, securityManager)
        eventBus = LocalEventBus(database.localEventDao())
    }

    @After
    fun tearDown() {
        database.close()
        localStorageManager.vaultRoot.deleteRecursively()
        localStorageManager.tmpDir.deleteRecursively()
    }

    @Test
    fun testDatabaseSchemaIndexOnActivity() = runBlocking {
        val now = System.currentTimeMillis()
        val activity1 = LocalActivity(
            title = "Test 1",
            description = "Activity 1",
            timestamp = now - 1000
        )
        val activity2 = LocalActivity(
            title = "Test 2",
            description = "Activity 2",
            timestamp = now
        )

        database.localActivityDao().insert(activity1)
        database.localActivityDao().insert(activity2)

        val recent = database.localActivityDao().getRecentActivities(5).first()
        assertEquals(2, recent.size)
        // Ordered by timestamp DESC
        assertEquals("Test 2", recent[0].title)
        assertEquals("Test 1", recent[1].title)
    }

    @Test
    fun testStorageBreakdownBypassesRecursiveScanWhenKnownBytesProvided() = runBlocking {
        val simulatedBytes = 1048576L * 50L // 50 MB
        val breakdown = localStorageManager.getStorageBreakdown(
            reservedLimitBytes = 1024L * 1024L * 1024L * 2L,
            storedFileCount = 10,
            knownUsedBytes = simulatedBytes
        )

        assertEquals(simulatedBytes, breakdown.usedBytes)
        assertEquals(10, breakdown.fileCount)
        assertTrue(breakdown.deviceTotalBytes > 0)
        assertTrue(breakdown.deviceFreeBytes > 0)
    }

    @Test
    fun testLocalVaultRepositoryStorageBreakdownUsesDatabaseAggregation() = runBlocking {
        val file1 = LocalFile(
            id = "perf_file_1",
            displayName = "File 1.dat",
            relativePath = "files/perf1.dat",
            sizeBytes = 2048L,
            mimeType = "application/octet-stream",
            contentHash = "a".repeat(64),
            createdAt = System.currentTimeMillis()
        )
        val file2 = LocalFile(
            id = "perf_file_2",
            displayName = "File 2.dat",
            relativePath = "files/perf2.dat",
            sizeBytes = 4096L,
            mimeType = "application/octet-stream",
            contentHash = "b".repeat(64),
            createdAt = System.currentTimeMillis()
        )

        database.localFileDao().insert(file1)
        database.localFileDao().insert(file2)

        val prefManager = PreferencesManager(context)
        val repository = LocalVaultRepositoryImpl(
            localFileDao = database.localFileDao(),
            storageRecordDao = database.storageRecordDao(),
            storageManager = localStorageManager,
            preferencesManager = prefManager,
            eventBus = eventBus
        )

        val breakdown = repository.getStorageBreakdown().first()
        assertEquals(6144L, breakdown.usedBytes)
        assertEquals(2, breakdown.fileCount)
    }

    @Test
    fun testTransfersViewModelDoesNotRunHeavyIntegrityScanOnInit() {
        val fakeTransferRepo = FakeTransferRepository()
        val viewModel = TransfersViewModel(fakeTransferRepo)

        // Integrity report should initially be null and isCheckingIntegrity false (never blocking screen start)
        assertNull(viewModel.integrityReport.value)
        assertFalse(viewModel.isCheckingIntegrity.value)
    }

    private class FakeTransferRepository : TransferRepository {
        override fun getAllTransfers(): Flow<List<TransferRecord>> = flowOf(emptyList())
        override fun getActiveTransfers(): Flow<List<TransferRecord>> = flowOf(emptyList())
        override fun getTransfer(transferId: String): Flow<TransferRecord?> = flowOf(null)
        override suspend fun getTransferOnce(transferId: String): TransferRecord? = null

        override suspend fun prepareOutgoingTransfer(fileId: String, chunkSize: Int): TransferRecord {
            throw UnsupportedOperationException()
        }

        override suspend fun initializeReconstruction(manifest: FileManifest, expectedChunkHashes: List<String>?): TransferRecord {
            throw UnsupportedOperationException()
        }

        override suspend fun writeChunk(transferId: String, chunkIndex: Int, chunkData: ByteArray, expectedHash: String): Boolean = true
        override suspend fun finalizeReconstruction(transferId: String): LocalFile {
            throw UnsupportedOperationException()
        }

        override suspend fun pauseTransfer(transferId: String): Boolean = true
        override suspend fun resumeTransfer(transferId: String): Boolean = true
        override suspend fun cancelTransfer(transferId: String): Boolean = true
        override suspend fun retryTransfer(transferId: String): Boolean = true

        override fun getChunksForTransfer(transferId: String): Flow<List<FileChunk>> = flowOf(emptyList())
        override suspend fun getManifest(fileId: String): FileManifest? = null

        override suspend fun runIntegrityCheck(): StorageIntegrityReport {
            throw UnsupportedOperationException()
        }

        override suspend fun recoverOrphanFile(relativePath: String): LocalFile {
            throw UnsupportedOperationException()
        }

        override suspend fun deduplicateFile(existingFileId: String, newName: String): LocalFile {
            throw UnsupportedOperationException()
        }

        override suspend fun safeDeleteFile(fileId: String): Boolean = true
    }
}
