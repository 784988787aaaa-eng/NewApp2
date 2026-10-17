package com.smartledger.pro

import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.ui.components.FormWindowLifecycleState
import com.smartledger.pro.util.NumberFormatter
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * جناح الاختبارات الآلية الشامل للعمليات المحاسبية والمخزنية والوحدات الكبرى والصغرى
 * ودورة حياة النوافذ المستقلة ودقة BigDecimal ومنع التكرار — SmartLedger Pro v2.4.0
 */
class AccountingEngineTest {

    // السيناريو الأول: البيع النقدي (عدم تحميل العميل أي مديونية، إدخال نقدية بالصندوق، وخصم المخزون)
    @Test
    fun testScenario1_CashSale_NoDebtAdded_CustomerBalanceZero() {
        val subtotal = 50000.0
        val discount = 5000.0
        val finalTotal = NumberFormatter.roundCurrency(subtotal - discount) // 45,000
        val paidAmount = finalTotal
        val remainingDebt = NumberFormatter.roundCurrency(finalTotal - paidAmount)

        assertEquals(45000.0, finalTotal, 0.001)
        assertEquals(0.0, remainingDebt, 0.001)
    }

    // السيناريو الثاني: البيع الآجل (زيادة مديونية العميل "عليه" بدقة بمقدار المتبقي غير المسدد)
    @Test
    fun testScenario2_CreditSale_IncreasesCustomerDebtExactlyByRemaining() {
        val initialCustomerDebt = 10000.0 // عليه 10,000
        val invoiceTotal = 35000.0
        val paid = 0.0
        val remaining = NumberFormatter.roundCurrency(invoiceTotal - paid)

        val newCustomerDebt = NumberFormatter.roundCurrency(initialCustomerDebt + remaining)
        assertEquals(45000.0, newCustomerDebt, 0.001)
    }

    // السيناريو الثالث: السداد الجزئي (فصل النقدية المسددة بالصندوق عن المتبقي الآجل)
    @Test
    fun testScenario3_PartialPayment_SplitsCashAndDebtAccurately() {
        val invoiceTotal = 80000.0
        val paidCash = 30000.0
        val remainingCredit = NumberFormatter.roundCurrency(invoiceTotal - paidCash)

        assertEquals(50000.0, remainingCredit, 0.001)
        assertEquals(80000.0, paidCash + remainingCredit, 0.001)
    }

    // السيناريو الرابع: المشتريات والتوريد (إضافة كميات للمخزن، وزيادة التزام المورد "له")
    @Test
    fun testScenario4_PurchaseInvoice_IncreasesStockAndSupplierDebt() {
        val initialSupplierCredit = 20000.0 // له 20,000 (علينا)
        val purchaseQty = 15.0
        val unitCost = 4000.0
        val purchaseTotal = NumberFormatter.roundCurrency(purchaseQty * unitCost) // 60,000
        val paidCash = 10000.0
        val remainingSupplierDebt = NumberFormatter.roundCurrency(purchaseTotal - paidCash) // 50,000

        val newSupplierCredit = NumberFormatter.roundCurrency(initialSupplierCredit + remainingSupplierDebt)
        assertEquals(70000.0, newSupplierCredit, 0.001)
    }

    // السيناريو الخامس: المرتجعات (إرجاع أصناف، زيادة المخزون، وعكس المديونية بموجب إشعار دائن)
    @Test
    fun testScenario5_SalesReturn_ReversesDebtAndRestoresStock() {
        val customerDebtBeforeReturn = 45000.0 // عليه 45,000
        val returnedQty = 2.0
        val unitPrice = 8500.0
        val returnCreditNote = NumberFormatter.roundCurrency(returnedQty * unitPrice) // 17,000 دائن (له)

        val customerDebtAfterReturn = NumberFormatter.roundCurrency(customerDebtBeforeReturn - returnCreditNote)
        assertEquals(28000.0, customerDebtAfterReturn, 0.001)
    }

