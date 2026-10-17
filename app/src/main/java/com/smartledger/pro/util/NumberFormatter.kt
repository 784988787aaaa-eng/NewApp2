package com.smartledger.pro.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * أداة موحدة لتنسيق وتحليل الأرقام والعملات والتواريخ بالأرقام الغربية (123) حصراً،
 * مع سياسة صارمة للتمييز بين فواصل الآلاف (`12,500` -> `12500`) والفواصل العشرية (`12.5` / `12٫5`)،
 * وضبط الدقة المحاسبية للمبالغ المالية عبر BigDecimal (HALF_UP).
 */
object NumberFormatter {

    private val usSymbols = DecimalFormatSymbols(Locale.US)
    private val twoDecimalsFormat = DecimalFormat("#,##0.00", usSymbols)
    private val zeroDecimalsFormat = DecimalFormat("#,##0", usSymbols)
    private val cleanQuantityFormat = DecimalFormat("#,##0.##", usSymbols)

    // نمط قياسي لفواصل الآلاف مثل 12,500 أو 1,250,000 أو 12,500.75
    private val thousandsGroupedPattern = Regex("""^-?\d{1,3}(?:,\d{3})+(?:\.\d+)?$""")

    /**
     * تقريب محاسبي صارم للمبالغ المالية (منزلتان عشريتان بنظام HALF_UP)
     */
    fun roundCurrency(value: Double, scale: Int = 2): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(scale.coerceIn(0, 4), RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * تحويل أي نص يحتوي على أرقام شرقية (٠-٩) أو فارسية (۰-۹) إلى أرقام غربية قياسية (0-9)
     */
    fun toWesternDigits(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * تطبيع النص الرقمي المدخل من المستخدم مع التمييز الدقيق بين فاصل الآلاف والفاصل العشري:
     * - `12,500` أو `1,250,000` -> تُعامل الفاصلة كفاصل آلاف وتصبح `12500` و `1250000`.
     * - `12,500.75` -> تُحذف فواصل الآلاف وتبقى النقطة العشرية `12500.75`.
     * - `12٫75` أو `12.75` أو `12,75` (منزلة أو منزلتان بعد الفاصلة المنفردة) -> تُعامل كفاصل عشري `12.75`.
     */
    fun normalizeNumericInput(input: String, allowDecimal: Boolean = true, allowNegative: Boolean = false): String {
        if (input.isEmpty()) return ""

        // 1. تحويل الأرقام الشرقية وتوحيد الرموز الأولية
        val western = toWesternDigits(input.trim())
            .replace("\u066C", "") // حذف فاصل الآلاف العربي (٬)
            .replace(" ", "")
            .replace("_", "")
            .replace('\u066B', '.') // تحويل الفاصل العشري العربي (٫) إلى نقطة
            .replace('،', ',')      // توحيد الفاصلة العربية مع الفاصلة الغربية لمعالجتها سياقياً

        // 2. معالجة الفاصلة `,` حسب السياق العددي
        val resolvedCommas = when {
            // إذا كان النص يطابق تجميع الآلاف القياسي (مثل 12,500 أو 1,250,000.50) فإن `,` هي فاصل آلاف قطعاً
            thousandsGroupedPattern.matches(western) -> western.replace(",", "")
            // إذا وجد كل من `,` و `.` في النص، فإن الرمز الذي يظهر أولاً هو فاصل آلاف
            western.contains(',') && western.contains('.') -> {
                if (western.lastIndexOf(',') < western.lastIndexOf('.')) {
                    western.replace(",", "")
                } else {
                    western.replace(".", "").replace(',', '.')
                }
            }
            // إذا وجدت أكثر من فاصلة واحدة (مثل 1,000,000) فهي فواصل آلاف
            western.count { it == ',' } > 1 -> western.replace(",", "")
            // إذا وجدت فاصلة منفردة متبوعة بـ 3 أرقام بالضبط والجزء الصحيح من 1 إلى 3 أرقام غير صفرية (مثل 12,500) فهي فاصل آلاف
            western.contains(',') -> {
                val parts = western.split(',')
                val intPart = parts[0].removePrefix("-")
                val fracPart = parts.getOrNull(1) ?: ""
                if (fracPart.length == 3 && intPart.length in 1..3 && intPart != "0") {
                    western.replace(",", "")
                } else {
                    western.replace(',', '.')
                }
            }
            else -> western
        }

        val sb = StringBuilder(resolvedCommas.length)
        var hasDot = false
        for (ch in resolvedCommas) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                '.' -> if (allowDecimal && !hasDot) {
                    hasDot = true
                    sb.append('.')
                }
                '-' -> if (allowNegative && sb.isEmpty()) sb.append('-')
            }
        }
        return sb.toString()
    }

