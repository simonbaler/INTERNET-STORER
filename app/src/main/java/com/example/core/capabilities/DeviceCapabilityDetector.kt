package com.example.core.capabilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.example.domain.model.discovery.CapabilityStatus
import com.example.domain.model.discovery.DeviceCapabilityReport

class DeviceCapabilityDetector(private val context: Context) {

    fun detectCapabilities(): DeviceCapabilityReport {
        val packageManager = context.packageManager
        val details = mutableMapOf<String, String>()

        // 1. Wi-Fi Direct
        val wifiDirectStatus = runCatching {
            val hasFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
            details["feature_wifi_direct"] = hasFeature.toString()
            if (!hasFeature) {
                CapabilityStatus.UNAVAILABLE
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager == null) {
                    CapabilityStatus.UNAVAILABLE
                } else if (!wifiManager.isWifiEnabled) {
                    CapabilityStatus.DISABLED
                } else {
                    CapabilityStatus.AVAILABLE
                }
            }
        }.getOrElse { error ->
            if (error is SecurityException) {
                details["wifi_direct_security_error"] = error.message.orEmpty()
                CapabilityStatus.PERMISSION_REQUIRED
            } else {
                details["wifi_direct_error"] = error.message.orEmpty()
                CapabilityStatus.UNKNOWN
            }
        }

        // 2. Wi-Fi Aware (NAN)
        val wifiAwareStatus = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
                details["feature_wifi_aware"] = hasFeature.toString()
                if (!hasFeature) CapabilityStatus.UNAVAILABLE else CapabilityStatus.SUPPORTED
            } else {
                CapabilityStatus.UNAVAILABLE
            }
        }.getOrDefault(CapabilityStatus.UNAVAILABLE)

        // 3. Bluetooth LE
        val bleStatus = runCatching {
            val hasBleFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            val hasBtFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            details["feature_ble"] = hasBleFeature.toString()
            details["feature_bt"] = hasBtFeature.toString()

            if (!hasBleFeature && !hasBtFeature) {
                CapabilityStatus.UNAVAILABLE
            } else {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                if (adapter == null) {
                    CapabilityStatus.UNAVAILABLE
                } else if (!adapter.isEnabled) {
                    CapabilityStatus.DISABLED
                } else {
                    CapabilityStatus.AVAILABLE
                }
            }
        }.getOrElse { error ->
            if (error is SecurityException) {
                details["ble_security_error"] = error.message.orEmpty()
                CapabilityStatus.PERMISSION_REQUIRED
            } else {
                details["ble_error"] = error.message.orEmpty()
                CapabilityStatus.UNKNOWN
            }
        }

        // 4. Local Network (LAN / mDNS / NSD)
        val localNetworkStatus = runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            if (capabilities == null) {
                CapabilityStatus.DISABLED
            } else {
                val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                details["active_transport_wifi"] = hasWifi.toString()
                details["active_transport_ethernet"] = hasEthernet.toString()

                if (hasWifi || hasEthernet || hasVpn) {
                    CapabilityStatus.AVAILABLE
                } else {
                    CapabilityStatus.SUPPORTED
                }
            }
        }.getOrElse {
            CapabilityStatus.UNKNOWN
        }

        // 5. Secure Local Storage
        val storageStatus = runCatching {
            val filesDir = context.filesDir
            if (filesDir != null && filesDir.usableSpace > 50L * 1024L * 1024L) {
                CapabilityStatus.AVAILABLE
            } else {
                CapabilityStatus.SUPPORTED
            }
        }.getOrDefault(CapabilityStatus.UNKNOWN)

        // 6. Battery Saver
        val isBatterySaverActive = runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isPowerSaveMode ?: false
        }.getOrDefault(false)

        return DeviceCapabilityReport(
            wifiDirect = wifiDirectStatus,
            wifiAware = wifiAwareStatus,
            bluetoothLe = bleStatus,
            localNetwork = localNetworkStatus,
            secureStorage = storageStatus,
            batterySaverActive = isBatterySaverActive,
            rawDetails = details
        )
    }
}
