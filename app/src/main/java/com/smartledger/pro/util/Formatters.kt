package com.smartledger.pro.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Formatters {

    private val usSymbols = DecimalFormatSymbols(Locale.US)

    fun normalizeDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                '٫', '،' -> sb.append('.')
                '٬' -> { }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun sanitizeDecimalInput(input: String, allowNegative: Boolean = false): String {
        val normalized = normalizeDigits(input)
        val sb = StringBuilder()
        var hasDot = false
        for ((idx, ch) in normalized.withIndex()) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch == '.' && !hasDot -> {
                    if (sb.isEmpty()) sb.append('0')
                    sb.append('.')
                    hasDot = true
                }
                ch == '-' && allowNegative && idx == 0 -> sb.append('-')
            }
        }
        return sb.toString()
    }

    fun sanitizeIntegerInput(input: String): String {
        return normalizeDigits(input).filter { it in '0'..'9' }
    }

    fun parseDoubleSafe(input: String, default: Double = 0.0): Double {
        val cleaned = normalizeDigits(input).replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: default
    }

    fun roundAmount(value: Double, decimals: Int = 2): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(decimals.coerceIn(0, 4), RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatCurrency(
        amount: Double,
        currencySymbol: String = "ر.ي",
        decimals: Int = 2,
        privacyMode: Boolean = false,
        showTrailingZeros: Boolean = false
    ): String {
        if (privacyMode) return "••••••"
        val formattedNumber = formatAmountOnly(amount, decimals, showTrailingZeros)
        return if (currencySymbol.isBlank()) formattedNumber else "$formattedNumber $currencySymbol"
    }

    fun formatAmountOnly(
        amount: Double,
        decimals: Int = 2,
        showTrailingZeros: Boolean = false
    ): String {
        val safeVal = roundAmount(amount, decimals)
        val roundedLong = safeVal.roundToLong()
        val isWholeNumber = abs(safeVal - roundedLong) < 0.0001
        val pattern = when {
            decimals <= 0 || (isWholeNumber && !showTrailingZeros) -> "#,##0"
            showTrailingZeros -> "#,##0." + "0".repeat(decimals.coerceIn(1, 4))
            else -> "#,##0." + "#".repeat(decimals.coerceIn(1, 4))
        }
        val df = DecimalFormat(pattern, usSymbols)
        return df.format(safeVal)
    }

    fun formatQuantity(qty: Double): String {
        val rounded = qty.roundToLong()
        return if (abs(qty - rounded) < 0.001) {
            String.format(Locale.US, "%d", rounded)
        } else {
            val df = DecimalFormat("#,##0.##", usSymbols)
            df.format(qty)
        }
    }

    fun nowTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun startOfCurrentWeekDate(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun startOfCurrentMonthDate(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun startOfCurrentYearDate(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) String.format(Locale.US, "%.1f KB", kb)
        else String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }
}
