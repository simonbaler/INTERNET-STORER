package com.example.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.navigation.BottomNavTab
import com.example.features.ai.AIPreviewScreen
import com.example.features.home.HomeScreen
import com.example.features.home.HomeViewModel
import com.example.features.messages.MessagesPreviewScreen
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.vault.VaultScreen
import com.example.features.vault.VaultViewModel
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

data class NavTabItem(
    val tab: BottomNavTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    vaultViewModel: VaultViewModel,
    onNavigateToConnectivity: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToOperations: () -> Unit,
    onNavigateToTransfers: () -> Unit = {},
    onNavigateToNearbyDevices: () -> Unit = {},
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetData: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(BottomNavTab.Home.route) }

    val navItems = listOf(
        NavTabItem(BottomNavTab.Home, Icons.Filled.Home, Icons.Outlined.Home),
        NavTabItem(BottomNavTab.Messages, Icons.Filled.Forum, Icons.Outlined.Forum),
        NavTabItem(BottomNavTab.Files, Icons.Filled.Folder, Icons.Outlined.Folder),
        NavTabItem(BottomNavTab.AI, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        NavTabItem(BottomNavTab.Settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = WarmIvoryBackground,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                navItems.forEach { item ->
                    val isSelected = currentTab == item.tab.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = item.tab.route },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.tab.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RoseDark,
                            selectedTextColor = RoseDark,
                            indicatorColor = Color(0xFFFFEEF2),
                            unselectedIconColor = MutedSlate,
                            unselectedTextColor = MutedSlate
                        ),
                        modifier = Modifier.testTag("tab_${item.tab.label.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                BottomNavTab.Home.route -> HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToMessages = { currentTab = BottomNavTab.Messages.route },
                    onNavigateToFiles = { currentTab = BottomNavTab.Files.route },
                    onNavigateToAI = { currentTab = BottomNavTab.AI.route },
                    onNavigateToConnectivity = onNavigateToConnectivity,
                    onNavigateToNearbyDevices = onNavigateToNearbyDevices
                )
                BottomNavTab.Messages.route -> MessagesPreviewScreen()
                BottomNavTab.Files.route -> VaultScreen(
                    viewModel = vaultViewModel,
                    onNavigateToOperations = onNavigateToOperations,
                    onNavigateToTransfers = onNavigateToTransfers
                )
                BottomNavTab.AI.route -> AIPreviewScreen()
                BottomNavTab.Settings.route -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToConnectivity = onNavigateToConnectivity,
                    onNavigateToStorage = onNavigateToStorage,
                    onNavigateToAppearance = onNavigateToAppearance,
                    onNavigateToPrivacy = onNavigateToPrivacy,
                    onNavigateToAbout = onNavigateToAbout,
                    onResetData = onResetData,
                    onNavigateToNearbyDevices = onNavigateToNearbyDevices
                )
            }
        }
    }
}
