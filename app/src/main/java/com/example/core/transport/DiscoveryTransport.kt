package com.example.core.transport

import com.example.domain.model.discovery.DiscoveryTransportType
import com.example.domain.model.discovery.NearbyDevice
import kotlinx.coroutines.flow.Flow

interface DiscoveryTransport {
    val type: DiscoveryTransportType
    suspend fun isSupported(): Boolean
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    fun observeDiscoveredDevices(): Flow<NearbyDevice>
}
