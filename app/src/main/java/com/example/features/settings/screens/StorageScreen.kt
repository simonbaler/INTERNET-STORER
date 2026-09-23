package com.example.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.RomanticCard
import com.example.features.settings.SettingsViewModel
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val usedMB = uiState.localStorageUsedBytes / (1024 * 1024)
    val freeGB = uiState.freeDeviceStorageBytes / (1024 * 1024 * 1024)
    val totalGB = uiState.totalDeviceStorageBytes / (1024 * 1024 * 1024)

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage & Vault",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = RoseDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = RoseDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvoryBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Local Storage Usage Card
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Local Vault Space Used",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoalText
                            )
                        )
                        Text(
                            text = "$usedMB MB",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RoseDark
                            )
                        )
                    }

                    val quotaBytes = when {
                        uiState.storagePreference.contains("500 MB") -> 500L * 1024L * 1024L
                        uiState.storagePreference.contains("1 GB") -> 1L * 1024L * 1024L * 1024L
                        uiState.storagePreference.contains("2 GB") -> 2L * 1024L * 1024L * 1024L
                        uiState.storagePreference.contains("5 GB") -> 5L * 1024L * 1024L * 1024L
                        uiState.storagePreference.contains("10 GB") -> 10L * 1024L * 1024L * 1024L
                        else -> 2L * 1024L * 1024L * 1024L
                    }
                    val fraction = if (quotaBytes > 0) (uiState.localStorageUsedBytes.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RosePrimary,
                        trackColor = Color(0xFFFFEEF2),
                        strokeCap = StrokeCap.Round
                    )

                    Text(
                        text = "Allocation budget: ${uiState.storagePreference}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                    )
                }
            }

            // Quota selection card
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Storage Allocation Quota",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                    )
                    Text(
                        text = "Space reserved on this internal device for InternetStorer's offline resources.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                    )

                    val quotaOptions = listOf(
                        "Light (500 MB)",
                        "Balanced (1 GB)",
                        "Balanced (2 GB)",
                        "Spacious (5 GB)",
                        "Extensive (10 GB)"
                    )

                    quotaOptions.forEach { option ->
                        val isSelected = uiState.storagePreference == option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) RoseDark else CardBorderSoft,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            color = if (isSelected) Color(0xFFFFF0F3) else Color.White,
                            onClick = { viewModel.updateStoragePreference(option) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) RoseDark else DarkCharcoalText
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = "Selected",
                                        tint = RoseDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.cleanTempStorage() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clean Stale Temporary Cache")
                    }
                }
            }

            // Device Overall Storage
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = LavenderAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Storage",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoalText
                            )
                        )
                        Text(
                            text = "$freeGB GB free of $totalGB GB available",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                        )
                    }
                }
            }

            // Vault Partition Breakdown
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Partition Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                    )

                    StorageRowItem(
                        icon = Icons.Filled.Storage,
                        title = "Local Room SQLite Database",
                        subtitle = "Profiles, activity log, settings, offline indices",
                        sizeStr = "12 MB"
                    )

                    StorageRowItem(
                        icon = Icons.Filled.Key,
                        title = "Keystore Security Keys",
                        subtitle = "Hardware-backed AES-256 GCM master key",
                        sizeStr = "< 1 MB"
                    )

                    StorageRowItem(
                        icon = Icons.Filled.Archive,
                        title = "Continuity Knowledge Guides",
                        subtitle = "Bundled emergency survival & network handbook",
                        sizeStr = "22 MB"
                    )

                    StorageRowItem(
                        icon = Icons.Filled.Folder,
                        title = "Local Cached Media",
                        subtitle = "Avatar vectors and UI cache",
                        sizeStr = "8 MB"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StorageRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    sizeStr: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RosePrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = DarkCharcoalText
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedSlate,
                    fontSize = 11.sp
                )
            )
        }
        Text(
            text = sizeStr,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = DarkCharcoalText
            )
        )
    }
}
