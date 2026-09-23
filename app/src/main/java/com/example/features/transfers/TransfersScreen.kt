package com.example.features.transfers

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.IntegrityIssueType
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
import com.example.ui.theme.PastelRose
import com.example.ui.theme.PeachAccent
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.SoftGold
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    viewModel: TransfersViewModel,
    onBack: () -> Unit,
    onSelectTransfer: (String) -> Unit
) {
    val allTransfers by viewModel.allTransfers.collectAsStateWithLifecycle()
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val isCheckingIntegrity by viewModel.isCheckingIntegrity.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var showNewTransferDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val filteredTransfers = remember(selectedFilter, allTransfers) {
        when (selectedFilter) {
            "ACTIVE" -> allTransfers.filter { !it.state.isTerminal() }
            "COMPLETED" -> allTransfers.filter { it.state == TransferState.COMPLETED }
            "FAILED" -> allTransfers.filter { it.state == TransferState.FAILED || it.state == TransferState.CANCELLED }
            else -> allTransfers
        }
    }

    Scaffold(
        containerColor = WarmIvoryBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Transfers & Integrity",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoalText
                            )
                        )
                        Text(
                            text = "Resumable chunk engine & offline vault check",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MutedSlate
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("transfers_back_button")) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkCharcoalText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runIntegrityCheck() },
                        enabled = !isCheckingIntegrity,
                        modifier = Modifier.testTag("scan_integrity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Integrity",
                            tint = RosePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WarmIvoryBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewTransferDialog = true },
                containerColor = RosePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_transfer_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Transfer")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Test Transfer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Integrity Status Card
            item {
                IntegrityOverviewCard(
                    report = integrityReport,
                    isScanning = isCheckingIntegrity,
                    onScanAgain = { viewModel.runIntegrityCheck() },
                    onRecoverOrphan = { viewModel.recoverOrphan(it) }
                )
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All (${allTransfers.size})",
                        "ACTIVE" to "Active (${allTransfers.count { !it.state.isTerminal() }})",
                        "COMPLETED" to "Completed (${allTransfers.count { it.state == TransferState.COMPLETED }})",
                        "FAILED" to "Issues (${allTransfers.count { it.state == TransferState.FAILED || it.state == TransferState.CANCELLED }})"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoseContainer,
                                selectedLabelColor = RoseDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Transfers List
            if (filteredTransfers.isEmpty()) {
                item {
                    EmptyTransfersCard(selectedFilter)
                }
            } else {
                items(filteredTransfers, key = { it.transferId }) { transfer ->
                    TransferItemCard(
                        transfer = transfer,
                        onClick = { onSelectTransfer(transfer.transferId) },
                        onPause = { viewModel.pauseTransfer(transfer.transferId) },
                        onResume = { viewModel.resumeTransfer(transfer.transferId) },
                        onCancel = { viewModel.cancelTransfer(transfer.transferId) },
                        onRetry = { viewModel.retryTransfer(transfer.transferId) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showNewTransferDialog) {
        NewTransferDialog(
            onDismiss = { showNewTransferDialog = false },
            onSubmit = { name, content ->
                showNewTransferDialog = false
                viewModel.startLocalReconstructionSample(name, content)
            }
        )
    }
}

@Composable
private fun IntegrityOverviewCard(
    report: com.example.domain.model.StorageIntegrityReport?,
    isScanning: Boolean,
    onScanAgain: () -> Unit,
    onRecoverOrphan: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RoseContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = RosePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Vault Integrity Guard",
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoalText,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isScanning) "Scanning SHA-256 integrity..." else "Cryptographic on-disk verification",
                            color = MutedSlate,
                            fontSize = 12.sp
                        )
                    }
                }

                if (report?.isClean == true) {
                    Text(
                        text = "100% HEALTHY",
                        color = OnlineGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(OnlineGreenSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else if (report != null) {
                    Text(
                        text = "${report.issues.size} ISSUE(S)",
                        color = OfflineRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(OfflineRoseSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IntegrityMetric(
                    label = "Files Checked",
                    value = report?.totalFilesChecked?.toString() ?: "-"
                )
                IntegrityMetric(
                    label = "Valid",
                    value = report?.validFiles?.toString() ?: "-",
                    color = OnlineGreen
                )
                IntegrityMetric(
                    label = "Missing",
                    value = report?.missingFiles?.toString() ?: "-",
                    color = if ((report?.missingFiles ?: 0) > 0) OfflineRose else MutedSlate
                )
                IntegrityMetric(
                    label = "Corrupted",
                    value = report?.corruptedFiles?.toString() ?: "-",
                    color = if ((report?.corruptedFiles ?: 0) > 0) OfflineRose else MutedSlate
                )
                IntegrityMetric(
                    label = "Orphans",
                    value = report?.orphanFiles?.toString() ?: "-",
                    color = if ((report?.orphanFiles ?: 0) > 0) LimitedOrange else MutedSlate
                )
            }

            // Display issues if any
            if (report != null && report.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RoseContainer, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Integrity Issues Detected:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = RoseDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    report.issues.take(3).forEach { issue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${issue.description}",
                                fontSize = 11.sp,
                                color = DarkCharcoalText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (issue.type == IntegrityIssueType.ORPHAN_FILE) {
                                TextButton(
                                    onClick = { onRecoverOrphan(issue.path) },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Recover", fontSize = 10.sp, color = RosePrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrityMetric(
    label: String,
    value: String,
    color: Color = DarkCharcoalText
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MutedSlate
        )
    }
}

@Composable
private fun TransferItemCard(
    transfer: TransferRecord,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transfer_item_${transfer.transferId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PastelRose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = RosePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = transfer.filename.ifEmpty { "Transfer ${transfer.transferId.take(8)}" },
                            fontWeight = FontWeight.SemiBold,
                            color = DarkCharcoalText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${transfer.direction.name} • ${transfer.formattedTotalSize}",
                            fontSize = 11.sp,
                            color = MutedSlate
                        )
                    }
                }

                TransferStateBadge(state = transfer.state)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (transfer.state) {
                    TransferState.COMPLETED -> OnlineGreen
                    TransferState.FAILED -> OfflineRose
                    TransferState.PAUSED -> LimitedOrange
                    else -> RosePrimary
                },
                trackColor = RoseContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${transfer.verifiedChunks} / ${transfer.totalChunks} chunks verified (${transfer.progressPercent}%)",
                    fontSize = 11.sp,
                    color = MutedSlate
                )

                // Inline Controls
                Row {
                    when (transfer.state) {
                        TransferState.TRANSFERRING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = LimitedOrange, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OfflineRose, modifier = Modifier.size(18.dp))
                            }
                        }
                        TransferState.PAUSED, TransferState.INTERRUPTED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = OnlineGreen, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OfflineRose, modifier = Modifier.size(18.dp))
                            }
                        }
                        TransferState.FAILED -> {
                            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = RosePrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                        else -> {
                            // Completed or terminal
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferStateBadge(state: TransferState) {
    val (bgColor, textColor, label) = when (state) {
        TransferState.CREATED -> Triple(RoseContainer, RoseDark, "CREATED")
        TransferState.PREPARING -> Triple(LavenderSoft, LavenderAccent, "PREPARING")
        TransferState.TRANSFERRING -> Triple(PastelRose, RosePrimary, "TRANSFERRING")
        TransferState.PAUSED -> Triple(LimitedOrangeSoft, LimitedOrange, "PAUSED")
        TransferState.INTERRUPTED -> Triple(RoseContainer, SoftGold, "INTERRUPTED")
        TransferState.VERIFYING -> Triple(LavenderSoft, LavenderAccent, "VERIFYING")
        TransferState.COMPLETED -> Triple(OnlineGreenSoft, OnlineGreen, "COMPLETED")
        TransferState.FAILED -> Triple(OfflineRoseSoft, OfflineRose, "FAILED")
        TransferState.CANCELLED -> Triple(CardBorderSoft, MutedSlate, "CANCELLED")
    }

    Text(
        text = label,
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun EmptyTransfersCard(filter: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhiteSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = PeachAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (filter == "ALL") "No Active Transfers" else "No $filter Transfers",
                fontWeight = FontWeight.Bold,
                color = DarkCharcoalText,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use 'Test Transfer' to verify local chunking and atomic reconstruction completely offline.",
                color = MutedSlate,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun NewTransferDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var fileName by remember { mutableStateOf("offline_document.txt") }
    var fileContent by remember {
        mutableStateOf(
            "InternetStorer 3.0 secure payload. This text is divided into streaming deterministic chunks, hashed individually with SHA-256, verified, and reconstructed into the local vault atomically."
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Test Local Chunk Transfer", fontWeight = FontWeight.Bold, color = DarkCharcoalText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Runs real offline chunking, per-chunk SHA-256 verification, and atomic file reconstruction into the vault.",
                    fontSize = 12.sp,
                    color = MutedSlate
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    label = { Text("Content to chunk & transfer") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fileName, fileContent) },
                colors = ButtonDefaults.buttonColors(containerColor = RosePrimary)
            ) {
                Text("Start Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedSlate)
            }
        }
    )
}
