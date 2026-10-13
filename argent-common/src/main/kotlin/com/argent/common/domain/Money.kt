package com.argent.common.domain

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(18)

    fun of(value: String): BigDecimal = BigDecimal(value).stripTrailingZeros()

    fun of(value: BigDecimal): BigDecimal = value.stripTrailingZeros()

    fun max(a: BigDecimal, b: BigDecimal): BigDecimal = a.max(b)

    fun min(a: BigDecimal, b: BigDecimal): BigDecimal = a.min(b)

    fun isPositive(v: BigDecimal): Boolean = v.signum() > 0

    fun isZero(v: BigDecimal): Boolean = v.compareTo(BigDecimal.ZERO) == 0

    fun scale(v: BigDecimal, scale: Int): BigDecimal =
        v.setScale(scale, RoundingMode.HALF_UP)

    fun notional(price: BigDecimal, quantity: BigDecimal): BigDecimal = price.multiply(quantity)
}
