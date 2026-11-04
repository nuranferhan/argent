package com.argent.client.core

import java.math.RoundingMode

/** Keeps long numeric values readable without regex-based text replacement. */
object UiNumberFormatting {
    fun limitFractionDigits(
        text: String,
        maximum: Int = 6,
    ): String {
        require(maximum >= 0)
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (!text[index].isDigit()) {
                result.append(text[index++])
                continue
            }
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.' || text[index] == ',')) index++
            val scanned = text.substring(start, index)
            val valueEnd = scanned.indexOfLast { it.isDigit() } + 1
            val value = scanned.substring(0, valueEnd)
            val suffix = scanned.substring(valueEnd)
            val separator = value.indexOfAny(charArrayOf('.', ','))
            val hasOneSeparator = separator >= 0 && value.indexOfAny(charArrayOf('.', ','), separator + 1) < 0
            val fractionDigits = if (hasOneSeparator) value.length - separator - 1 else 0
            if (hasOneSeparator && fractionDigits > maximum && separator > 0 && fractionDigits > 0) {
                val normalized = value.replace(',', '.')
                val formatted =
                    normalized.toBigDecimalOrNull()
                        ?.setScale(maximum, RoundingMode.HALF_UP)
                        ?.stripTrailingZeros()
                        ?.toPlainString()
                result.append(formatted ?: value).append(suffix)
            } else {
                result.append(scanned)
            }
        }
        return result.toString()
    }
}
