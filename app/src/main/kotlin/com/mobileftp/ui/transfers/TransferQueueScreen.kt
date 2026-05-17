package com.mobileftp.ui.transfers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.domain.model.ChunkProgress
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.ui.components.RaycastButton
import com.mobileftp.ui.components.RaycastButtonStyle
import com.mobileftp.ui.components.TransferCard
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.util.HumanReadableUtils

@Composable
fun TransferQueueScreen(
    viewModel: TransferQueueViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalRaycastColors.current

    val totalActive = state.active.size
    val totalSpeed = remember(state.active) {
        // Naive estimate: sum chunk speed across active jobs (best signal we have here)
        state.active.sumOf { _ -> 0L }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Surface),
        contentPadding = PaddingValues(RaycastSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Transfers", style = RaycastType.DisplayLarge.copy(color = colors.TextPrimary))
                    Text("Active queue", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.AccentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "$totalActive active · ${HumanReadableUtils.bytesPerSecond(totalSpeed)}",
                        style = RaycastType.LabelMono.copy(color = colors.AccentBlue)
                    )
                }
            }
        }

        if (state.active.isEmpty() && state.pending.isEmpty() && state.completed.isEmpty()) {
            item { EmptyState() }
        }

        if (state.active.isNotEmpty()) {
            item { SectionHeader("ACTIVE", state.active.size) }
            items(state.active, key = { it.id }) { job ->
                TransferRow(job = job, viewModel = viewModel, isActive = true)
            }
        }

        if (state.pending.isNotEmpty()) {
            item { SectionHeader("PENDING", state.pending.size) }
            items(state.pending, key = { it.id }) { job ->
                TransferRow(job = job, viewModel = viewModel, isActive = false)
            }
        }

        if (state.completed.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("COMPLETED", state.completed.size)
                    RaycastButton(
                        text = "Clear",
                        onClick = { viewModel.clearFinished() },
                        style = RaycastButtonStyle.Secondary
                    )
                }
            }
            items(state.completed, key = { it.id }) { job ->
                TransferRow(job = job, viewModel = viewModel, isActive = false)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    val colors = LocalRaycastColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = RaycastSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
        Text("$count", style = RaycastType.LabelMono.copy(color = colors.TextTertiary))
    }
}

@Composable
private fun TransferRow(
    job: TransferJob,
    viewModel: TransferQueueViewModel,
    isActive: Boolean
) {
    val chunks by viewModel.observeChunks(job.id).collectAsState(initial = emptyList())
    val chunkProgress = chunks.map {
        ChunkProgress(
            index = it.index,
            state = it.state,
            transferredBytes = it.transferredBytes,
            totalBytes = it.endOffset - it.startOffset,
            speedBytesPerSec = it.speedBytesPerSec
        )
    }
    val totalSpeed = chunkProgress.filter { it.state == ChunkState.ACTIVE }.sumOf { it.speedBytesPerSec }
    val eta = if (totalSpeed > 0L) {
        val remaining = (job.totalBytes - job.transferredBytes).coerceAtLeast(0L)
        (remaining * 1000L) / totalSpeed
    } else 0L
    Column {
        TransferCard(
            job = job,
            chunks = chunkProgress,
            speedBps = totalSpeed,
            etaMillis = eta
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = RaycastSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isActive) {
                RaycastButton(
                    text = "Cancel",
                    onClick = { viewModel.cancel(job.id) },
                    style = RaycastButtonStyle.Destructive
                )
            } else if (job.state == com.mobileftp.domain.model.TransferState.FAILED ||
                job.state == com.mobileftp.domain.model.TransferState.PAUSED) {
                RaycastButton(
                    text = "Retry",
                    onClick = { viewModel.retry(job.id) },
                    style = RaycastButtonStyle.Primary
                )
            }
            RaycastButton(
                text = "Remove",
                onClick = { viewModel.delete(job.id) },
                style = RaycastButtonStyle.Secondary
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = LocalRaycastColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = colors.AccentStart,
            modifier = Modifier.size(32.dp)
        )
        Text("No transfers yet", style = RaycastType.BodyMedium.copy(color = colors.TextSecondary))
        Text(
            "Start one by browsing files in the Client tab",
            style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
        )
    }
}
