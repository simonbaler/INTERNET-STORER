# InternetStorer 3.0: Phase 04 — Nearby Device Discovery Engine Architecture

## 1. Overview & Core Philosophy
Phase 04 of **InternetStorer 3.0** introduces the **Nearby Device Discovery Engine**, establishing a real, secure, lifecycle-safe, capability-aware foundation for discovering nearby InternetStorer devices without requiring the public Internet.

### Absolute Product Truths:
1. **InternetStorer cannot create free carrier or mobile Internet.**
2. **Nearby discovery does not mean Internet access.**
3. **A discovered device is not automatically a connected or trusted device.**
4. **No fake devices, fake signal strength, fake distances, or fake battery levels.**
5. **Only report capabilities actually present on the physical Android hardware.**
6. **No P2P file transfers, messaging, or mesh routing are implemented in Phase 04.**

---

## 2. Target Architecture & Module Layout

```
UI Layer (NearbyDevicesScreen, DeviceDetailScreen, DiscoveryStatusCard)
      ↓
NearbyDevicesViewModel
      ↓
NearbyDeviceRepository (Room Source of Truth)
      ↓
NearbyDiscoveryEngine (Deterministic State Machine & Deduplication)
      ↓
DiscoveryTransport Abstraction
      ↓
Concrete Android Transports:
  ├── NsdDiscoveryTransport (mDNS / DNS-SD on LAN/Ad-hoc)
  ├── WifiDirectDiscoveryTransport (Wi-Fi P2P)
  └── BleDiscoveryTransport (Bluetooth LE 5.0+ Scanning)
      ↓
Physical Nearby Devices (Zero Mock Data)
```

---

## 3. Local Device Identity (`DeviceIdentityManager`)

- **Identifier Structure:** `is-dev-<sha256-hex-8>` (e.g., `is-dev-a1b2c3d4`).
- **Privacy Assurance:** Never uses hardware serial numbers, phone numbers, SIM ICCID, or IMEI. Entropy is derived from Android Keystore master keys and cryptographically secure random seeds.
- **Persistence:** Saved in Android Jetpack DataStore; survives application restarts and remains stable across discovery sessions.
- **Protocol Version:** Initialized to protocol version 1 (`PROTOCOL_VERSION = 1`).
- **Cryptographic Key Reference:** Maintains `publicIdentityKeyReference` (`keystore://internetstorer_device_identity`) ready for cryptographic signature exchange in future phases.

---

## 4. Hardware Capability Detection (`DeviceCapabilityDetector`)

Android platform capabilities are directly queried against `PackageManager` and active system services without assuming capability from the Android version alone:
- **Wi-Fi Direct:** `PackageManager.FEATURE_WIFI_DIRECT` + `WifiManager.isWifiEnabled`.
- **Wi-Fi Aware (NAN):** `PackageManager.FEATURE_WIFI_AWARE`.
- **Bluetooth LE:** `PackageManager.FEATURE_BLUETOOTH_LE` + `BluetoothAdapter.isEnabled`.
- **Local Network:** `ConnectivityManager.getNetworkCapabilities` (active Wi-Fi, Ethernet, or VPN).
- **Secure Local Storage:** Checks available space on app storage partition.
- **Battery State:** Respects system battery saver status (`PowerManager.isPowerSaveMode`).

Status states returned: `AVAILABLE`, `SUPPORTED`, `DISABLED`, `PERMISSION_REQUIRED`, `UNAVAILABLE`, `UNKNOWN`.

---

## 5. Discovery Transports & Abstraction (`DiscoveryTransport`)

```kotlin
interface DiscoveryTransport {
    val type: DiscoveryTransportType
    suspend fun isSupported(): Boolean
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    fun observeDiscoveredDevices(): Flow<NearbyDevice>
}
```

### Supported Transports in Phase 04:
1. **`NsdDiscoveryTransport`:**
   - Real Android `NsdManager` (Network Service Discovery).
   - Service type: `_internetstorer._tcp.`
   - Discovers nodes on the same local Wi-Fi, Ethernet, or portable hotspot network without public Internet.
   - Registers service beacon if discoverability is toggled ON.
2. **`WifiDirectDiscoveryTransport`:**
   - Real Android `WifiP2pManager`.
   - Listens for `WIFI_P2P_PEERS_CHANGED_ACTION` and queries peer lists.
   - Cleans up receivers safely on stop.
3. **`BleDiscoveryTransport`:**
   - Real Android `BluetoothLeScanner`.
   - Filters on service UUID `0000FE25-0000-1000-8000-00805F9B34FB`.
   - Safely catches `SecurityException` when Bluetooth permissions are ungranted.

---

## 6. Deterministic State Machine (`NearbyDiscoveryEngine`)

The discovery engine follows strict state transitions:
- `IDLE`: Engine inactive.
- `STARTING`: Verifying permissions and initializing transport hardware.
- `DISCOVERING`: Scanning actively across supported transports.
- `DEVICE_FOUND`: At least one genuine nearby node has been discovered.
- `STOPPING`: Unregistering callbacks and closing radio sockets.
- `STOPPED`: All discovery jobs halted.
- `PERMISSION_REQUIRED`: Missing runtime permissions.
- `UNSUPPORTED`: No compatible discovery hardware available.
- `ERROR`: Recoverable runtime error encountered.

### Multi-Transport Deduplication:
When the same physical device is detected through both Wi-Fi Direct and BLE, the engine deduplicates using the unique `deviceId`, combining reported `DiscoveryTransportType` and `DeviceCapability` sets into a single unified record.

### Write Throttling:
To protect battery and flash endurance, frequent beacons update in-memory state while database writes are throttled to at most once per 10 seconds per device.

---

## 7. Data Persistence (`NearbyDeviceDao` & Room Migration)

- **Database Version:** Upgraded to Version 5.
- **Migration:** `MIGRATION_4_5` creates the `nearby_devices` table with primary key `deviceId`, indexes on `lastSeen` and `trustState`, and JSON metadata columns.
- **Trust State:** Initialized to `UNTRUSTED`. Users can manually mark nodes as `VERIFIED` or `BLOCKED`.
- **Privacy Actions:** "Forget Device" deletes the record from database; "Clear Discovered Device History" wipes all records.

---

## 8. Romantic-Futuristic User Interface

Following the InternetStorer design system:
- **Color Palette:** Warm ivory surfaces (`#FAF7F5`), pure white cards, soft rose/lavender glow, Dark Charcoal text.
- **Discovery Status Card:** Glowing breathing pulse indicator, real hardware status chips, and direct toggles for Discovery and Discoverability.
- **Honest Peer Cards:** Clean display of node name, ID, verified badge, transport chips, and relative last seen time. Strictly omits simulated signal bars, fake distances, or fake speeds.
- **Device Inspection Screen:** Complete technical inspection of reported capabilities, protocol versions, transport breakdown, and trust management actions.
