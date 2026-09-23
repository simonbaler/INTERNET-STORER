package com.example.core.storage

import com.example.domain.model.FileManifest
import com.example.domain.model.VaultError
import org.json.JSONException
import org.json.JSONObject

class FileManifestManager {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB
    }

    /**
     * Serializes a FileManifest into a canonical, deterministic JSON string.
     * Keys are ordered consistently to guarantee deterministic output.
     */
    fun serialize(manifest: FileManifest): String {
        validate(manifest)
        val json = JSONObject()
        // Insert in fixed alphabetical order
        json.put("chunkSize", manifest.chunkSize)
        json.put("contentHash", manifest.contentHash)
        json.put("createdAt", manifest.createdAt)
        json.put("encryptionMetadata", manifest.encryptionMetadata)
        json.put("fileId", manifest.fileId)
        json.put("filename", manifest.filename)
        json.put("mimeType", manifest.mimeType)
        json.put("modifiedAt", manifest.modifiedAt)
        json.put("schemaVersion", manifest.schemaVersion)
        json.put("sizeBytes", manifest.sizeBytes)
        json.put("totalChunks", manifest.totalChunks)
        json.put("version", manifest.version)
        return json.toString()
    }

    /**
     * Deserializes and validates a JSON manifest string.
     * Throws VaultError.InvalidManifest if malformed or violates constraints.
     */
    fun deserialize(jsonString: String): FileManifest {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw VaultError.InvalidManifest("Manifest JSON cannot be empty")
        }

        try {
            val json = JSONObject(trimmed)

            if (!json.has("fileId") || !json.has("contentHash") || !json.has("sizeBytes") || !json.has("filename")) {
                throw VaultError.InvalidManifest("Missing required manifest fields (fileId, contentHash, sizeBytes, filename)")
            }

            val schemaVersion = if (json.has("schemaVersion")) json.getInt("schemaVersion") else 1
            if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
                throw VaultError.InvalidManifest("Unsupported schema version $schemaVersion (supported: 1..$CURRENT_SCHEMA_VERSION)")
            }

            val fileId = json.getString("fileId")
            val contentHash = json.getString("contentHash")
            val sizeBytes = json.getLong("sizeBytes")
            val mimeType = json.optString("mimeType", "application/octet-stream")
            val filename = json.getString("filename")
            val version = json.optInt("version", 1)
            val encryptionMetadata = json.optString("encryptionMetadata", "NONE")
            val chunkSize = json.optInt("chunkSize", DEFAULT_CHUNK_SIZE)
            val totalChunks = json.optInt("totalChunks", -1)
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())
            val modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis())

            val manifest = FileManifest(
                fileId = fileId,
                contentHash = contentHash,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                filename = filename,
                version = version,
                encryptionMetadata = encryptionMetadata,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                schemaVersion = schemaVersion
            )

            validate(manifest)
            return manifest
        } catch (e: JSONException) {
            throw VaultError.InvalidManifest("Malformed JSON syntax: ${e.message}")
        }
    }

    /**
     * Validates semantic constraints of a manifest.
     */
    fun validate(manifest: FileManifest) {
        if (manifest.schemaVersion < 1 || manifest.schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw VaultError.InvalidManifest("Unsupported schema version: ${manifest.schemaVersion}")
        }
        if (manifest.fileId.isBlank()) {
            throw VaultError.InvalidManifest("fileId cannot be blank")
        }
        if (manifest.contentHash.isBlank() || manifest.contentHash.length != 64 || !manifest.contentHash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            throw VaultError.InvalidManifest("contentHash must be a valid 64-character SHA-256 hexadecimal string")
        }
        if (manifest.sizeBytes < 0) {
            throw VaultError.InvalidManifest("sizeBytes cannot be negative: ${manifest.sizeBytes}")
        }
        if (manifest.chunkSize <= 0) {
            throw VaultError.InvalidManifest("chunkSize must be greater than zero: ${manifest.chunkSize}")
        }
        val expectedChunks = if (manifest.sizeBytes == 0L) 1 else ((manifest.sizeBytes + manifest.chunkSize - 1) / manifest.chunkSize).toInt()
        if (manifest.totalChunks != expectedChunks) {
            throw VaultError.InvalidManifest("totalChunks mismatch: expected $expectedChunks for size ${manifest.sizeBytes} with chunkSize ${manifest.chunkSize}, got ${manifest.totalChunks}")
        }
        if (manifest.filename.isBlank()) {
            throw VaultError.InvalidManifest("filename cannot be blank")
        }
        if (manifest.filename.contains('\u0000') || manifest.filename.contains('\r') || manifest.filename.contains('\n') || manifest.filename.contains("..")) {
            throw VaultError.InvalidManifest("filename contains forbidden control characters or path traversal sequences")
        }
        if (manifest.encryptionMetadata != "NONE" && manifest.encryptionMetadata != "AES_256_GCM") {
            throw VaultError.InvalidManifest("Unsupported or invalid encryption metadata: ${manifest.encryptionMetadata}")
        }
        if (manifest.version < 1) {
            throw VaultError.InvalidManifest("version must be >= 1")
        }
    }

    /**
     * Calculates the deterministic chunk count for a given file size and chunk size.
     */
    fun calculateTotalChunks(sizeBytes: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): Int {
        require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(chunkSize > 0) { "chunkSize must be positive" }
        return if (sizeBytes == 0L) 1 else ((sizeBytes + chunkSize - 1) / chunkSize).toInt()
    }
}
