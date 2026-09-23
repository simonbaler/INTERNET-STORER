package com.example.features.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.LocalFile
import com.example.domain.model.FileCategory
import com.example.domain.model.StorageHealth
import com.example.domain.model.formatBytes
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PastelPeach
import com.example.ui.theme.PastelRose
import com.example.ui.theme.RoseDark
import com.example.ui.theme.WarmIvoryBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onNavigateToOperations: () -> Unit = {},
    onNavigateToTransfers: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    var importEncryptOption by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFileFromUri(uri, context, importEncryptOption)
        }
    }

    Scaffold(
        containerColor = WarmIvoryBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VaultTopBar(
                health = uiState.storageBreakdown.health,
                onNavigateToOperations = onNavigateToOperations,
                onNavigateToTransfers = onNavigateToTransfers,
                onBack = onBack
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateDialog(true) },
                    containerColor = LavenderSoft,
                    contentColor = RoseDark,
                    modifier = Modifier.testTag("create_note_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAdd,
                        contentDescription = "Create Local Note"
                    )
                }

                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    containerColor = RoseDark,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("import_file_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Import File to Vault"
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StorageCapacityCard(
                    breakdown = uiState.storageBreakdown
                )
            }

            item {
                CategoryChipsRow(
                    selectedCategory = uiState.selectedCategory,
                    onSelect = { viewModel.selectCategory(it) }
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_field"),
                    placeholder = { Text("Search stored files...", color = MutedSlate) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MutedSlate)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MutedSlate)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stored Resources (${uiState.filteredFiles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = importEncryptOption,
                            onCheckedChange = { importEncryptOption = it },
                            colors = CheckboxDefaults.colors(checkedColor = RoseDark),
                            modifier = Modifier.testTag("encrypt_next_import_checkbox")
                        )
                        Text(
                            text = "Encrypt imports",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RoseDark)
                    }
                }
            }

            if (uiState.filteredFiles.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyVaultCard(
                        onImport = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onCreate = { viewModel.showCreateDialog(true) }
                    )
                }
            } else {
                items(uiState.filteredFiles, key = { it.id }) { file ->
                    LocalFileItemCard(
                        file = file,
                        onTogglePin = { viewModel.togglePin(file.id) },
                        onSelect = { viewModel.selectFileForDetail(file) },
                        onVerify = { viewModel.verifyIntegrity(file.id) },
                        onDelete = { viewModel.requestDeleteConfirmation(file) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Create Note Dialog
    if (uiState.showCreateNoteDialog) {
        CreateNoteDialog(
            onDismiss = { viewModel.showCreateDialog(false) },
            onCreate = { title, content, encrypt ->
                viewModel.createLocalDocument(title, content, encrypt)
            }
        )
    }

    // Delete Confirmation Dialog
    uiState.showDeleteConfirmDialog?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete '${fileToDelete.displayName}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This permanently deletes the file from local storage. Real device space will be reclaimed immediately.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDark),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Detail Sheet
    uiState.selectedFileForDetail?.let { detailFile ->
        FileDetailBottomSheet(
            file = detailFile,
            preview = uiState.previewContent,
            onDismiss = { viewModel.closeDetail() },
            onVerify = { viewModel.verifyIntegrity(detailFile.id) },
            onDelete = {
                viewModel.closeDetail()
                viewModel.requestDeleteConfirmation(detailFile)
            }
        )
    }
}

@Composable
fun VaultTopBar(
    health: StorageHealth,
    onNavigateToOperations: () -> Unit,
    onNavigateToTransfers: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkCharcoalText
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column {
                Text(
                    text = "Offline Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
                Text(
                    text = "Internal device storage engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val (badgeBg, badgeText, badgeColor) = when (health) {
                StorageHealth.HEALTHY -> Triple(Color(0xFFE8F5E9), "Healthy", Color(0xFF2E7D32))
                StorageHealth.NEAR_LIMIT -> Triple(Color(0xFFFFF3E0), "Near Limit", Color(0xFFE65100))
                StorageHealth.LIMIT_REACHED -> Triple(Color(0xFFFFEBEE), "Full", Color(0xFFC62828))
                StorageHealth.DEVICE_STORAGE_CRITICAL -> Triple(Color(0xFFFFEBEE), "Low Disk", Color(0xFFC62828))
            }

            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onNavigateToTransfers,
                modifier = Modifier.testTag("transfers_queue_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Transfers & File Integrity",
                    tint = RoseDark
                )
            }

            IconButton(
                onClick = onNavigateToOperations,
                modifier = Modifier.testTag("operations_queue_button")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Offline Operations Queue",
                    tint = RoseDark
                )
            }
        }
    }
}

@Composable
fun StorageCapacityCard(
    breakdown: com.example.domain.model.StorageBreakdown
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderSoft))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vault Space Allocation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
                Text(
                    text = "${(breakdown.usagePercentage * 100).toInt()}% used",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = RoseDark
                )
            }

            LinearProgressIndicator(
                progress = { breakdown.usagePercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = RoseDark,
                trackColor = LavenderSoft,
                strokeCap = StrokeCap.Round
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Vault Stored",
                        fontSize = 11.sp,
                        color = MutedSlate
                    )
                    Text(
                        text = breakdown.formattedUsed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Configured Quota",
                        fontSize = 11.sp,
                        color = MutedSlate
                    )
                    Text(
                        text = breakdown.formattedReserved,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Device Free Space",
                        fontSize = 11.sp,
                        color = MutedSlate
                    )
                    Text(
                        text = breakdown.formattedDeviceFree,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChipsRow(
    selectedCategory: FileCategory,
    onSelect: (FileCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FileCategory.values().forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onSelect(cat) },
                label = { Text(cat.displayName, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RoseDark,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun LocalFileItemCard(
    file: LocalFile,
    onTogglePin: () -> Unit,
    onSelect: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isEncrypted = file.encryptionVersion > 0
    val formattedDate = remember(file.createdAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(file.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("file_card_${file.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderSoft))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isEncrypted) LavenderSoft else PastelPeach),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isEncrypted -> Icons.Default.Lock
                        file.mimeType.startsWith("image/") -> Icons.Default.Image
                        file.mimeType.contains("zip") -> Icons.Default.Archive
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = if (isEncrypted) LavenderAccent else RoseDark,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isEncrypted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = LavenderSoft,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "AES-GCM",
                                color = LavenderAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )

                    if (file.status == "CORRUPTED") {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Corrupted",
                                color = Color(0xFFC62828),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Pin button
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.testTag("pin_file_${file.id}")
            ) {
                Icon(
                    imageVector = if (file.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (file.isPinned) "Unpin" else "Pin",
                    tint = if (file.isPinned) RoseDark else MutedSlate
                )
            }

            // Menu button
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("menu_file_${file.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MutedSlate
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Inspect Details") },
                        onClick = {
                            menuExpanded = false
                            onSelect()
                        },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Verify Integrity") },
                        onClick = {
                            menuExpanded = false
                            onVerify()
                        },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFC62828)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFC62828)) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyVaultCard(
    onImport: () -> Unit,
    onCreate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderSoft))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(PastelRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = RoseDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Your Offline Vault is Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkCharcoalText
            )

            Text(
                text = "Add documents, research, or encrypted notes to access them anytime without Internet connectivity.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCreate,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Note")
                }

                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File")
                }
            }
        }
    }
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, content: String, encrypt: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var encrypt by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Local Document",
                fontWeight = FontWeight.Bold,
                color = DarkCharcoalText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    placeholder = { Text("e.g. offline_handbook") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Type text to store locally...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = encrypt,
                        onCheckedChange = { encrypt = it },
                        colors = CheckboxDefaults.colors(checkedColor = RoseDark),
                        modifier = Modifier.testTag("note_encrypt_checkbox")
                    )
                    Column {
                        Text(
                            text = "Encrypt with Android Keystore",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkCharcoalText
                        )
                        Text(
                            text = "AES-GCM 256-bit encryption",
                            fontSize = 11.sp,
                            color = MutedSlate
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title.trim(), content, encrypt)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoseDark),
                modifier = Modifier.testTag("submit_create_note_button")
            ) {
                Text("Save to Vault")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailBottomSheet(
    file: LocalFile,
    preview: String?,
    onDismiss: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WarmIvoryBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoalText
                    )
                    Text(
                        text = file.relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Metadata card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(label = "Size", value = "${formatBytes(file.sizeBytes)} (${file.sizeBytes} bytes)")
                    DetailRow(label = "MIME Type", value = file.mimeType)
                    DetailRow(
                        label = "Encryption",
                        value = if (file.encryptionVersion > 0) "AES-GCM (Hardware Keystore)" else "Unencrypted (Direct Storage)"
                    )
                    DetailRow(label = "Status", value = file.status)
                    DetailRow(
                        label = "Created",
                        value = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.createdAt))
                    )
                    DetailRow(
                        label = "Last Accessed",
                        value = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastAccessedAt))
                    )
                    DetailRow(
                        label = "SHA-256 Hash",
                        value = file.contentHash,
                        isMonospace = true
                    )
                }
            }

            // Preview if available
            if (preview != null) {
                Text(
                    text = "Content Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoalText
                )
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Text(
                        text = preview,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = DarkCharcoalText
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onVerify,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_integrity_button")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify Hash")
                }

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_detail_button")
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isMonospace: Boolean = false) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MutedSlate,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = DarkCharcoalText,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}
