package com.example.core.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
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
import java.util.UUID

class BleDiscoveryTransport(
    private val context: Context,
    private val permissionManager: DiscoveryPermissionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : DiscoveryTransport {

    override val type: DiscoveryTransportType = DiscoveryTransportType.BLUETOOTH_LE

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _discoveredDevices = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 64)
    private val mutex = Mutex()
    private var isDiscovering = false
    private var activeScanCallback: ScanCallback? = null

    override suspend fun isSupported(): Boolean {
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        return hasBle && bluetoothAdapter != null
    }

    override suspend fun startDiscovery() = mutex.withLock {
        if (isDiscovering) return@withLock
        if (!isSupported()) return@withLock
        if (!permissionManager.hasBluetoothScanPermission()) return@withLock

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) return@withLock

        val scanner: BluetoothLeScanner = try {
            adapter.bluetoothLeScanner ?: return@withLock
        } catch (e: Exception) {
            return@withLock
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                scope.launch {
                    processScanResult(result)?.let { device ->
                        _discoveredDevices.emit(device)
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                if (results == null) return
                scope.launch {
                    results.forEach { result ->
                        processScanResult(result)?.let { device ->
                            _discoveredDevices.emit(device)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            scanner.startScan(listOf(scanFilter), scanSettings, callback)
            activeScanCallback = callback
            isDiscovering = true
        } catch (e: SecurityException) {
            isDiscovering = false
            activeScanCallback = null
        } catch (e: Exception) {
            isDiscovering = false
            activeScanCallback = null
        }
    }

    private fun processScanResult(result: ScanResult): NearbyDevice? {
        val device = result.device ?: return null
        val address = device.address ?: return null

        val addressHash = runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(address.toByteArray())
            digest.take(6).joinToString("") { "%02x".format(it) }
        }.getOrDefault("ble")

        val generatedId = "is-ble-$addressHash"
        val name = try {
            device.name?.takeIf { it.isNotBlank() } ?: "BLE Peer $addressHash"
        } catch (e: SecurityException) {
            "BLE Peer $addressHash"
        }

        val now = System.currentTimeMillis()
        return NearbyDevice(
            deviceId = generatedId,
            displayName = name.take(32),
            deviceType = DeviceType.PHONE,
            protocolVersion = 1,
            transports = setOf(DiscoveryTransportType.BLUETOOTH_LE),
            capabilities = setOf(DeviceCapability.BLUETOOTH_LE, DeviceCapability.SECURE_STORAGE),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = now,
            lastSeen = now,
            metadata = emptyMap()
        )
    }

    override suspend fun stopDiscovery() = mutex.withLock {
        val adapter = bluetoothAdapter
        val callback = activeScanCallback

        if (adapter != null && callback != null && adapter.isEnabled) {
            try {
                adapter.bluetoothLeScanner?.stopScan(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }

        activeScanCallback = null
        isDiscovering = false
    }

    override fun observeDiscoveredDevices(): Flow<NearbyDevice> = _discoveredDevices.asSharedFlow()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FE25-0000-1000-8000-00805F9B34FB")
    }
}
