package com.argent.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ChartGeometry
import java.util.Locale

@Composable
internal fun PriceChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    currentPrice: Float? = null,
) {
    val pointSnapshot = points.toList()
    val safePoints = pointSnapshot.ifEmpty { listOfNotNull(currentPrice?.takeIf(Float::isFinite)) }
    val chartScale = remember(safePoints) { ChartGeometry.scale(safePoints) }
    val axisValues = remember(safePoints) { ChartGeometry.scale(safePoints).axisValues }
    Column(modifier.background(Ink, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                axisValues.forEach {
                    Text(
                        String.format(Locale.US, "%.2f", it),
                        color = Color(0xFFBAC4D1),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                repeat(axisValues.size) { index ->
                    val y = size.height * index / (axisValues.size - 1).coerceAtLeast(1)
                    drawLine(
                        Line,
                        Offset(0f, y),
                        Offset(size.width, y),
                    )
                }
                val normalized = chartScale.normalized
                when (normalized.size) {
                    0 -> Unit
                    1 -> {
                        val y = size.height * (1f - normalized.single())
                        drawLine(
                            Gold,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 2.5f,
                        )
                    }
                    else -> {
                        val path = Path()
                        normalized.forEachIndexed { index, value ->
                            val x = size.width * index / (normalized.size - 1)
                            val y = size.height * (1f - value)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, Gold, style = Stroke(width = 2.5f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 72.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            chartScale.sampleLabels.forEach { label ->
                Text(
                    label.toString(),
                    color = Muted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
