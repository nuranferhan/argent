package com.argent.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.argent.client.core.ChartGeometry

/** Desktop renderer for the shared chart geometry. */
@Composable
internal fun SharedPricePlot(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    gridColor: Color,
    fillColor: Color? = null,
) {
    val pointSnapshot = points.toList()
    val scale = remember(pointSnapshot) { ChartGeometry.scale(pointSnapshot) }
    Canvas(modifier) {
        repeat(scale.axisValues.size) { index ->
            val y = size.height * index / (scale.axisValues.size - 1).coerceAtLeast(1)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        when (scale.normalized.size) {
            0 -> Unit
            1 -> {
                val y = size.height * (1f - scale.normalized.single())
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5f)
            }
            else -> {
                val line = Path()
                val area = Path()
                scale.normalized.forEachIndexed { index, value ->
                    val x = size.width * index / (scale.normalized.size - 1)
                    val y = size.height * (1f - value)
                    if (index == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, size.height)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                fillColor?.let { color ->
                    area.lineTo(size.width, size.height)
                    area.close()
                    drawPath(area, color.copy(alpha = 0.16f))
                }
                drawPath(line, lineColor, style = Stroke(width = 2.5f))
            }
        }
    }
}
