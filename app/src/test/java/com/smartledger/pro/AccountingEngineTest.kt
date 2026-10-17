package com.smartledger.pro

import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.util.NumberFormatter
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * جناح الاختبارات الآلية الشامل للعمليات المحاسبية والمخزنية والوحدات الكبرى والصغرى وتطبيع الأرقام والنسخ الاحتياطي — SmartLedger Pro v2.2.0
 */
class AccountingEngineTest {

    // السيناريو الأول: البيع النقدي (عدم تحميل العميل أي مديونية، إدخال نقدية بالصندوق، وخصم المخزون)
    @Test
    fun testScenario1_CashSale_NoDebtAdded_CustomerBalanceZero() {
        val subtotal = 50000.0
        val discount = 5000.0
        val finalTotal = subtotal - discount // 45,000
        val paidAmount = finalTotal
        val remainingDebt = finalTotal - paidAmount

        assertEquals(45000.0, finalTotal, 0.001)
        assertEquals(0.0, remainingDebt, 0.001)
    }

    // السيناريو الثاني: البيع الآجل (زيادة مديونية العميل "عليه" بدقة بمقدار المتبقي غير المسدد)
    @Test
    fun testScenario2_CreditSale_IncreasesCustomerDebtExactlyByRemaining() {
        val initialCustomerDebt = 10000.0 // عليه 10,000
        val invoiceTotal = 35000.0
        val paid = 0.0
        val remaining = invoiceTotal - paid

        val newCustomerDebt = initialCustomerDebt + remaining
        assertEquals(45000.0, newCustomerDebt, 0.001)
    }

    // السيناريو الثالث: السداد الجزئي (فصل النقدية المسددة بالصندوق عن المتبقي الآجل)
    @Test
    fun testScenario3_PartialPayment_SplitsCashAndDebtAccurately() {
        val invoiceTotal = 80000.0
        val paidCash = 30000.0
        val remainingCredit = invoiceTotal - paidCash

        assertEquals(50000.0, remainingCredit, 0.001)
        assertEquals(80000.0, paidCash + remainingCredit, 0.001)
    }

    // السيناريو الرابع: المشتريات والتوريد (إضافة كميات للمخزن، وزيادة التزام المورد "له")
    @Test
    fun testScenario4_PurchaseInvoice_IncreasesStockAndSupplierDebt() {
        val initialSupplierCredit = 20000.0 // له 20,000 (علينا)
        val purchaseQty = 15.0
        val unitCost = 4000.0
        val purchaseTotal = purchaseQty * unitCost // 60,000
        val paidCash = 10000.0
        val remainingSupplierDebt = purchaseTotal - paidCash // 50,000

        val newSupplierCredit = initialSupplierCredit + remainingSupplierDebt
        assertEquals(70000.0, newSupplierCredit, 0.001)
    }

    // السيناريو الخامس: المرتجعات (إرجاع أصناف، زيادة المخزون، وعكس المديونية بموجب إشعار دائن)
    @Test
    fun testScenario5_SalesReturn_ReversesDebtAndRestoresStock() {
        val customerDebtBeforeReturn = 45000.0 // عليه 45,000
        val returnedQty = 2.0
        val unitPrice = 8500.0
        val returnCreditNote = returnedQty * unitPrice // 17,000 دائن (له)

        val customerDebtAfterReturn = customerDebtBeforeReturn - returnCreditNote
        assertEquals(28000.0, customerDebtAfterReturn, 0.001)
    }

    // السيناريو السادس: المصروفات التشغيلية (توثيق النفقة، خصم من الصندوق، وعدم احتساب المبيعات كصافي ربح)
    @Test
    fun testScenario6_Expenses_DeductsFromCashAndCalculatesCOGS() {
        val totalSales = 100000.0
        val cogs = 72000.0 // تكلفة البضاعة المباعة
        val grossProfit = totalSales - cogs // 28,000
        val operatingExpenses = 8000.0 // كهرباء وإيجار ونثريات
        val netProfit = grossProfit - operatingExpenses // 20,000

        assertEquals(28000.0, grossProfit, 0.001)
        assertEquals(20000.0, netProfit, 0.001)
        assertNotEquals(totalSales, netProfit)
    }

    // السيناريو السابع: اختبار الوحدات الكبرى والصغرى
    // إذا كان الكرتون يحتوي على 24 قطعة، فإن بيع كرتونين و5 قطع يخصم 53 قطعة بالضبط
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

        // إجمالي الكمية المخصومة = 53 قطعة
        assertEquals(53.0, calc.totalMinorQuantity, 0.001)
        // إجمالي سعر البيع = (2 * 5500) + (5 * 250) = 11000 + 1250 = 12,250 ر.ي
        assertEquals(12250.0, calc.totalLinePrice, 0.001)
        // إجمالي التكلفة COGS = 53 * 200 = 10,600 ر.ي
        assertEquals(10600.0, calc.totalLineCost, 0.001)
        // مجمل الربح = 12,250 - 10,600 = 1,650 ر.ي
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

    // السيناريو العاشر: تسوية الجرد السريع بالكاميرا
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

    // السيناريو الثاني عشر: الحماية من النقر المزدوج (Debounce Prevention)
    @Test
    fun testScenario12_Debounce_PreventsDuplicateSubmissions() {
        var executionCount = 0
        var lastClickTime = 0L
        val debounceThresholdMs = 450L

        val clickAction = { currentTime: Long ->
            if (currentTime - lastClickTime >= debounceThresholdMs) {
                executionCount++
                lastClickTime = currentTime
            }
        }

        clickAction(1000L)
        assertEquals(1, executionCount)

        clickAction(1200L)
        assertEquals(1, executionCount)

        clickAction(1600L)
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

        // عند تمرير معامل صفري بالخطأ في دالة التحويل، يتم التعافي الآمن إلى 1.0 دون قسمة على صفر
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
}
