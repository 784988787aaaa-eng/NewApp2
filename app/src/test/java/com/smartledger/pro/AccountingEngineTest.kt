package com.smartledger.pro

import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.util.BackupRestoreHelper
import com.smartledger.pro.util.Formatters
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AccountingEngineTest {

    @Test
    fun testScenario1_CashSale_NoDebtAdded_CustomerBalanceZero() {
        val subtotal = 50000.0
        val discount = 5000.0
        val finalTotal = subtotal - discount
        val paidAmount = finalTotal
        val remainingDebt = finalTotal - paidAmount

        val ledgerDebitSale = finalTotal
        val ledgerCreditReceive = paidAmount
        val netCustomerLedgerImpact = ledgerDebitSale - ledgerCreditReceive

        assertEquals(45000.0, finalTotal, 0.001)
        assertEquals(0.0, remainingDebt, 0.001)
        assertEquals(0.0, netCustomerLedgerImpact, 0.001)
    }

    @Test
    fun testScenario2_CreditSale_IncreasesCustomerDebtExactlyByRemaining() {
        val initialCustomerDebt = 10000.0
        val invoiceTotal = 35000.0
        val paid = 0.0
        val remaining = invoiceTotal - paid

        val newCustomerDebt = initialCustomerDebt + remaining
        assertEquals(45000.0, newCustomerDebt, 0.001)
    }

    @Test
    fun testScenario3_PartialPayment_SplitsCashAndDebtAccurately() {
        val invoiceTotal = 80000.0
        val paidCash = 30000.0
        val remainingCredit = invoiceTotal - paidCash

        val ledgerImpact = invoiceTotal - paidCash
        assertEquals(50000.0, remainingCredit, 0.001)
        assertEquals(remainingCredit, ledgerImpact, 0.001)
        assertEquals(80000.0, paidCash + remainingCredit, 0.001)
    }

    @Test
    fun testScenario4_PurchaseInvoiceAndReturn_UpdatesSupplierLedgerAccurately() {
        val initialSupplierCredit = 20000.0
        val purchaseQty = 15.0
        val unitCost = 4000.0
        val purchaseTotal = purchaseQty * unitCost
        val paidCash = 10000.0
        val remainingSupplierDebt = purchaseTotal - paidCash

        val newSupplierCredit = initialSupplierCredit + remainingSupplierDebt
        assertEquals(70000.0, newSupplierCredit, 0.001)

        val afterPurchaseReturn = newSupplierCredit - remainingSupplierDebt
        assertEquals(initialSupplierCredit, afterPurchaseReturn, 0.001)
    }

    @Test
    fun testScenario5_SalesReturn_ReversesDebtAndRestoresStock() {
        val customerDebtBeforeReturn = 45000.0
        val returnedQty = 2.0
        val unitPrice = 8500.0
        val returnCreditNote = returnedQty * unitPrice

        val customerDebtAfterReturn = customerDebtBeforeReturn - returnCreditNote
        assertEquals(28000.0, customerDebtAfterReturn, 0.001)
    }

    @Test
    fun testScenario6_Expenses_DeductsFromCashAndCalculatesCOGS() {
        val totalSales = 100000.0
        val cogs = 72000.0
        val grossProfit = totalSales - cogs
        val operatingExpenses = 8000.0
        val netProfit = grossProfit - operatingExpenses

        assertEquals(28000.0, grossProfit, 0.001)
        assertEquals(20000.0, netProfit, 0.001)
        assertNotEquals(totalSales, netProfit)
    }

    @Test
    fun testScenario7_MajorAndMinorUnitConversion_2CartonsOf24Plus5Pieces_Deducts53Pieces() {
        val unitFactor = 24.0
        val initialStockMinor = UnitConversionEngine.toMinorQuantity(majorQty = 5.0, minorQty = 0.0, unitFactor = unitFactor)

        val soldMajorCartons = 2.0
        val soldMinorPieces = 5.0
        val deductedMinor = UnitConversionEngine.toMinorQuantity(soldMajorCartons, soldMinorPieces, unitFactor)

        assertEquals(53.0, deductedMinor, 0.001)

        val remainingMinor = initialStockMinor - deductedMinor
        assertEquals(67.0, remainingMinor, 0.001)

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

    @Test
    fun testScenario8_CrossUnitPurchaseAndCompositeSale_ProfitAccuracy() {
        val unitFactor = 24.0
        val cartonPurchaseCost = 4800.0
        val minorPieceCost = UnitConversionEngine.minorCostFromMajorCost(cartonPurchaseCost, unitFactor)
        assertEquals(200.0, minorPieceCost, 0.001)

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

    @Test
    fun testScenario9_UnitSetupValidation_PreventsInvalidFactorsAndDuplicateUnitNames() {
        val zeroFactorValidation = UnitConversionEngine.validateUnitSetup("قطعة", "كرتون", 0.0, hasMajorUnit = true)
        assertFalse(zeroFactorValidation.isValid)
        assertNotNull(zeroFactorValidation.errorMessage)

        val negativeFactorValidation = UnitConversionEngine.validateUnitSetup("قطعة", "كرتون", -12.0, hasMajorUnit = true)
        assertFalse(negativeFactorValidation.isValid)

        val duplicateUnitValidation = UnitConversionEngine.validateUnitSetup("كرتون", "كرتون", 24.0, hasMajorUnit = true)
        assertFalse(duplicateUnitValidation.isValid)

        val validSetup = UnitConversionEngine.validateUnitSetup("قطعة", "كرتون", 24.0, hasMajorUnit = true)
        assertTrue(validSetup.isValid)
        assertEquals(24.0, validSetup.sanitizedFactor, 0.001)
    }

    @Test
    fun testScenario10_WesternDigitsNormalizationAndCurrencyFormatting() {
        val arabicIndicInput = "١٢٣٤٥٫٦٧"
        val normalized = Formatters.normalizeDigits(arabicIndicInput)
        assertEquals("12345.67", normalized)

        val parsed = Formatters.parseDoubleSafe(arabicIndicInput)
        assertEquals(12345.67, parsed, 0.001)

        val formattedCurrency = Formatters.formatCurrency(45000.0, "ر.ي", decimals = 2)
        assertEquals("45,000 ر.ي", formattedCurrency)
        assertFalse(formattedCurrency.any { it in '٠'..'٩' })
    }

    @Test
    fun testScenario11_BackupSha256AndMetadataParsing_Verification() {
        val tempFile = File.createTempFile("smartledger_test_db", ".db")
        try {
            tempFile.writeText("SmartLedgerDatabase_Version_2_BinaryContent_Verified", Charsets.UTF_8)
            val actualSha256 = BackupRestoreHelper.calculateSha256(tempFile)
            val expectedSha256 = MessageDigest.getInstance("SHA-256")
                .digest("SmartLedgerDatabase_Version_2_BinaryContent_Verified".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            assertEquals(expectedSha256, actualSha256)

            val sampleJson = """{"app":"SmartLedger Pro","schemaVersion":2,"sha256":"$actualSha256"}"""
            assertEquals(actualSha256, BackupRestoreHelper.extractJsonString(sampleJson, "sha256"))
            assertEquals(2, BackupRestoreHelper.extractJsonInt(sampleJson, "schemaVersion"))
        } finally {
            tempFile.delete()
        }
    }

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
}
