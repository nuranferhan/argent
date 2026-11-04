package com.argent.client.core

/** Shared chart scale and sample labels; renderers own colors and platform-specific drawing. */
object ChartGeometry {
    data class Scale(val normalized: List<Float>, val axisValues: List<Float>, val sampleLabels: List<Int>)

    fun normalize(values: List<Float>): List<Float> = scale(values, axisSteps = 5).normalized

    fun scale(
        values: List<Float>,
        axisSteps: Int = 5,
    ): Scale {
        require(axisSteps >= 2)
        val finiteValues = values.filter(Float::isFinite)
        if (finiteValues.isEmpty()) return Scale(emptyList(), emptyList(), emptyList())
        val min = finiteValues.minOrNull() ?: return Scale(emptyList(), emptyList(), emptyList())
        val max = finiteValues.maxOrNull() ?: return Scale(emptyList(), emptyList(), emptyList())
        val padding =
            ((max - min) * 0.08f)
                .coerceAtLeast(kotlin.math.abs(max) * 0.0005f)
                .coerceAtLeast(0.01f)
        val low = min - padding
        val high = max + padding
        val spread = high - low
        val normalized = finiteValues.map { ((it - low) / spread).coerceIn(0f, 1f) }
        val axisValues = (0 until axisSteps).map { index -> high - spread * index / (axisSteps - 1) }
        val sampleLabels = listOf(0, (finiteValues.size - 1) / 2, finiteValues.size - 1).map { it + 1 }
        return Scale(normalized, axisValues, sampleLabels)
    }
}
