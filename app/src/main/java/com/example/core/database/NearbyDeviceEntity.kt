package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.discovery.DeviceCapability
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DeviceType
import com.example.domain.model.discovery.DiscoveryTransportType
import com.example.domain.model.discovery.NearbyDevice

@Entity(
    tableName = "nearby_devices",
    indices = [
        Index(value = ["lastSeen"], name = "index_nearby_devices_lastSeen"),
        Index(value = ["trustState"], name = "index_nearby_devices_trustState"),
        Index(value = ["isBlocked"], name = "index_nearby_devices_isBlocked")
    ]
)
data class NearbyDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val displayName: String,
    val deviceType: String,
    val protocolVersion: Int,
    val transportTypes: String, // comma separated e.g. "WIFI_DIRECT,LOCAL_NETWORK"
    val capabilities: String,   // comma separated e.g. "WIFI_DIRECT,LOCAL_NETWORK"
    val trustState: String,     // e.g. "UNTRUSTED", "VERIFIED", "BLOCKED"
    val isBlocked: Boolean,
    val firstSeen: Long,
    val lastSeen: Long,
    val customNote: String? = null,
    val metadataJson: String = "{}"
) {
    fun toDomain(): NearbyDevice {
        val parsedTransports = transportTypes.split(",")
            .mapNotNull { DiscoveryTransportType.fromString(it.trim()) }
            .toSet()

        val parsedCapabilities = capabilities.split(",")
            .mapNotNull { DeviceCapability.fromString(it.trim()) }
            .toSet()

        return NearbyDevice(
            deviceId = deviceId,
            displayName = displayName,
            deviceType = DeviceType.fromString(deviceType),
            protocolVersion = protocolVersion,
            transports = parsedTransports,
            capabilities = parsedCapabilities,
            trustState = DeviceTrustState.fromString(trustState),
            isBlocked = isBlocked,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            customNote = customNote,
            metadata = emptyMap()
        )
    }

    companion object {
        fun fromDomain(device: NearbyDevice): NearbyDeviceEntity {
            return NearbyDeviceEntity(
                deviceId = device.deviceId,
                displayName = device.displayName.take(64),
                deviceType = device.deviceType.name,
                protocolVersion = device.protocolVersion,
                transportTypes = device.transports.joinToString(",") { it.name },
                capabilities = device.capabilities.joinToString(",") { it.name },
                trustState = device.trustState.name,
                isBlocked = device.isBlocked || device.trustState == DeviceTrustState.BLOCKED,
                firstSeen = device.firstSeen,
                lastSeen = device.lastSeen,
                customNote = device.customNote,
                metadataJson = "{}"
            )
        }
    }
}
