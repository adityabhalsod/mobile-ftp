package com.mobileftp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastSpacing

/**
 * Raycast-style card surface.
 * - SurfaceElevated fill, hairline 1dp BorderDefault stroke.
 * - 10dp corner radius, 16dp padding.
 * - No drop shadow — elevation communicated by surface color step alone.
 * - Hover/Press → SurfaceOverlay fill animated 120ms.
 */
@Composable
fun RaycastCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(RaycastSpacing.lg),
    border: Boolean = true,
    background: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalRaycastColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val baseColor = background ?: colors.SurfaceElevated
    val targetBg = when {
        selected || pressed -> colors.SurfaceOverlay
        else -> baseColor
    }
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(120),
        label = "card-bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.BorderFocus else colors.BorderDefault,
        animationSpec = tween(120),
        label = "card-border"
    )

    val shape = RoundedCornerShape(RaycastShapes.CardRadius)
    val baseModifier = modifier
        .clip(shape)
        .background(animatedBg)
        .let { if (border) it.border(1.dp, borderColor, shape) else it }

    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else baseModifier

    Box(modifier = clickableModifier.padding(paddingValues)) { content() }
}
