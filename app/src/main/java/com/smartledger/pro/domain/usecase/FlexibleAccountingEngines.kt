package com.smartledger.pro.domain.usecase

import com.smartledger.pro.data.local.entity.CategoryEntity
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.MeasurementUnitEntity
import com.smartledger.pro.util.NumberFormatter
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * محرك التحقق من التصنيفات الهرمية ومنع الدورات المغلقة (HierarchicalCategoryValidator).
 *
 * يضمن:
 * 1. منع ربط التصنيف بنفسه (`categoryId == parentId`).
 * 2. كشف ومنع الدورات الهرمية متعددة المستويات (`A -> B -> C -> A`).
 * 3. منع تكرار الاسم داخل نفس المجال (`Domain`) ونفس المستوى الأب (`ParentId`).
 * 4. توليد المسار الهرمي الكامل للعرض (`رئيسي ◂ فرعي ◂ تفصيلي`).
 */
object HierarchicalCategoryValidator {

    fun validateAndCheckCycle(
        categoryId: Long,
        name: String,
        domain: String,
        parentId: Long?,
        existingCategories: List<CategoryEntity>
    ): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("اسم التصنيف مطلوب ولا يمكن تركه فارغاً."))
        }
        val cleanDomain = domain.trim().uppercase()
        if (cleanDomain.isEmpty()) {
            return Result.failure(IllegalArgumentException("مجال التصنيف مطلوب."))
        }

        if (parentId != null && parentId > 0L) {
            if (categoryId > 0L && categoryId == parentId) {
                return Result.failure(IllegalArgumentException("لا يمكن ربط التصنيف بنفسه كتصنيف أب."))
            }
            val byId = existingCategories.associateBy { it.Id }
            val parentEntity = byId[parentId]
                ?: return Result.failure(IllegalArgumentException("التصنيف الأب المختار غير موجود."))

            if (!parentEntity.Domain.equals(cleanDomain, ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("يجب أن ينتمي التصنيف الفرعي لنفس مجال التصنيف الأب."))
            }

            // تتبع سلسلة الآباء صعوداً لمنع أي دورة هرمية (Cycle Detection)
            val visited = mutableSetOf<Long>()
            if (categoryId > 0L) visited.add(categoryId)
            var cursorId: Long? = parentId
            while (cursorId != null && cursorId > 0L) {
                if (!visited.add(cursorId)) {
                    return Result.failure(
                        IllegalStateException("لا يمكن نقل التصنيف إلى أحد فروعه التابعة لأن ذلك ينشئ دورة هرمية مغلقة.")
                    )
                }
                cursorId = byId[cursorId]?.ParentId
            }
        }

        val normalizedParentId = if (parentId != null && parentId > 0L) parentId else null
        val duplicate = existingCategories.firstOrNull { existing ->
            existing.Id != categoryId &&
                existing.Domain.equals(cleanDomain, ignoreCase = true) &&
                (existing.ParentId ?: 0L) == (normalizedParentId ?: 0L) &&
                existing.Name.trim().equals(cleanName, ignoreCase = true)
        }
        if (duplicate != null) {
            return Result.failure(
                IllegalArgumentException("يوجد تصنيف بنفس الاسم «$cleanName» داخل نفس المستوى مسبقاً.")
            )
        }

        return Result.success(Unit)
    }

    fun formatCategoryPath(
        categoryId: Long?,
        allCategories: List<CategoryEntity>
    ): String {
        if (categoryId == null || categoryId <= 0L) return ""
        val byId = allCategories.associateBy { it.Id }
        val parts = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var cursorId: Long? = categoryId
        while (cursorId != null && cursorId > 0L && visited.add(cursorId)) {
            val cat = byId[cursorId] ?: break
            parts.add(0, cat.Name)
            cursorId = cat.ParentId
        }
        return parts.joinToString(" ◂ ")
    }

    /**
     * إرجاع معرف التصنيف الأب مع كافة معرفات التصنيفات الفرعية التابعة له (للتجميع الهرمي في التقارير والفلترة)
     */
    fun collectSubtreeIds(rootCategoryId: Long, allCategories: List<CategoryEntity>): Set<Long> {
        val result = mutableSetOf(rootCategoryId)
        val childrenMap = allCategories.groupBy { it.ParentId }
        val queue = ArrayDeque<Long>()
        queue.add(rootCategoryId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            childrenMap[current]?.forEach { child ->
                if (result.add(child.Id)) {
                    queue.add(child.Id)
                }
            }
        }
        return result
    }
}

