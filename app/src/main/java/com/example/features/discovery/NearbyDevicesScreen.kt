package com.example.features.discovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DeviceType
import com.example.domain.model.discovery.DiscoveryState
import com.example.domain.model.discovery.DiscoveryTransportType
import com.example.domain.model.discovery.NearbyDevice
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LimitedOrange
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.PastelPeach
import com.example.ui.theme.PastelRose
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.SubtleGray
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyDevicesScreen(
    viewModel: NearbyDevicesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDeviceDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsGranted()
    }

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nearby Devices",
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("nearby_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkCharcoalText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshCapabilities() },
                        modifier = Modifier.testTag("nearby_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Capabilities",
                            tint = RoseDark
                        )
                    }
                    if (state.devices.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllDevices() },
                            modifier = Modifier.testTag("nearby_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = SubtleGray
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvoryBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Truth & Privacy Disclaimer Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PastelRose.copy(alpha = 0.5f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = RoseDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Discovery finds nearby nodes without the Internet. Discovered nodes are untrusted until verified. InternetStorer cannot create free carrier internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkCharcoalText,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 2. Permission Banner if missing
            if (state.missingPermissions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_banner_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LimitedOrange.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = LimitedOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Nearby Permissions Required",
                                    fontWeight = FontWeight.Bold,
                                    color = LimitedOrange,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "Android requires permissions for Nearby Wi-Fi Devices and Bluetooth scanning to discover local physical nodes without Internet.",
                                fontSize = 12.sp,
                                color = DarkCharcoalText,
                                lineHeight = 16.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(state.missingPermissions.toTypedArray())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LimitedOrange),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("request_permissions_button")
                                ) {
                                    Text("Grant Permissions", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Discovery Engine Status Card
            item {
                DiscoveryStatusCard(
                    state = state.discoveryState,
                    statusMessage = state.statusMessage,
                    isDiscoveryEnabled = state.isDiscoveryEnabled,
                    isDiscoverable = state.isDiscoverable,
                    capabilities = state.capabilities,
                    onToggleDiscovery = { viewModel.toggleDiscovery(it) },
                    onToggleDiscoverability = { viewModel.toggleDiscoverability(it) }
                )
            }

            // 4. Discovered Devices Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERED PEERS (${state.devices.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SubtleGray
                    )

                    if (state.discoveryState.isScanning()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = RosePrimary
                            )
                            Text(
                                text = "Scanning...",
                                fontSize = 11.sp,
                                color = RoseDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 5. Device Items or Empty State
            if (state.devices.isEmpty()) {
                item {
                    EmptyDiscoveryState(state = state.discoveryState)
                }
            } else {
                items(state.devices, key = { it.deviceId }) { device ->
                    NearbyDeviceCard(
                        device = device,
                        onClick = {
                            viewModel.selectDevice(device)
                            onNavigateToDeviceDetail(device.deviceId)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyDeviceCard(
    device: NearbyDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceIcon: ImageVector = when (device.deviceType) {
        DeviceType.TABLET -> Icons.Default.Tablet
        DeviceType.PHONE -> Icons.Default.PhoneAndroid
        else -> Icons.Default.Devices
    }

    val (badgeText, badgeBg, badgeColor) = when (device.trustState) {
        DeviceTrustState.VERIFIED -> Triple("Verified", OnlineGreenSoft, OnlineGreen)
        DeviceTrustState.BLOCKED -> Triple("Blocked", PastelPeach, LimitedOrange)
        DeviceTrustState.UNTRUSTED -> Triple("Untrusted", PastelRose, RoseDark)
    }

    val relativeTime = remember(device.lastSeen) {
        formatRelativeTime(device.lastSeen)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("device_card_${device.deviceId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PastelRose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = RoseDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = device.sanitizedDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.deviceId,
                            fontSize = 11.sp,
                            color = MutedSlate
                        )
                    }
                }

                // Trust Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transport Tags & Relative Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    device.transports.forEach { transport ->
                        val label = when (transport) {
                            DiscoveryTransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                            DiscoveryTransportType.BLUETOOTH_LE -> "BLE"
                            DiscoveryTransportType.LOCAL_NETWORK -> "Local LAN"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF7F2FA))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkCharcoalText
                            )
                        }
                    }
                }

                Text(
                    text = "Seen $relativeTime",
                    fontSize = 11.sp,
                    color = SubtleGray
                )
            }
        }
    }
}

@Composable
fun EmptyDiscoveryState(state: DiscoveryState) {
    val (message, subMessage, icon) = when (state) {
        DiscoveryState.IDLE -> Triple(
            "Nearby discovery is turned off.",
            "Toggle discovery on to look for nearby InternetStorer nodes.",
            Icons.Default.Devices
        )
        DiscoveryState.STARTING -> Triple(
            "Preparing nearby discovery...",
            "Initializing hardware radios and local transports.",
            Icons.Default.Refresh
        )
        DiscoveryState.DISCOVERING -> Triple(
            "Looking for nearby InternetStorer devices...",
            "Listening for direct Wi-Fi, Bluetooth LE, and local mDNS beacons.",
            Icons.Default.Search
        )
        DiscoveryState.PERMISSION_REQUIRED -> Triple(
            "Nearby permission is required.",
            "Grant nearby device permissions to discover local hardware nodes.",
            Icons.Default.Security
        )
        DiscoveryState.UNSUPPORTED -> Triple(
            "Nearby discovery is not supported on this device.",
            "Neither Wi-Fi Direct, Bluetooth LE, nor Local LAN transports are available.",
            Icons.Default.Warning
        )
        DiscoveryState.ERROR -> Triple(
            "Nearby discovery encountered a problem.",
            "Please check device radio states and permissions.",
            Icons.Default.Warning
        )
        else -> Triple(
            "No nearby InternetStorer devices found.",
            "Ensure peers have InternetStorer open and Discoverability enabled.",
            Icons.Default.Devices
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_discovery_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PastelRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = RoseDark,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkCharcoalText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 5000 -> "just now"
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}
