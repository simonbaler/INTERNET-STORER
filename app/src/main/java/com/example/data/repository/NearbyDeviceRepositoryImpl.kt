package com.example.data.repository

import com.example.core.database.NearbyDeviceDao
import com.example.core.database.NearbyDeviceEntity
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.NearbyDevice
import com.example.domain.repository.NearbyDeviceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NearbyDeviceRepositoryImpl(
    private val nearbyDeviceDao: NearbyDeviceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NearbyDeviceRepository {

    override fun observeDiscoveredDevices(): Flow<List<NearbyDevice>> {
        return nearbyDeviceDao.getAllDevices()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeUnblockedDevices(): Flow<List<NearbyDevice>> {
        return nearbyDeviceDao.getUnblockedDevices()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeDevice(deviceId: String): Flow<NearbyDevice?> {
        return nearbyDeviceDao.getDeviceById(deviceId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun getDevice(deviceId: String): NearbyDevice? = withContext(ioDispatcher) {
        nearbyDeviceDao.getDeviceByIdOnce(deviceId)?.toDomain()
    }

    override suspend fun updateTrustState(
        deviceId: String,
        trustState: DeviceTrustState
    ) = withContext(ioDispatcher) {
        val isBlocked = (trustState == DeviceTrustState.BLOCKED)
        nearbyDeviceDao.updateTrustState(deviceId, trustState.name, isBlocked)
    }

    override suspend fun setDeviceBlocked(
        deviceId: String,
        isBlocked: Boolean
    ) = withContext(ioDispatcher) {
        val trustState = if (isBlocked) DeviceTrustState.BLOCKED else DeviceTrustState.UNTRUSTED
        nearbyDeviceDao.updateTrustState(deviceId, trustState.name, isBlocked)
    }

    override suspend fun updateCustomNote(
        deviceId: String,
        note: String?
    ) = withContext(ioDispatcher) {
        val sanitizedNote = note?.trim()?.take(256)
        nearbyDeviceDao.updateCustomNote(deviceId, sanitizedNote)
    }

    override suspend fun forgetDevice(deviceId: String) = withContext(ioDispatcher) {
        nearbyDeviceDao.deleteDeviceById(deviceId)
    }

    override suspend fun clearAllDevices() = withContext(ioDispatcher) {
        nearbyDeviceDao.clearAllDevices()
    }

    override suspend fun saveDevice(device: NearbyDevice) = withContext(ioDispatcher) {
        // If device already exists, preserve user-assigned trust state and note unless explicitly set
        val existing = nearbyDeviceDao.getDeviceByIdOnce(device.deviceId)
        val entityToSave = if (existing != null) {
            val mergedTransports = (existing.toDomain().transports + device.transports).joinToString(",") { it.name }
            val mergedCapabilities = (existing.toDomain().capabilities + device.capabilities).joinToString(",") { it.name }
            NearbyDeviceEntity(
                deviceId = device.deviceId,
                displayName = device.displayName.take(64),
                deviceType = device.deviceType.name,
                protocolVersion = device.protocolVersion,
                transportTypes = mergedTransports,
                capabilities = mergedCapabilities,
                trustState = existing.trustState,
                isBlocked = existing.isBlocked,
                firstSeen = existing.firstSeen,
                lastSeen = maxOf(existing.lastSeen, device.lastSeen),
                customNote = existing.customNote,
                metadataJson = existing.metadataJson
            )
        } else {
            NearbyDeviceEntity.fromDomain(device)
        }
        nearbyDeviceDao.insertOrUpdate(entityToSave)
    }

    override suspend fun updateLastSeen(
        deviceId: String,
        lastSeen: Long
    ) = withContext(ioDispatcher) {
        nearbyDeviceDao.updateLastSeen(deviceId, lastSeen)
    }
}
