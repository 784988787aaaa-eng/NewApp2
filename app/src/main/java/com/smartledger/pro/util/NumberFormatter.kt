package com.smartledger.pro.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * أداة موحدة لتنسيق الأرقام والعملات والتواريخ بالأرقام الغربية (123) حصراً
 * وتحويل أي أرقام عربية شرقية (٠-٩) أو فارسية (۰-۹) مدخلة من لوحة المفاتيح إلى أرقام غربية قياسية.
 */
object NumberFormatter {

    private val usSymbols = DecimalFormatSymbols(Locale.US)
    private val twoDecimalsFormat = DecimalFormat("#,##0.00", usSymbols)
    private val zeroDecimalsFormat = DecimalFormat("#,##0", usSymbols)
    private val cleanQuantityFormat = DecimalFormat("#,##0.##", usSymbols)

    /**
     * تطبيع النص الرقمي المدخل من المستخدم لتحويل الأرقام الهندية/الشرقية والفواصل العربية إلى أرقام غربية قياسية
     */
    fun normalizeNumericInput(input: String, allowDecimal: Boolean = true, allowNegative: Boolean = false): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        var hasDot = false
        for (i in input.indices) {
            val ch = input[i]
            val mapped: Char? = when (ch) {
                in '0'..'9' -> ch
                in '\u0660'..'\u0669' -> ('0'.code + (ch.code - '\u0660'.code)).toChar()
                in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - '\u06F0'.code)).toChar()
                '.', ',', '٫', '،' -> if (allowDecimal && !hasDot) {
                    hasDot = true
                    '.'
                } else null
                '-' -> if (allowNegative && sb.isEmpty()) '-' else null
                else -> null
            }
            if (mapped != null) {
                sb.append(mapped)
            }
        }
        return sb.toString()
    }

    /**
     * تحويل أي نص عام قد يحتوي على أرقام شرقية إلى أرقام غربية (123)
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
     * تحويل نص رقمي إلى قيمة عشرية بأمان
     */
    fun parseDouble(input: String, default: Double = 0.0): Double {
        val clean = normalizeNumericInput(input.trim(), allowDecimal = true, allowNegative = true)
        return clean.toDoubleOrNull() ?: default
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
        val absVal = abs(amount)
        val formatted = if (decimalPlaces <= 0 || abs(absVal - absVal.roundToLong()) < 0.005) {
            zeroDecimalsFormat.format(absVal)
        } else {
            twoDecimalsFormat.format(absVal)
        }
        val signPrefix = when {
            amount < -0.001 -> "-"
            showSign && amount > 0.001 -> "+"
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
        val absVal = abs(amount)
        val formatted = if (decimalPlaces <= 0 || abs(absVal - absVal.roundToLong()) < 0.005) {
            zeroDecimalsFormat.format(absVal)
        } else {
            twoDecimalsFormat.format(absVal)
        }
        val sign = if (amount < -0.001) "-" else ""
        return "$sign$formatted"
    }

    /**
     * تنسيق الكميات المخزنية بدون أصفار عشرية زائدة
     */
    fun formatQuantity(qty: Double): String {
        return cleanQuantityFormat.format(qty)
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

    /**
     * الحصول على الطابع الزمني القياسي للتخزين في قاعدة البيانات
     */
    fun nowTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    /**
     * الحصول على تاريخ اليوم فقط
     */
    fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
