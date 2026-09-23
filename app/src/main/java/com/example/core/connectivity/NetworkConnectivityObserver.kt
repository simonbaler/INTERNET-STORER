package com.example.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkConnectivityObserver(
    private val context: Context
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun observe(): Flow<ConnectivityInfo> = callbackFlow {
        val initialInfo = getCurrentConnectivity()
        trySend(initialInfo)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentConnectivity())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentConnectivity())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentConnectivity())
            }

            override fun onUnavailable() {
                trySend(
                    ConnectivityInfo(
                        status = ConnectivityStatus.OFFLINE,
                        networkType = NetworkType.NONE,
                        isMetered = false,
                        isInternetValidated = false
                    )
                )
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder().build()
                connectivityManager?.registerNetworkCallback(request, callback)
            }
        } catch (_: Exception) {
            trySend(
                ConnectivityInfo(
                    status = ConnectivityStatus.OFFLINE,
                    networkType = NetworkType.NONE
                )
            )
        }

        awaitClose {
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                // Ignore if already unregistered or manager unavailable
            }
        }
    }.distinctUntilChanged()

    override fun getCurrentConnectivity(): ConnectivityInfo {
        val cm = connectivityManager ?: return ConnectivityInfo(
            status = ConnectivityStatus.UNKNOWN,
            networkType = NetworkType.UNKNOWN
        )

        val activeNetwork = cm.activeNetwork ?: return ConnectivityInfo(
            status = ConnectivityStatus.OFFLINE,
            networkType = NetworkType.NONE
        )

        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return ConnectivityInfo(
            status = ConnectivityStatus.OFFLINE,
            networkType = NetworkType.NONE
        )

        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            else -> NetworkType.UNKNOWN
        }

        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val status = when {
            hasInternetCapability && isValidated -> ConnectivityStatus.ONLINE
            hasInternetCapability || networkType != NetworkType.UNKNOWN -> ConnectivityStatus.LIMITED
            else -> ConnectivityStatus.OFFLINE
        }

        return ConnectivityInfo(
            status = status,
            networkType = networkType,
            isMetered = isMetered,
            isInternetValidated = isValidated
        )
    }
}
