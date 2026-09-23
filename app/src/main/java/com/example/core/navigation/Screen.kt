package com.example.core.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object ProfileSetup : Screen("profile_setup")
    object Main : Screen("main")

    // Settings sub-screens
    object SettingsConnectivity : Screen("settings_connectivity")
    object SettingsStorage : Screen("settings_storage")
    object SettingsAppearance : Screen("settings_appearance")
    object SettingsPrivacy : Screen("settings_privacy")
    object SettingsAbout : Screen("settings_about")
    object OfflineOperations : Screen("offline_operations")
    object Transfers : Screen("transfers")
    object TransferDetail : Screen("transfer_detail/{transferId}") {
        fun createRoute(transferId: String) = "transfer_detail/$transferId"
    }
    object StorageIntegrity : Screen("storage_integrity")
    object NearbyDevices : Screen("nearby_devices")
    object DeviceDetail : Screen("device_detail/{deviceId}") {
        fun createRoute(deviceId: String) = "device_detail/$deviceId"
    }
}

sealed class BottomNavTab(val route: String, val label: String) {
    object Home : BottomNavTab("tab_home", "Home")
    object Messages : BottomNavTab("tab_messages", "Messages")
    object Files : BottomNavTab("tab_files", "Files")
    object AI : BottomNavTab("tab_ai", "Offline AI")
    object Settings : BottomNavTab("tab_settings", "Settings")
}
