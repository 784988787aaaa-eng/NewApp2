package com.smartledger.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.entity.BusinessIdentityEntity
import com.smartledger.pro.data.local.entity.ExpenseEntity
import com.smartledger.pro.data.local.entity.PersonEntity
import com.smartledger.pro.data.local.entity.ProductEntity
import com.smartledger.pro.data.repository.*
import com.smartledger.pro.domain.usecase.CreatePurchaseUseCase
import com.smartledger.pro.domain.usecase.CreateSaleUseCase
import com.smartledger.pro.domain.usecase.SaleLineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبارات التكامل الفعلية مع قاعدة بيانات Room SQLite الحقيقية بالإصدار 2 (RoomAccountingIntegrationTest).
 */
@RunWith(AndroidJUnit4::class)
class RoomAccountingIntegrationTest {

    private lateinit var activeDb: SmartLedgerDatabase
    private lateinit var accountRepo: AccountRepository
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var invoiceRepo: InvoiceRepository
    private lateinit var voucherRepo: VoucherRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var reportRepo: ReportRepository
    private lateinit var identityRepo: BusinessIdentityRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var unitRepo: MeasurementUnitRepository
    private lateinit var financialAccountRepo: FinancialAccountRepository
    private lateinit var warehouseRepo: WarehouseRepository
    private lateinit var createSaleUseCase: CreateSaleUseCase
    private lateinit var createPurchaseUseCase: CreatePurchaseUseCase

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        activeDb = Room.inMemoryDatabaseBuilder(context, SmartLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SmartLedgerDatabase.seedInitialData(activeDb)

        accountRepo = AccountRepository { activeDb }
        inventoryRepo = InventoryRepository { activeDb }
        invoiceRepo = InvoiceRepository { activeDb }
        voucherRepo = VoucherRepository { activeDb }
        expenseRepo = ExpenseRepository { activeDb }
        reportRepo = ReportRepository { activeDb }
        identityRepo = BusinessIdentityRepository { activeDb }
        categoryRepo = CategoryRepository { activeDb }
        unitRepo = MeasurementUnitRepository { activeDb }
        financialAccountRepo = FinancialAccountRepository { activeDb }
        warehouseRepo = WarehouseRepository { activeDb }
        createSaleUseCase = CreateSaleUseCase(invoiceRepo)
        createPurchaseUseCase = CreatePurchaseUseCase(invoiceRepo)
    }

    @After
    fun tearDown() {
        runCatching { activeDb.close() }
    }

