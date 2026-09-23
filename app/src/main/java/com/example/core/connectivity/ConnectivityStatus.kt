package com.example.core.connectivity

enum class ConnectivityStatus {
    ONLINE,
    LIMITED,
    OFFLINE,
    UNKNOWN
}

enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    NONE,
    UNKNOWN
}

data class ConnectivityInfo(
    val status: ConnectivityStatus = ConnectivityStatus.UNKNOWN,
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val isMetered: Boolean = false,
    val isInternetValidated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isOnline: Boolean get() = status == ConnectivityStatus.ONLINE
    val isOffline: Boolean get() = status == ConnectivityStatus.OFFLINE
    val isLimited: Boolean get() = status == ConnectivityStatus.LIMITED
}
