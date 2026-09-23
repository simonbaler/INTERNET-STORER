package com.example.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.connectivity.ConnectivityInfo
import com.example.core.connectivity.ConnectivityStatus
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.CardGlowGradient
import com.example.ui.theme.LimitedOrange
import com.example.ui.theme.LimitedOrangeSoft
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.OfflineRose
import com.example.ui.theme.OfflineRoseSoft
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OnlineGreenSoft
import com.example.ui.theme.RoseDark
import com.example.ui.theme.RosePrimary
import com.example.ui.theme.RoseSoft
import kotlin.math.cos
import kotlin.math.sin

/**
 * Romantic Heart Breathing Animation with subtle ambient glowing particles
 */
@Composable
fun RomanticHeartBreathing(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    reducedMotion: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
    val pulseScale by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    }

    val particleRotation by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particles"
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Romantic pulsing heart symbol" },
        contentAlignment = Alignment.Center
    ) {
        // Ambient particle glow canvas
        Canvas(modifier = Modifier.size(size * 1.5f)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2.6f

            // Orbiting ambient glowing motes
            for (i in 0 until 5) {
                val angleRad = Math.toRadians((particleRotation + i * 72.0)).toFloat()
                val particleX = center.x + radius * cos(angleRad)
                val particleY = center.y + radius * sin(angleRad)
                drawCircle(
                    color = Color(0x55FF758F),
                    radius = 4.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            }

            // Soft radial aura behind heart
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35FF758F), Color(0x10C77DFF), Color.Transparent),
                    center = center,
                    radius = radius * 1.3f
                ),
                radius = radius * 1.2f,
                center = center
            )
        }

        // Heart Icon in center
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFECEF), Color(0xFFFFF0F5))
                    )
                )
                .border(1.5.dp, Color(0x40FF758F), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = RosePrimary,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

/**
 * Connectivity Pill showing Live Network State
 */
@Composable
fun ConnectivityPill(
    connectivityInfo: ConnectivityInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (bgColor, textColor, icon, label) = when (connectivityInfo.status) {
        ConnectivityStatus.ONLINE -> Quadruple(
            OnlineGreenSoft,
            OnlineGreen,
            Icons.Filled.Wifi,
            "Connected ♡"
        )
        ConnectivityStatus.LIMITED -> Quadruple(
            LimitedOrangeSoft,
            LimitedOrange,
            Icons.Filled.Info,
            "Connection is limited"
        )
        ConnectivityStatus.OFFLINE -> Quadruple(
            OfflineRoseSoft,
            OfflineRose,
            Icons.Filled.WifiOff,
            "Offline, but you're still okay ♡"
        )
        ConnectivityStatus.UNKNOWN -> Quadruple(
            Color(0xFFF3F3F5),
            MutedSlate,
            Icons.Outlined.CloudOff,
            "Checking status..."
        )
    }

    val clickableMod = if (onClick != null) {
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .testTag("connectivity_pill")
            .then(clickableMod),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Phase Badge indicating current or future phase
 */
@Composable
fun PhaseBadge(
    phaseText: String,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false
) {
    val bg = if (isCurrent) Color(0xFFFFEEF2) else Color(0xFFF5F0F8)
    val text = if (isCurrent) RoseDark else Color(0xFF6B4E82)
    val border = if (isCurrent) Color(0x55FF758F) else Color(0x35C77DFF)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = phaseText,
            color = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Rounded Romantic Card container
 */
@Composable
fun RomanticCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardMod = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardMod
            .fillMaxWidth()
            .border(1.dp, CardBorderSoft, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .background(CardGlowGradient)
                .padding(20.dp)
        ) {
            content()
        }
    }
}

/**
 * Romantic Micro-interaction Pill Button
 */
@Composable
fun RomanticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.96f else 1f

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(52.dp)
            .testTag("action_button_${text.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(26.dp),
        color = if (enabled) RosePrimary else Color(0xFFE4D6D9),
        contentColor = Color.White,
        shadowElevation = if (enabled) 3.dp else 0.dp,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = Color.White
            )
        }
    }
}

/**
 * Circular Offline Readiness Gauge
 */
@Composable
fun OfflineReadinessGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    val progress = (score / 100f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = this.size.width - strokeWidth

            // Background Track
            drawArc(
                color = Color(0xFFFFEEF2),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
            )

            // Active Progress
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF758F),
                        Color(0xFFFFA07A),
                        Color(0xFFC77DFF),
                        Color(0xFFFF758F)
                    )
                ),
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = RoseDark
                )
            )
            Text(
                text = "READY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = MutedSlate
                )
            )
        }
    }
}
