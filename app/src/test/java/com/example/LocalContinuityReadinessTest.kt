package com.example

import com.example.core.connectivity.ConnectivityInfo
import com.example.core.connectivity.ConnectivityStatus
import com.example.core.connectivity.NetworkType
import com.example.core.database.UserProfile
import com.example.core.security.SecurityStatus
import com.example.domain.usecase.CalculateOfflineReadinessUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContinuityReadinessTest {

    private val useCase = CalculateOfflineReadinessUseCase()

    @Test
    fun `full readiness achieves 100 score in offline mode`() {
        val profile = UserProfile(
            id = 1,
            displayName = "Alice",
            avatarId = "heart_rose",
            preferredLanguage = "English",
            storagePreference = "Balanced (2 GB)"
        )
        val securityStatus = SecurityStatus(
            isInitialized = true,
            isHardwareBacked = true,
            keyAlias = "InternetStorer_Master_Key"
        )
        val connectivityInfo = ConnectivityInfo(
            status = ConnectivityStatus.OFFLINE,
            networkType = NetworkType.NONE
        )

        val result = useCase(
            profile = profile,
            securityStatus = securityStatus,
            connectivityInfo = connectivityInfo,
            hasStorageAllocated = true
        )

        assertEquals(100, result.score)
        assertEquals(4, result.details.size)
        assertTrue(result.details.all { it.isReady })
    }

    @Test
    fun `partial readiness gives graceful score without crashes`() {
        val result = useCase(
            profile = null,
            securityStatus = SecurityStatus(isInitialized = false, isHardwareBacked = false, keyAlias = "key"),
            connectivityInfo = ConnectivityInfo(status = ConnectivityStatus.OFFLINE, networkType = NetworkType.NONE),
            hasStorageAllocated = false
        )

        // 10 (guest profile) + 10 (init keystore) + 10 (storage) + 25 (zero network autonomy) = 55
        assertEquals(55, result.score)
        assertTrue(result.details.any { !it.isReady })
    }

    @Test
    fun `readiness details never include unverified mesh or cloud features`() {
        val result = useCase(
            profile = null,
            securityStatus = SecurityStatus(isInitialized = true, isHardwareBacked = false, keyAlias = "key"),
            connectivityInfo = ConnectivityInfo(status = ConnectivityStatus.OFFLINE, networkType = NetworkType.NONE)
        )

        val titles = result.details.map { it.title.lowercase() }
        assertTrue(titles.none { it.contains("mesh") || it.contains("cloud") || it.contains("p2p") || it.contains("sync") })
    }
}
