package com.example.domain.repository

import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.NearbyDevice
import kotlinx.coroutines.flow.Flow

interface NearbyDeviceRepository {
    fun observeDiscoveredDevices(): Flow<List<NearbyDevice>>
    fun observeUnblockedDevices(): Flow<List<NearbyDevice>>
    fun observeDevice(deviceId: String): Flow<NearbyDevice?>
    suspend fun getDevice(deviceId: String): NearbyDevice?
    suspend fun updateTrustState(deviceId: String, trustState: DeviceTrustState)
    suspend fun setDeviceBlocked(deviceId: String, isBlocked: Boolean)
    suspend fun updateCustomNote(deviceId: String, note: String?)
    suspend fun forgetDevice(deviceId: String)
    suspend fun clearAllDevices()
    suspend fun saveDevice(device: NearbyDevice)
    suspend fun updateLastSeen(deviceId: String, lastSeen: Long)
}
