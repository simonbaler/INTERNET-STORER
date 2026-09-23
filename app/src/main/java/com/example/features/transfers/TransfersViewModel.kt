package com.example.features.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.storage.FileManifestManager
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.StorageIntegrityReport
import com.example.domain.model.TransferRecord
import com.example.domain.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class TransfersViewModel(
    private val transferRepository: TransferRepository
) : ViewModel() {

    val allTransfers: StateFlow<List<TransferRecord>> = transferRepository.getAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTransfers: StateFlow<List<TransferRecord>> = transferRepository.getActiveTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _integrityReport = MutableStateFlow<StorageIntegrityReport?>(null)
    val integrityReport: StateFlow<StorageIntegrityReport?> = _integrityReport.asStateFlow()

    private val _isCheckingIntegrity = MutableStateFlow(false)
    val isCheckingIntegrity: StateFlow<Boolean> = _isCheckingIntegrity.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun pauseTransfer(transferId: String) {
        viewModelScope.launch {
            try {
                transferRepository.pauseTransfer(transferId)
            } catch (e: Exception) {
                _userMessage.value = e.message
            }
        }
    }

    fun resumeTransfer(transferId: String) {
        viewModelScope.launch {
            try {
                transferRepository.resumeTransfer(transferId)
            } catch (e: Exception) {
                _userMessage.value = e.message
            }
        }
    }

    fun cancelTransfer(transferId: String) {
        viewModelScope.launch {
            try {
                transferRepository.cancelTransfer(transferId)
            } catch (e: Exception) {
                _userMessage.value = e.message
            }
        }
    }

    fun retryTransfer(transferId: String) {
        viewModelScope.launch {
            try {
                transferRepository.retryTransfer(transferId)
            } catch (e: Exception) {
                _userMessage.value = e.message
            }
        }
    }

    fun runIntegrityCheck() {
        viewModelScope.launch {
            _isCheckingIntegrity.value = true
            try {
                val report = transferRepository.runIntegrityCheck()
                _integrityReport.value = report
            } catch (e: Exception) {
                _userMessage.value = "Integrity check failed: ${e.message}"
            } finally {
                _isCheckingIntegrity.value = false
            }
        }
    }

    fun recoverOrphan(path: String) {
        viewModelScope.launch {
            try {
                val recovered = transferRepository.recoverOrphanFile(path)
                _userMessage.value = "Recovered '${recovered.displayName}' into vault."
                runIntegrityCheck()
            } catch (e: Exception) {
                _userMessage.value = "Recovery failed: ${e.message}"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Creates and runs a local reconstruction transfer completely offline.
     * Generates a manifest, chunks it, and writes chunks sequentially to demonstrate
     * real resumable transfer and integrity verification without any mock data.
     */
    fun startLocalReconstructionSample(name: String, sampleContent: String) {
        viewModelScope.launch {
            try {
                val contentBytes = sampleContent.toByteArray(Charsets.UTF_8)
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(contentBytes).joinToString("") { "%02x".format(it) }

                val fileId = UUID.randomUUID().toString()
                val chunkSize = 64 // Small chunk for demonstration of multi-chunk progress
                val totalChunks = ((contentBytes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)

                val manifest = FileManifest(
                    fileId = fileId,
                    contentHash = hash,
                    sizeBytes = contentBytes.size.toLong(),
                    mimeType = "text/plain",
                    filename = name,
                    version = 1,
                    encryptionMetadata = "NONE",
                    chunkSize = chunkSize,
                    totalChunks = totalChunks,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )

                // Compute per-chunk hashes
                val chunkHashes = (0 until totalChunks).map { i ->
                    val offset = i * chunkSize
                    val len = minOf(chunkSize, contentBytes.size - offset)
                    val chunkMd = MessageDigest.getInstance("SHA-256")
                    chunkMd.update(contentBytes, offset, len)
                    chunkMd.digest().joinToString("") { "%02x".format(it) }
                }

                val transfer = transferRepository.initializeReconstruction(manifest, chunkHashes)

                // Write chunks
                for (i in 0 until totalChunks) {
                    val offset = i * chunkSize
                    val len = minOf(chunkSize, contentBytes.size - offset)
                    val chunkSlice = contentBytes.copyOfRange(offset, offset + len)
                    transferRepository.writeChunk(transfer.transferId, i, chunkSlice, chunkHashes[i])
                }

                // Finalize atomic reconstruction
                val localFile = transferRepository.finalizeReconstruction(transfer.transferId)
                _userMessage.value = "Successfully reconstructed '${localFile.displayName}' (${localFile.sizeBytes} bytes)"
                runIntegrityCheck()
            } catch (e: Exception) {
                _userMessage.value = "Reconstruction error: ${e.message}"
            }
        }
    }
}
