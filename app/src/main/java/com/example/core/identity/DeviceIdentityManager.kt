package com.example.core.identity

import android.content.Context
import android.os.Build
import com.example.core.datastore.PreferencesManager
import com.example.core.security.SecurityManager
import com.example.domain.model.discovery.DeviceIdentity
import com.example.domain.model.discovery.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class DeviceIdentityManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val securityManager: SecurityManager
) {
    private val mutex = Mutex()
    private var cachedIdentity: DeviceIdentity? = null

    suspend fun getOrCreateIdentity(): DeviceIdentity = mutex.withLock {
        cachedIdentity?.let { return@withLock it }

        withContext(Dispatchers.IO) {
            val (persistedId, persistedKeyRef, persistedCreatedAt) = preferencesManager.getDeviceIdentityData()
            val customName = preferencesManager.customDeviceName.first()

            if (persistedId != null && persistedKeyRef != null && persistedCreatedAt != null && DeviceIdentity.isValidDeviceId(persistedId)) {
                val identity = DeviceIdentity(
                    deviceId = persistedId,
                    displayName = customName ?: generateDefaultDisplayName(),
                    deviceType = detectDeviceType(),
                    protocolVersion = PROTOCOL_VERSION,
                    publicIdentityKeyReference = persistedKeyRef,
                    createdAt = persistedCreatedAt,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                cachedIdentity = identity
                return@withContext identity
            }

            // Generate a fresh stable device identity
            val now = System.currentTimeMillis()
            val keyRef = "keystore://internetstorer_device_identity"
            // Use SecurityManager / Keystore + UUID digest to ensure entropy without using IMEI/MAC/serial
            val seedData = "${UUID.randomUUID()}-${now}-${securityManager.getSecurityStatus().keyAlias}".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(seedData)
            val hexId = digest.take(8).joinToString("") { "%02x".format(it) }
            val newDeviceId = "is-dev-$hexId"

            preferencesManager.setDeviceIdentity(
                deviceId = newDeviceId,
                keyRef = keyRef,
                createdAt = now
            )

            val newIdentity = DeviceIdentity(
                deviceId = newDeviceId,
                displayName = customName ?: generateDefaultDisplayName(),
                deviceType = detectDeviceType(),
                protocolVersion = PROTOCOL_VERSION,
                publicIdentityKeyReference = keyRef,
                createdAt = now,
                lastUpdatedAt = now
            )
            cachedIdentity = newIdentity
            newIdentity
        }
    }

    suspend fun updateCustomDisplayName(name: String): DeviceIdentity = mutex.withLock {
        val sanitized = name.trim().take(32)
        preferencesManager.setCustomDeviceName(sanitized)
        val current = getOrCreateIdentity()
        val updated = current.copy(
            displayName = if (sanitized.isNotBlank()) sanitized else generateDefaultDisplayName(),
            lastUpdatedAt = System.currentTimeMillis()
        )
        cachedIdentity = updated
        updated
    }

    private fun generateDefaultDisplayName(): String {
        val model = Build.MODEL.trim()
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .take(20)
        return if (model.isNotBlank()) "InternetStorer ($model)" else "InternetStorer Node"
    }

    private fun detectDeviceType(): DeviceType {
        val config = context.resources.configuration
        return if (config.smallestScreenWidthDp >= 600) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1
    }
}
