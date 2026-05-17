package com.mobileftp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileftp.domain.model.RemoteFile
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.util.HumanReadableUtils


/**
 * Single file/folder row in the FTP browser.
 * - 52dp height, divider at bottom
 * - Folder icon → AccentAmber, file → TextSecondary
 * - Selected → SurfaceOverlay fill animated 120ms
 * - Long-press → invokes onLongPress (parent shows context sheet)
 * - Press → scale 0.98 spring
 */
@Composable
fun FileBrowserRow(
    file: RemoteFile,
    selected: Boolean = false,
    multiSelectMode: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "row-scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.SurfaceOverlay else colors.Surface,
        animationSpec = tween(120),
        label = "row-bg"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = RaycastSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.Check else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (selected) colors.AccentBlue else colors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) colors.AccentAmber else colors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = file.name,
                    style = RaycastType.BodyMedium.copy(color = colors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.permissions,
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary),
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!file.isDirectory) {
                    Text(
                        text = HumanReadableUtils.bytes(file.size),
                        style = RaycastType.LabelMono.copy(color = colors.TextSecondary)
                    )
                }
                Text(
                    text = HumanReadableUtils.relative(file.modifiedTimestamp),
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                )
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.BorderSubtle)
        )
    }
}
