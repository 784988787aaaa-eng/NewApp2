package com.smartledger.pro.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

object UnitConversionEngine {

    data class StockBreakdown(
        val totalMinor: Double,
        val majorUnits: Long,
        val remainingMinor: Double,
        val formattedText: String
    )

    data class CompositeLineCalculation(
        val totalMinorQuantity: Double,
        val totalLinePrice: Double,
        val totalLineCost: Double,
        val effectiveMinorUnitCost: Double,
        val grossProfit: Double
    )

    data class UnitValidationResult(
        val isValid: Boolean,
        val sanitizedFactor: Double,
        val errorMessage: String? = null
    )

    fun validateUnitSetup(
        smallUnit: String,
        largeUnit: String?,
        unitFactor: Double,
        hasMajorUnit: Boolean = !largeUnit.isNullOrBlank()
    ): UnitValidationResult {
        val cleanSmall = smallUnit.trim()
        if (cleanSmall.isEmpty()) {
            return UnitValidationResult(false, 1.0, "يرجى إدخال اسم الوحدة الأساسية (مثل: حبة، قطعة، كجم)")
        }
        if (!hasMajorUnit || largeUnit.isNullOrBlank()) {
            return UnitValidationResult(true, 1.0, null)
        }
        val cleanLarge = largeUnit.trim()
        if (cleanLarge.equals(cleanSmall, ignoreCase = true)) {
            return UnitValidationResult(false, 1.0, "اسم الوحدة الكبرى يجب أن يختلف عن الوحدة الصغرى")
        }
        if (unitFactor.isNaN() || unitFactor.isInfinite() || unitFactor <= 1.0) {
            return UnitValidationResult(
                false,
                unitFactor,
                "معامل التحويل للوحدة الكبرى ($cleanLarge) يجب أن يكون أكبر من 1 (مثال: 12 أو 24 $cleanSmall)"
            )
        }
        return UnitValidationResult(true, roundSafe(unitFactor, 4), null)
    }

    fun describeUnitRelation(smallUnit: String, largeUnit: String?, unitFactor: Double): String {
        val cleanSmall = smallUnit.ifBlank { "قطعة" }
        if (largeUnit.isNullOrBlank() || unitFactor <= 1.0) {
            return "يباع ويُجرد بوحدة واحدة: $cleanSmall"
        }
        return "كل 1 ${largeUnit.trim()} = ${formatQuantity(unitFactor)} $cleanSmall"
    }

    fun toMinorQuantity(majorQty: Double, minorQty: Double, unitFactor: Double): Double {
        val safeFactor = if (unitFactor <= 0.0 || unitFactor.isNaN() || unitFactor.isInfinite()) 1.0 else unitFactor
        val safeMajor = if (majorQty < 0.0 || majorQty.isNaN()) 0.0 else majorQty
        val safeMinor = if (minorQty < 0.0 || minorQty.isNaN()) 0.0 else minorQty
        return roundSafe((safeMajor * safeFactor) + safeMinor, 4)
    }

    fun breakdownStock(
        totalMinorQty: Double,
        smallUnit: String,
        largeUnit: String?,
        unitFactor: Double
    ): StockBreakdown {
        val cleanSmall = smallUnit.ifBlank { "قطعة" }
        val safeFactor = if (unitFactor <= 1.0 || largeUnit.isNullOrBlank()) 1.0 else unitFactor

        if (safeFactor <= 1.0 || largeUnit.isNullOrBlank()) {
            val formatted = "${formatQuantity(totalMinorQty)} $cleanSmall"
            return StockBreakdown(totalMinorQty, 0L, totalMinorQty, formatted)
        }

        val sign = if (totalMinorQty < 0) "-" else ""
        val absMinor = roundSafe(abs(totalMinorQty), 4)
        val majorCount = floor((absMinor + 1e-9) / safeFactor).toLong()
        val remainderMinor = roundSafe((absMinor - (majorCount * safeFactor)).coerceAtLeast(0.0), 4)

        val formatted = when {
            majorCount > 0 && remainderMinor > 0.001 ->
                "$sign$majorCount $largeUnit و ${formatQuantity(remainderMinor)} $cleanSmall"
            majorCount > 0 ->
                "$sign$majorCount $largeUnit (${formatQuantity(absMinor)} $cleanSmall)"
            else ->
                "$sign${formatQuantity(remainderMinor)} $cleanSmall"
        }

        return StockBreakdown(totalMinorQty, majorCount, remainderMinor, formatted)
    }

    fun minorCostFromMajorCost(majorPurchaseCost: Double, unitFactor: Double): Double {
        val safeFactor = if (unitFactor <= 0.0 || unitFactor.isNaN()) 1.0 else unitFactor
        return roundSafe(majorPurchaseCost / safeFactor, 4)
    }

    fun calculateCompositeSale(
        majorQty: Double,
        minorQty: Double,
        unitFactor: Double,
        minorSalePrice: Double,
        majorSalePrice: Double = 0.0,
        minorUnitCost: Double,
        discountAmount: Double = 0.0
    ): CompositeLineCalculation {
        val safeFactor = if (unitFactor <= 0.0 || unitFactor.isNaN()) 1.0 else unitFactor
        val totalMinor = toMinorQuantity(majorQty, minorQty, safeFactor)

        val effectiveMajorPrice = if (majorSalePrice > 0.0) majorSalePrice else roundSafe(minorSalePrice * safeFactor, 2)
        val grossPrice = roundSafe((majorQty * effectiveMajorPrice) + (minorQty * minorSalePrice), 2)
        val netPrice = roundSafe((grossPrice - discountAmount).coerceAtLeast(0.0), 2)
        val totalCost = roundSafe(totalMinor * minorUnitCost, 2)
        val profit = roundSafe(netPrice - totalCost, 2)

        return CompositeLineCalculation(totalMinor, netPrice, totalCost, minorUnitCost, profit)
    }

    private fun roundSafe(value: Double, scale: Int): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    private fun formatQuantity(qty: Double): String {
        val rounded = qty.roundToLong()
        return if (abs(qty - rounded) < 0.001) String.format(Locale.US, "%d", rounded)
        else String.format(Locale.US, "%.2f", qty)
    }
}
