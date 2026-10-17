package com.smartledger.pro.domain.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * محرك التحويل المحاسبي والمخزني بين الوحدات الكبرى والصغرى (Major & Minor Units Engine)
 *
 * القاعدة الهندسية الصارمة:
 * - التمثيل الداخلي الموحد في قاعدة البيانات (QuantityMinor) يكون دائماً بالوحدة الصغرى الأساسية.
 * - عند الشراء أو البيع بالوحدة الكبرى (مثل كرتون = 24 قطعة) أو بكمية مركبة (كرتونين و5 قطع)،
 *   يتم التحويل فوراً إلى إجمالي الوحدات الصغرى (2 * 24 + 5 = 53 قطعة) بدون أي فقد أو تعارض.
 * - يمنع أي معامل تحويل صفري أو سالب، ويعالج أخطاء الفاصلة العشرية (Floating-Point Precision).
 */
object UnitConversionEngine {

    private const val EPSILON = 0.0001

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

    /**
     * التحقق من صلاحية معامل التحويل بين الوحدة الكبرى والصغرى
     */
    fun isValidUnitFactor(unitFactor: Double, hasLargeUnit: Boolean): Boolean {
        return if (!hasLargeUnit) {
            unitFactor >= 1.0
        } else {
            unitFactor > 1.0 && !unitFactor.isNaN() && !unitFactor.isInfinite()
        }
    }

    /**
     * توليد شرح نصي واضح للعلاقة بين الوحدة الكبرى والصغرى
     * مثال: "كل 1 كرتون يحتوي على 24 قطعة"
     */
    fun explainUnitRelation(smallUnit: String, largeUnit: String?, unitFactor: Double): String {
        val cleanSmall = smallUnit.trim().ifBlank { "قطعة" }
        val cleanLarge = largeUnit?.trim()
        if (cleanLarge.isNullOrBlank() || unitFactor <= 1.0) {
            return "يباع ويُجرد هذا الصنف بوحدة أساسية واحدة ($cleanSmall)"
        }
        return "كل 1 $cleanLarge = ${formatQuantity(unitFactor)} $cleanSmall"
    }

    /**
     * تحويل الكمية المركبة (وحدات كبرى + وحدات صغرى) إلى إجمالي الوحدات الصغرى الأساسية
     * مثال: 2 كرتون (معامل 24) + 5 قطع = 53 قطعة
     */
    fun toMinorQuantity(
        majorQty: Double,
        minorQty: Double,
        unitFactor: Double
    ): Double {
        val safeFactor = if (unitFactor <= 0.0 || unitFactor.isNaN()) 1.0 else unitFactor
        val safeMajor = if (majorQty < 0.0 || majorQty.isNaN()) 0.0 else majorQty
        val safeMinor = if (minorQty < 0.0 || minorQty.isNaN()) 0.0 else minorQty
        return roundClean((safeMajor * safeFactor) + safeMinor)
    }

    /**
     * تفكيك إجمالي الوحدات الصغرى إلى (عدد الوحدات الكبرى + المتبقي من الوحدة الصغرى) للعرض الواضح
     */
    fun breakdownStock(
        totalMinorQty: Double,
        smallUnit: String,
        largeUnit: String?,
        unitFactor: Double
    ): StockBreakdown {
        val cleanSmall = smallUnit.trim().ifBlank { "قطعة" }
        val safeFactor = if (unitFactor <= 1.0 || largeUnit.isNullOrBlank()) 1.0 else unitFactor

        if (safeFactor <= 1.0 || largeUnit.isNullOrBlank()) {
            val formatted = "${formatQuantity(totalMinorQty)} $cleanSmall"
            return StockBreakdown(
                totalMinor = totalMinorQty,
                majorUnits = 0L,
                remainingMinor = totalMinorQty,
                formattedText = formatted
            )
        }

        val sign = if (totalMinorQty < -EPSILON) "-" else ""
        val absMinor = roundClean(abs(totalMinorQty))
        val majorCount = floor((absMinor + EPSILON) / safeFactor).toLong()
        val remainderMinor = roundClean(absMinor - (majorCount * safeFactor)).coerceAtLeast(0.0)

        val formatted = when {
            majorCount > 0 && remainderMinor > 0.001 ->
                "$sign$majorCount $largeUnit و ${formatQuantity(remainderMinor)} $cleanSmall"
            majorCount > 0 ->
                "$sign$majorCount $largeUnit (${formatQuantity(absMinor)} $cleanSmall)"
            else ->
                "$sign${formatQuantity(remainderMinor)} $cleanSmall"
        }

        return StockBreakdown(
            totalMinor = totalMinorQty,
            majorUnits = majorCount,
            remainingMinor = remainderMinor,
            formattedText = formatted
        )
    }

    /**
     * حساب تكلفة الوحدة الصغرى عند الشراء بالوحدة الكبرى
     * مثال: شراء كرتون (24 قطعة) بسعر 4,800 ر.ي -> تكلفة القطعة الواحدة = 200 ر.ي
     */
    fun minorCostFromMajorCost(majorPurchaseCost: Double, unitFactor: Double): Double {
        val safeFactor = if (unitFactor <= 0.0 || unitFactor.isNaN()) 1.0 else unitFactor
        return roundClean(majorPurchaseCost / safeFactor)
    }

    /**
     * حساب إجمالي البند وتكلفته وربحه عند البيع بكمية مركبة (كبرى + صغرى)
     */
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

        val effectiveMajorPrice = if (majorSalePrice > 0.0) majorSalePrice else (minorSalePrice * safeFactor)
        val grossPrice = (majorQty * effectiveMajorPrice) + (minorQty * minorSalePrice)
        val netPrice = roundClean((grossPrice - discountAmount).coerceAtLeast(0.0))
        val totalCost = roundClean(totalMinor * minorUnitCost)
        val profit = roundClean(netPrice - totalCost)

        return CompositeLineCalculation(
            totalMinorQuantity = totalMinor,
            totalLinePrice = netPrice,
            totalLineCost = totalCost,
            effectiveMinorUnitCost = minorUnitCost,
            grossProfit = profit
        )
    }

    private fun roundClean(value: Double): Double {
        val nearestLong = value.roundToLong()
        return if (abs(value - nearestLong) < EPSILON) {
            nearestLong.toDouble()
        } else {
            kotlin.math.round(value * 10000.0) / 10000.0
        }
    }

    fun formatQuantity(qty: Double): String {
        val rounded = qty.roundToLong()
        return if (abs(qty - rounded) < 0.001) {
            rounded.toString()
        } else {
            String.format(Locale.US, "%.2f", qty)
        }
    }
}
