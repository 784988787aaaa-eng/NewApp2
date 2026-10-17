package com.smartledger.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.entity.BusinessIdentityEntity
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
 * اختبارات التكامل الفعلية مع قاعدة بيانات Room SQLite الحقيقية (RoomAccountingIntegrationTest).
 *
 * تختبر الكود الإنتاجي الفعلي (`SmartLedgerDatabase`, `AccountRepository`, `InventoryRepository`,
 * `InvoiceRepository`, `VoucherRepository`, `ExpenseRepository`, `ReportRepository`, `BusinessIdentityRepository`,
 * `CreateSaleUseCase`, `CreatePurchaseUseCase`) وتتحقق من استعلامات SQL والمعاملات الذرية وإعادة ربط المستودعات بعد الاستعادة.
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

        // 1. بيع 6 قطع بالآجل (المتاح 10 قطع)
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

        // 2. محاولة بيع 7 قطع باستخدام مسودة قديمة تدعي أن المتاح لا يزال 10 قطع!
        // يجب أن ترفض المعاملة داخل InvoiceRepository العملية لأن الرصيد الفعلي في Room أصبح 4 قطع فقط!
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

        // 3. إصدار فاتورة ثانية سليمة بـ 2 قطعة والتأكد من عدم تكرار رقم الفاتورة
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

        // 4. التحقق من الإلغاء المحاسبي الموثق (القيد العكسي) دون حذف السجل الأصلي
        val voucherId = voucherRepo.createVoucher(customerId, "Receive", 1500.0, "دفعة من العميل").getOrThrow()
        val beforeCancelBalance = accountRepo.getAccountSummaries(1L).first().first { it.PersonId == customerId }.NetBalance
        // 5000 افتتاحي + 3600 فاتورة آجلة - 1500 سند قبض = 7100
        assertEquals(7100.0, beforeCancelBalance, 0.001)

        val revOpId = accountRepo.deleteOperation(voucherId, "خطأ في إدخال مبلغ السند").getOrThrow()
        assertTrue(revOpId > 0L)

        // بقاء الحركة الأصلية + إضافة القيد العكسي وعودة الرصيد إلى 8600
        val allOps = accountRepo.getAccountOperations(customerId).first()
        assertTrue(allOps.any { it.Id == voucherId })
        assertTrue(allOps.any { it.Id == revOpId && it.ReferenceNumber == "REV-OP-$voucherId" })
        val afterCancelBalance = accountRepo.getAccountSummaries(1L).first().first { it.PersonId == customerId }.NetBalance
        assertEquals(8600.0, afterCancelBalance, 0.001)
    }

    @Test
    fun testRoomIntegration_DynamicDatabaseProvider_SurvivesCloseAndReopenOnRestore() = runBlocking {
        // حفظ حساب في القاعدة الأولى
        val firstId = accountRepo.saveAccount(
            person = PersonEntity(BookId = 1, Name = "عميل قبل الاستعادة", CreatedAt = "2026-10-17 10:00:00"),
            openingBalance = 1000.0,
            isDebit = true
        ).getOrThrow()
        assertTrue(firstId > 0L)

        // محاكاة إغلاق القاعدة الأولى واستبدالها بقاعدة ثانية مستعادة دون إعادة إنشاء المستودعات (Repositories)
        val context = ApplicationProvider.getApplicationContext<Context>()
        activeDb.close()
        activeDb = Room.inMemoryDatabaseBuilder(context, SmartLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SmartLedgerDatabase.seedInitialData(activeDb)
        SmartLedgerDatabase.notifyDatabaseRestored()

        // التحقق من أن نفس نسخة AccountRepository و BusinessIdentityRepository تعمل على القاعدة الجديدة فوراً دون استثناء
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
