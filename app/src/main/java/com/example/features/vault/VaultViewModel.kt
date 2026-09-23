package com.example.features.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.LocalFile
import com.example.core.offline.OperationTypes
import com.example.domain.model.FileCategory
import com.example.domain.model.StorageBreakdown
import com.example.domain.repository.LocalVaultRepository
import com.example.domain.repository.OfflineOperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

data class VaultUiState(
    val files: List<LocalFile> = emptyList(),
    val filteredFiles: List<LocalFile> = emptyList(),
    val storageBreakdown: StorageBreakdown = StorageBreakdown(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedFileForDetail: LocalFile? = null,
    val previewContent: String? = null,
    val userMessage: String? = null,
    val showCreateNoteDialog: Boolean = false,
    val showDeleteConfirmDialog: LocalFile? = null
)

class VaultViewModel(
    private val vaultRepository: LocalVaultRepository,
    private val operationRepository: OfflineOperationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        observeFilesAndStorage()
    }

    private fun observeFilesAndStorage() {
        viewModelScope.launch {
            combine(
                vaultRepository.getAllFiles(),
                vaultRepository.getStorageBreakdown()
            ) { files, storage ->
                Pair(files, storage)
            }.collect { (files, storage) ->
                _uiState.update { state ->
                    val filtered = applyFilter(files, state.selectedCategory, state.searchQuery)
                    state.copy(
                        files = files,
                        filteredFiles = filtered,
                        storageBreakdown = storage
                    )
                }
            }
        }
    }

    fun selectCategory(category: FileCategory) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredFiles = applyFilter(state.files, category, state.searchQuery)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredFiles = applyFilter(state.files, state.selectedCategory, query)
            )
        }
    }

    private fun applyFilter(files: List<LocalFile>, category: FileCategory, query: String): List<LocalFile> {
        return files.filter { file ->
            val matchesCategory = when (category) {
                FileCategory.ALL -> true
                FileCategory.PINNED -> file.isPinned
                FileCategory.SECURE_PAYLOADS -> file.encryptionVersion > 0
                FileCategory.DOCUMENTS -> {
                    val cat = FileCategory.fromMimeType(file.mimeType, file.encryptionVersion > 0)
                    cat == FileCategory.DOCUMENTS
                }
                FileCategory.MEDIA -> {
                    val cat = FileCategory.fromMimeType(file.mimeType, file.encryptionVersion > 0)
                    cat == FileCategory.MEDIA
                }
                FileCategory.ARCHIVES -> {
                    val cat = FileCategory.fromMimeType(file.mimeType, file.encryptionVersion > 0)
                    cat == FileCategory.ARCHIVES
                }
            }
            val matchesQuery = query.isBlank() || file.displayName.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    fun importFileFromUri(uri: Uri, context: Context, encrypt: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var fileName = "imported_file_${System.currentTimeMillis()}"
                var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { it.copy(isLoading = false, userMessage = "Could not open selected file") }
                    return@launch
                }

                val result = vaultRepository.importFile(
                    displayName = fileName,
                    inputStream = inputStream,
                    mimeType = mimeType,
                    encrypt = encrypt
                )

                result.fold(
                    onSuccess = { file ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                userMessage = "Safely stored '${file.displayName}' in local vault."
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                userMessage = "Import failed: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error importing file: ${e.message}") }
            }
        }
    }

    fun createLocalDocument(title: String, content: String, encrypt: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateNoteDialog = false) }
            try {
                val cleanTitle = if (title.endsWith(".txt") || title.endsWith(".md")) title else "$title.txt"
                val stream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                val result = vaultRepository.importFile(
                    displayName = cleanTitle,
                    inputStream = stream,
                    mimeType = "text/plain",
                    encrypt = encrypt
                )
                result.fold(
                    onSuccess = { file ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                userMessage = "Created '${file.displayName}' in local vault."
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                userMessage = "Creation failed: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Error creating document: ${e.message}") }
            }
        }
    }

    fun togglePin(fileId: String) {
        viewModelScope.launch {
            vaultRepository.togglePin(fileId)
        }
    }

    fun requestDeleteConfirmation(file: LocalFile) {
        _uiState.update { it.copy(showDeleteConfirmDialog = file) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmDialog = null) }
    }

    fun confirmDelete() {
        val fileToDelete = _uiState.value.showDeleteConfirmDialog ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirmDialog = null, isLoading = true) }
            vaultRepository.deleteFile(fileToDelete.id).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedFileForDetail = if (it.selectedFileForDetail?.id == fileToDelete.id) null else it.selectedFileForDetail,
                            userMessage = "Removed '${fileToDelete.displayName}' from vault."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, userMessage = "Deletion error: ${error.message}")
                    }
                }
            )
        }
    }

    fun verifyIntegrity(fileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Enqueue offline operation for audit and execute
            operationRepository.enqueueOperation(
                type = OperationTypes.VERIFY_INTEGRITY,
                payloadReference = fileId,
                priority = 10
            )
            val result = vaultRepository.verifyFileIntegrity(fileId)
            result.fold(
                onSuccess = { valid ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = if (valid) "Integrity verified: SHA-256 hash matches disk content."
                            else "WARNING: File hash mismatch! File marked corrupted."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, userMessage = "Integrity check failed: ${error.message}")
                    }
                }
            )
        }
    }

    fun selectFileForDetail(file: LocalFile) {
        viewModelScope.launch {
            var preview: String? = null
            if (file.mimeType.startsWith("text/") || file.mimeType.contains("json") || file.displayName.endsWith(".txt") || file.displayName.endsWith(".md")) {
                preview = vaultRepository.readTextFile(file.id).getOrNull()
            }
            _uiState.update {
                it.copy(
                    selectedFileForDetail = file,
                    previewContent = preview
                )
            }
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selectedFileForDetail = null, previewContent = null) }
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateNoteDialog = show) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        fun provideFactory(
            vaultRepository: LocalVaultRepository,
            operationRepository: OfflineOperationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(vaultRepository, operationRepository) as T
            }
        }
    }
}
