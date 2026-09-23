package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfile::class,
        AppSettings::class,
        LocalActivity::class,
        OfflineState::class,
        OfflineOperation::class,
        LocalEvent::class,
        LocalFile::class,
        StorageRecord::class,
        FileManifestEntity::class,
        FileChunkEntity::class,
        TransferEntity::class,
        TransferChunkEntity::class,
        FileVersionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun localActivityDao(): LocalActivityDao
    abstract fun offlineStateDao(): OfflineStateDao
    abstract fun localFileDao(): LocalFileDao
    abstract fun offlineOperationDao(): OfflineOperationDao
    abstract fun localEventDao(): LocalEventDao
    abstract fun storageRecordDao(): StorageRecordDao
    abstract fun fileManifestDao(): FileManifestDao
    abstract fun fileChunkDao(): FileChunkDao
    abstract fun transferDao(): TransferDao
    abstract fun transferChunkDao(): TransferChunkDao
    abstract fun fileVersionDao(): FileVersionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `offline_operations` (
                        `id` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `payloadReference` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        `nextAttemptAt` INTEGER,
                        `priority` INTEGER NOT NULL,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_operations_status` ON `offline_operations` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_operations_priority` ON `offline_operations` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_operations_nextAttemptAt` ON `offline_operations` (`nextAttemptAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_events` (
                        `id` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `payloadReference` TEXT,
                        `processed` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_events_eventType` ON `local_events` (`eventType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_events_processed` ON `local_events` (`processed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_events_createdAt` ON `local_events` (`createdAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_files` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `encryptionVersion` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `lastAccessedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_files_status` ON `local_files` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_files_isPinned` ON `local_files` (`isPinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_files_createdAt` ON `local_files` (`createdAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `storage_records` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `bytesUsed` INTEGER NOT NULL,
                        `bytesReserved` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Extend local_files with referenceCount, version, originalName
                db.execSQL("ALTER TABLE `local_files` ADD COLUMN `referenceCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `local_files` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `local_files` ADD COLUMN `originalName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_files_contentHash` ON `local_files` (`contentHash`)")

                // 2. File manifests
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `file_manifests` (
                        `fileId` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `encryptionMetadata` TEXT NOT NULL,
                        `chunkSize` INTEGER NOT NULL,
                        `totalChunks` INTEGER NOT NULL,
                        `manifestJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `modifiedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_manifests_contentHash` ON `file_manifests` (`contentHash`)")

                // 3. File chunks
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `file_chunks` (
                        `fileId` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `offset` INTEGER NOT NULL,
                        `length` INTEGER NOT NULL,
                        `chunkHash` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `verifiedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`, `chunkIndex`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_chunks_fileId` ON `file_chunks` (`fileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_chunks_chunkHash` ON `file_chunks` (`chunkHash`)")

                // 4. Transfers
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transfers` (
                        `transferId` TEXT NOT NULL,
                        `fileId` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `transferredBytes` INTEGER NOT NULL,
                        `totalChunks` INTEGER NOT NULL,
                        `verifiedChunks` INTEGER NOT NULL,
                        `chunkSize` INTEGER NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`transferId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_fileId` ON `transfers` (`fileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_state` ON `transfers` (`state`)")

                // 5. Transfer chunks
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transfer_chunks` (
                        `transferId` TEXT NOT NULL,
                        `fileId` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `offset` INTEGER NOT NULL,
                        `length` INTEGER NOT NULL,
                        `chunkHash` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `verifiedAt` INTEGER,
                        PRIMARY KEY(`transferId`, `chunkIndex`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_chunks_transferId` ON `transfer_chunks` (`transferId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_chunks_status` ON `transfer_chunks` (`status`)")

                // 6. File versions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `file_versions` (
                        `versionId` TEXT NOT NULL,
                        `fileId` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `parentVersionId` TEXT,
                        `contentHash` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `modifiedAt` INTEGER NOT NULL,
                        `changeDescription` TEXT NOT NULL,
                        PRIMARY KEY(`versionId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_fileId` ON `file_versions` (`fileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_versionNumber` ON `file_versions` (`fileId`, `versionNumber`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "internet_storer.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