    @Test
    fun testRoomIntegration_CashAndCreditSale_StockReverification_AndSequentialInvoiceNumbers() = runBlocking {
        val customerId = accountRepo.saveAccount(
            person = PersonEntity(BookId = 1, Name = "عميل اختبار Room", CreatedAt = "2026-10-17 10:00:00"),
            openingBalance = 5000.0,
            isDebit = true
        ).getOrThrow()

        val productId = inventoryRepo.saveProduct(
            product = ProductEntity(
                Barcode = "6290001",
                Name = "صنف اختبار مخزني",
                SmallUnit = "قطعة",
                LargeUnit = "كرتون",
                UnitFactor = 10.0,
                CostPrice = 400.0,
                RetailPrice = 600.0,
                WholesalePrice = 5800.0,
                CreatedAt = "2026-10-17 10:00:00"
            ),
            openingQty = 10.0
        ).getOrThrow()

        val customerSummary = accountRepo.getAccountSummaries(1L).first().first { it.PersonId == customerId }
        assertEquals(5000.0, customerSummary.NetBalance, 0.001)

        val draft1 = SaleLineDraft(
            productId = productId,
            name = "صنف اختبار مخزني",
            barcode = "6290001",
            smallUnit = "قطعة",
            largeUnit = "كرتون",
            unitFactor = 10.0,
            majorQuantity = 0.0,
            minorQuantity = 6.0,
            minorUnitPrice = 600.0,
            majorUnitPrice = 5800.0,
            minorUnitCost = 400.0,
            availableStockMinor = 10.0
        )
        val sale1 = createSaleUseCase.execute(
            draftLines = listOf(draft1),
            customer = customerSummary,
            paymentType = "Credit",
            discountAmount = 0.0,
            paidAmountInput = 0.0,
            notes = "فاتورة أولى",
            allowNegativeStock = false
        ).getOrThrow()

        assertEquals(4.0, activeDb.productDao().getProductStock(productId), 0.001)

        val staleDraft2 = draft1.copy(minorQuantity = 7.0, availableStockMinor = 10.0)
        val failedConcurrentSale = createSaleUseCase.execute(
            draftLines = listOf(staleDraft2),
            customer = customerSummary,
            paymentType = "Cash",
            discountAmount = 0.0,
            paidAmountInput = 4200.0,
            notes = "محاولة بيع متزامنة تتجاوز الرصيد الفعلي",
            allowNegativeStock = false
        )
        assertTrue(failedConcurrentSale.isFailure)
        assertEquals(4.0, activeDb.productDao().getProductStock(productId), 0.001)

        val validDraft2 = draft1.copy(minorQuantity = 2.0, availableStockMinor = 4.0)
        val sale2 = createSaleUseCase.execute(
            draftLines = listOf(validDraft2),
            customer = customerSummary,
            paymentType = "Cash",
            discountAmount = 0.0,
            paidAmountInput = 1200.0,
            notes = "فاتورة ثانية",
            allowNegativeStock = false
        ).getOrThrow()

        assertNotEquals(sale1.InvoiceNumber, sale2.InvoiceNumber)
        assertEquals(2.0, activeDb.productDao().getProductStock(productId), 0.001)

        val voucherId = voucherRepo.createVoucher(customerId, "Receive", 1500.0, "دفعة من العميل").getOrThrow()
        val beforeCancelBalance = accountRepo.getAccountSummaries(1L).first().first { it.PersonId == customerId }.NetBalance
        assertEquals(7100.0, beforeCancelBalance, 0.001)

        val revOpId = accountRepo.deleteOperation(voucherId, "خطأ في إدخال مبلغ السند").getOrThrow()
        assertTrue(revOpId > 0L)

        val allOps = accountRepo.getAccountOperations(customerId).first()
        assertTrue(allOps.any { it.Id == voucherId })
        assertTrue(allOps.any { it.Id == revOpId && it.ReferenceNumber == "REV-OP-$voucherId" })
        val afterCancelBalance = accountRepo.getAccountSummaries(1L).first().first { it.PersonId == customerId }.NetBalance
        assertEquals(8600.0, afterCancelBalance, 0.001)
    }

