package com.argent.client.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ChartGeometryTest : FunSpec({
    test("normalizes a varying price series into chart bounds") {
        val result = ChartGeometry.normalize(listOf(10f, 15f, 20f))
        result shouldHaveSize 3
        result.zipWithNext().all { (left, right) -> left < right } shouldBe true
        result.all { it in 0f..1f } shouldBe true
    }

    test("flat and empty series remain finite and drawable") {
        ChartGeometry.normalize(emptyList()) shouldBe emptyList()
        ChartGeometry.normalize(listOf(7f, 7f, 7f)) shouldBe listOf(0.5f, 0.5f, 0.5f)
    }

    test("desktop and Android share the same price axis and sample positions") {
        val scale = ChartGeometry.scale(listOf(10f, 15f, 20f, 12f))
        scale.axisValues shouldHaveSize 5
        scale.sampleLabels shouldBe listOf(1, 2, 4)
        scale.normalized shouldHaveSize 4
        scale.axisValues.zipWithNext().all { (upper, lower) -> upper > lower } shouldBe true
    }
})
