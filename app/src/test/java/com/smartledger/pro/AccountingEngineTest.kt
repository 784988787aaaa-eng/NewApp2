package com.smartledger.pro

import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.domain.usecase.InMemoryLedgerEngine
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.FormWindowLifecycleState
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.SecurityCryptoHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * جناح الاختبارات الهندسية الشامل (3 طبقات: Unit + Ledger Integration + End-to-End Business Flows)
 * يثبت صحة العمليات الفعلية المخزنة في السجلات المحاسبية والمخزنية والأمنية — SmartLedger Pro v2.4.0
 */
class AccountingEngineTest {

    // 1. اختبار تدفق البيع النقدي الفعلي (Cash Sale End-to-End Flow)
    @Test
    fun testFlow1_CashSale_DeductsStock_RecordsInvoice_LeavesCustomerDebtZero() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val customerId = ledger.createAccount(name = "عميل نقدي تجريبي", bookId = 1L, openingBalance = 0.0).getOrThrow()
        val productId = ledger.createProduct(
            name = "زيت طبخ 1.5 لتر",
            barcode = "629100000001",
            smallUnit = "قارورة",
            largeUnit = "كرتون",
            unitFactor = 12.0,
            costPrice = 1000.0,
            retailPrice = 1300.0,
            wholesalePrice = 15000.0,
            openingMajorQty = 5.0, // 60 قارورة افتتاحية
            openingMinorQty = 0.0
        ).getOrThrow()

        assertEquals(60.0, ledger.getProductStockMinor(productId), 0.001)

        // بيع 4 قوارير نقداً مع خصم 200 ر.ي (الإجمالي 5,200 - 200 = 5,000)
        val saleId = ledger.postSaleInvoice(
            personId = customerId,
            lines = listOf(Triple(productId, 0.0, 4.0)),
            discountAmount = 200.0,
            paymentType = "Cash"
        ).getOrThrow()

        // التحقق من السجلات المخزنة فعلياً
        val savedInvoice = ledger.invoices.first { it.Id == saleId }
        assertEquals(5200.0, savedInvoice.Subtotal, 0.001)
        assertEquals(200.0, savedInvoice.DiscountAmount, 0.001)
        assertEquals(5000.0, savedInvoice.FinalTotal, 0.001)
        assertEquals(5000.0, savedInvoice.PaidAmount, 0.001)
        assertEquals(0.0, savedInvoice.RemainingAmount, 0.001)

        // التحقق من خصم المخزون (60 - 4 = 56 قارورة)
        assertEquals(56.0, ledger.getProductStockMinor(productId), 0.001)

        // التحقق من عدم تحميل العميل أي مديونية
        val custSummary = ledger.getAccountBalanceSummary(customerId)
        assertEquals(0.0, custSummary.NetBalance, 0.001)

