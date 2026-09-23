package com.example.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.RomanticButton
import com.example.core.ui.components.RomanticCard
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    viewModel: ProfileViewModel,
    onContinue: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Your Local Space",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = RoseDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WarmIvoryBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Privacy guarantee badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OnlineGreenSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = OnlineGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Local. No account, no cloud servers, zero tracking.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OnlineGreen,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            // Display Name
            Column {
                Text(
                    text = "What should we call you?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkCharcoalText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { viewModel.updateDisplayName(it) },
                    placeholder = { Text("e.g. Alex, Maya...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = RosePrimary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("display_name_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = RosePrimary,
                        unfocusedIndicatorColor = CardBorderSoft
                    ),
                    singleLine = true
                )
            }

            // Avatar Selection
            Column {
                Text(
                    text = "Choose your companion symbol",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkCharcoalText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.availableAvatars.forEach { (id, label) ->
                        val isSelected = uiState.selectedAvatar == id
                        val icon = when (id) {
                            "heart_rose" -> Icons.Filled.Favorite
                            "star_soft" -> Icons.Filled.Star
                            "spark_lavender" -> Icons.Filled.AutoAwesome
                            "shield_warm" -> Icons.Filled.Security
                            else -> Icons.Filled.Waves
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) RoseContainer else Color.White)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) RosePrimary else CardBorderSoft,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.selectAvatar(id) }
                                .padding(vertical = 14.dp)
                                .testTag("avatar_$id"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) RosePrimary else MutedSlate,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Storage Preference
            Column {
                Text(
                    text = "Offline Storage Allocation",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkCharcoalText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reserve space on your device for emergency knowledge and files.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MutedSlate,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    viewModel.storageOptions.forEach { option ->
                        val isSelected = uiState.storagePreference == option
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) RosePrimary else Color.White,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, CardBorderSoft) else null,
                            modifier = Modifier.clickable { viewModel.selectStoragePreference(option) }
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) Color.White else DarkCharcoalText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Preferred Language
            Column {
                Text(
                    text = "Language Preference",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = DarkCharcoalText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.languages.take(4).forEach { lang ->
                        val isSelected = uiState.preferredLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) LavenderSoft else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) LavenderAccent else CardBorderSoft
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectLanguage(lang) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) LavenderAccent else DarkCharcoalText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Submit Button
            RomanticButton(
                text = "Enter InternetStorer ♡",
                onClick = { viewModel.saveProfile(onContinue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_profile_button"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