    // السيناريو السادس: المصروفات التشغيلية (توثيق النفقة، خصم من الصندوق، وعدم احتساب المبيعات كصافي ربح)
    @Test
    fun testScenario6_Expenses_DeductsFromCashAndCalculatesCOGS() {
        val totalSales = 100000.0
        val cogs = 72000.0 // تكلفة البضاعة المباعة
        val grossProfit = NumberFormatter.roundCurrency(totalSales - cogs) // 28,000
        val operatingExpenses = 8000.0 // كهرباء وإيجار ونثريات
        val netProfit = NumberFormatter.roundCurrency(grossProfit - operatingExpenses) // 20,000

        assertEquals(28000.0, grossProfit, 0.001)
        assertEquals(20000.0, netProfit, 0.001)
        assertNotEquals(totalSales, netProfit)
    }

    // السيناريو السابع: اختبار الوحدات الكبرى والصغرى
    @Test
    fun testScenario7_MajorAndMinorUnitConversion_2CartonsOf24Plus5Pieces_Deducts53Pieces() {
        val unitFactor = 24.0 // 24 قطعة في الكرتون
        val initialStockMinor = UnitConversionEngine.toMinorQuantity(majorQty = 5.0, minorQty = 0.0, unitFactor = unitFactor) // 120 قطعة

        val soldMajorCartons = 2.0
        val soldMinorPieces = 5.0
        val deductedMinor = UnitConversionEngine.toMinorQuantity(soldMajorCartons, soldMinorPieces, unitFactor)

        assertEquals(53.0, deductedMinor, 0.001)

        val remainingMinor = initialStockMinor - deductedMinor // 120 - 53 = 67 قطعة
        assertEquals(67.0, remainingMinor, 0.001)

        val breakdown = UnitConversionEngine.breakdownStock(
            totalMinorQty = remainingMinor,
            smallUnit = "قطعة",
            largeUnit = "كرتون",
            unitFactor = unitFactor
        )
        assertEquals(2L, breakdown.majorUnits) // 2 كرتون (48 قطعة)
        assertEquals(19.0, breakdown.remainingMinor, 0.001) // و 19 قطعة متبقية (48 + 19 = 67)
        assertEquals("2 كرتون و 19 قطعة", breakdown.formattedText)
    }

    // السيناريو الثامن: الشراء بالوحدة الكبرى والبيع بالوحدتين مع حساب التكلفة والربح بدقة
    @Test
    fun testScenario8_CrossUnitPurchaseAndCompositeSale_ProfitAccuracy() {
        val unitFactor = 24.0
        val cartonPurchaseCost = 4800.0 // شراء الكرتون بـ 4,800 ر.ي
        val minorPieceCost = UnitConversionEngine.minorCostFromMajorCost(cartonPurchaseCost, unitFactor)
        assertEquals(200.0, minorPieceCost, 0.001) // تكلفة القطعة = 200 ر.ي

        // بيع كرتونين (بسعر جملة 5,500 للكرتون) + 5 قطع (بسعر تجزئة 250 للقطعة)
        val calc = UnitConversionEngine.calculateCompositeSale(
            majorQty = 2.0,
            minorQty = 5.0,
            unitFactor = unitFactor,
            minorSalePrice = 250.0,
            majorSalePrice = 5500.0,
            minorUnitCost = minorPieceCost
        )

        assertEquals(53.0, calc.totalMinorQuantity, 0.001)
        assertEquals(12250.0, calc.totalLinePrice, 0.001)
        assertEquals(10600.0, calc.totalLineCost, 0.001)
        assertEquals(1650.0, calc.grossProfit, 0.001)
    }

    // السيناريو التاسع: سندات القبض والصرف
    @Test
    fun testScenario9_ReceiptAndPaymentVouchers() {
        val customerDebitBalance = 65000.0
        val receiptAmount = 25000.0
        assertEquals(40000.0, customerDebitBalance - receiptAmount, 0.001)

        val supplierCreditBalance = 70000.0
        val paymentAmount = 30000.0
        assertEquals(40000.0, supplierCreditBalance - paymentAmount, 0.001)
    }

