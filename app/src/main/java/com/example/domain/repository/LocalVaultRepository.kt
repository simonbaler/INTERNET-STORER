package com.example.domain.repository

import com.example.core.database.LocalFile
import com.example.domain.model.StorageBreakdown
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface LocalVaultRepository {
    fun getAllFiles(): Flow<List<LocalFile>>
    fun getPinnedFiles(): Flow<List<LocalFile>>
    fun getFileById(id: String): Flow<LocalFile?>
    suspend fun getFileByIdOnce(id: String): LocalFile?
    suspend fun importFile(
        displayName: String,
        inputStream: InputStream,
        mimeType: String,
        encrypt: Boolean = false
    ): Result<LocalFile>
    suspend fun openFileInputStream(fileId: String): Result<InputStream>
    suspend fun readTextFile(fileId: String): Result<String>
    suspend fun togglePin(fileId: String): Result<Boolean>
    suspend fun deleteFile(fileId: String): Result<Unit>
    suspend fun verifyFileIntegrity(fileId: String): Result<Boolean>
    fun getStorageBreakdown(): Flow<StorageBreakdown>
    suspend fun cleanTempFiles(): Int
}
