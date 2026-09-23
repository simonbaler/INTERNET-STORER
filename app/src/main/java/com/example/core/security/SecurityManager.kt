package com.example.core.security

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        return 31 * ciphertext.contentHashCode() + iv.contentHashCode()
    }
}

data class SecurityStatus(
    val isInitialized: Boolean,
    val isHardwareBacked: Boolean,
    val keyAlias: String,
    val algorithm: String = "AES/GCM/NoPadding",
    val keySizeBits: Int = 256
)

interface SecurityManager {
    suspend fun initialize(): SecurityStatus
    fun getSecurityStatus(): SecurityStatus
    fun getOrCreateSecretKey(): javax.crypto.SecretKey
    fun encrypt(data: ByteArray): Result<EncryptedPayload>
    fun decrypt(payload: EncryptedPayload): Result<ByteArray>
    fun encryptString(plainText: String): Result<String>
    fun decryptString(encryptedBase64: String): Result<String>
}
