package com.mobileftp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mobileftp.domain.model.SpeedSample
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.util.HumanReadableUtils

/**
 * Canvas-based area chart of last N speed samples.
 * - Filled with AccentBlue → AccentPurple gradient at 25% alpha (clipped to chart path).
 * - Stroke: AccentBlue 1.5dp.
 * - No axes labels — overlay chips show current / peak / average.
 */
@Composable
fun ThroughputGraph(
    samples: List<SpeedSample>,
    currentBps: Long,
    peakBps: Long,
    avgBps: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (samples.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val maxValue = (samples.maxOfOrNull { it.bytesPerSecond } ?: 1L)
                .coerceAtLeast(1L)
            val n = samples.size.coerceAtLeast(2)
            val stepX = w / (n - 1).coerceAtLeast(1)

            val fillPath = Path()
            val strokePath = Path()
            samples.forEachIndexed { i, sample ->
                val x = i * stepX
                val y = h - (sample.bytesPerSecond.toFloat() / maxValue.toFloat()) * h
                if (i == 0) {
                    fillPath.moveTo(0f, h)
                    fillPath.lineTo(x, y)
                    strokePath.moveTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                    strokePath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            val gradient = Brush.verticalGradient(
                colors = listOf(
                    colors.AccentBlue.copy(alpha = 0.25f),
                    colors.AccentPurple.copy(alpha = 0.05f)
                ),
                startY = 0f,
                endY = h
            )
            drawPath(path = fillPath, brush = gradient)
            drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(listOf(colors.AccentBlue, colors.AccentPurple)),
                style = Stroke(width = 3.5f)
            )
        }

        Row(
            modifier = Modifier
                .padding(RaycastSpacing.sm)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
        ) {
            ThroughputChip("CUR", HumanReadableUtils.bytesPerSecond(currentBps), colors.AccentBlue)
            ThroughputChip("PK", HumanReadableUtils.bytesPerSecond(peakBps), colors.AccentPurple)
            ThroughputChip("AVG", HumanReadableUtils.bytesPerSecond(avgBps), colors.TextSecondary)
        }
    }
}

@Composable
private fun ThroughputChip(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    val colors = LocalRaycastColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(RaycastShapes.ChipRadius))
            .background(colors.SurfaceCard)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = RaycastType.MetaMono.copy(color = colors.TextTertiary))
            Text(value, style = RaycastType.LabelMono.copy(color = accent))
        }
    }
}
