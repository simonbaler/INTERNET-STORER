package com.example.core.discovery

import com.example.core.capabilities.DeviceCapabilityDetector
import com.example.core.permissions.DiscoveryPermissionManager
import com.example.core.transport.DiscoveryTransport
import com.example.domain.model.discovery.DeviceCapabilityReport
import com.example.domain.model.discovery.DiscoveryState
import com.example.domain.model.discovery.NearbyDevice
import com.example.domain.repository.NearbyDeviceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class NearbyDiscoveryEngine(
    val transports: List<DiscoveryTransport>,
    private val capabilityDetector: DeviceCapabilityDetector,
    private val permissionManager: DiscoveryPermissionManager,
    private val repository: NearbyDeviceRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _discoveryState = MutableStateFlow(DiscoveryState.IDLE)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val mutex = Mutex()
    private val transportJobs = mutableListOf<Job>()

    // In-memory deduplication and throttle cache: deviceId -> (device, lastDbPersistTimestamp)
    private val deviceCache = ConcurrentHashMap<String, Pair<NearbyDevice, Long>>()

    suspend fun checkCapabilities(): DeviceCapabilityReport {
        return capabilityDetector.detectCapabilities()
    }

    suspend fun startDiscovery() = mutex.withLock {
        val currentState = _discoveryState.value
        if (currentState == DiscoveryState.STARTING || currentState == DiscoveryState.DISCOVERING || currentState == DiscoveryState.DEVICE_FOUND) {
            // Already scanning, prevent duplicate scanners
            return@withLock
        }

        // 1. Permission check
        if (!permissionManager.hasAllRequiredPermissions()) {
            _discoveryState.value = DiscoveryState.PERMISSION_REQUIRED
            return@withLock
        }

        // 2. Capability check
        val report = capabilityDetector.detectCapabilities()
        if (!report.isAnyDiscoverySupported()) {
            _discoveryState.value = DiscoveryState.UNSUPPORTED
            return@withLock
        }

        _discoveryState.value = DiscoveryState.STARTING
        _errorMessage.value = null

        // 3. Launch active transports
        transportJobs.forEach { it.cancel() }
        transportJobs.clear()

        var activeTransportCount = 0
        for (transport in transports) {
            if (transport.isSupported()) {
                activeTransportCount++
                val job = scope.launch {
                    try {
                        transport.startDiscovery()
                        transport.observeDiscoveredDevices().collect { incomingDevice ->
                            handleDiscoveredDevice(incomingDevice)
                        }
                    } catch (e: CancellationException) {
                        // Clean job cancellation
                    } catch (e: Exception) {
                        // Transport-specific non-fatal error
                    }
                }
                transportJobs.add(job)
            }
        }

        if (activeTransportCount == 0) {
            _discoveryState.value = DiscoveryState.UNSUPPORTED
        } else {
            _discoveryState.value = DiscoveryState.DISCOVERING
        }
    }

    private suspend fun handleDiscoveredDevice(device: NearbyDevice) {
        val deviceId = device.deviceId
        val now = System.currentTimeMillis()

        // Multi-transport deduplication: Merge transports if already seen
        val existingEntry = deviceCache[deviceId]
        val mergedDevice = if (existingEntry != null) {
            val (existingDevice, _) = existingEntry
            existingDevice.copy(
                displayName = if (existingDevice.displayName.startsWith("Direct Peer") || existingDevice.displayName.startsWith("BLE Peer")) {
                    device.displayName
                } else {
                    existingDevice.displayName
                },
                transports = existingDevice.transports + device.transports,
                capabilities = existingDevice.capabilities + device.capabilities,
                lastSeen = now
            )
        } else {
            device.copy(firstSeen = now, lastSeen = now)
        }

        val lastPersist = existingEntry?.second ?: 0L
        val shouldPersist = (existingEntry == null) || (now - lastPersist > PERSIST_THROTTLE_MS)

        deviceCache[deviceId] = Pair(mergedDevice, if (shouldPersist) now else lastPersist)

        if (shouldPersist) {
            repository.saveDevice(mergedDevice)
        }

        if (_discoveryState.value == DiscoveryState.DISCOVERING) {
            _discoveryState.value = DiscoveryState.DEVICE_FOUND
        }
    }

    suspend fun stopDiscovery() = mutex.withLock {
        val current = _discoveryState.value
        if (current == DiscoveryState.STOPPING || current == DiscoveryState.STOPPED || current == DiscoveryState.IDLE) {
            return@withLock
        }

        _discoveryState.value = DiscoveryState.STOPPING

        // Stop all transport listeners
        transportJobs.forEach { it.cancel() }
        transportJobs.clear()

        for (transport in transports) {
            try {
                transport.stopDiscovery()
            } catch (e: Exception) {
                // Safe ignore
            }
        }

        // Flush any unpersisted devices in cache
        val now = System.currentTimeMillis()
        for ((_, pair) in deviceCache) {
            val (device, lastPersisted) = pair
            if (now - lastPersisted > 1000) {
                repository.saveDevice(device)
            }
        }

        _discoveryState.value = DiscoveryState.IDLE
    }

    suspend fun resetState() = mutex.withLock {
        stopDiscovery()
        deviceCache.clear()
        _discoveryState.value = DiscoveryState.IDLE
        _errorMessage.value = null
    }

    companion object {
        private const val PERSIST_THROTTLE_MS = 10_000L // At most 1 DB write per 10s per device
    }
}
