package com.example.core.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.data.AppContainer
import com.example.features.home.HomeViewModel
import com.example.features.main.MainScreen
import com.example.features.onboarding.OnboardingScreen
import com.example.features.onboarding.OnboardingViewModel
import com.example.features.profile.ProfileSetupScreen
import com.example.features.profile.ProfileViewModel
import com.example.features.settings.SettingsViewModel
import com.example.features.settings.screens.AboutScreen
import com.example.features.settings.screens.AppearanceScreen
import com.example.features.settings.screens.ConnectivityScreen
import com.example.features.settings.screens.PrivacyScreen
import com.example.features.settings.screens.StorageScreen
import com.example.features.splash.SplashScreen
import com.example.features.splash.SplashViewModel
import com.example.features.operations.OfflineOperationsScreen
import com.example.features.operations.OfflineOperationsViewModel
import com.example.features.transfers.TransferDetailScreen
import com.example.features.transfers.TransferDetailViewModel
import com.example.features.transfers.TransfersScreen
import com.example.features.transfers.TransfersViewModel
import com.example.features.vault.VaultViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.example.features.discovery.NearbyDevicesScreen
import com.example.features.discovery.NearbyDevicesViewModel
import com.example.features.discovery.DeviceDetailScreen

@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val settingsViewModel = remember { SettingsViewModel(appContainer, context) }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Screen.Splash.route) {
            val splashViewModel = remember { SplashViewModel(appContainer) }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            val onboardingViewModel = remember { OnboardingViewModel(appContainer) }
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onFinishOnboarding = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ProfileSetup.route) {
            val profileViewModel = remember { ProfileViewModel(appContainer) }
            ProfileSetupScreen(
                viewModel = profileViewModel,
                onContinue = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            val homeViewModel = remember { HomeViewModel(appContainer) }
            val vaultViewModel = remember {
                VaultViewModel(appContainer.localVaultRepository, appContainer.offlineOperationRepository)
            }

            MainScreen(
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                vaultViewModel = vaultViewModel,
                onNavigateToConnectivity = {
                    navController.navigate(Screen.SettingsConnectivity.route)
                },
                onNavigateToStorage = {
                    navController.navigate(Screen.SettingsStorage.route)
                },
                onNavigateToOperations = {
                    navController.navigate(Screen.OfflineOperations.route)
                },
                onNavigateToTransfers = {
                    navController.navigate(Screen.Transfers.route)
                },
                onNavigateToNearbyDevices = {
                    navController.navigate(Screen.NearbyDevices.route)
                },
                onNavigateToAppearance = {
                    navController.navigate(Screen.SettingsAppearance.route)
                },
                onNavigateToPrivacy = {
                    navController.navigate(Screen.SettingsPrivacy.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.SettingsAbout.route)
                },
                onResetData = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OfflineOperations.route) {
            val opsViewModel = remember {
                OfflineOperationsViewModel(appContainer.offlineOperationRepository, appContainer.activityRepository)
            }
            OfflineOperationsScreen(
                viewModel = opsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsConnectivity.route) {
            ConnectivityScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsStorage.route) {
            StorageScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsAppearance.route) {
            AppearanceScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsPrivacy.route) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SettingsAbout.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Transfers.route) {
            val transfersViewModel = remember {
                TransfersViewModel(appContainer.transferRepository)
            }
            TransfersScreen(
                viewModel = transfersViewModel,
                onBack = { navController.popBackStack() },
                onSelectTransfer = { transferId ->
                    navController.navigate(Screen.TransferDetail.createRoute(transferId))
                }
            )
        }

        composable(
            route = Screen.TransferDetail.route,
            arguments = listOf(navArgument("transferId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transferId = backStackEntry.arguments?.getString("transferId") ?: ""
            val detailViewModel = remember(transferId) {
                TransferDetailViewModel(transferId, appContainer.transferRepository)
            }
            TransferDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NearbyDevices.route) {
            val nearbyViewModel = remember {
                NearbyDevicesViewModel(appContainer)
            }
            NearbyDevicesScreen(
                viewModel = nearbyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDeviceDetail = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
                }
            )
        }

        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val nearbyViewModel = remember {
                NearbyDevicesViewModel(appContainer)
            }
            val state by nearbyViewModel.uiState.collectAsStateWithLifecycle()
            val device = state.devices.find { it.deviceId == deviceId } ?: state.selectedDevice

            DeviceDetailScreen(
                device = device,
                onNavigateBack = { navController.popBackStack() },
                onUpdateTrustState = { trustState ->
                    nearbyViewModel.updateDeviceTrust(deviceId, trustState)
                },
                onBlockDevice = {
                    nearbyViewModel.blockDevice(deviceId)
                    navController.popBackStack()
                },
                onForgetDevice = {
                    nearbyViewModel.forgetDevice(deviceId)
                    navController.popBackStack()
                }
            )
        }
    }
}
