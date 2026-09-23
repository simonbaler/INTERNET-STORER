package com.example.core.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.core.identity.DeviceIdentityManager
import com.example.domain.model.discovery.DeviceCapability
import com.example.domain.model.discovery.DeviceIdentity
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DeviceType
import com.example.domain.model.discovery.DiscoveryTransportType
import com.example.domain.model.discovery.NearbyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class NsdDiscoveryTransport(
    private val context: Context,
    private val identityManager: DeviceIdentityManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : DiscoveryTransport {

    override val type: DiscoveryTransportType = DiscoveryTransportType.LOCAL_NETWORK

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _discoveredDevices = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 64)
    private val mutex = Mutex()
    private var isDiscovering = false
    private var isAdvertising = false

    private var activeDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var activeRegistrationListener: NsdManager.RegistrationListener? = null

    override suspend fun isSupported(): Boolean {
        return nsdManager != null
    }

    override suspend fun startDiscovery() = mutex.withLock {
        if (isDiscovering || nsdManager == null) return@withLock

        val identity = identityManager.getOrCreateIdentity()
        val manager = nsdManager ?: return@withLock

        // Start broadcasting our service if supported
        startAdvertisingInternal(manager, identity)

        // Start discovering peers on the local network
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isDiscovering = false
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                isDiscovering = true
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                isDiscovering = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                if (serviceInfo.serviceType.contains(SERVICE_TYPE_BASE)) {
                    resolveServiceSafely(manager, serviceInfo, identity.deviceId)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                // Device went offline or left local network
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            activeDiscoveryListener = listener
            isDiscovering = true
        } catch (e: Exception) {
            isDiscovering = false
            activeDiscoveryListener = null
        }
    }

    private fun resolveServiceSafely(manager: NsdManager, serviceInfo: NsdServiceInfo, ownDeviceId: String) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                // Resolution failed (e.g. transient network busy)
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                if (resolvedInfo == null) return
                scope.launch {
                    parseResolvedService(resolvedInfo, ownDeviceId)?.let { device ->
                        _discoveredDevices.emit(device)
                    }
                }
            }
        }

        try {
            manager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            // Ignore concurrent resolve errors
        }
    }

    private fun parseResolvedService(serviceInfo: NsdServiceInfo, ownDeviceId: String): NearbyDevice? {
        val serviceName = serviceInfo.serviceName ?: return null

        // Attributes can be read on API 21+
        val attributes = serviceInfo.attributes ?: emptyMap()
        val rawDeviceId = attributes["id"]?.let { String(it, StandardCharsets.UTF_8) }
            ?: serviceName.removePrefix("IS-")

        val sanitizedDeviceId = rawDeviceId.trim().take(64)
        if (!DeviceIdentity.isValidDeviceId(sanitizedDeviceId) || sanitizedDeviceId == ownDeviceId) {
            return null
        }

        val rawName = attributes["name"]?.let { String(it, StandardCharsets.UTF_8) } ?: serviceName
        val rawType = attributes["type"]?.let { String(it, StandardCharsets.UTF_8) } ?: "PHONE"
        val protocolVersion = attributes["ver"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() } ?: 1

        val now = System.currentTimeMillis()
        return NearbyDevice(
            deviceId = sanitizedDeviceId,
            displayName = rawName.trim().take(32).ifBlank { "InternetStorer Peer" },
            deviceType = DeviceType.fromString(rawType),
            protocolVersion = protocolVersion,
            transports = setOf(DiscoveryTransportType.LOCAL_NETWORK),
            capabilities = setOf(DeviceCapability.LOCAL_NETWORK, DeviceCapability.SECURE_STORAGE),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = now,
            lastSeen = now,
            metadata = emptyMap()
        )
    }

    private fun startAdvertisingInternal(manager: NsdManager, identity: DeviceIdentity) {
        if (isAdvertising) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "IS-${identity.deviceId}"
            serviceType = SERVICE_TYPE
            port = LOCAL_DISCOVERY_PORT
            setAttribute("id", identity.deviceId)
            setAttribute("name", identity.displayName.take(32))
            setAttribute("type", identity.deviceType.name)
            setAttribute("ver", identity.protocolVersion.toString())
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                isAdvertising = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isAdvertising = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                isAdvertising = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                isAdvertising = false
            }
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            activeRegistrationListener = listener
        } catch (e: Exception) {
            isAdvertising = false
            activeRegistrationListener = null
        }
    }

    override suspend fun stopDiscovery() = mutex.withLock {
        val manager = nsdManager ?: return@withLock

        activeDiscoveryListener?.let { listener ->
            try {
                manager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Safe ignore if already stopped
            }
        }
        activeDiscoveryListener = null
        isDiscovering = false

        activeRegistrationListener?.let { listener ->
            try {
                manager.unregisterService(listener)
            } catch (e: Exception) {
                // Safe ignore if already unregistered
            }
        }
        activeRegistrationListener = null
        isAdvertising = false
    }

    override fun observeDiscoveredDevices(): Flow<NearbyDevice> = _discoveredDevices.asSharedFlow()

    companion object {
        const val SERVICE_TYPE_BASE = "_internetstorer._tcp"
        const val SERVICE_TYPE = "_internetstorer._tcp."
        const val LOCAL_DISCOVERY_PORT = 52834
    }
}
