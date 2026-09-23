package com.example.core.connectivity

import kotlinx.coroutines.flow.Flow

interface ConnectivityObserver {
    fun observe(): Flow<ConnectivityInfo>
    fun getCurrentConnectivity(): ConnectivityInfo
}
