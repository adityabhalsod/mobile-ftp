package com.mobileftp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType

/**
 * Settings list row.
 * - 52dp height, label + trailing slot
 * - Bottom hairline divider (BorderSubtle)
 * - Used as a row inside a RaycastCard
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val colors = LocalRaycastColors.current
    Column(
        modifier = modifier.fillMaxWidth().let { mod ->
            if (onClick != null) mod.clickable { onClick() } else mod
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (description == null) 52.dp else 64.dp)
                .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = RaycastType.BodyMedium.copy(color = colors.TextPrimary))
                if (description != null) {
                    Text(description, style = RaycastType.MetaMono.copy(color = colors.TextSecondary))
                }
            }
            trailing()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.BorderSubtle)
        )
    }
}

@Composable
fun SettingGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current
    Text(
        title.uppercase(),
        style = RaycastType.LabelMono.copy(color = colors.TextSecondary),
        modifier = modifier.padding(
            top = RaycastSpacing.md,
            bottom = RaycastSpacing.xs,
            start = RaycastSpacing.lg
        )
    )
}

@Composable
fun RaycastSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalRaycastColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.PrimaryButton,
            checkedTrackColor = colors.AccentBlue,
            checkedBorderColor = colors.AccentBlue,
            uncheckedThumbColor = colors.TextSecondary,
            uncheckedTrackColor = colors.SurfaceCard,
            uncheckedBorderColor = colors.BorderDefault
        )
    )
}
