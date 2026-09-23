package com.example.domain.repository

import com.example.core.database.OfflineOperation
import kotlinx.coroutines.flow.Flow

interface OfflineOperationRepository {
    fun getAllOperations(): Flow<List<OfflineOperation>>
    suspend fun enqueueOperation(type: String, payloadReference: String? = null, priority: Int = 0): OfflineOperation
    suspend fun executeOperation(opId: String): Boolean
    suspend fun retryOperation(opId: String): Boolean
    suspend fun cancelOperation(opId: String): Boolean
    suspend fun processPending(maxBatch: Int = 5): Int
    suspend fun recoverStale(): Int
}
