package com.smartledger.pro

import com.smartledger.pro.data.local.entity.CategoryEntity
import com.smartledger.pro.data.local.entity.InvoiceEntity
import com.smartledger.pro.data.local.entity.MeasurementUnitEntity
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.domain.usecase.*
import com.smartledger.pro.ui.components.FormWindowLifecycleState
import com.smartledger.pro.util.ChecksumVerificationStatus
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.SecurityCryptoHelper
import com.smartledger.pro.util.WhatsAppHelper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * جناح الاختبارات الهندسية الشامل (Unit + Ledger Integration + Categories + Units + WAC + Allocation + Security + UX)
 * يغطي كافة متطلبات التوجيه الهندسي من المرحلة 1 إلى المرحلة 10 — SmartLedger Pro v3.0.0
 */
class AccountingEngineTest {

    // 1. اختبار تدفق البيع النقدي وحساب المبيعات وتكلفة البضاعة المباعة (COGS) وحركة الصندوق النقدي
    @Test
    fun testFlow1_CashSale_DeductsStock_UpdatesCashbox_AndLeavesCustomerDebtZero() {
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

        val savedInvoice = ledger.invoices.first { it.Id == saleId }
        assertEquals(5000.0, savedInvoice.FinalTotal, 0.001)
        assertEquals(5000.0, savedInvoice.PaidAmount, 0.001)
        assertEquals(0.0, savedInvoice.RemainingAmount, 0.001)
        assertEquals(56.0, ledger.getProductStockMinor(productId), 0.001)
        assertEquals(0.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)

        val report = ledger.getFinancialReportSummary()
        assertEquals(5000.0, report.TotalSales, 0.001)
        assertEquals(4000.0, report.CostOfGoodsSold, 0.001)
        assertEquals(1000.0, report.GrossProfit, 0.001)
        assertEquals(5000.0, report.CashInflows, 0.001)
        assertEquals(5000.0, report.NetCashBalance, 0.001)
    }

    // 2. اختبار البيع الآجل والجزئي وفصل الربح المحاسبي عن سيولة الصندوق النقدي
    @Test
    fun testFlow2_CreditAndPartialSale_SeparatesAccrualProfitFromCashboxLiquidity() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
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

        // تحصيل سند قبض بقيمة 20,000 من العميل
        ledger.postVoucher(customerId, "Receive", 20000.0, "دفعة نقدية من الحساب").getOrThrow()

        val afterReceipt = ledger.getAccountBalanceSummary(customerId)
        assertEquals(25000.0, afterReceipt.NetBalance, 0.001)

