package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NearbyDeviceDao {

    @Query("SELECT * FROM nearby_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<NearbyDeviceEntity>>

    @Query("SELECT * FROM nearby_devices WHERE isBlocked = 0 ORDER BY lastSeen DESC")
    fun getUnblockedDevices(): Flow<List<NearbyDeviceEntity>>

    @Query("SELECT * FROM nearby_devices WHERE deviceId = :deviceId LIMIT 1")
    fun getDeviceById(deviceId: String): Flow<NearbyDeviceEntity?>

    @Query("SELECT * FROM nearby_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceByIdOnce(deviceId: String): NearbyDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: NearbyDeviceEntity)

    @Query("UPDATE nearby_devices SET lastSeen = :lastSeen WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, lastSeen: Long)

    @Query("UPDATE nearby_devices SET trustState = :trustState, isBlocked = :isBlocked WHERE deviceId = :deviceId")
    suspend fun updateTrustState(deviceId: String, trustState: String, isBlocked: Boolean)

    @Query("UPDATE nearby_devices SET customNote = :note WHERE deviceId = :deviceId")
    suspend fun updateCustomNote(deviceId: String, note: String?)

    @Query("DELETE FROM nearby_devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    @Query("DELETE FROM nearby_devices")
    suspend fun clearAllDevices()

    @Query("SELECT COUNT(*) FROM nearby_devices")
    fun getDeviceCount(): Flow<Int>
}
