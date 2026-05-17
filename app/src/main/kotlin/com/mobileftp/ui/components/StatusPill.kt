package com.mobileftp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastType

enum class StatusPillState { Running, Stopped, Error, Info, Warning }

/**
 * Compact status indicator pill: colored bg + dot + label.
 * Running state pulses the dot (scale 0.8–1.0 looping 1200ms).
 */
@Composable
fun StatusPill(
    state: StatusPillState,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current

    val (accent, bg) = when (state) {
        StatusPillState.Running -> colors.AccentGreen to colors.AccentGreen.copy(alpha = 0.15f)
        StatusPillState.Stopped -> colors.TextTertiary to colors.TextTertiary.copy(alpha = 0.08f)
        StatusPillState.Error   -> colors.AccentStart to colors.AccentStart.copy(alpha = 0.15f)
        StatusPillState.Info    -> colors.AccentBlue to colors.AccentBlue.copy(alpha = 0.15f)
        StatusPillState.Warning -> colors.AccentAmber to colors.AccentAmber.copy(alpha = 0.15f)
    }

    val pulse = rememberInfiniteTransition(label = "pill-pulse")
    val pulseScale = pulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pill-pulse-scale"
    ).value

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(RaycastShapes.PillRadius))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .scale(if (state == StatusPillState.Running) pulseScale else 1f)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = label,
            style = RaycastType.LabelMono.copy(color = accent)
        )
    }
}


