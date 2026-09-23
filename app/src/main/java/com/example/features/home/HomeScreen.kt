package com.example.features.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.core.connectivity.ConnectivityStatus
import com.example.core.ui.components.ConnectivityPill
import com.example.core.ui.components.OfflineReadinessGauge
import com.example.core.ui.components.PhaseBadge
import com.example.core.ui.components.RomanticCard
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OfflineRose
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToMessages: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToConnectivity: () -> Unit,
    onNavigateToNearbyDevices: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showKnowledgeDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmIvoryBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Greeting & Connectivity Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.greeting,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = RoseDark,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.testTag("home_greeting")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MutedSlate,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                ConnectivityPill(
                    connectivityInfo = uiState.connectivityInfo,
                    onClick = onNavigateToConnectivity
                )
            }
        }

        // Primary Card: Offline Readiness
        item {
            val readiness = uiState.readiness
            val score = readiness?.score ?: 100

            RomanticCard(
                onClick = onNavigateToConnectivity
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Local Continuity Readiness",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = DarkCharcoalText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                PhaseBadge(phaseText = "Phase 01 Active", isCurrent = true)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val statusCopy = when (uiState.connectivityInfo.status) {
                                ConnectivityStatus.ONLINE -> "You're connected ♡ Everything is up to date."
                                ConnectivityStatus.LIMITED -> "Connection is limited. We'll prioritize what matters."
                                ConnectivityStatus.OFFLINE -> "You're offline, but you're okay ♡ Local world active."
                                ConnectivityStatus.UNKNOWN -> "Continuous autonomous monitoring active."
                            }

                            Text(
                                text = statusCopy,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MutedSlate,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        OfflineReadinessGauge(
                            score = score,
                            size = 84.dp,
                            modifier = Modifier.testTag("offline_readiness_gauge")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Readiness components list
                    readiness?.details?.forEach { detail ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = RosePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = DarkCharcoalText,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${detail.scoreContribution}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RoseDark,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Grid (Messages, Files, AI, Knowledge)
        item {
            Text(
                text = "Digital Continuity Tools",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Messages",
                    badge = "Phase 2",
                    icon = Icons.Filled.Forum,
                    tint = RosePrimary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_messages"),
                    onClick = onNavigateToMessages
                )
                QuickActionCard(
                    title = "Files",
                    badge = "Phase 2",
                    icon = Icons.Filled.Folder,
                    tint = LavenderAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_files"),
                    onClick = onNavigateToFiles
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Offline AI",
                    badge = "Phase 8",
                    icon = Icons.Filled.AutoAwesome,
                    tint = Color(0xFFF28482),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_ai"),
                    onClick = onNavigateToAI
                )
                QuickActionCard(
                    title = "Knowledge",
                    badge = "Ready",
                    icon = Icons.Filled.Book,
                    tint = OnlineGreen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_knowledge"),
                    onClick = { showKnowledgeDialog = true }
                )
            }
        }

        // Nearby Preview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToNearbyDevices() }
                    .border(1.dp, CardBorderSoft, RoundedCornerShape(20.dp))
                    .testTag("quick_action_nearby_discovery"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF6EFF9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NearMe,
                            contentDescription = null,
                            tint = LavenderAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nearby Devices",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DarkCharcoalText
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            PhaseBadge(phaseText = "Phase 4 Active")
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Discover local InternetStorer nodes without public Internet.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MutedSlate,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // Airplane Mode / Offline Mode Simulation Switch for Testing
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFBF4F6),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleForcedOfflineMode() }
                    .testTag("toggle_offline_mode")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isForcedOffline) Icons.Filled.SensorsOff else Icons.Filled.Sensors,
                        contentDescription = null,
                        tint = if (uiState.isForcedOffline) OfflineRose else RosePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isForcedOffline) "Forced Offline Test Mode: ACTIVE" else "Simulate Airplane Mode",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (uiState.isForcedOffline) OfflineRose else DarkCharcoalText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (uiState.isForcedOffline) "Tap to restore real connectivity monitoring" else "Verify zero-network resilience instantly",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedSlate)
                        )
                    }
                }
            }
        }

        // Recent Local Activity Section
        item {
            Text(
                text = "Local Activity Log",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
            )
        }

        if (uiState.recentActivities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent activity yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MutedSlate)
                    )
                }
            }
        } else {
            items(uiState.recentActivities, key = { it.id }) { activity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, CardBorderSoft, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoseContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = RosePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activity.title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = DarkCharcoalText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = activity.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MutedSlate,
                                fontSize = 12.sp
                            )
                        )
                    }

                    val formattedTime = remember(activity.timestamp) {
                        timeFormatter.format(Date(activity.timestamp))
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(color = MutedSlate)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Knowledge Vault Dialog
    if (showKnowledgeDialog) {
        AlertDialog(
            onDismissRequest = { showKnowledgeDialog = false },
            title = {
                Text(
                    text = "Offline Knowledge Vault ♡",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = RoseDark
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "InternetStorer Continuity Handbook is bundled directly inside your device:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = DarkCharcoalText)
                    )
                    Text(
                        text = "• Core emergency protocols & first-aid guides\n• Local identity cryptographic keys\n• Encrypted local file storage rules\n• Direct device-to-device rendezvous procedures",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MutedSlate,
                            lineHeight = 20.sp
                        )
                    )
                    Text(
                        text = "All stored offline in your Room database without network dependencies.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OnlineGreen,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showKnowledgeDialog = false }) {
                    Text("Close", color = RosePrimary)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    badge: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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

                PhaseBadge(phaseText = badge)
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
            )
        }
    }
}
