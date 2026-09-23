package com.example.features.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.LocalEvent
import com.example.core.database.OfflineOperation
import com.example.core.offline.OperationTypes
import com.example.domain.repository.ActivityRepository
import com.example.domain.repository.OfflineOperationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OperationsUiState(
    val operations: List<OfflineOperation> = emptyList(),
    val isRunning: Boolean = false,
    val userMessage: String? = null
)

class OfflineOperationsViewModel(
    private val operationRepository: OfflineOperationRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            operationRepository.getAllOperations().collect { ops ->
                _uiState.update { it.copy(operations = ops) }
            }
        }
    }

    fun triggerMaintenance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            operationRepository.enqueueOperation(OperationTypes.CLEANUP_TEMP, priority = 5)
            operationRepository.enqueueOperation(OperationTypes.RECALCULATE_STORAGE, priority = 5)
            val processed = operationRepository.processPending(maxBatch = 5)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    userMessage = "Maintenance executed: processed $processed local operations."
                )
            }
        }
    }

    fun retryOperation(opId: String) {
        viewModelScope.launch {
            val success = operationRepository.retryOperation(opId)
            _uiState.update {
                it.copy(userMessage = if (success) "Operation re-executed successfully." else "Operation retry scheduled.")
            }
        }
    }

    fun cancelOperation(opId: String) {
        viewModelScope.launch {
            operationRepository.cancelOperation(opId)
        }
    }

    fun processPending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true) }
            val count = operationRepository.processPending(maxBatch = 5)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    userMessage = "Processed $count pending local operations."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        fun provideFactory(
            operationRepository: OfflineOperationRepository,
            activityRepository: ActivityRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OfflineOperationsViewModel(operationRepository, activityRepository) as T
            }
        }
    }
}
