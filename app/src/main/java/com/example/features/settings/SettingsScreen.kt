package com.example.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.RomanticCard
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OfflineRose
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToConnectivity: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetData: () -> Unit,
    onNavigateToNearbyDevices: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Vault",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = RoseDark,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card Header
            RomanticCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(RoseContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = RosePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.userProfile?.displayName ?: "Explorer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoalText
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Storage: ${uiState.userProfile?.storagePreference ?: "Balanced (2 GB)"}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                        )
                    }
                }
            }

            // Settings Navigation Rows
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderSoft, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    SettingsRow(
                        title = "Connectivity & Offline",
                        subtitle = "Autonomous detection, test airplane mode",
                        icon = Icons.Filled.Sensors,
                        tint = RosePrimary,
                        tag = "nav_settings_connectivity",
                        onClick = onNavigateToConnectivity
                    )

                    SettingsDivider()

                    SettingsRow(
                        title = "Nearby Devices",
                        subtitle = "Direct node discovery, radio hardware status",
                        icon = Icons.Filled.NearMe,
                        tint = LavenderAccent,
                        tag = "nav_settings_nearby",
                        onClick = onNavigateToNearbyDevices
                    )

                    SettingsDivider()

                    SettingsRow(
                        title = "Storage & Local Vault",
                        subtitle = "Partition usage, cached guides, quota",
                        icon = Icons.Filled.Storage,
                        tint = LavenderAccent,
                        tag = "nav_settings_storage",
                        onClick = onNavigateToStorage
                    )

                    SettingsDivider()

                    SettingsRow(
                        title = "Appearance & Motion",
                        subtitle = "Themes, animation intensity, pulse",
                        icon = Icons.Filled.Palette,
                        tint = Color(0xFFF28482),
                        tag = "nav_settings_appearance",
                        onClick = onNavigateToAppearance
                    )

                    SettingsDivider()

                    SettingsRow(
                        title = "Privacy & Sovereignty",
                        subtitle = "Zero cloud upload, local ownership",
                        icon = Icons.Filled.Security,
                        tint = Color(0xFF2A9D8F),
                        tag = "nav_settings_privacy",
                        onClick = onNavigateToPrivacy
                    )

                    SettingsDivider()

                    SettingsRow(
                        title = "About InternetStorer",
                        subtitle = "Version 3.0, 16-phase roadmap, mission",
                        icon = Icons.Filled.Info,
                        tint = RoseDark,
                        tag = "nav_settings_about",
                        onClick = onNavigateToAbout
                    )
                }
            }

            // Data Reset Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetDialog = true }
                    .testTag("reset_app_data_button")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = OfflineRose,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset App State & Replay Onboarding",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OfflineRose
                            )
                        )
                        Text(
                            text = "Clears local preferences and restarts the welcome flow",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset App Data?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = RoseDark,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "This will reset your local onboarding status and return to the initial welcome screen.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = DarkCharcoalText)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetAppData(onResetData)
                    }
                ) {
                    Text("Reset", color = OfflineRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = DarkCharcoalText
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedSlate,
                    fontSize = 12.sp
                )
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MutedSlate.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 18.dp)
            .background(CardBorderSoft)
    )
}