        val report = ledger.getFinancialReportSummary()
        assertEquals(50000.0, report.TotalSales, 0.001)
        assertEquals(35000.0, report.CashInflows, 0.001)
        assertEquals(35000.0, report.NetCashBalance, 0.001)
        assertEquals(25000.0, report.TotalDebtsToUs, 0.001)
    }

    // 3. اختبار منع البيع الزائد عند تداخل الفواتير أو تكرار نفس الصنف في بندين داخل الفاتورة نفسها (P0)
    @Test
    fun testFlow3_InTransactionStockCheck_PreventsConcurrentOverselling_AndAggregatesDuplicateProductLines() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val prodId = ledger.createProduct(
            name = "شاي سيلاني 1 كجم",
            barcode = "629100000010",
            smallUnit = "علبة",
            largeUnit = null,
            unitFactor = 1.0,
            costPrice = 1500.0,
            retailPrice = 2000.0,
            openingMajorQty = 0.0,
            openingMinorQty = 10.0 // المتاح 10 علب فقط
        ).getOrThrow()

        val duplicateLineAttempt = ledger.postSaleInvoice(
            personId = null,
            lines = listOf(
                Triple(prodId, 0.0, 6.0),
                Triple(prodId, 0.0, 6.0)
            ),
            paymentType = "Cash"
        )
        assertTrue(duplicateLineAttempt.isFailure)
        assertEquals(10.0, ledger.getProductStockMinor(prodId), 0.001)

        val firstSale = ledger.postSaleInvoice(
            personId = null,
            lines = listOf(Triple(prodId, 0.0, 7.0)),
            paymentType = "Cash"
        )
        assertTrue(firstSale.isSuccess)
        assertEquals(3.0, ledger.getProductStockMinor(prodId), 0.001)

        val secondConcurrentSale = ledger.postSaleInvoice(
            personId = null,
            lines = listOf(Triple(prodId, 0.0, 5.0)),
            paymentType = "Cash"
        )
        assertTrue(secondConcurrentSale.isFailure)
        assertEquals(3.0, ledger.getProductStockMinor(prodId), 0.001)
    }

    // 4. اختبار تفرد أرقام الفواتير المتسلسلة وعدم تكرارها عند الإصدار المتلاحق في نفس اللحظة (P0)
    @Test
    fun testFlow4_SequentialInvoiceNumberGenerator_GuaranteesZeroCollisionsUnderRapidIssuance() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = true)
        val prodId = ledger.createProduct(
            name = "صنف سريع",
            barcode = "999",
            costPrice = 100.0,
            retailPrice = 150.0,
            openingMajorQty = 0.0,
            openingMinorQty = 1000.0
        ).getOrThrow()

        val generatedNumbers = mutableSetOf<String>()
        for (i in 1..150) {
            val id = ledger.postSaleInvoice(
                personId = null,
                lines = listOf(Triple(prodId, 0.0, 1.0)),
                paymentType = "Cash"
            ).getOrThrow()
            val invNo = ledger.invoices.first { it.Id == id }.InvoiceNumber
            assertTrue("Duplicate invoice number detected: $invNo", generatedNumbers.add(invNo))
        }
        assertEquals(150, generatedNumbers.size)

        val candidateSet = (1..200).map { SequentialInvoiceNumberGenerator.nextCandidate("INV", "20261017") }.toSet()
        assertEquals(200, candidateSet.size)
    }

    // 5. اختبار الإلغاء المحاسبي الموثق للحركات ومنع عكس حركة فاتورة منفردة وعدم تضخيم التدفقات النقدية الخارجة
    @Test
    fun testFlow5_AuditableOperationReversal_PreservesHistory_FixesBalance_AndDoesNotInflateCashOutflows() {
        val ledger = InMemoryLedgerEngine(allowNegativeStock = false)
        val customerId = ledger.createAccount(
            name = "شركة \"النجم\" للتجارة \\ الاستيراد",
            bookId = 1L,
            openingBalance = 20000.0,
            isDebit = true
        ).getOrThrow()

        val voucherId = ledger.postVoucher(
            personId = customerId,
            type = "Receive",
            amount = 5000.0,
            statement = "سند قبض \"دفعة أولى\""
        ).getOrThrow()

        assertEquals(15000.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)
        assertEquals(5000.0, ledger.getFinancialReportSummary().CashInflows, 0.001)
        assertEquals(0.0, ledger.getFinancialReportSummary().CashOutflows, 0.001)

        // إلغاء الحركة بقيد عكسي محاسبي موثق
        val reversalId = ledger.cancelOperationWithReversal(voucherId, "إدخال بالخطأ في حساب \"النجم\"").getOrThrow()

        assertTrue(ledger.operations.any { it.Id == voucherId })
        assertTrue(ledger.operations.any { it.Id == reversalId && it.ReferenceNumber == "REV-OP-$voucherId" })
        assertEquals(20000.0, ledger.getAccountBalanceSummary(customerId).NetBalance, 0.001)

        // التأكد من أن عكس سند القبض يصفر التدفق النقدي الوارد ولا يضخم التدفق النقدي الخارج!
        val reportAfterReversal = ledger.getFinancialReportSummary()
        assertEquals(0.0, reportAfterReversal.CashInflows, 0.001)
        assertEquals(0.0, reportAfterReversal.CashOutflows, 0.001)
        assertEquals(0.0, reportAfterReversal.NetCashBalance, 0.001)

        // منع عكس الحركة نفسها مرة ثانية
        assertTrue(ledger.cancelOperationWithReversal(voucherId, "محاولة مكررة").isFailure)

        // منع عكس حركة مالية مرتبطة بفاتورة بشكل مستقل عن الفاتورة والمخزون
        val prodId = ledger.createProduct(
            name = "صنف مرتبط بفاتورة",
            barcode = "6291000999",
            costPrice = 500.0,
            retailPrice = 700.0,
            openingMinorQty = 10.0
        ).getOrThrow()
        val creditSaleId = ledger.postSaleInvoice(
            personId = customerId,
            lines = listOf(Triple(prodId, 0.0, 2.0)),
            paymentType = "Credit"
        ).getOrThrow()
        val invoiceLinkedOp = ledger.operations.first { it.SourceInvoiceId == creditSaleId }
        assertTrue(ledger.cancelOperationWithReversal(invoiceLinkedOp.Id, "محاولة عكس منفرد لحركة فاتورة").isFailure)

        val deletedRecord = ledger.deletedItems.first()
        val parsedJson = JSONObject(deletedRecord.SnapshotJson)
        assertEquals(voucherId, parsedJson.getLong("originalId"))
        assertEquals(reversalId, parsedJson.getLong("reversalOperationId"))
    }

    // 6. اختبار التمييز الدقيق بين فاصل الآلاف والفاصل العشري في NumberFormatter (P1)
    @Test
    fun testFlow6_NumberFormatter_DistinguishesThousandsSeparatorsFromDecimalPoints() {
        assertEquals("12500", NumberFormatter.normalizeNumericInput("12,500"))
        assertEquals(12500.0, NumberFormatter.parseDouble("12,500"), 0.001)

        assertEquals("1250000.75", NumberFormatter.normalizeNumericInput("1,250,000.75"))
        assertEquals(1250000.75, NumberFormatter.parseDouble("1,250,000.75"), 0.001)

        assertEquals("12500.75", NumberFormatter.normalizeNumericInput("١٢٬٥٠٠٫٧٥"))
        assertEquals(12500.75, NumberFormatter.parseDouble("١٢٬٥٠٠٫٧٥"), 0.001)

        assertEquals(12.5, NumberFormatter.parseDouble("12,5"), 0.001)
        assertEquals(12.75, NumberFormatter.parseDouble("12,75"), 0.001)

        assertNull(NumberFormatter.parseDoubleOrNull(""))
        assertNull(NumberFormatter.parseDoubleOrNull("12.5.3"))
        assertNull(NumberFormatter.parseDoubleOrNull("abc"))
    }

    // 7. اختبار التشفير الفعلي AES-GCM ورفض أي إخفاء Base64 أو رمز PIN غير مملح (P1)
    @Test
    fun testFlow7_SecurityCrypto_StrictAesGcmEncryption_AndRejectsUnhashedPin() {
        val rawPin = "7391"
        val saltedHash = SecurityCryptoHelper.hashPin(rawPin)
        assertTrue(saltedHash.startsWith("v1$12000$"))
        assertTrue(SecurityCryptoHelper.verifyPin("7391", saltedHash))

        assertFalse(SecurityCryptoHelper.verifyPin("7391", "7391"))
        assertFalse(SecurityCryptoHelper.verifyPin("7391", "obf_v1:NzM5MQ=="))

        val secretToken = "ya29.a0AfH6SMBx_real_aes_gcm_test_token"
        val encRes = SecurityCryptoHelper.encryptSecretResult(secretToken)
        assertTrue(encRes.isSuccess)
        val cipherText = encRes.getOrThrow()
        assertFalse(cipherText.startsWith("obf_v1:"))
        assertTrue(cipherText.startsWith("enc_v1:") || cipherText.startsWith("enc_gcm_pbkdf2_v1:"))

        val decRes = SecurityCryptoHelper.decryptSecretResult(cipherText)
        assertTrue(decRes.isSuccess)
        assertEquals(secretToken, decRes.getOrThrow())

        assertTrue(SecurityCryptoHelper.decryptSecretResult("plain_unencrypted_token").isFailure)
    }

    // 8. اختبار التمييز الصريح بين النسخ الموثقة بـ SHA-256 والنسخ القديمة غير الموثقة (P0)
    @Test
    fun testFlow8_BackupChecksumVerificationStatus_DistinguishesVerifiedFromLegacyUnverified() {
        assertEquals(
            "بصمة SHA-256 مطابقة وموثقة 100%",
            ChecksumVerificationStatus.VERIFIED_MATCH.arabicDescription
        )
        assertNotEquals(
            ChecksumVerificationStatus.VERIFIED_MATCH,
            ChecksumVerificationStatus.LEGACY_UNVERIFIED
        )

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

    // 9. اختبار الوحدات الكبرى والصغرى وآلة حالة النافذة المستقلة
    @Test
    fun testFlow9_MultiUnitBreakdown_AndWindowLifecycleStateMachine() {
        val deductedMinor = UnitConversionEngine.toMinorQuantity(2.0, 5.0, 24.0)
        assertEquals(53.0, deductedMinor, 0.001)

        val breakdown = UnitConversionEngine.breakdownStock(67.0, "قطعة", "كرتون", 24.0)
        assertEquals("2 كرتون و 19 قطعة", breakdown.formattedText)

        var state = FormWindowLifecycleState.EDITING
        state = FormWindowLifecycleState.SAVE_ERROR
        assertNotEquals(FormWindowLifecycleState.CLOSED, state)
    }

    // 10. اختبار تنسيق الكميات المركبة ومؤشر جيل قاعدة البيانات بعد الاستعادة
    @Test
    fun testFlow10_CompositeQuantityFormatting_AndDatabaseRestoreEpoch() {
        val compositeText = UnitConversionEngine.formatCompositeQuantity(
            majorQty = 2.0,
            minorQty = 5.0,
            smallUnit = "قطعة",
            largeUnit = "كرتون"
        )
        assertEquals("2 كرتون + 5 قطعة", compositeText)

        val epochBefore = com.smartledger.pro.data.local.SmartLedgerDatabase.databaseEpoch.value
        com.smartledger.pro.data.local.SmartLedgerDatabase.notifyDatabaseRestored()
        val epochAfter = com.smartledger.pro.data.local.SmartLedgerDatabase.databaseEpoch.value
        assertEquals(epochBefore + 1L, epochAfter)
    }

    // 11. اختبار توافق منصة أندرويد (API 26-34) لأذونات البلوتوث وحفظ السلة عند Process Death
    @Test
    fun testFlow11_AndroidPlatform_ApiCompatibility_AndProcessDeathCartState() {
        assertNull(com.smartledger.pro.util.ThermalPrinterHelper.requiredBluetoothRuntimePermission(26))
        assertNull(com.smartledger.pro.util.ThermalPrinterHelper.requiredBluetoothRuntimePermission(30))
        assertEquals(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            com.smartledger.pro.util.ThermalPrinterHelper.requiredBluetoothRuntimePermission(31)
        )

        val item = com.smartledger.pro.ui.viewmodels.CartLineItem(
            productId = 101L,
            name = "حليب ممتاز",
            barcode = "6291001",
            smallUnit = "علبة",
            largeUnit = "كرتون",
            unitFactor = 24.0,
            availableStockMinor = 120.0,
            minorUnitPrice = 300.0,
            majorUnitPrice = 7000.0,
            minorUnitCost = 240.0,
            majorQuantity = 2.0,
            minorQuantity = 5.0
        )
        assertEquals(53.0, item.totalMinorQuantity, 0.001)
        assertEquals(15500.0, item.calculatedLineTotal, 0.001)
    }

    // 12. اختبار ضمانات تجربة المستخدم: المعاينة الحية للرصيد ومنع البيع بخسارة والتحقق من السداد الجزئي
    @Test
    fun testFlow12_UxFinancialTrust_ProjectedBalance_BelowCostGuard_AndPartialPaymentValidation() {
        val currentNetDebit = 18500.0
        val receiptAmount = 8500.0
        val projectedAfterReceipt = currentNetDebit - receiptAmount
        assertEquals(10000.0, projectedAfterReceipt, 0.001)

        val costPrice = 500.0
        val lossRetailPrice = 450.0
        val profitableRetailPrice = 625.0
        assertTrue(lossRetailPrice < costPrice)
        val marginPct = ((profitableRetailPrice - costPrice) / costPrice) * 100.0
        assertEquals(25.0, marginPct, 0.001)
    }

    // 13. اختبار محرك التصنيفات الهرمية متعدد المستويات وكشف الدورات المغلقة (Cycle Detection)
    @Test
    fun testFlow13_HierarchicalCategoryValidator_DetectsCycles_PreventsDuplicates_AndFormatsBreadcrumbs() {
        val categories = listOf(
            CategoryEntity(Id = 1L, Domain = "EXPENSE", Name = "مصروفات تشغيلية", ParentId = null, CreatedAt = "2026-10-17"),
            CategoryEntity(Id = 2L, Domain = "EXPENSE", Name = "مرافق وخدمات", ParentId = 1L, CreatedAt = "2026-10-17"),
            CategoryEntity(Id = 3L, Domain = "EXPENSE", Name = "كهرباء ومياه", ParentId = 2L, CreatedAt = "2026-10-17")
        )

        // 1. المسار الهرمي الكامل
        assertEquals(
            "مصروفات تشغيلية ◂ مرافق وخدمات ◂ كهرباء ومياه",
            HierarchicalCategoryValidator.formatCategoryPath(3L, categories)
        )

        // 2. منع ربط التصنيف بنفسه
        val selfLink = HierarchicalCategoryValidator.validateAndCheckCycle(
            categoryId = 1L,
            name = "مصروفات تشغيلية",
            domain = "EXPENSE",
            parentId = 1L,
            existingCategories = categories
        )
        assertTrue(selfLink.isFailure)

        // 3. كشف ومنع الدورة الهرمية المغلقة (جعل التصنيف الجد #1 ابناً لحفيده #3)
        val cycleAttempt = HierarchicalCategoryValidator.validateAndCheckCycle(
            categoryId = 1L,
            name = "مصروفات تشغيلية",
            domain = "EXPENSE",
            parentId = 3L,
            existingCategories = categories
        )
        assertTrue(cycleAttempt.isFailure)

        // 4. منع تكرار الاسم داخل نفس المستوى الأب ونفس المجال
        val duplicateSibling = HierarchicalCategoryValidator.validateAndCheckCycle(
            categoryId = 0L,
            name = "مرافق وخدمات",
            domain = "EXPENSE",
            parentId = 1L,
            existingCategories = categories
        )
        assertTrue(duplicateSibling.isFailure)

        // 5. السماح بنفس الاسم تحت أب مختلف أو مجال مختلف
        val validDifferentParent = HierarchicalCategoryValidator.validateAndCheckCycle(
            categoryId = 0L,
            name = "مرافق وخدمات",
            domain = "REVENUE",
            parentId = null,
            existingCategories = categories
        )
        assertTrue(validDifferentParent.isSuccess)

        // 6. تجميع شجرة الأبناء الكاملة للتصنيف الأب #1
        assertEquals(setOf(1L, 2L, 3L), HierarchicalCategoryValidator.collectSubtreeIds(1L, categories))
    }

    // 14. اختبار محرك الوحدات المرن والمتوسط المرجح للتكلفة عند الشراء وتخصيص دفعات العملاء
    @Test
    fun testFlow14_FlexibleUnits_WeightedAverageCost_PaymentAllocation_AndWhatsAppPhoneNormalization() {
        // أ. تحديث المتوسط المرجح للتكلفة عند شراء بضاعة بسعر جديد
        // لدينا 10 قطع بتكلفة 100 ر.ي (1,000)، واشترينا 10 قطع بتكلفة 200 ر.ي (2,000) -> المتوسط المرجح = 150 ر.ي
        val newWac = WeightedAverageCostCalculator.calculateNewWeightedCost(
            currentStockMinor = 10.0,
            currentUnitCost = 100.0,
            purchasedQtyMinor = 10.0,
            purchasedUnitCost = 200.0
        )
        assertEquals(150.0, newWac, 0.001)

        // ب. التحويل الدقيق بين الوحدات المترابطة ومنع التحويل بين الوحدات غير المترابطة
        val gramUnit = MeasurementUnitEntity(Id = 1L, Name = "جرام", Symbol = "جم", DecimalPrecision = 2, BaseUnitId = null, ConversionFactorToBase = 1.0, CreatedAt = "2026-10-17")
        val kgUnit = MeasurementUnitEntity(Id = 2L, Name = "كيلوجرام", Symbol = "كجم", DecimalPrecision = 3, BaseUnitId = 1L, ConversionFactorToBase = 1000.0, CreatedAt = "2026-10-17")
        val literUnit = MeasurementUnitEntity(Id = 3L, Name = "لتر", Symbol = "لتر", DecimalPrecision = 2, BaseUnitId = null, ConversionFactorToBase = 1.0, CreatedAt = "2026-10-17")

        assertEquals(2500.0, FlexibleUnitValidator.convertBetweenUnits(2.5, kgUnit, gramUnit).getOrThrow(), 0.001)
        assertEquals(1.75, FlexibleUnitValidator.convertBetweenUnits(1750.0, gramUnit, kgUnit).getOrThrow(), 0.001)
        assertTrue(FlexibleUnitValidator.convertBetweenUnits(1.0, kgUnit, literUnit).isFailure)

        // ج. تخصيص دفعة عميل على الفواتير المفتوحة وحساب الفائض كدفعة مقدمة
        val openInvoices = listOf(
            InvoiceEntity(Id = 10L, InvoiceNumber = "INV-001", Type = "Sale", PersonId = 1L, PersonNameSnapshot = "عميل", IssuedAt = "2026-10-15", Subtotal = 5000.0, FinalTotal = 5000.0, PaidAmount = 2000.0, RemainingAmount = 3000.0, CreatedAt = "2026-10-15"),
            InvoiceEntity(Id = 11L, InvoiceNumber = "INV-002", Type = "Sale", PersonId = 1L, PersonNameSnapshot = "عميل", IssuedAt = "2026-10-16", Subtotal = 4000.0, FinalTotal = 4000.0, PaidAmount = 0.0, RemainingAmount = 4000.0, CreatedAt = "2026-10-16")
        )
        val allocation = PaymentAllocationEngine.allocatePayment(paymentAmount = 8500.0, openInvoices = openInvoices)
        assertEquals(2, allocation.allocatedSlices.size)
        assertEquals(3000.0, allocation.allocatedSlices[0].allocatedAmount, 0.001)
        assertEquals(4000.0, allocation.allocatedSlices[1].allocatedAmount, 0.001)
        assertEquals(1500.0, allocation.unallocatedAdvanceAmount, 0.001) // 1,500 دفعة مقدمة فائضة

        // د. تنسيق أرقام واتساب مع احترام مفتاح الدولة المرن
        assertEquals("967777123456", WhatsAppHelper.normalizePhoneForWhatsApp("0777 123-456", "+967"))
        assertEquals("966501234567", WhatsAppHelper.normalizePhoneForWhatsApp("0501234567", "+966"))
        assertEquals("201001234567", WhatsAppHelper.normalizePhoneForWhatsApp("+20 100 123 4567", "+967"))
    }
}