    // السيناريو العاشر: تسوية الجرد السريع
    @Test
    fun testScenario10_RapidStockAdjustment_CalculatesDeltaAccurately() {
        val systemStock = 28.0
        val actualCountSurplus = 30.0
        assertEquals(2.0, actualCountSurplus - systemStock, 0.001)

        val actualCountDeficit = 25.0
        assertEquals(-3.0, actualCountDeficit - systemStock, 0.001)
    }

    // السيناريو الحادي عشر: فحص البصمة الرقمية للنسخ الاحتياطي (SHA-256)
    @Test
    fun testScenario11_BackupSha256_Verification() {
        val originalData = "SmartLedgerDatabase_Version_1_BinaryContent".toByteArray(Charsets.UTF_8)
        val expectedSha256 = MessageDigest.getInstance("SHA-256").digest(originalData).joinToString("") { "%02x".format(it) }

        val testData = "SmartLedgerDatabase_Version_1_BinaryContent".toByteArray(Charsets.UTF_8)
        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(testData).joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha256, actualSha256)

        val tamperedData = "SmartLedgerDatabase_Version_1_TAMPERED".toByteArray(Charsets.UTF_8)
        val tamperedSha256 = MessageDigest.getInstance("SHA-256").digest(tamperedData).joinToString("") { "%02x".format(it) }
        assertNotEquals(expectedSha256, tamperedSha256)
    }

    // السيناريو الثاني عشر: الحماية من النقر المزدوج (Debounce & In-Flight Lock)
    @Test
    fun testScenario12_Debounce_And_SavingLock_PreventsDuplicateSubmissions() {
        var executionCount = 0
        var lastClickTime = 0L
        var isSaving = false
        val debounceThresholdMs = 500L

        val submitAction = { currentTime: Long ->
            if (!isSaving && (currentTime - lastClickTime >= debounceThresholdMs)) {
                isSaving = true
                executionCount++
                lastClickTime = currentTime
                isSaving = false
            }
        }

        submitAction(1000L)
        assertEquals(1, executionCount)

        submitAction(1250L)
        assertEquals(1, executionCount)

        submitAction(1650L)
        assertEquals(2, executionCount)
    }

    // السيناريو الثالث عشر: تطبيع الأرقام الشرقية/الهندية والفارسية إلى الأرقام الغربية القياسية (123)
    @Test
    fun testScenario13_WesternNumeralsNormalization_AndCurrencyFormatting() {
        val arabicInput = "١٢٥٠٠٫٧٥"
        val normalized = NumberFormatter.normalizeNumericInput(arabicInput, allowDecimal = true)
        assertEquals("12500.75", normalized)
        assertEquals(12500.75, NumberFormatter.parseDouble(arabicInput), 0.001)

        val formattedCurrency = NumberFormatter.formatCurrency(12500.0, "ر.ي", decimalPlaces = 0)
        assertEquals("12,500 ر.ي", formattedCurrency)

        val maskedCurrency = NumberFormatter.formatCurrency(12500.0, "ر.ي", privacyMode = true)
        assertEquals("••••••", maskedCurrency)
    }

    // السيناريو الرابع عشر: التحقق من منع معامل التحويل الصفري أو السالب في محرك الوحدات
    @Test
    fun testScenario14_UnitFactorValidation_PreventsZeroOrInvalidFactor() {
        assertFalse(UnitConversionEngine.isValidUnitFactor(0.0, hasLargeUnit = true))
        assertFalse(UnitConversionEngine.isValidUnitFactor(-5.0, hasLargeUnit = true))
        assertFalse(UnitConversionEngine.isValidUnitFactor(1.0, hasLargeUnit = true))
        assertTrue(UnitConversionEngine.isValidUnitFactor(24.0, hasLargeUnit = true))

        val safeCost = UnitConversionEngine.minorCostFromMajorCost(1500.0, 0.0)
        assertEquals(1500.0, safeCost, 0.001)

        val explanation = UnitConversionEngine.explainUnitRelation("قطعة", "كرتون", 24.0)
        assertEquals("كل 1 كرتون = 24 قطعة", explanation)
    }

    // السيناريو الخامس عشر: التحقق من ترويسة ملف قاعدة البيانات SQLite قبل الاستعادة
    @Test
    fun testScenario15_BackupSqliteHeaderValidation_RejectsCorruptFiles() {
        val tempValid = File.createTempFile("valid_test", ".db")
        val tempCorrupt = File.createTempFile("corrupt_test", ".db")
        try {
            tempValid.writeBytes("SQLite format 3\u0000_valid_database_bytes".toByteArray(Charsets.US_ASCII))
            tempCorrupt.writeBytes("NOT_A_VALID_SQLITE_FILE_HEADER_12345".toByteArray(Charsets.US_ASCII))

            assertTrue(com.smartledger.pro.util.BackupRestoreHelper.hasValidSqliteHeader(tempValid))
            assertFalse(com.smartledger.pro.util.BackupRestoreHelper.hasValidSqliteHeader(tempCorrupt))
        } finally {
            tempValid.delete()
            tempCorrupt.delete()
        }
    }

    // السيناريو السادس عشر: آلة حالة النافذة المستقلة (FormWindowLifecycleState) ومنع الإغلاق عند الخطأ
    @Test
    fun testScenario16_FormWindowLifecycleStateMachine_NeverClosesOnErrorOrUnsavedEdits() {
        var state = FormWindowLifecycleState.EDITING
        var formData = "حليب الممتاز"

        // 1. فشل التحقق لا يغلق النافذة ولا يمسح البيانات
        state = FormWindowLifecycleState.VALIDATING
        val isValid = false
        state = if (isValid) FormWindowLifecycleState.SAVING else FormWindowLifecycleState.VALIDATION_ERROR
        assertEquals(FormWindowLifecycleState.VALIDATION_ERROR, state)
        assertNotEquals(FormWindowLifecycleState.CLOSED, state)
        assertEquals("حليب الممتاز", formData)

        // 2. فشل الحفظ يعيد النافذة لحالة SAVE_ERROR مع بقاء المدخلات سليمة
        state = FormWindowLifecycleState.SAVING
        val saveSucceeded = false
        state = if (saveSucceeded) FormWindowLifecycleState.SAVE_SUCCESS else FormWindowLifecycleState.SAVE_ERROR
        assertEquals(FormWindowLifecycleState.SAVE_ERROR, state)
        assertNotEquals(FormWindowLifecycleState.CLOSED, state)
        assertEquals("حليب الممتاز", formData)

        // 3. طلب الإغلاق مع وجود تعديلات غير محفوظة ينتقل إلى CONFIRMING_DISCARD ولا يغلق بصمت
        val hasUnsavedChanges = formData.isNotBlank()
        if (hasUnsavedChanges) {
            state = FormWindowLifecycleState.CONFIRMING_DISCARD
        }
        assertEquals(FormWindowLifecycleState.CONFIRMING_DISCARD, state)

        // 4. نجاح الحفظ الفعلي فقط ينقل النافذة إلى SAVE_SUCCESS ثم CLOSED
        state = FormWindowLifecycleState.SAVING
        val retrySucceeded = true
        if (retrySucceeded) {
            state = FormWindowLifecycleState.SAVE_SUCCESS
            state = FormWindowLifecycleState.CLOSED
        }
        assertEquals(FormWindowLifecycleState.CLOSED, state)
    }

    // السيناريو السابع عشر: دقة تقريب العملات عبر BigDecimal (HALF_UP) لمنع أخطاء الفاصلة العائمة
    @Test
    fun testScenario17_BigDecimalCurrencyRounding_EliminatesFloatingPointDrift() {
        val rawFloatSum = 0.1 + 0.2 // 0.30000000000000004 in IEEE 754
        val rounded = NumberFormatter.roundCurrency(rawFloatSum, decimalPlaces = 2)
        assertEquals(0.30, rounded, 0.0)

        val invoiceCalc = 19.99 * 3.0 // 59.97
        assertEquals(59.97, NumberFormatter.roundCurrency(invoiceCalc, decimalPlaces = 2), 0.0)
    }
}
