package com.zaxo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaxo.app.ui.theme.ZaxoTheme

/**
 * StatusRing – Circular avatar with a colored ring border indicating status state.
 *
 * Ring logic:
 *   • Green ring (#27AE60, 3 dp stroke) → contact has unviewed active statuses
 *   • Gray ring  (#C8CDD4, 3 dp stroke) → all statuses viewed by current user
 *   • No ring                         → no active statuses
 *
 * The ring is drawn via Canvas, then [NeuAvatar] is placed inside with padding
 * so the avatar sits comfortably within the ring.
 */
@Composable
fun StatusRing(
    photoUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    hasActiveStatus: Boolean = false,
    hasUnviewedStatus: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = ZaxoTheme.colors
    val ringStrokeWidth = 3.dp
    val ringGapWidth = 3.dp          // gap between ring and avatar
    val density = LocalDensity.current

    // Ring colour depends on view state
    val ringColor = when {
        hasActiveStatus && hasUnviewedStatus -> Color(0xFF27AE60) // green – unviewed
        hasActiveStatus && !hasUnviewedStatus -> Color(0xFFC8CDD4) // gray – all viewed
        else -> Color.Transparent                                   // no active status
    }

    val showRing = hasActiveStatus

    // Total outer size includes ring stroke + gap on each side + avatar
    val totalSize = if (showRing) {
        size + (ringStrokeWidth + ringGapWidth) * 2
    } else {
        size
    }

    val avatarSize = if (showRing) {
        size
    } else {
        size
    }

    Box(
        modifier = modifier
            .size(totalSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Draw ring via Canvas
        if (showRing) {
            val strokePx = with(density) { ringStrokeWidth.toPx() }
            val totalPx = with(density) { totalSize.toPx() }

            Canvas(modifier = Modifier.size(totalSize)) {
                val halfStroke = strokePx / 2f
                drawArc(
                    color = ringColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(totalPx - strokePx, totalPx - strokePx),
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        // Avatar sits inside the ring with appropriate padding
        val innerPadding = if (showRing) ringStrokeWidth + ringGapWidth else 0.dp
        Box(
            modifier = Modifier
                .size(avatarSize)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            NeuAvatar(
                photoUrl = photoUrl,
                name = name,
                size = avatarSize - innerPadding * 2,
                isOnline = false
            )
        }
    }
}
