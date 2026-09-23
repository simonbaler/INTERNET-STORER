package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.connectivity.ConnectivityInfo
import com.example.core.connectivity.ConnectivityStatus
import com.example.core.connectivity.NetworkType
import com.example.core.database.AppDatabase
import com.example.core.database.LocalActivity
import com.example.core.database.UserProfile
import com.example.core.datastore.PreferencesManager
import com.example.core.security.AndroidKeystoreSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase01OfflineAuditTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var securityManager: AndroidKeystoreSecurityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = PreferencesManager(context)
        securityManager = AndroidKeystoreSecurityManager()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRoomDatabaseOfflinePersistence() = runBlocking {
        // Test user profile local storage
        val profile = UserProfile(
            id = 1,
            displayName = "Offline Pioneer",
            avatarId = "heart_rose",
            preferredLanguage = "English",
            storagePreference = "Balanced (2 GB)"
        )
        db.userProfileDao().insertOrUpdate(profile)

        val retrieved = db.userProfileDao().getUserProfileOnce()
        assertNotNull(retrieved)
        assertEquals("Offline Pioneer", retrieved?.displayName)

        // Test activity log offline recording
        val activity = LocalActivity(
            title = "Autonomous Zero-Network Boot",
            description = "Startup completed without external network",
            category = "SYSTEM"
        )
        db.localActivityDao().insert(activity)

        val activities = db.localActivityDao().getRecentActivities(5).first()
        assertEquals(1, activities.size)
        assertEquals("Autonomous Zero-Network Boot", activities[0].title)
    }

    @Test
    fun testPreferencesManagerForcedOfflineSync() = runBlocking {
        // Toggle forced offline mode
        preferencesManager.setForcedOffline(true)
        val isOffline = preferencesManager.isForcedOffline.first()
        assertTrue(isOffline)

        preferencesManager.setForcedOffline(false)
        val isRestored = preferencesManager.isForcedOffline.first()
        assertFalse(isRestored)
    }

    @Test
    fun testSecurityManagerInitializesGracefully() = runBlocking {
        val status = securityManager.initialize()
        assertTrue(status.isInitialized)
        assertNotNull(status.keyAlias)
    }

    @Test
    fun testConnectivityModelOfflineSafety() {
        val offlineInfo = ConnectivityInfo(
            status = ConnectivityStatus.OFFLINE,
            networkType = NetworkType.NONE,
            isMetered = false,
            isInternetValidated = false
        )
        assertTrue(offlineInfo.isOffline)
        assertFalse(offlineInfo.isOnline)
        assertFalse(offlineInfo.isLimited)
    }
}
