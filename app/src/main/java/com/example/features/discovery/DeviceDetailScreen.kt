package com.example.features.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DeviceType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(
    device: NearbyDevice?,
    onNavigateBack: () -> Unit,
    onUpdateTrustState: (DeviceTrustState) -> Unit,
    onBlockDevice: () -> Unit,
    onForgetDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device Inspection",
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("device_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkCharcoalText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvoryBackground)
            )
        }
    ) { innerPadding ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No device selected.",
                    color = MutedSlate
                )
            }
            return@Scaffold
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_detail_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val deviceIcon: ImageVector = when (device.deviceType) {
                        DeviceType.TABLET -> Icons.Default.Tablet
                        DeviceType.PHONE -> Icons.Default.PhoneAndroid
                        else -> Icons.Default.Devices
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PastelRose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = RoseDark,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = device.sanitizedDisplayName(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Trust Badge
                    val (badgeText, badgeBg, badgeColor) = when (device.trustState) {
                        DeviceTrustState.VERIFIED -> Triple("VERIFIED NODE", OnlineGreenSoft, OnlineGreen)
                        DeviceTrustState.BLOCKED -> Triple("BLOCKED", PastelPeach, LimitedOrange)
                        DeviceTrustState.UNTRUSTED -> Triple("UNTRUSTED PEER", PastelRose, RoseDark)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            // Identity Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DEVICE IDENTITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 1.sp
                    )

                    DetailRow(label = "Device ID", value = device.deviceId, isMonospace = true)
                    DetailRow(label = "Device Type", value = device.deviceType.name)
                    DetailRow(label = "Protocol Version", value = "v${device.protocolVersion}")
                    DetailRow(label = "First Discovered", value = dateFormat.format(Date(device.firstSeen)))
                    DetailRow(label = "Last Seen", value = dateFormat.format(Date(device.lastSeen)))
                }
            }

            // Transports & Capabilities
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DISCOVERY TRANSPORTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 1.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        device.transports.forEach { transport ->
                            val label = when (transport) {
                                DiscoveryTransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                                DiscoveryTransportType.BLUETOOTH_LE -> "Bluetooth LE"
                                DiscoveryTransportType.LOCAL_NETWORK -> "Local Network (mDNS)"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PastelRose)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = RoseDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = CardBorderSoft)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "REPORTED CAPABILITIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 1.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        device.capabilities.forEach { cap ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3E8FF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cap.name.replace("_", " "),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF7C3AED)
                                )
                            }
                        }
                    }
                }
            }

            // Trust Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TRUST & PRIVACY ACTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubtleGray,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onUpdateTrustState(DeviceTrustState.VERIFIED) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OnlineGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("verify_device_button")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trust", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onUpdateTrustState(DeviceTrustState.UNTRUSTED) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("untrust_device_button")
                        ) {
                            Text("Untrusted", fontSize = 13.sp, color = DarkCharcoalText)
                        }

                        Button(
                            onClick = onBlockDevice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PastelPeach,
                                contentColor = LimitedOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_device_button")
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onForgetDevice,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forget_device_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Forget Device From History")
                    }
                }
            }

            // Security Truth Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PastelRose.copy(alpha = 0.6f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = RoseDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Phase 04 Isolation Guarantee",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A discovered device is not connected. Nearby discovery operates strictly as a beacon and does not exchange files, messages, or network credentials. Free carrier/mobile internet cannot be generated.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MutedSlate
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = DarkCharcoalText
        )
    }
}
