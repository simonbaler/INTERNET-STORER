package com.example.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.PhaseBadge
import com.example.core.ui.components.RomanticButton
import com.example.core.ui.components.RomanticHeartBreathing
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.WarmIvoryBackground

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit
) {
    val currentIndex by viewModel.currentSlideIndex.collectAsState()
    val slide = viewModel.slides[currentIndex]
    val isLast = currentIndex == viewModel.slides.size - 1

    Scaffold(
        containerColor = WarmIvoryBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhaseBadge(
                    phaseText = slide.badge,
                    isCurrent = slide.id == "ready" || slide.id == "welcome"
                )

                if (!isLast) {
                    TextButton(
                        onClick = { viewModel.completeOnboarding(onFinishOnboarding) },
                        modifier = Modifier.testTag("skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            color = MutedSlate,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Center Romantic Visual Graphic
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                RomanticHeartBreathing(
                    size = 120.dp,
                    modifier = Modifier.testTag("onboarding_illustration")
                )
            }

            // Slide text content with animated transition
            AnimatedContent(
                targetState = slide,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "slide_transition",
                modifier = Modifier.fillMaxWidth()
            ) { targetSlide ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = targetSlide.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = RoseDark,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.testTag("slide_title")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = targetSlide.description,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = DarkCharcoalText.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        ),
                        modifier = Modifier.testTag("slide_description")
                    )

                    if (targetSlide.note != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF3EAF8))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = targetSlide.note,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = LavenderAccent,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Bottom controls: Indicators and Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    viewModel.slides.indices.forEach { index ->
                        val isSelected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 22.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) RosePrimary else Color(0xFFE2D6DA))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                RomanticButton(
                    text = if (isLast) "Get Started" else "Next",
                    onClick = { viewModel.nextSlide(onFinishOnboarding) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