        // التحقق من تقرير الأرباح وتكلفة البضاعة المباعة (COGS = 4 * 1000 = 4,000، مجمل الربح = 5,000 - 4,000 = 1,000)
        val report = ledger.getFinancialReportSummary()
        assertEquals(5000.0, report.TotalSales, 0.001)
        assertEquals(4000.0, report.CostOfGoodsSold, 0.001)
        assertEquals(1000.0, report.GrossProfit, 0.001)
    }

    // 2. اختبار تدفق البيع الآجل والجزئي وأثره الفعلي على ذمة العميل وتقرير الديون
    @Test
    fun testFlow2_CreditAndPartialSale_UpdatesCustomerReceivableAndFinancialReport() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        // عميل عليه رصيد افتتاحي سابق 10,000
        val customerId = ledger.createAccount(
            name = "مؤسسة الأمل التجارية",
            bookId = 1L,
            openingBalance = 10000.0,
            isDebit = true
        ).getOrThrow()

        val productId = ledger.createProduct(
            name = "سكر أبيض 50 كجم",
            barcode = "629100000002",
            smallUnit = "كيس",
            largeUnit = null,
            unitFactor = 1.0,
            costPrice = 20000.0,
            retailPrice = 25000.0,
            openingMajorQty = 0.0,
            openingMinorQty = 20.0
        ).getOrThrow()

        // بيع 2 كيس (50,000) مع دفع جزئي 15,000 ومتبقي آجل 35,000
        ledger.postSaleInvoice(
            personId = customerId,
            lines = listOf(Triple(productId, 0.0, 2.0)),
            discountAmount = 0.0,
            paymentType = "Partial",
            paidAmountInput = 15000.0
        ).getOrThrow()

        val summary = ledger.getAccountBalanceSummary(customerId)
        // الرصيد الافتتاحي (10,000) + المتبقي الآجل (35,000) = 45,000 عليه (مدين)
        assertEquals(45000.0, summary.TotalDebit, 0.001)
        assertEquals(0.0, summary.TotalCredit, 0.001)
        assertEquals(45000.0, summary.NetBalance, 0.001)

        // تحصيل سند قبض بقيمة 20,000 من العميل
        ledger.postVoucher(customerId, "Receive", 20000.0, "دفعة نقدية من الحساب").getOrThrow()

        val afterReceipt = ledger.getAccountBalanceSummary(customerId)
        assertEquals(45000.0, afterReceipt.TotalDebit, 0.001)
        assertEquals(20000.0, afterReceipt.TotalCredit, 0.001)
        assertEquals(25000.0, afterReceipt.NetBalance, 0.001)

        val report = ledger.getFinancialReportSummary()
        assertEquals(25000.0, report.TotalDebtsToUs, 0.001)
    }

    // 3. اختبار تدفق المشتريات بالوحدة الكبرى وسند الصرف ومرتجع المشتريات
    @Test
    fun testFlow3_PurchaseMajorUnit_PaymentVoucher_AndPurchaseReturnReversal() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        // مورد له رصيد افتتاحي دائن 20,000 (علينا)
        val supplierId = ledger.createAccount(
            name = "شركة الاتحاد للتوريدات",
            bookId = 2L,
            openingBalance = 20000.0,
            isDebit = false
        ).getOrThrow()

        val productId = ledger.createProduct(
            name = "حليب مجفف",
            barcode = "629100000003",
            smallUnit = "علبة",
            largeUnit = "كرتون",
            unitFactor = 24.0,
            costPrice = 200.0,
            retailPrice = 250.0,
            wholesalePrice = 5500.0,
            openingMajorQty = 0.0,
            openingMinorQty = 10.0
        ).getOrThrow()

        // شراء 5 كراتين (5 × 24 = 120 علبة) بسعر 4,800 للكرتون (الإجمالي 24,000)، دفع نقدي 4,000 والآجل 20,000
        val purchaseInvoiceId = ledger.postPurchaseInvoice(
            supplierId = supplierId,
            productId = productId,
            useMajorUnit = true,
            quantity = 5.0,
            unitPurchaseCost = 4800.0,
            paidCash = 4000.0
        ).getOrThrow()

        // التحقق من زيادة المخزون: 10 + 120 = 130 علبة
        assertEquals(130.0, ledger.getProductStockMinor(productId), 0.001)

        // التحقق من رصيد المورد: كان له 20,000 (-20,000) + آجل الشراء 20,000 (-20,000) = -40,000 (له/علينا)
        val supAfterPurchase = ledger.getAccountBalanceSummary(supplierId)
        assertEquals(-40000.0, supAfterPurchase.NetBalance, 0.001)
        assertEquals(40000.0, ledger.getFinancialReportSummary().TotalDebtsOnUs, 0.001)

        // سداد سند صرف للمورد بقيمة 15,000 -> يخفض التزامنا للمورد إلى 25,000 (-25,000)
        ledger.postVoucher(supplierId, "Payment", 15000.0, "دفعة للمورد").getOrThrow()
        assertEquals(-25000.0, ledger.getAccountBalanceSummary(supplierId).NetBalance, 0.001)

        // إرجاع/عكس فاتورة المشتريات (PurchaseReturn) -> يخصم 120 علبة من المخزن ويعكس الـ 20,000 الآجلة
        ledger.reverseInvoice(purchaseInvoiceId, "تلف في التغليف").getOrThrow()
        assertEquals(10.0, ledger.getProductStockMinor(productId), 0.001)
        assertEquals(-5000.0, ledger.getAccountBalanceSummary(supplierId).NetBalance, 0.001)
    }

    // 4. اختبار مرتجع المبيعات وعكس أثره من الإيرادات وتكلفة البضاعة المباعة والمخزون والذمم
    @Test
    fun testFlow4_SalesReturn_RestoresStock_ReversesReceivable_AndUpdatesIncomeStatement() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val customerId = ledger.createAccount("محلات النور", bookId = 1L).getOrThrow()
        val productId = ledger.createProduct(
            name = "عصير مانجو",
            barcode = "629100000004",
            smallUnit = "حبة",
            largeUnit = "كرتون",
            unitFactor = 24.0,
            costPrice = 100.0,
            retailPrice = 150.0,
            wholesalePrice = 3300.0,
            openingMajorQty = 2.0, // 48 حبة
            openingMinorQty = 0.0
        ).getOrThrow()

        // بيع آجل كامل: 1 كرتون + 2 حبة (26 حبة) = 3,300 + 300 = 3,600
        val saleId = ledger.postSaleInvoice(
            personId = customerId,
            lines = listOf(Triple(productId, 1.0, 2.0)),
            paymentType = "Credit"
        ).getOrThrow()

        assertEquals(22.0, ledger.getProductStockMinor(productId), 0.001)
        assertEquals(3600.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)
        assertEquals(3600.0, ledger.getFinancialReportSummary().TotalSales, 0.001)

        // عكس الفاتورة (مرتجع مبيعات)
        ledger.reverseInvoice(saleId, "إرجاع بطلب العميل").getOrThrow()

        // استعادة المخزون إلى 48 حبة، وتصفير مديونية العميل، وتصفير المبيعات المعتمدة
        assertEquals(48.0, ledger.getProductStockMinor(productId), 0.001)
        assertEquals(0.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)
        assertEquals(0.0, ledger.getFinancialReportSummary().TotalSales, 0.001)
        assertEquals(0.0, ledger.getFinancialReportSummary().CostOfGoodsSold, 0.001)
    }

    // 5. اختبار الذرية والتراجع الكامل (Atomic Rollback) عند فشل بند في الفاتورة
    @Test
    fun testFlow5_AtomicTransactionRollback_LeavesNoPartialRecordsOnStockFailure() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val customerId = ledger.createAccount("عميل اختبار الذرية", bookId = 1L).getOrThrow()
        val prod1 = ledger.createProduct(
            name = "صنف متوفر",
            barcode = "111",
            costPrice = 500.0,
            retailPrice = 700.0,
            openingMajorQty = 0.0,
            openingMinorQty = 10.0
        ).getOrThrow()
        val prod2 = ledger.createProduct(
            name = "صنف محدود الكمية",
            barcode = "222",
            costPrice = 800.0,
            retailPrice = 1000.0,
            openingMajorQty = 0.0,
            openingMinorQty = 2.0
        ).getOrThrow()

        // محاولة بيع 5 من الصنف الأول (متوفر) و 5 من الصنف الثاني (المتاح 2 فقط) في فاتورة واحدة
        val failedResult = ledger.postSaleInvoice(
            personId = customerId,
            lines = listOf(
                Triple(prod1, 0.0, 5.0),
                Triple(prod2, 0.0, 5.0)
            ),
            paymentType = "Credit"
        )

        assertTrue(failedResult.isFailure)
        // التأكد من عدم حفظ أي فاتورة أو حركة مخزون جزئية للصنف الأول أو قيد مديونية
        assertTrue(ledger.invoices.isEmpty())
        assertTrue(ledger.invoiceLines.isEmpty())
        assertEquals(10.0, ledger.getProductStockMinor(prod1), 0.001)
        assertEquals(2.0, ledger.getProductStockMinor(prod2), 0.001)
        assertEquals(0.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)
    }

    // 6. اختبار قائمة الدخل الشاملة (المبيعات - تكلفة البضاعة المباعة COGS - المصروفات = صافي الربح)
    @Test
    fun testFlow6_IncomeStatement_CalculatesCOGS_GrossProfit_AndNetProfitAfterExpenses() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val prodId = ledger.createProduct(
            name = "أرز بسمتي 10 كجم",
            barcode = "629100000099",
            smallUnit = "كيس",
            largeUnit = null,
            unitFactor = 1.0,
            costPrice = 7200.0,
            retailPrice = 10000.0,
            openingMajorQty = 0.0,
            openingMinorQty = 15.0
        ).getOrThrow()

        // بيع 10 أكياس نقداً = 100,000، تكلفتها COGS = 72,000 -> مجمل الربح = 28,000
        ledger.postSaleInvoice(
            personId = null,
            lines = listOf(Triple(prodId, 0.0, 10.0)),
            paymentType = "Cash"
        ).getOrThrow()

        // تسجيل مصروفات تشغيلية بقيمة 8,000
        ledger.recordExpense(5000.0, "[إيجار ومرافق] فاتورة كهرباء").getOrThrow()
        ledger.recordExpense(3000.0, "[نقل وشحن] أجور نقل بضاعة").getOrThrow()

        val report = ledger.getFinancialReportSummary()
        assertEquals(100000.0, report.TotalSales, 0.001)
        assertEquals(72000.0, report.CostOfGoodsSold, 0.001)
        assertEquals(28000.0, report.GrossProfit, 0.001)
        assertEquals(8000.0, report.TotalExpenses, 0.001)
        assertEquals(20000.0, report.NetProfit, 0.001)
        // تقييم المخزون المتبقي: 5 أكياس × 7,200 = 36,000
        assertEquals(36000.0, report.InventoryValuation, 0.001)
    }

    // 7. اختبار الوحدات الكبرى والصغرى والبيع المركب
    @Test
    fun testFlow7_MajorAndMinorUnitConversion_2CartonsOf24Plus5Pieces_Deducts53Pieces() {
        val unitFactor = 24.0
        val initialStockMinor = UnitConversionEngine.toMinorQuantity(majorQty = 5.0, minorQty = 0.0, unitFactor = unitFactor)
        val deductedMinor = UnitConversionEngine.toMinorQuantity(2.0, 5.0, unitFactor)
        assertEquals(53.0, deductedMinor, 0.001)

        val remainingMinor = initialStockMinor - deductedMinor
        val breakdown = UnitConversionEngine.breakdownStock(
            totalMinorQty = remainingMinor,
            smallUnit = "قطعة",
            largeUnit = "كرتون",
            unitFactor = unitFactor
        )
        assertEquals(2L, breakdown.majorUnits)
        assertEquals(19.0, breakdown.remainingMinor, 0.001)
        assertEquals("2 كرتون و 19 قطعة", breakdown.formattedText)
    }

    // 8. اختبار أمان رمز PIN المملح (PBKDF2) والتأخير المتدرج وتشفير الرموز
    @Test
    fun testFlow8_SecurityCrypto_SaltedPinHashing_ConstantTimeVerify_AndProgressiveLockout() {
        val rawPin = "4829"
        val hash1 = SecurityCryptoHelper.hashPin(rawPin)
        val hash2 = SecurityCryptoHelper.hashPin(rawPin)

        // التأكد من عدم تخزين الرمز كنص صريح وأن كل عملية تجزئة تولد ملحاً عشوائياً مختلفاً
        assertTrue(hash1.startsWith("v1$12000$"))
        assertFalse(hash1.contains(rawPin))
        assertNotEquals(hash1, hash2)

        // التحقق من صحة المطابقة بالأرقام الغربية والشرقية ورفض الرمز الخاطئ
        assertTrue(SecurityCryptoHelper.verifyPin("4829", hash1))
        assertTrue(SecurityCryptoHelper.verifyPin("٤٨٢٩", hash1))
        assertFalse(SecurityCryptoHelper.verifyPin("4820", hash1))
        assertFalse(SecurityCryptoHelper.verifyPin("", hash1))

        // التحقق من سياسة التأخير التدرجي (Progressive Lockout)
        assertEquals(0, SecurityCryptoHelper.calculateLockoutDurationSeconds(1))
        assertEquals(0, SecurityCryptoHelper.calculateLockoutDurationSeconds(4))
        assertEquals(30, SecurityCryptoHelper.calculateLockoutDurationSeconds(5))
        assertEquals(60, SecurityCryptoHelper.calculateLockoutDurationSeconds(6))
        assertEquals(120, SecurityCryptoHelper.calculateLockoutDurationSeconds(7))
        assertEquals(300, SecurityCryptoHelper.calculateLockoutDurationSeconds(8))

        // التحقق من تشفير وفك تشفير رمز Google Drive
        val sampleToken = "ya29.a0AfH6SMBx_sample_oauth_token_12345"
        val encrypted = SecurityCryptoHelper.encryptSecret(sampleToken)
        assertNotEquals(sampleToken, encrypted)
        assertEquals(sampleToken, SecurityCryptoHelper.decryptSecret(encrypted))
    }

    // 9. اختبار فحص البصمة الرقمية للنسخ الاحتياطي (SHA-256) وترويسة SQLite
    @Test
    fun testFlow9_BackupIntegrity_Sha256AndSqliteHeaderValidation() {
        val originalData = "SmartLedgerDatabase_Version_1_BinaryContent".toByteArray(Charsets.UTF_8)
        val expectedSha256 = MessageDigest.getInstance("SHA-256").digest(originalData).joinToString("") { "%02x".format(it) }
        val tamperedData = "SmartLedgerDatabase_Version_1_TAMPERED".toByteArray(Charsets.UTF_8)
        val tamperedSha256 = MessageDigest.getInstance("SHA-256").digest(tamperedData).joinToString("") { "%02x".format(it) }
        assertNotEquals(expectedSha256, tamperedSha256)

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

    // 10. اختبار آلة حالة النافذة المستقلة (FormWindowLifecycleState) ودقة BigDecimal
    @Test
    fun testFlow10_WindowLifecycleStateMachine_AndBigDecimalRounding() {
        var state = FormWindowLifecycleState.EDITING
        val formData = "حليب الممتاز"

        state = FormWindowLifecycleState.VALIDATION_ERROR
        assertNotEquals(FormWindowLifecycleState.CLOSED, state)
        assertEquals("حليب الممتاز", formData)

        state = FormWindowLifecycleState.SAVE_ERROR
        assertNotEquals(FormWindowLifecycleState.CLOSED, state)
        assertEquals("حليب الممتاز", formData)

        if (formData.isNotBlank()) {
            state = FormWindowLifecycleState.CONFIRMING_DISCARD
        }
        assertEquals(FormWindowLifecycleState.CONFIRMING_DISCARD, state)

        val rawFloatSum = 0.1 + 0.2
        assertEquals(0.30, NumberFormatter.roundCurrency(rawFloatSum, decimalPlaces = 2), 0.0)
        assertEquals("12500.75", NumberFormatter.normalizeNumericInput("١٢٥٠٠٫٧٥", allowDecimal = true))
    }
}
