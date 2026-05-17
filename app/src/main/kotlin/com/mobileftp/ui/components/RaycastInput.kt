package com.mobileftp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType

/**
 * Raycast-style input field.
 * - SurfaceCard background, 1dp BorderDefault → BorderFocus animated on focus.
 * - 8dp radius, 48dp height.
 * - Optional label above (LabelMono TextSecondary), prefix icon, trailing slot.
 * - When [isPassword] is true, a built-in eye icon toggles visibility.
 */
@Composable
fun RaycastInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    prefixIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalRaycastColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.BorderFocus else colors.BorderDefault,
        animationSpec = tween(120),
        label = "input-border"
    )

    var passwordVisible by remember { mutableStateOf(false) }
    val visualTransformation: VisualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> VisualTransformation.None
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label.uppercase(),
                style = RaycastType.LabelMono.copy(color = colors.TextSecondary),
                modifier = Modifier.padding(bottom = RaycastSpacing.xs, start = RaycastSpacing.xs)
            )
        }

        val shape = RoundedCornerShape(RaycastShapes.InputRadius)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(colors.SurfaceCard)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = RaycastSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (prefixIcon != null) {
                Icon(
                    imageVector = prefixIcon,
                    contentDescription = null,
                    tint = colors.TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = RaycastSpacing.sm)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = RaycastType.BodyMedium.copy(color = colors.TextTertiary)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    cursorBrush = SolidColor(colors.AccentBlue),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                        imeAction = imeAction
                    ),
                    interactionSource = interactionSource,
                    textStyle = RaycastType.BodyMedium.copy(color = colors.TextPrimary)
                )
            }
            if (isPassword) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = colors.TextSecondary,
                    modifier = Modifier
                        .padding(start = RaycastSpacing.sm)
                        .size(18.dp)
                        .clickable { passwordVisible = !passwordVisible }
                )
            }
            if (trailing != null) {
                Box(
                    modifier = Modifier.padding(start = RaycastSpacing.sm)
                ) { trailing() }
            }
        }
    }
}