/**
 * محرك التحقق والتحويل الدقيق لوحدات القياس المرنة (FlexibleUnitValidator).
 */
object FlexibleUnitValidator {

    fun validateUnit(
        unit: MeasurementUnitEntity,
        existingUnits: List<MeasurementUnitEntity>
    ): Result<Unit> {
        val cleanName = unit.Name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("اسم الوحدة مطلوب."))
        }
        if (unit.DecimalPrecision !in 0..4) {
            return Result.failure(IllegalArgumentException("الدقة العشرية للوحدة يجب أن تكون بين 0 و 4 منازل."))
        }
        if (unit.ConversionFactorToBase <= 0.0 || unit.ConversionFactorToBase.isNaN() || unit.ConversionFactorToBase.isInfinite()) {
            return Result.failure(IllegalArgumentException("معامل التحويل للوحدة يجب أن يكون أكبر من صفر."))
        }
        if (unit.BaseUnitId != null && unit.BaseUnitId > 0L) {
            if (unit.Id > 0L && unit.Id == unit.BaseUnitId) {
                return Result.failure(IllegalArgumentException("لا يمكن ربط الوحدة بنفسها كوحدة أساسية."))
            }
            val byId = existingUnits.associateBy { it.Id }
            if (!byId.containsKey(unit.BaseUnitId)) {
                return Result.failure(IllegalArgumentException("الوحدة الأساسية المرجعية غير موجودة."))
            }
        }
        val duplicate = existingUnits.firstOrNull {
            it.Id != unit.Id && it.Name.trim().equals(cleanName, ignoreCase = true)
        }
        if (duplicate != null) {
            return Result.failure(IllegalArgumentException("الوحدة «$cleanName» معرفة مسبقاً."))
        }
        return Result.success(Unit)
    }

    /**
     * تحويل الكمية بين وحدتين تشتركان في نفس المرجع الأساسي باستخدام BigDecimal لمنع أخطاء الفاصلة العائمة
     */
    fun convertBetweenUnits(
        quantity: Double,
        fromUnit: MeasurementUnitEntity,
        toUnit: MeasurementUnitEntity
    ): Result<Double> {
        if (quantity < 0.0) {
            return Result.failure(IllegalArgumentException("الكمية لا يمكن أن تكون سالبة."))
        }
        val fromRootId = fromUnit.BaseUnitId ?: fromUnit.Id
        val toRootId = toUnit.BaseUnitId ?: toUnit.Id
        if (fromRootId != toRootId || fromRootId <= 0L) {
            return Result.failure(
                IllegalArgumentException("لا يمكن التحويل بين «${fromUnit.Name}» و«${toUnit.Name}» لعدم وجود علاقة تحويل مشتركة بينهما.")
            )
        }
        val fromFactor = BigDecimal.valueOf(if (fromUnit.BaseUnitId == null) 1.0 else fromUnit.ConversionFactorToBase)
        val toFactor = BigDecimal.valueOf(if (toUnit.BaseUnitId == null) 1.0 else toUnit.ConversionFactorToBase)
        if (toFactor.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.failure(IllegalArgumentException("معامل التحويل للوحدة الهدف غير صالح."))
        }
        val qtyInBase = BigDecimal.valueOf(quantity).multiply(fromFactor)
        val converted = qtyInBase.divide(toFactor, 4, RoundingMode.HALF_UP).toDouble()
        return Result.success(converted)
    }
}

/**
 * حاسبة المتوسط المرجح للتكلفة (WeightedAverageCostCalculator)
 * تحدّث تكلفة الوحدة الصغرى عند توريد مشتريات جديدة بسعر تكلفة مختلف.
 */
object WeightedAverageCostCalculator {

