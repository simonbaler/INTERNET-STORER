package com.example.features.discovery

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.discovery.CapabilityStatus
import com.example.domain.model.discovery.DeviceCapabilityReport
import com.example.domain.model.discovery.DiscoveryState
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LimitedOrange
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.PastelRose
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.SubtleGray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryStatusCard(
    state: DiscoveryState,
    statusMessage: String,
    isDiscoveryEnabled: Boolean,
    isDiscoverable: Boolean,
    capabilities: DeviceCapabilityReport?,
    onToggleDiscovery: (Boolean) -> Unit,
    onToggleDiscoverability: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val (badgeColor, badgeBg) = when (state) {
        DiscoveryState.DISCOVERING -> Pair(RosePrimary, PastelRose)
        DiscoveryState.DEVICE_FOUND -> Pair(OnlineGreen, OnlineGreenSoft)
        DiscoveryState.STARTING -> Pair(Color(0xFFC77DFF), Color(0xFFF3E8FF))
        DiscoveryState.PERMISSION_REQUIRED -> Pair(RoseDark, PastelRose)
        DiscoveryState.UNSUPPORTED -> Pair(LimitedOrange, Color(0xFFFFF3E0))
        DiscoveryState.ERROR -> Pair(RoseDark, PastelRose)
        else -> Pair(SubtleGray, Color(0xFFF5F5F5))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discovery_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .graphicsLayer {
                                    if (state.isScanning()) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                    }

                    Column {
                        Text(
                            text = "Discovery Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    }
                }

                // Discovery Switch
                Switch(
                    checked = isDiscoveryEnabled,
                    onCheckedChange = onToggleDiscovery,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RosePrimary,
                        uncheckedThumbColor = SubtleGray,
                        uncheckedTrackColor = Color(0xFFEFEFEF)
                    ),
                    modifier = Modifier.testTag("discovery_toggle")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware Capability Indicators
            Text(
                text = "REAL HARDWARE CAPABILITIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = SubtleGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CapabilityPill(
                    icon = Icons.Default.WifiTethering,
                    name = "Wi-Fi Direct",
                    status = capabilities?.wifiDirect ?: CapabilityStatus.UNKNOWN
                )
                CapabilityPill(
                    icon = Icons.Default.Bluetooth,
                    name = "Bluetooth LE",
                    status = capabilities?.bluetoothLe ?: CapabilityStatus.UNKNOWN
                )
                CapabilityPill(
                    icon = Icons.Default.Wifi,
                    name = "Local Network",
                    status = capabilities?.localNetwork ?: CapabilityStatus.UNKNOWN
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = CardBorderSoft)
            Spacer(modifier = Modifier.height(12.dp))

            // Privacy Discoverability Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Discoverability",
                        tint = if (isDiscoverable) RoseDark else SubtleGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Device Discoverability",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkCharcoalText
                        )
                        Text(
                            text = if (isDiscoverable) "Visible to nearby InternetStorer nodes" else "Silent / Invisible to local scans",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    }
                }

                Switch(
                    checked = isDiscoverable,
                    onCheckedChange = onToggleDiscoverability,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFC77DFF),
                        uncheckedThumbColor = SubtleGray,
                        uncheckedTrackColor = Color(0xFFEFEFEF)
                    ),
                    modifier = Modifier.testTag("discoverability_toggle")
                )
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: ImageVector,
    name: String,
    status: CapabilityStatus
) {
    val (statusText, chipColor, textColor) = when (status) {
        CapabilityStatus.AVAILABLE -> Triple("Available", OnlineGreenSoft, OnlineGreen)
        CapabilityStatus.SUPPORTED -> Triple("Supported", Color(0xFFF3E8FF), Color(0xFF8B5CF6))
        CapabilityStatus.DISABLED -> Triple("Disabled", Color(0xFFFFF3E0), LimitedOrange)
        CapabilityStatus.PERMISSION_REQUIRED -> Triple("Needs Perm", PastelRose, RoseDark)
        CapabilityStatus.UNAVAILABLE -> Triple("No Hardware", Color(0xFFF5F5F5), SubtleGray)
        CapabilityStatus.UNKNOWN -> Triple("Unknown", Color(0xFFF5F5F5), SubtleGray)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(chipColor)
            .border(1.dp, chipColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$name: $statusText",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
