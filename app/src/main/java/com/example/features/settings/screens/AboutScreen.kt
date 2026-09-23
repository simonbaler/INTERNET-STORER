package com.example.features.settings.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.PhaseBadge
import com.example.core.ui.components.RomanticCard
import com.example.core.ui.components.RomanticHeartBreathing
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About InternetStorer",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RomanticHeartBreathing(size = 80.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "InternetStorer",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = RoseDark,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Version 3.0 • Phase 01",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MutedSlate,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                PhaseBadge(
                    phaseText = "Offline-First Foundation + Romantic Product Experience",
                    isCurrent = true
                )
            }

            // Core Principle Card
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Core Principle",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RoseDark
                        )
                    )
                    Text(
                        text = "“Internet unavailable must NEVER mean application unavailable.”",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = DarkCharcoalText,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        )
                    )
                }
            }

            // Mission Statement Card
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Mission Statement",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                    )
                    Text(
                        text = "Build the production-grade Android foundation of InternetStorer, an AI-powered Digital Continuity Network designed to keep users connected to their important digital resources even when Internet connectivity is unavailable.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MutedSlate,
                            lineHeight = 22.sp
                        )
                    )
                }
            }

            // Roadmap Snapshot
            RomanticCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "16-Phase Network Roadmap",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                    )

                    RoadmapRow(phase = "Phase 01", name = "Offline-First Foundation", isDone = true)
                    RoadmapRow(phase = "Phase 02", name = "Offline Core + Local Storage", isDone = false)
                    RoadmapRow(phase = "Phase 03", name = "Secure Local File System", isDone = false)
                    RoadmapRow(phase = "Phase 04", name = "Nearby Device Discovery", isDone = false)
                    RoadmapRow(phase = "Phase 05", name = "Offline P2P Messaging", isDone = false)
                    RoadmapRow(phase = "Phase 06", name = "P2P File Transfer + Resume", isDone = false)
                    RoadmapRow(phase = "Phase 07", name = "Store-and-Forward Mesh", isDone = false)
                    RoadmapRow(phase = "Phase 08", name = "On-Device AI Engine", isDone = false)
                    RoadmapRow(phase = "Phase 09", name = "Local RAG Knowledge Engine", isDone = false)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoadmapRow(phase: String, name: String, isDone: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isDone) Color(0xFFFFEEF2) else Color(0xFFF6F3F5)
        ) {
            Text(
                text = phase,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isDone) RoseDark else MutedSlate,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isDone) DarkCharcoalText else MutedSlate,
                fontWeight = if (isDone) FontWeight.SemiBold else FontWeight.Normal
            ),
            modifier = Modifier.weight(1f)
        )
        if (isDone) {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = RosePrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