    fun calculateNewWeightedCost(
        currentStockMinor: Double,
        currentUnitCost: Double,
        purchasedQtyMinor: Double,
        purchasedUnitCost: Double
    ): Double {
        if (purchasedQtyMinor <= 0.0) return NumberFormatter.roundCurrency(currentUnitCost, 4)
        if (purchasedUnitCost <= 0.0) return NumberFormatter.roundCurrency(currentUnitCost, 4)
        val effectiveExistingQty = currentStockMinor.coerceAtLeast(0.0)
        if (effectiveExistingQty <= 0.0001 || currentUnitCost <= 0.0) {
            return NumberFormatter.roundCurrency(purchasedUnitCost, 4)
        }
        val existingValue = BigDecimal.valueOf(effectiveExistingQty).multiply(BigDecimal.valueOf(currentUnitCost))
        val newPurchaseValue = BigDecimal.valueOf(purchasedQtyMinor).multiply(BigDecimal.valueOf(purchasedUnitCost))
        val totalQty = BigDecimal.valueOf(effectiveExistingQty).add(BigDecimal.valueOf(purchasedQtyMinor))
        return existingValue.add(newPurchaseValue)
            .divide(totalQty, 4, RoundingMode.HALF_UP)
            .toDouble()
    }
}

/**
 * محرك تخصيص دفعات العملاء والموردين (PaymentAllocationEngine)
 * يوزع الدفعة المسددة على الفواتير المفتوحة (إما لفاتورة محددة أو للأقدم فالأحدث) ويحسب الفائض كدفعة مقدمة.
 */
data class InvoiceAllocationSlice(
    val invoiceId: Long,
    val invoiceNumber: String,
    val allocatedAmount: Double,
    val remainingAfterAllocation: Double
)

data class PaymentAllocationResult(
    val totalPaymentAmount: Double,
    val allocatedSlices: List<InvoiceAllocationSlice>,
    val unallocatedAdvanceAmount: Double
)

object PaymentAllocationEngine {

    fun allocatePayment(
        paymentAmount: Double,
        openInvoices: List<InvoiceEntity>,
        targetInvoiceId: Long? = null
    ): PaymentAllocationResult {
        val cleanPayment = NumberFormatter.roundCurrency(paymentAmount.coerceAtLeast(0.0))
        if (cleanPayment <= 0.0 || openInvoices.isEmpty()) {
            return PaymentAllocationResult(
                totalPaymentAmount = cleanPayment,
                allocatedSlices = emptyList(),
                unallocatedAdvanceAmount = cleanPayment
            )
        }

        val orderedInvoices = if (targetInvoiceId != null && targetInvoiceId > 0L) {
            val target = openInvoices.filter { it.Id == targetInvoiceId }
            val others = openInvoices.filter { it.Id != targetInvoiceId }.sortedBy { it.IssuedAt }
            target + others
        } else {
            openInvoices.sortedBy { it.IssuedAt }
        }

        var remainingToAllocate = cleanPayment
        val slices = mutableListOf<InvoiceAllocationSlice>()

        for (inv in orderedInvoices) {
            if (remainingToAllocate <= 0.001) break
            val openRem = NumberFormatter.roundCurrency(inv.RemainingAmount.coerceAtLeast(0.0))
            if (openRem <= 0.001) continue
            val slice = NumberFormatter.roundCurrency(minOf(remainingToAllocate, openRem))
            if (slice > 0.0) {
                val remAfter = NumberFormatter.roundCurrency((openRem - slice).coerceAtLeast(0.0))
                slices.add(
                    InvoiceAllocationSlice(
                        invoiceId = inv.Id,
                        invoiceNumber = inv.InvoiceNumber,
                        allocatedAmount = slice,
                        remainingAfterAllocation = remAfter
                    )
                )
                remainingToAllocate = NumberFormatter.roundCurrency((remainingToAllocate - slice).coerceAtLeast(0.0))
            }
        }

        return PaymentAllocationResult(
            totalPaymentAmount = cleanPayment,
            allocatedSlices = slices,
            unallocatedAdvanceAmount = remainingToAllocate
        )
    }
}
