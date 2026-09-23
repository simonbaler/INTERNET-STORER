package com.example.domain.usecase

import com.example.core.connectivity.ConnectivityInfo
import com.example.core.database.UserProfile
import com.example.core.security.SecurityStatus

data class ReadinessDetail(
    val title: String,
    val description: String,
    val scoreContribution: Int,
    val isReady: Boolean
)

data class LocalContinuityReadinessResult(
    val score: Int, // 0 - 100
    val summary: String,
    val details: List<ReadinessDetail>
)

typealias OfflineReadinessResult = LocalContinuityReadinessResult

class CalculateOfflineReadinessUseCase {

    operator fun invoke(
        profile: UserProfile?,
        securityStatus: SecurityStatus,
        connectivityInfo: ConnectivityInfo,
        hasStorageAllocated: Boolean = true
    ): LocalContinuityReadinessResult {
        val details = mutableListOf<ReadinessDetail>()
        var score = 0

        // 1. Local Profile & Identity in Room (25 pts)
        val profileReady = profile != null && profile.displayName.isNotBlank()
        val profileScore = if (profileReady) 25 else 10
        score += profileScore
        details.add(
            ReadinessDetail(
                title = "Local Digital Identity",
                description = if (profileReady) "Identity configured locally without cloud dependency" else "Default guest profile loaded",
                scoreContribution = profileScore,
                isReady = profileReady
            )
        )

        // 2. Hardware/Software Keystore AES-256 Vault (25 pts)
        val secReady = securityStatus.isInitialized
        val secScore = if (secReady) 25 else 10
        score += secScore
        val secDesc = when {
            secReady && securityStatus.isHardwareBacked -> "Hardware-backed AES-256 cryptographic vault active"
            secReady -> "Local AES-256 cryptographic vault active"
            else -> "Initializing secure local storage"
        }
        details.add(
            ReadinessDetail(
                title = "Local Keystore Vault",
                description = secDesc,
                scoreContribution = secScore,
                isReady = secReady
            )
        )

        // 3. Local Database & Storage Partition (25 pts)
        val storageScore = if (hasStorageAllocated) 25 else 10
        score += storageScore
        details.add(
            ReadinessDetail(
                title = "Local Continuity Storage",
                description = "Encrypted local Room database and storage partition ready",
                scoreContribution = storageScore,
                isReady = hasStorageAllocated
            )
        )

        // 4. Offline Boot & Network Autonomy (25 pts)
        val autonomyScore = 25
        score += autonomyScore
        val autonomyDesc = when {
            connectivityInfo.isOffline -> "Offline autonomy active; all local features available without network"
            connectivityInfo.isLimited -> "Limited network detected; prioritizing local offline resources"
            else -> "Autonomous local engine active; ready for immediate zero-network fallback"
        }
        details.add(
            ReadinessDetail(
                title = "Zero-Network Autonomy",
                description = autonomyDesc,
                scoreContribution = autonomyScore,
                isReady = true
            )
        )

        val summary = when {
            score >= 90 -> "Your digital world is fully fortified and ready offline ♡"
            score >= 70 -> "High readiness; core local continuity resources available"
            else -> "Initializing local digital continuity resources..."
        }

        return LocalContinuityReadinessResult(
            score = score.coerceIn(0, 100),
            summary = summary,
            details = details
        )
    }
}
