package com.example.features.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.FileChunk
import com.example.domain.model.FileManifest
import com.example.domain.model.TransferRecord
import com.example.domain.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferDetailViewModel(
    val transferId: String,
    private val transferRepository: TransferRepository
) : ViewModel() {

    val transfer: StateFlow<TransferRecord?> = transferRepository.getTransfer(transferId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chunks: StateFlow<List<FileChunk>> = transferRepository.getChunksForTransfer(transferId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _manifest = MutableStateFlow<FileManifest?>(null)
    val manifest: StateFlow<FileManifest?> = _manifest.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        loadManifest()
    }

    private fun loadManifest() {
        viewModelScope.launch {
            val rec = transferRepository.getTransferOnce(transferId)
            if (rec != null) {
                _manifest.value = transferRepository.getManifest(rec.fileId)
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            try {
                transferRepository.pauseTransfer(transferId)
            } catch (e: Exception) {
                _actionMessage.value = e.message
            }
        }
    }

    fun resume() {
        viewModelScope.launch {
            try {
                transferRepository.resumeTransfer(transferId)
            } catch (e: Exception) {
                _actionMessage.value = e.message
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            try {
                transferRepository.cancelTransfer(transferId)
            } catch (e: Exception) {
                _actionMessage.value = e.message
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            try {
                transferRepository.retryTransfer(transferId)
            } catch (e: Exception) {
                _actionMessage.value = e.message
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
