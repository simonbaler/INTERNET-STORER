package com.example.core.offline

import com.example.core.database.LocalEvent
import com.example.core.database.LocalEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

object EventTypes {
    const val PROFILE_CREATED = "PROFILE_CREATED"
    const val PROFILE_UPDATED = "PROFILE_UPDATED"
    const val FILE_IMPORTED = "FILE_IMPORTED"
    const val FILE_DELETED = "FILE_DELETED"
    const val FILE_UPDATED = "FILE_UPDATED"
    const val STORAGE_LIMIT_CHANGED = "STORAGE_LIMIT_CHANGED"
    const val OFFLINE_OPERATION_CREATED = "OFFLINE_OPERATION_CREATED"
    const val OFFLINE_OPERATION_COMPLETED = "OFFLINE_OPERATION_COMPLETED"
    const val OFFLINE_OPERATION_FAILED = "OFFLINE_OPERATION_FAILED"
    const val CONNECTIVITY_CHANGED = "CONNECTIVITY_CHANGED"
    const val SETTING_CHANGED = "SETTING_CHANGED"
    const val TEMP_STORAGE_CLEANED = "TEMP_STORAGE_CLEANED"
    const val FILE_INTEGRITY_VERIFIED = "FILE_INTEGRITY_VERIFIED"
}

class LocalEventBus(
    private val localEventDao: LocalEventDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _events = MutableSharedFlow<LocalEvent>(replay = 20, extraBufferCapacity = 64)
    val events: SharedFlow<LocalEvent> = _events.asSharedFlow()

    fun emit(
        eventType: String,
        payloadReference: String? = null,
        priority: Int = 0
    ) {
        val event = LocalEvent(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            createdAt = System.currentTimeMillis(),
            payloadReference = payloadReference,
            processed = false,
            priority = priority
        )

        scope.launch(Dispatchers.IO) {
            localEventDao.insert(event)
            _events.emit(event)
        }
    }

    suspend fun emitSync(
        eventType: String,
        payloadReference: String? = null,
        priority: Int = 0
    ): LocalEvent {
        val event = LocalEvent(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            createdAt = System.currentTimeMillis(),
            payloadReference = payloadReference,
            processed = false,
            priority = priority
        )
        localEventDao.insert(event)
        _events.emit(event)
        return event
    }

    suspend fun markProcessed(eventId: String) {
        localEventDao.markProcessed(eventId)
    }
}
