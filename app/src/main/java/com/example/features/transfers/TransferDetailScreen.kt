package com.example.features.transfers

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChunkState
import com.example.domain.model.FileChunk
import com.example.domain.model.TransferRecord
import com.example.domain.model.TransferState
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.LimitedOrange
import com.example.ui.theme.LimitedOrangeSoft
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OfflineRose
import com.example.ui.theme.OfflineRoseSoft
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.PastelPeach
import com.example.ui.theme.PastelRose
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDetailScreen(
    viewModel: TransferDetailViewModel,
    onBack: () -> Unit
) {
    val transfer by viewModel.transfer.collectAsState()
    val chunks by viewModel.chunks.collectAsState()
    val manifest by viewModel.manifest.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        containerColor = WarmIvoryBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transfer Inspection",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("transfer_detail_back")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkCharcoalText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvoryBackground)
            )
        }
    ) { innerPadding ->
        if (transfer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Transfer record not found", color = MutedSlate)
            }
        } else {
            val record = transfer!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Card
                item {
                    TransferHeaderCard(record = record)
                }

                // Controls row
                item {
                    TransferControlsRow(
                        state = record.state,
                        onPause = { viewModel.pause() },
                        onResume = { viewModel.resume() },
                        onCancel = { viewModel.cancel() },
                        onRetry = { viewModel.retry() }
                    )
                }

                // Manifest & Identity Card
                item {
                    ManifestIdentityCard(record = record, manifest = manifest)
                }

                // Chunks List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Chunks & Integrity (${chunks.size})",
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${record.verifiedChunks}/${record.totalChunks} Verified",
                            fontSize = 12.sp,
                            color = if (record.verifiedChunks == record.totalChunks) OnlineGreen else RosePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Chunks items
                items(chunks, key = { it.chunkIndex }) { chunk ->
                    ChunkItemCard(chunk = chunk)
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TransferHeaderCard(record: TransferRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(RoseContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = RosePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = record.filename,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${record.direction.name} • ${record.formattedTotalSize}",
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                    }
                }

                TransferStateBadge(state = record.state)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { record.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when (record.state) {
                    TransferState.COMPLETED -> OnlineGreen
                    TransferState.FAILED -> OfflineRose
                    TransferState.PAUSED -> LimitedOrange
                    else -> RosePrimary
                },
                trackColor = RoseContainer
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${record.verifiedChunks} / ${record.totalChunks} Chunks Verified",
                    fontSize = 12.sp,
                    color = MutedSlate,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${record.progressPercent}%",
                    fontSize = 12.sp,
                    color = DarkCharcoalText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TransferControlsRow(
    state: TransferState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (state) {
            TransferState.TRANSFERRING -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LimitedOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pause")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = OfflineRose)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", color = OfflineRose)
                }
            }
            TransferState.PAUSED, TransferState.INTERRUPTED -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = OfflineRose)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", color = OfflineRose)
                }
            }
            TransferState.FAILED -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RosePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            else -> {
                // Completed or cancelled
            }
        }
    }
}

@Composable
private fun ManifestIdentityCard(record: TransferRecord, manifest: com.example.domain.model.FileManifest?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = RosePrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cryptographic Manifest", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoalText)
            }

            DetailRow(label = "Transfer ID", value = record.transferId)
            DetailRow(label = "File ID", value = record.fileId)
            DetailRow(label = "Content SHA-256", value = record.contentHash, isMono = true)
            DetailRow(label = "Chunk Size", value = "${record.chunkSize / 1024} KB (${record.chunkSize} B)")
            if (manifest != null) {
                DetailRow(label = "MIME Type", value = manifest.mimeType)
                DetailRow(label = "Encryption", value = manifest.encryptionMetadata)
                DetailRow(label = "Version", value = "v${manifest.version}")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = MutedSlate)
        Text(
            text = value,
            fontSize = 11.sp,
            color = DarkCharcoalText,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}

@Composable
private fun ChunkItemCard(chunk: FileChunk) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val icon = when (chunk.status) {
                    ChunkState.VERIFIED -> Icons.Default.CheckCircle to OnlineGreen
                    ChunkState.PROCESSING -> Icons.Default.Sync to LavenderAccent
                    ChunkState.FAILED -> Icons.Default.Error to OfflineRose
                    else -> Icons.Default.HourglassEmpty to MutedSlate
                }
                Icon(imageVector = icon.first, contentDescription = null, tint = icon.second, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Chunk #${chunk.chunkIndex} (${chunk.length} B)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = DarkCharcoalText
                    )
                    Text(
                        text = "Offset: ${chunk.offset} • ${chunk.chunkHash.take(16)}...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MutedSlate
                    )
                }
            }

            ChunkStateBadge(state = chunk.status)
        }
    }
}

@Composable
private fun ChunkStateBadge(state: ChunkState) {
    val (bgColor, textColor) = when (state) {
        ChunkState.VERIFIED -> OnlineGreenSoft to OnlineGreen
        ChunkState.PROCESSING -> LavenderSoft to LavenderAccent
        ChunkState.FAILED -> OfflineRoseSoft to OfflineRose
        ChunkState.PENDING -> RoseContainer to RoseDark
    }

    Text(
        text = state.name,
        color = textColor,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
