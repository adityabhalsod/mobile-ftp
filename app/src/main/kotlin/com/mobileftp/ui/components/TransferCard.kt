package com.mobileftp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileftp.domain.model.ChunkProgress
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.domain.model.TransferDirection
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.domain.model.TransferState
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.util.HumanReadableUtils


/**
 * Transfer progress card.
 * - Header: filename + speed + ETA
 * - Thin 2dp progress track + filled gradient
 * - Chunk bar row: micro-bars per chunk, color-coded by state
 * - Tap → expands per-chunk detail rows
 */
@Composable
fun TransferCard(
    job: TransferJob,
    chunks: List<ChunkProgress>,
    speedBps: Long,
    etaMillis: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bufferSize: Int = 0
) {
    val colors = LocalRaycastColors.current
    var expanded by remember { mutableStateOf(false) }

    RaycastCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { expanded = !expanded; onClick?.invoke() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
            ) {
                Icon(
                    imageVector = if (job.direction == TransferDirection.DOWNLOAD)
                        Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = if (job.direction == TransferDirection.DOWNLOAD) colors.AccentBlue else colors.AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = job.fileName,
                    style = RaycastType.BodyMedium.copy(color = colors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = HumanReadableUtils.bytesPerSecond(speedBps),
                    style = RaycastType.LabelMono.copy(color = colors.AccentBlue)
                )
                Text(
                    text = HumanReadableUtils.durationMillis(etaMillis),
                    style = RaycastType.MetaMono.copy(color = colors.TextSecondary)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            ProgressTrack(progress = job.progress, colors = colors)

            ChunkBars(chunks = chunks, colors = colors)

            Row(horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)) {
                Text(
                    "${HumanReadableUtils.bytes(job.transferredBytes)} / ${HumanReadableUtils.bytes(job.totalBytes)}",
                    style = RaycastType.MetaMono.copy(color = colors.TextSecondary)
                )
                if (job.resumed) {
                    Text(
                        "Resumed from ${(job.resumedFromBytes * 100 / job.totalBytes.coerceAtLeast(1L))}%",
                        style = RaycastType.MetaMono.copy(color = colors.AccentAmber)
                    )
                }
                Text(
                    job.state.name,
                    style = RaycastType.MetaMono.copy(
                        color = when (job.state) {
                            TransferState.COMPLETED -> colors.AccentGreen
                            TransferState.FAILED -> colors.AccentStart
                            TransferState.ACTIVE -> colors.AccentBlue
                            else -> colors.TextTertiary
                        }
                    )
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.xs)) {
                    chunks.forEach { chunk ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
                        ) {
                            Text(
                                "#%02d".format(chunk.index),
                                style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                            )
                            Text(
                                "${HumanReadableUtils.bytes(chunk.transferredBytes)}/${HumanReadableUtils.bytes(chunk.totalBytes)}",
                                style = RaycastType.MetaMono.copy(color = colors.TextSecondary)
                            )
                            Text(
                                HumanReadableUtils.bytesPerSecond(chunk.speedBytesPerSec),
                                style = RaycastType.MetaMono.copy(color = colors.AccentBlue)
                            )
                            Text(
                                chunk.state.name,
                                style = RaycastType.MetaMono.copy(color = chunkColor(chunk.state, colors))
                            )
                        }
                    }
                    if (bufferSize > 0) {
                        Text(
                            "buffer ${HumanReadableUtils.bytes(bufferSize.toLong())}",
                            style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ProgressTrack(
    progress: Float,
    colors: com.mobileftp.ui.theme.RaycastColorPalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(colors.BorderDefault)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Brush.horizontalGradient(listOf(colors.AccentBlue, colors.AccentPurple)))
        )
    }
}

@Composable
private fun ChunkBars(
    chunks: List<ChunkProgress>,
    colors: com.mobileftp.ui.theme.RaycastColorPalette
) {
    if (chunks.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chunks.forEach { chunk ->
            val color = chunkColor(chunk.state, colors)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.BorderDefault)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            if (chunk.state == ChunkState.DONE) 1f
                            else if (chunk.totalBytes <= 0L) 0f
                            else (chunk.transferredBytes.toFloat() / chunk.totalBytes.toFloat()).coerceIn(0f, 1f)
                        )
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(color)
                )
            }
        }
    }
}

private fun chunkColor(state: ChunkState, colors: com.mobileftp.ui.theme.RaycastColorPalette): Color =
    when (state) {
        ChunkState.PENDING -> colors.BorderDefault
        ChunkState.ACTIVE -> colors.AccentBlue
        ChunkState.DONE -> colors.AccentGreen
        ChunkState.ERROR -> colors.AccentStart
    }
