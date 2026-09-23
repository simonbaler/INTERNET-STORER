package com.example.core.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.example.core.permissions.DiscoveryPermissionManager
import com.example.domain.model.discovery.DeviceCapability
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
import java.security.MessageDigest

class WifiDirectDiscoveryTransport(
    private val context: Context,
    private val permissionManager: DiscoveryPermissionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : DiscoveryTransport {

    override val type: DiscoveryTransportType = DiscoveryTransportType.WIFI_DIRECT

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private val _discoveredDevices = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 64)
    private val mutex = Mutex()
    private var isDiscovering = false
    private var receiver: BroadcastReceiver? = null

    override suspend fun isSupported(): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        return hasFeature && wifiP2pManager != null
    }

    override suspend fun startDiscovery() = mutex.withLock {
        if (isDiscovering) return@withLock
        if (!isSupported()) return@withLock
        if (!permissionManager.hasNearbyWifiDevicesPermission()) return@withLock

        val manager = wifiP2pManager ?: return@withLock
        val p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
        channel = p2pChannel

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }

        val p2pReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeersSafely(manager, p2pChannel)
                    }
                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                        if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED && isDiscovering) {
                            // Can restart scan if appropriate
                        }
                    }
                }
            }
        }

        try {
            context.registerReceiver(p2pReceiver, intentFilter)
            receiver = p2pReceiver

            manager.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    isDiscovering = true
                }

                override fun onFailure(reasonCode: Int) {
                    isDiscovering = false
                }
            })
            isDiscovering = true
        } catch (e: SecurityException) {
            isDiscovering = false
            cleanupReceiver()
        } catch (e: Exception) {
            isDiscovering = false
            cleanupReceiver()
        }
    }

    private fun requestPeersSafely(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        try {
            manager.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                if (peers == null) return@requestPeers
                scope.launch {
                    val now = System.currentTimeMillis()
                    peers.deviceList.forEach { p2pDevice ->
                        val device = convertToNearbyDevice(p2pDevice, now)
                        _discoveredDevices.emit(device)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Missing permission at runtime
        } catch (e: Exception) {
            // Transient failure
        }
    }

    private fun convertToNearbyDevice(device: WifiP2pDevice, timestamp: Long): NearbyDevice {
        val addressHash = runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(device.deviceAddress.toByteArray())
            digest.take(6).joinToString("") { "%02x".format(it) }
        }.getOrDefault("peer")

        val generatedId = "is-p2p-$addressHash"
        val name = device.deviceName.takeIf { it.isNotBlank() } ?: "Direct Peer $addressHash"

        return NearbyDevice(
            deviceId = generatedId,
            displayName = name.take(32),
            deviceType = DeviceType.PHONE,
            protocolVersion = 1,
            transports = setOf(DiscoveryTransportType.WIFI_DIRECT),
            capabilities = setOf(DeviceCapability.WIFI_DIRECT, DeviceCapability.SECURE_STORAGE),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = timestamp,
            lastSeen = timestamp,
            metadata = emptyMap()
        )
    }

    override suspend fun stopDiscovery() = mutex.withLock {
        val manager = wifiP2pManager
        val p2pChannel = channel

        if (manager != null && p2pChannel != null) {
            try {
                manager.stopPeerDiscovery(p2pChannel, null)
            } catch (e: Exception) {
                // Ignore
            }
        }

        cleanupReceiver()
        channel = null
        isDiscovering = false
    }

    private fun cleanupReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
        receiver = null
    }

    override fun observeDiscoveredDevices(): Flow<NearbyDevice> = _discoveredDevices.asSharedFlow()
}
