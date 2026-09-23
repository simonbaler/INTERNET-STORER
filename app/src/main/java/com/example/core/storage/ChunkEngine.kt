package com.example.core.storage

import com.example.domain.model.ChunkState
import com.example.domain.model.FileChunk
import com.example.domain.model.VaultError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

class ChunkEngine(
    val defaultChunkSize: Int = 64 * 1024 // 64 KB default production-safe
) {

    /**
     * Splits a file into deterministic chunks and computes per-chunk SHA-256 hashes
     * using purely streaming I/O.
     */
    suspend fun createChunksForFile(
        file: File,
        fileId: String,
        transferId: String,
        chunkSize: Int = defaultChunkSize
    ): List<FileChunk> = withContext(Dispatchers.IO) {
        require(chunkSize > 0) { "chunkSize must be greater than 0: $chunkSize" }
        if (!file.exists()) {
            throw VaultError.MissingSourceFile(file.absolutePath)
        }

        val totalLength = file.length()
        val chunks = mutableListOf<FileChunk>()

        if (totalLength == 0L) {
            val emptyDigest = MessageDigest.getInstance("SHA-256").digest(ByteArray(0)).joinToString("") { "%02x".format(it) }
            chunks.add(
                FileChunk(
                    transferId = transferId,
                    fileId = fileId,
                    chunkIndex = 0,
                    offset = 0L,
                    length = 0,
                    chunkHash = emptyDigest,
                    status = ChunkState.VERIFIED,
                    createdAt = System.currentTimeMillis(),
                    verifiedAt = System.currentTimeMillis()
                )
            )
            return@withContext chunks
        }

        var offset = 0L
        var chunkIndex = 0
        val buffer = ByteArray(chunkSize)

        FileInputStream(file).use { fis ->
            while (offset < totalLength) {
                val bytesToRead = minOf(chunkSize.toLong(), totalLength - offset).toInt()
                var readTotal = 0
                while (readTotal < bytesToRead) {
                    val r = fis.read(buffer, readTotal, bytesToRead - readTotal)
                    if (r == -1) break
                    readTotal += r
                }

                if (readTotal != bytesToRead) {
                    throw VaultError.FileCorrupted(fileId, "Premature end of file while reading chunk #$chunkIndex at offset $offset")
                }

                val md = MessageDigest.getInstance("SHA-256")
                md.update(buffer, 0, readTotal)
                val chunkHash = md.digest().joinToString("") { "%02x".format(it) }

                chunks.add(
                    FileChunk(
                        transferId = transferId,
                        fileId = fileId,
                        chunkIndex = chunkIndex,
                        offset = offset,
                        length = readTotal,
                        chunkHash = chunkHash,
                        status = ChunkState.VERIFIED,
                        createdAt = System.currentTimeMillis(),
                        verifiedAt = System.currentTimeMillis()
                    )
                )

                offset += readTotal
                chunkIndex++
            }
        }

        chunks
    }

    /**
     * Reads a specific chunk from a file into a byte array.
     */
    suspend fun readChunk(file: File, offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw VaultError.MissingSourceFile(file.absolutePath)
        }
        if (offset < 0L) {
            throw VaultError.InvalidChunk(0, "Offset cannot be negative: $offset")
        }
        if (length < 0) {
            throw VaultError.InvalidChunk(0, "Length cannot be negative: $length")
        }
        if (offset + length > file.length()) {
            throw VaultError.InvalidChunk(0, "Offset $offset + length $length exceeds total file length ${file.length()}")
        }
        if (length == 0) {
            return@withContext ByteArray(0)
        }

        val buffer = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(buffer)
        }
        buffer
    }

    /**
     * Writes a chunk into a target staging file at the given offset.
     * Verifies the chunk's SHA-256 before writing.
     */
    suspend fun writeChunkToTarget(
        targetFile: File,
        offset: Long,
        chunkBytes: ByteArray,
        expectedHash: String,
        chunkIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (offset < 0L) {
            throw VaultError.InvalidChunk(chunkIndex, "Offset cannot be negative: $offset")
        }
        if (chunkIndex < 0) {
            throw VaultError.InvalidChunk(chunkIndex, "Chunk index cannot be negative: $chunkIndex")
        }
        if (expectedHash.isBlank() || expectedHash.length != 64 || !expectedHash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            throw VaultError.InvalidChunk(chunkIndex, "Invalid expected SHA-256 hash: $expectedHash")
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(chunkBytes)
        val actualHash = md.digest().joinToString("") { "%02x".format(it) }

        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            throw VaultError.InvalidChunk(chunkIndex, "Hash mismatch: expected $expectedHash, got $actualHash")
        }

        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.seek(offset)
            raf.write(chunkBytes)
            raf.channel.force(true)
        }
        true
    }

    /**
     * Computes SHA-256 of any File via streaming 8 KB buffers.
     */
    suspend fun calculateStreamingFileHash(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw VaultError.MissingSourceFile(file.absolutePath)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
