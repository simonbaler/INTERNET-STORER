package com.example.features.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.components.RomanticHeartBreathing
import com.example.ui.theme.DarkCharcoalText
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoseDark

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val statusText by viewModel.statusText.collectAsState()
    val navTarget by viewModel.navTarget.collectAsState()

    var elementsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        elementsVisible = true
    }

    LaunchedEffect(navTarget) {
        when (navTarget) {
            SplashNavTarget.Onboarding -> onNavigateToOnboarding()
            SplashNavTarget.Main -> onNavigateToMain()
            SplashNavTarget.Idle -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF7F9),
                        Color(0xFFFFF0F3),
                        Color(0xFFF9EBF0)
                    )
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Romantic glowing heart with orbiting motes
            RomanticHeartBreathing(
                size = 110.dp,
                modifier = Modifier.testTag("splash_heart_icon")
            )

            Spacer(modifier = Modifier.height(36.dp))

            AnimatedVisibility(
                visible = elementsVisible,
                enter = fadeIn(tween(700)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(700)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "InternetStorer",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = RoseDark,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Your digital world, wherever you are.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = DarkCharcoalText.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(54.dp))

            // Real local initialization status
            AnimatedVisibility(
                visible = elementsVisible,
                enter = fadeIn(tween(900))
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MutedSlate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    modifier = Modifier.testTag("splash_status_text")
                )
            }
        }
    }
}