    @Test
    fun testRoomIntegration_FlexibleCategories_FinancialAccounts_InternalTransfers_AndWarehouses() = runBlocking {
        // 1. التصنيفات الهرمية ومنع الحذف النهائي عند الارتباط بنفقة
        val parentCatId = categoryRepo.saveCategory(domain = "EXPENSE", name = "مصاريف إدارية").getOrThrow()
        val childCatId = categoryRepo.saveCategory(domain = "EXPENSE", name = "اتصالات وإنترنت", parentId = parentCatId).getOrThrow()
        assertTrue(categoryRepo.saveCategory(id = parentCatId, domain = "EXPENSE", name = "مصاريف إدارية", parentId = childCatId).isFailure)

        // 2. الصناديق والمحافظ والتحويل الداخلي الذري
        val cashboxId = financialAccountRepo.saveFinancialAccount(
            name = "صندوق الكاشير",
            accountType = "CASH_BOX",
            openingBalance = 50000.0,
            allowNegativeBalance = false,
            isDefault = true
        ).getOrThrow()
        val walletId = financialAccountRepo.saveFinancialAccount(
            name = "محفظة إلكترونية",
            accountType = "E_WALLET",
            openingBalance = 10000.0,
            allowNegativeBalance = false
        ).getOrThrow()

        // تسجيل مصروف مرتبط بالتصنيف والصندوق
        val expenseId = expenseRepo.saveExpense(
            ExpenseEntity(
                ExpenseDate = "2026-10-17 12:00:00",
                CategoryId = childCatId,
                Amount = 5000.0,
                Statement = "اشتراك إنترنت",
                PaymentSource = "صندوق الكاشير",
                FinancialAccountId = cashboxId,
                CreatedAt = "2026-10-17 12:00:00"
            )
        ).getOrThrow()

        // محاولة حذف التصنيف المرتبط بنفقة -> يتم تعطيله بدلاً من حذفه
        categoryRepo.deleteOrDeactivateCategory(childCatId).getOrThrow()
        val deactivatedCat = activeDb.categoryDao().getCategoryById(childCatId)
        assertNotNull(deactivatedCat)
        assertFalse(deactivatedCat!!.IsActive)

        // تحويل داخلي ذري بقيمة 15,000 مع رسوم 100 من الصندوق إلى المحفظة
        financialAccountRepo.createInternalTransfer(
            fromAccountId = cashboxId,
            toAccountId = walletId,
            amount = 15000.0,
            feeAmount = 100.0
        ).getOrThrow()

        val accountSummaries = financialAccountRepo.observeAccountBalances().first()
        val cashboxSummary = accountSummaries.first { it.Id == cashboxId }
        val walletSummary = accountSummaries.first { it.Id == walletId }
        // الصندوق: 50,000 افتتاحي - 5,000 مصروف - 15,100 تحويل ورسوم = 29,900
        assertEquals(29900.0, cashboxSummary.NetBalance, 0.001)
        // المحفظة: 10,000 افتتاحي + 15,000 تحويل وارد = 25,000
        assertEquals(25000.0, walletSummary.NetBalance, 0.001)

        // أرشفة المصروف واستعادته مع الحفاظ على نفس المعرف الأصلي (expenseId)
        expenseRepo.deleteExpense(expenseId, "أرشفة مؤقتة")
        val deletedExpenseItem = accountRepo.getAllDeletedItems().first().first { it.EntityType == "Expense" && it.OriginalId == expenseId }
        accountRepo.restoreDeletedItem(deletedExpenseItem).getOrThrow()
        val restoredExpense = activeDb.expenseDao().getExpenseById(expenseId)
        assertNotNull(restoredExpense)
        assertEquals(0, restoredExpense!!.IsArchived ?: 0)
    }

    @Test
    fun testRoomIntegration_DynamicDatabaseProvider_SurvivesCloseAndReopenOnRestore() = runBlocking {
        val firstId = accountRepo.saveAccount(
            person = PersonEntity(BookId = 1, Name = "عميل قبل الاستعادة", CreatedAt = "2026-10-17 10:00:00"),
            openingBalance = 1000.0,
            isDebit = true
        ).getOrThrow()
        assertTrue(firstId > 0L)

        val context = ApplicationProvider.getApplicationContext<Context>()
        activeDb.close()
        activeDb = Room.inMemoryDatabaseBuilder(context, SmartLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SmartLedgerDatabase.seedInitialData(activeDb)
        SmartLedgerDatabase.notifyDatabaseRestored()

        val restoredCustomerId = accountRepo.saveAccount(
            person = PersonEntity(BookId = 1, Name = "عميل بعد الاستعادة", CreatedAt = "2026-10-17 11:00:00"),
            openingBalance = 2500.0,
            isDebit = true
        ).getOrThrow()
        assertTrue(restoredCustomerId > 0L)

        val summaries = accountRepo.getAccountSummaries(1L).first()
        assertEquals(1, summaries.size)
        assertEquals("عميل بعد الاستعادة", summaries.first().PersonName)
        assertEquals(2500.0, summaries.first().NetBalance, 0.001)

        identityRepo.saveBusinessIdentity(
            BusinessIdentityEntity(
                Id = 1,
                TradeName = "منشأة بعد الاستعادة",
                LegalName = "منشأة بعد الاستعادة",
                Phone1 = "777111222",
                Address = "صنعاء",
                ReceiptHeader = "مرحباً",
                ReceiptFooter = "شكراً"
            )
        )
        assertEquals("منشأة بعد الاستعادة", identityRepo.observeBusinessIdentity().first()?.TradeName)
    }
}
