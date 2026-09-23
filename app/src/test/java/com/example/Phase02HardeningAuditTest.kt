package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppDatabase
import com.example.core.database.OfflineOperation
import com.example.core.database.UserProfile
import com.example.core.offline.EventTypes
import com.example.core.offline.LocalEventBus
import com.example.core.offline.OfflineOperationEngine
import com.example.core.offline.OperationTypes
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.security.EncryptedPayload
import com.example.core.storage.LocalStorageManager
import com.example.domain.model.VaultError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase02HardeningAuditTest {

    private lateinit var context: Context
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var storageManager: LocalStorageManager
    private lateinit var database: AppDatabase
    private lateinit var eventBus: LocalEventBus
    private lateinit var engine: OfflineOperationEngine

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        securityManager = AndroidKeystoreSecurityManager()
        securityManager.initialize()
        storageManager = LocalStorageManager(context, securityManager)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventBus = LocalEventBus(database.localEventDao())
        engine = OfflineOperationEngine(
            database.offlineOperationDao(),
            database.localFileDao(),
            database.storageRecordDao(),
            storageManager,
            eventBus
        )
    }

    @After
    fun tearDown() {
        database.close()
        val vaultDir = File(context.filesDir, "internet_storer_vault")
        if (vaultDir.exists()) {
            vaultDir.deleteRecursively()
        }
    }

    @Test
    fun testEncryptionIntegrityAndCorruptionResistance() {
        val testData = "Top Secret Field Operations & Emergency Mesh Coordinates".toByteArray(Charsets.UTF_8)

        // 1. Valid encryption and decryption
        val encryptResult = securityManager.encrypt(testData)
        assertTrue(encryptResult.isSuccess)
        val payload = encryptResult.getOrThrow()
        assertEquals(12, payload.iv.size)
        assertTrue(payload.ciphertext.isNotEmpty())

        val decryptResult = securityManager.decrypt(payload)
        assertTrue(decryptResult.isSuccess)
        assertTrue(testData.contentEquals(decryptResult.getOrThrow()))

        // 2. Tampered / modified ciphertext -> GCM authentication tag verification MUST fail safely
        val tamperedCiphertext = payload.ciphertext.clone()
        tamperedCiphertext[tamperedCiphertext.size - 1] = (tamperedCiphertext[tamperedCiphertext.size - 1].toInt() xor 0xFF).toByte()
        val tamperedPayload = EncryptedPayload(ciphertext = tamperedCiphertext, iv = payload.iv)
        val tamperedDecryptResult = securityManager.decrypt(tamperedPayload)
        assertTrue(tamperedDecryptResult.isFailure)

        // 3. Tampered / modified IV -> Decryption MUST fail safely
        val tamperedIv = payload.iv.clone()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0xFF).toByte()
        val tamperedIvPayload = EncryptedPayload(ciphertext = payload.ciphertext, iv = tamperedIv)
        val tamperedIvResult = securityManager.decrypt(tamperedIvPayload)
        assertTrue(tamperedIvResult.isFailure)

        // 4. Truncated ciphertext -> Decryption MUST fail safely
        val truncatedCiphertext = payload.ciphertext.copyOfRange(0, (payload.ciphertext.size / 2).coerceAtLeast(1))
        val truncatedPayload = EncryptedPayload(ciphertext = truncatedCiphertext, iv = payload.iv)
        val truncatedResult = securityManager.decrypt(truncatedPayload)
        assertTrue(truncatedResult.isFailure)

        // 5. String decryption of truncated payload (< 12 bytes IV)
        val shortBase64 = android.util.Base64.encodeToString(ByteArray(8), android.util.Base64.NO_WRAP)
        val shortResult = securityManager.decryptString(shortBase64)
        assertTrue(shortResult.isFailure)
    }

    @Test
    fun testPathTraversalSecurityHardening() {
        // Test parent directory traversal attempt
        var caught = false
        try {
            storageManager.sanitizeFileName("../../../etc/passwd")
        } catch (_: VaultError.PathTraversalAttempt) {
            caught = true
        }
        assertTrue("Directory traversal with ../ must be rejected", caught)

        // Test Windows style traversal attempt
        caught = false
        try {
            storageManager.sanitizeFileName("..\\..\\windows\\system32")
        } catch (_: VaultError.PathTraversalAttempt) {
            caught = true
        }
        assertTrue("Directory traversal with ..\\ must be rejected", caught)

        // Test null byte injection attempt
        caught = false
        try {
            storageManager.sanitizeFileName("safe_name\u0000.txt")
        } catch (_: VaultError.PathTraversalAttempt) {
            caught = true
        }
        assertTrue("Null byte injection must be rejected", caught)

        // Test newline injection attempt
        caught = false
        try {
            storageManager.sanitizeFileName("safe_name\n.txt")
        } catch (_: VaultError.PathTraversalAttempt) {
            caught = true
        }
        assertTrue("Newline injection must be rejected", caught)

        // Test resolving file escaping vault root
        caught = false
        try {
            storageManager.resolveVaultFile("../../../outside_vault.txt")
        } catch (_: VaultError.PathTraversalAttempt) {
            caught = true
        }
        assertTrue("Path escaping vault root must be rejected", caught)
    }

    @Test
    fun testStorageQuotaEnforcement() = runBlocking {
        val smallLimit = 5000L // 5 KB limit

        // 1. Write 2 KB file -> succeeds
        val data2KB = ByteArray(2048) { 0x42 }
        val result1 = storageManager.importStream(
            displayName = "file_2kb.bin",
            inputStream = ByteArrayInputStream(data2KB),
            mimeType = "application/octet-stream",
            encrypt = false,
            reservedLimitBytes = smallLimit
        )
        assertNotNull(result1)
        assertEquals(2048L, result1.sizeBytes)

        // 2. Try to write 4 KB file -> total would be 6 KB > 5 KB limit -> throws StorageLimitReached
        val data4KB = ByteArray(4096) { 0x55 }
        var quotaExceeded = false
        try {
            storageManager.importStream(
                displayName = "file_4kb.bin",
                inputStream = ByteArrayInputStream(data4KB),
                mimeType = "application/octet-stream",
                encrypt = false,
                reservedLimitBytes = smallLimit
            )
        } catch (_: VaultError.StorageLimitReached) {
            quotaExceeded = true
        }
        assertTrue("Writing beyond reserved storage quota must be rejected", quotaExceeded)
    }

    @Test
    fun testConcurrentWritesSafety() = runBlocking {
        val limit = 500_000L

        // Run 5 simultaneous writes in parallel
        val jobs = (1..5).map { index ->
            async {
                val data = ByteArray(1024) { index.toByte() }
                storageManager.importStream(
                    displayName = "concurrent_file_$index.bin",
                    inputStream = ByteArrayInputStream(data),
                    mimeType = "application/octet-stream",
                    encrypt = (index % 2 == 0),
                    reservedLimitBytes = limit
                )
            }
        }

        val results = jobs.awaitAll()
        assertEquals(5, results.size)

        // Verify each written file exists and has correct integrity
        for (res in results) {
            val valid = storageManager.verifyIntegrity(res.relativePath, res.contentHash)
            assertTrue("Concurrent file ${res.relativePath} integrity must be valid", valid)
        }
    }

    @Test
    fun testOperationStateMachineStrictness() = runBlocking {
        // Enqueue an operation
        val op = engine.enqueueOperation(
            operationType = OperationTypes.CLEANUP_TEMP,
            payloadReference = null,
            priority = 5
        )
        assertEquals("PENDING", op.status)

        // Atomic claim
        val rowsAffected = database.offlineOperationDao().claimOperation(op.id)
        assertEquals(1, rowsAffected)

        // Secondary claim while PROCESSING must return 0 (no duplicate processing)
        val duplicateClaim = database.offlineOperationDao().claimOperation(op.id)
        assertEquals(0, duplicateClaim)

        // Finish operation
        engine.executeOperation(op.id)
        val completedOp = database.offlineOperationDao().getOperationById(op.id)
        assertEquals("COMPLETED", completedOp?.status)

        // State Machine Rule: COMPLETED operation CANNOT be retried
        val retryCompleted = engine.retryOperation(op.id)
        assertFalse("COMPLETED operation cannot be retried", retryCompleted)

        // State Machine Rule: COMPLETED operation CANNOT be cancelled
        val cancelCompleted = engine.cancelOperation(op.id)
        assertFalse("COMPLETED operation cannot be cancelled", cancelCompleted)

        // Create a failed operation
        val failedOp = OfflineOperation(
            id = "failed-op-1",
            operationType = OperationTypes.CLEANUP_TEMP,
            status = "FAILED",
            retryCount = 5,
            errorCode = "NetworkUnavailable",
            errorMessage = "No route to host"
        )
        database.offlineOperationDao().insert(failedOp)

        // FAILED operation CAN be retried -> transitions to PENDING and executes
        val retryFailed = engine.retryOperation("failed-op-1")
        assertTrue("FAILED operation can be retried", retryFailed)
        val retriedOp = database.offlineOperationDao().getOperationById("failed-op-1")
        assertEquals("COMPLETED", retriedOp?.status)
    }

    @Test
    fun testRoomMigration1To2SchemaSurvival() {
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Phase 01 v1 tables
                    db.execSQL("""
                        CREATE TABLE `user_profile` (
                            `id` INTEGER NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `avatarId` TEXT NOT NULL,
                            `preferredLanguage` TEXT NOT NULL,
                            `storagePreference` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `app_settings` (
                            `id` INTEGER NOT NULL,
                            `themeMode` TEXT NOT NULL,
                            `animationIntensity` TEXT NOT NULL,
                            `forcedOfflineMode` INTEGER NOT NULL,
                            `lastBackupTimestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `local_activity` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `offline_state` (
                            `id` INTEGER NOT NULL,
                            `readinessScore` INTEGER NOT NULL,
                            `lastCalculated` INTEGER NOT NULL,
                            `networkState` TEXT NOT NULL,
                            `storageUsedBytes` INTEGER NOT NULL,
                            `isKeystoreReady` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // Insert pre-existing Phase 01 test records
                    db.execSQL("INSERT INTO user_profile (id, displayName, avatarId, preferredLanguage, storagePreference, createdAt) VALUES (1, 'Auditor Profile', 'heart_rose', 'English', 'Balanced (2 GB)', 1700000000000)")
                    db.execSQL("INSERT INTO local_activity (id, title, description, category, timestamp) VALUES (1, 'V1 Activity', 'Recorded before migration', 'SYSTEM', 1700000000000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(helperConfig)
        val db = helper.writableDatabase

        // Execute MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(db)

        // 1. Verify Phase 01 data is 100% intact
        val profileCursor = db.query("SELECT displayName FROM user_profile WHERE id = 1")
        assertTrue(profileCursor.moveToFirst())
        assertEquals("Auditor Profile", profileCursor.getString(0))
        profileCursor.close()

        val activityCursor = db.query("SELECT title FROM local_activity WHERE id = 1")
        assertTrue(activityCursor.moveToFirst())
        assertEquals("V1 Activity", activityCursor.getString(0))
        activityCursor.close()

        // 2. Verify all 4 new Phase 02 tables exist and can accept queries/inserts
        val tables = listOf("offline_operations", "local_events", "local_files", "storage_records")
        for (table in tables) {
            val cursor = db.query("SELECT COUNT(*) FROM `$table`")
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }

        db.close()
        context.deleteDatabase("migration_test.db")
    }
}
