package com.mobileftp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType

/**
 * Horizontal breadcrumb path bar — tap any segment to navigate back.
 * Active segment uses TextPrimary SemiBold; inactive segments TextSecondary.
 */
@Composable
fun BreadcrumbBar(
    path: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current
    val segments = remember(path) { buildSegments(path) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        segments.forEachIndexed { index, seg ->
            val isLast = index == segments.lastIndex
            Text(
                text = seg.label,
                style = RaycastType.TitleSmall.copy(
                    color = if (isLast) colors.TextPrimary else colors.TextSecondary,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium
                ),
                modifier = Modifier.clickable { onNavigate(seg.path) }
            )
            if (!isLast) {
                Text(
                    "›",
                    style = RaycastType.TitleSmall.copy(color = colors.TextTertiary)
                )
            }
        }
    }
}

private data class Segment(val label: String, val path: String)

private fun buildSegments(path: String): List<Segment> {
    val cleaned = if (path.isBlank()) "/" else path
    if (cleaned == "/") return listOf(Segment("/", "/"))
    val parts = cleaned.trim('/').split("/").filter { it.isNotEmpty() }
    val result = mutableListOf(Segment("/", "/"))
    var current = ""
    for (p in parts) {
        current += "/$p"
        result += Segment(p, current)
    }
    return result
}
