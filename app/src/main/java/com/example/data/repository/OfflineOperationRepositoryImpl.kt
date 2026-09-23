package com.example.data.repository

import com.example.core.database.OfflineOperation
import com.example.core.offline.OfflineOperationEngine
import com.example.domain.repository.OfflineOperationRepository
import kotlinx.coroutines.flow.Flow

class OfflineOperationRepositoryImpl(
    private val engine: OfflineOperationEngine
) : OfflineOperationRepository {

    override fun getAllOperations(): Flow<List<OfflineOperation>> = engine.allOperations

    override suspend fun enqueueOperation(
        type: String,
        payloadReference: String?,
        priority: Int
    ): OfflineOperation = engine.enqueueOperation(type, payloadReference, priority)

    override suspend fun executeOperation(opId: String): Boolean = engine.executeOperation(opId)

    override suspend fun retryOperation(opId: String): Boolean = engine.retryOperation(opId)

    override suspend fun cancelOperation(opId: String): Boolean = engine.cancelOperation(opId)

    override suspend fun processPending(maxBatch: Int): Int = engine.processPendingOperations(maxBatch)

    override suspend fun recoverStale(): Int = engine.recoverStaleOperations()
}
