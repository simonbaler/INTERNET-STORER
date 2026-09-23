package com.example.domain.model.discovery

enum class DeviceType {
    PHONE,
    TABLET,
    LAPTOP,
    DESKTOP,
    SERVER,
    UNKNOWN;

    companion object {
        fun fromString(value: String): DeviceType = runCatching {
            valueOf(value.uppercase())
        }.getOrDefault(UNKNOWN)
    }
}

enum class DiscoveryTransportType {
    WIFI_DIRECT,
    BLUETOOTH_LE,
    LOCAL_NETWORK;

    companion object {
        fun fromString(value: String): DiscoveryTransportType? = runCatching {
            valueOf(value.uppercase())
        }.getOrNull()
    }
}

enum class DeviceCapability {
    WIFI_DIRECT,
    WIFI_AWARE,
    BLUETOOTH_LE,
    LOCAL_NETWORK,
    SECURE_STORAGE,
    BATTERY_SAVER;

    companion object {
        fun fromString(value: String): DeviceCapability? = runCatching {
            valueOf(value.uppercase())
        }.getOrNull()
    }
}

enum class CapabilityStatus {
    SUPPORTED,
    AVAILABLE,
    DISABLED,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    UNKNOWN
}

data class DeviceCapabilityReport(
    val wifiDirect: CapabilityStatus,
    val wifiAware: CapabilityStatus,
    val bluetoothLe: CapabilityStatus,
    val localNetwork: CapabilityStatus,
    val secureStorage: CapabilityStatus,
    val batterySaverActive: Boolean,
    val rawDetails: Map<String, String> = emptyMap()
) {
    fun isAnyDiscoveryAvailable(): Boolean =
        wifiDirect == CapabilityStatus.AVAILABLE ||
        bluetoothLe == CapabilityStatus.AVAILABLE ||
        localNetwork == CapabilityStatus.AVAILABLE

    fun isAnyDiscoverySupported(): Boolean =
        isAnyDiscoveryAvailable() ||
        wifiDirect == CapabilityStatus.SUPPORTED ||
        bluetoothLe == CapabilityStatus.SUPPORTED ||
        localNetwork == CapabilityStatus.SUPPORTED
}

enum class DeviceTrustState {
    UNTRUSTED,
    VERIFIED,
    BLOCKED;

    companion object {
        fun fromString(value: String): DeviceTrustState = runCatching {
            valueOf(value.uppercase())
        }.getOrDefault(UNTRUSTED)
    }
}

enum class DiscoveryState {
    IDLE,
    STARTING,
    DISCOVERING,
    DEVICE_FOUND,
    STOPPING,
    STOPPED,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    ERROR;

    fun isScanning(): Boolean = this == STARTING || this == DISCOVERING || this == DEVICE_FOUND
}

data class DeviceIdentity(
    val deviceId: String,
    val displayName: String,
    val deviceType: DeviceType,
    val protocolVersion: Int = 1,
    val publicIdentityKeyReference: String,
    val createdAt: Long,
    val lastUpdatedAt: Long
) {
    init {
        require(isValidDeviceId(deviceId)) {
            "Invalid deviceId: must be 8-64 alphanumeric characters, dashes, or underscores"
        }
    }

    companion object {
        fun isValidDeviceId(id: String): Boolean {
            if (id.length !in 8..64) return false
            return id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}

data class NearbyDevice(
    val deviceId: String,
    val displayName: String,
    val deviceType: DeviceType,
    val protocolVersion: Int = 1,
    val transports: Set<DiscoveryTransportType>,
    val capabilities: Set<DeviceCapability>,
    val trustState: DeviceTrustState = DeviceTrustState.UNTRUSTED,
    val isBlocked: Boolean = false,
    val firstSeen: Long,
    val lastSeen: Long,
    val customNote: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(DeviceIdentity.isValidDeviceId(deviceId)) {
            "Invalid deviceId: must be 8-64 alphanumeric characters, dashes, or underscores"
        }
    }

    fun sanitizedDisplayName(): String {
        val trimmed = displayName.trim().take(32)
        return if (trimmed.isBlank()) "InternetStorer Device" else trimmed
    }
}