    /**
     * تحليل صارم للنص الرقمي يعيد `null` إذا كان النص فارغاً أو غير صالح أو ملتبساً
     * (بدلاً من إعادة 0.0 بصمت في المسارات التي تتطلب التحقق من صحة الإدخال).
     */
    fun parseDoubleOrNull(input: String, allowNegative: Boolean = false): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val western = toWesternDigits(trimmed)
            .replace("\u066C", "")
            .replace(" ", "")
            .replace("_", "")
            .replace('\u066B', '.')
            .replace('،', ',')

        // رفض النصوص التي تحتوي على أكثر من علامة عشرية صريحة مثل "12.5.3"
        if (western.count { it == '.' } > 1) return null

        val normalized = normalizeNumericInput(trimmed, allowDecimal = true, allowNegative = allowNegative)
        if (normalized.isEmpty() || normalized == "-" || normalized == ".") return null
        val parsed = normalized.toDoubleOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite()) return null
        return roundCurrency(parsed, 4)
    }

    /**
     * تحويل نص رقمي إلى قيمة عشرية مقربة محاسبياً مع قيمة بديلة عند الفراغ
     */
    fun parseDouble(input: String, default: Double = 0.0): Double {
        return parseDoubleOrNull(input, allowNegative = true) ?: default
    }

    /**
     * تنسيق مبلغ مالي مع رمز العملة وعدد المنازل العشرية
     */
    fun formatCurrency(
        amount: Double,
        currencySymbol: String = "ر.ي",
        decimalPlaces: Int = 2,
        privacyMode: Boolean = false,
        showSign: Boolean = false
    ): String {
        if (privacyMode) return "••••••"
        val roundedAmount = roundCurrency(amount, 2)
        val absVal = abs(roundedAmount)
        val formatted = if (decimalPlaces <= 0 || abs(absVal - absVal.roundToLong()) < 0.005) {
            zeroDecimalsFormat.format(absVal)
        } else {
            twoDecimalsFormat.format(absVal)
        }
        val signPrefix = when {
            roundedAmount < -0.001 -> "-"
            showSign && roundedAmount > 0.001 -> "+"
            else -> ""
        }
        return if (currencySymbol.isBlank()) {
            "$signPrefix$formatted"
        } else {
            "$signPrefix$formatted $currencySymbol"
        }
    }

    /**
     * تنسيق رقم بدون رمز عملة
     */
    fun formatAmount(amount: Double, decimalPlaces: Int = 2): String {
        val roundedAmount = roundCurrency(amount, 2)
        val absVal = abs(roundedAmount)
        val formatted = if (decimalPlaces <= 0 || abs(absVal - absVal.roundToLong()) < 0.005) {
            zeroDecimalsFormat.format(absVal)
        } else {
            twoDecimalsFormat.format(absVal)
        }
        val sign = if (roundedAmount < -0.001) "-" else ""
        return "$sign$formatted"
    }

    /**
     * تنسيق الكميات المخزنية بدون أصفار عشرية زائدة
     */
    fun formatQuantity(qty: Double): String {
        return cleanQuantityFormat.format(roundCurrency(qty, 3))
    }

    /**
     * تنسيق حجم الملف بالبايت إلى نص مقروء (KB / MB) بالأرقام الغربية
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.1f KB", kb)
        } else {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        }
    }

    fun nowTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
