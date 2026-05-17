package com.mobileftp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastMotion
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastType

enum class RaycastButtonStyle { Primary, Secondary, Destructive, Ghost }

/**
 * Universal Raycast button — Primary (white pill), Secondary (transparent), Destructive (flame).
 * Press → scale 0.97 spring animation.
 *
 * Leading/trailing slots inherit the button's foreground via LocalContentColor,
 * so passing a plain Icon() without an explicit tint will render correctly.
 */
@Composable
fun RaycastButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RaycastButtonStyle = RaycastButtonStyle.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalRaycastColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = RaycastMotion.SpringBounce,
        label = "btn-scale"
    )

    val (bg, fg, borderColor) = when (style) {
        RaycastButtonStyle.Primary ->
            Triple(colors.PrimaryButton, colors.PrimaryButtonText, Color.Transparent)
        RaycastButtonStyle.Secondary ->
            Triple(Color.Transparent, colors.TextSecondary, colors.BorderDefault)
        RaycastButtonStyle.Destructive ->
            Triple(colors.AccentStart.copy(alpha = 0.15f), colors.AccentStart, colors.AccentStart.copy(alpha = 0.4f))
        RaycastButtonStyle.Ghost ->
            Triple(Color.Transparent, colors.TextPrimary, Color.Transparent)
    }

    // Pill shape for Primary; rounded rectangle for everything else.
    val shape = if (style == RaycastButtonStyle.Primary)
        RoundedCornerShape(RaycastShapes.PillRadius) else RoundedCornerShape(RaycastShapes.ButtonRadius)
    val padding = if (style == RaycastButtonStyle.Primary)
        PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    else PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    val effectiveFg = if (enabled) fg else fg.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = fg),
                onClick = onClick
            )
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        // Provide the button's foreground color so leading/trailing icons inherit it.
        CompositionLocalProvider(LocalContentColor provides effectiveFg) {
            if (leading != null) {
                Row(
                    modifier = Modifier.size(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) { leading() }
            }
            Text(
                text = text,
                style = RaycastType.TitleSmall.copy(
                    color = effectiveFg,
                    fontWeight = if (style == RaycastButtonStyle.Primary || style == RaycastButtonStyle.Destructive)
                        androidx.compose.ui.text.font.FontWeight.SemiBold
                    else androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            if (trailing != null) {
                Row(
                    modifier = Modifier.size(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) { trailing() }
            }
        }
    }
}
