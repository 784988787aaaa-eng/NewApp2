package com.smartledger.pro.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.data.repository.*
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val reportRepo = ReportRepository(db)
    private val operationDao = db.operationDao()
    private val accountRepo = AccountRepository(db)

    val metrics: StateFlow<DashboardMetrics> = reportRepo.getDashboardMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0))

    val recentOps: StateFlow<List<OperationEntity>> = operationDao.getRecentOperationsFlow(limit = 15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountsMap: StateFlow<Map<Long, String>> = accountRepo.getAccountSummaries(null)
        .map { list -> list.associate { it.PersonId to it.PersonName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val accountRepo = AccountRepository(db)
    private val voucherRepo = VoucherRepository(db)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Long?>(null)
    val selectedBookId = _selectedBookId.asStateFlow()

    private val _balanceFilter = MutableStateFlow("ALL")
    val balanceFilter = _balanceFilter.asStateFlow()

    val books: StateFlow<List<BookEntity>> = accountRepo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountBalanceSummary>> = combine(_searchQuery, _selectedBookId, _balanceFilter) { q, b, f ->
        Triple(q, b, f)
    }.flatMapLatest { (query, bookId, balFilter) ->
        val baseFlow = if (query.isBlank()) accountRepo.getAccountSummaries(bookId) else accountRepo.searchAccounts(query.trim(), bookId)
        baseFlow.map { list ->
            when (balFilter) {
                "DEBIT" -> list.filter { it.NetBalance > 0.001 }
                "CREDIT" -> list.filter { it.NetBalance < -0.001 }
                "SETTLED" -> list.filter { kotlin.math.abs(it.NetBalance) <= 0.001 }
                else -> list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun onBookSelected(bookId: Long?) { _selectedBookId.value = if (bookId == null || bookId <= 0) null else bookId }
    fun onBalanceFilterChanged(filter: String) { _balanceFilter.value = filter }

    fun saveAccount(
        id: Long = 0,
        name: String,
        bookId: Long,
        phone: String?,
        creditLimit: Double,
        openingBalance: Double,
        isDebit: Boolean,
        note: String?,
        updateOpeningIfEdited: Boolean = false
    ) {
        viewModelScope.launch {
            val now = Formatters.nowTimestamp()
            val person = PersonEntity(
                Id = id,
                BookId = bookId,
                Name = name.trim(),
                Phone = phone?.trim()?.ifBlank { null },
                AccountCode = if (id > 0) null else "ACC-" + (System.currentTimeMillis() % 100000),
                CreditLimit = creditLimit,
                Note = note?.trim()?.ifBlank { null },
                CreatedAt = now
            )
            accountRepo.saveAccount(person, openingBalance, isDebit, updateOpeningIfEdited)
        }
    }

    fun recordQuickVoucher(personId: Long, type: String, amount: Double, statement: String, refNumber: String? = null) {
        viewModelScope.launch { voucherRepo.createVoucher(personId, type, amount, statement, refNumber) }
    }

    fun deleteAccount(id: Long, reason: String? = null) {
        viewModelScope.launch { accountRepo.softDeleteAccount(id, reason) }
    }

    fun getOperationsForAccount(personId: Long): Flow<List<OperationEntity>> = accountRepo.getAccountOperations(personId)

    fun deleteOperation(operationId: Long, reason: String? = null) {
        viewModelScope.launch { accountRepo.deleteOperation(operationId, reason) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val inventoryRepo = InventoryRepository(db)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _stockFilter = MutableStateFlow("ALL")
    val stockFilter = _stockFilter.asStateFlow()

    val products: StateFlow<List<ProductStockItem>> = combine(_searchQuery, _stockFilter) { q, f -> Pair(q, f) }
        .flatMapLatest { (q, filter) ->
            val baseFlow = if (q.isBlank()) inventoryRepo.getProducts() else inventoryRepo.searchProducts(q.trim())
            baseFlow.map { list ->
                when (filter) {
                    "LOW_STOCK" -> list.filter { it.CurrentStockMinor <= it.AlertThreshold }
                    "IN_STOCK" -> list.filter { it.CurrentStockMinor > it.AlertThreshold }
                    "OUT_OF_STOCK" -> list.filter { it.CurrentStockMinor <= 0.0 }
                    "MULTI_UNIT" -> list.filter { !it.LargeUnit.isNullOrBlank() && it.UnitFactor > 1.0 }
                    else -> list
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun onStockFilterChanged(filter: String) { _stockFilter.value = filter }
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>> = inventoryRepo.getMovementsForProduct(productId)
    suspend fun checkBarcodeConflict(barcode: String?, excludeProductId: Long = 0L): ProductEntity? = inventoryRepo.checkBarcodeConflict(barcode, excludeProductId)

    suspend fun saveProductAsync(
        id: Long = 0,
        barcode: String?,
        name: String,
        categoryId: Long?,
        warehouse: String,
        shelfLocation: String? = null,
        smallUnit: String,
        largeUnit: String?,
        unitFactor: Double,
        costPrice: Double,
        retailPrice: Double,
        wholesalePrice: Double = 0.0,
        alertThreshold: Double,
        openingMajorQty: Double = 0.0,
        openingMinorQty: Double = 0.0,
        notes: String? = null
    ): Result<Long> {
        val now = Formatters.nowTimestamp()
        val safeFactor = if (unitFactor <= 1.0 || largeUnit.isNullOrBlank()) 1.0 else unitFactor
        val totalOpeningMinor = UnitConversionEngine.toMinorQuantity(openingMajorQty, openingMinorQty, safeFactor)
        val effectiveMajorPrice = if (wholesalePrice > 0.0) wholesalePrice else (retailPrice * safeFactor)

        val product = ProductEntity(
            Id = id,
            Barcode = barcode?.trim()?.ifBlank { null },
            Name = name.trim(),
            CategoryId = categoryId,
            Warehouse = warehouse.trim().ifBlank { "المستودع الرئيسي" },
            ShelfLocation = shelfLocation?.trim()?.ifBlank { null },
            SmallUnit = smallUnit.trim().ifBlank { "قطعة" },
            LargeUnit = if (safeFactor > 1.0) largeUnit?.trim()?.ifBlank { null } else null,
            UnitFactor = safeFactor,
            CostPrice = costPrice,
            RetailPrice = retailPrice,
            WholesalePrice = effectiveMajorPrice,
            AlertThreshold = alertThreshold,
            OpeningQuantityMinor = totalOpeningMinor,
            OpeningCost = costPrice,
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now
        )
        return inventoryRepo.saveProduct(product, totalOpeningMinor)
    }

    fun deleteProduct(id: Long, reason: String? = null) {
        viewModelScope.launch { inventoryRepo.softDeleteProduct(id, reason) }
    }

    fun commitStockAdjustment(productId: Long, actualQty: Double, currentQty: Double, notes: String? = null) {
        viewModelScope.launch { inventoryRepo.commitStockAdjustment(productId, actualQty, currentQty, notes) }
    }
}

class PosViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val invoiceRepo = InvoiceRepository(db)
    private val productRepo = InventoryRepository(db)
    private val accountRepo = AccountRepository(db)
    private val identityDao = db.businessIdentityDao()

    val products: StateFlow<List<ProductStockItem>> = productRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = 1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentInvoices: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityDao.getIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var selectedCustomer by mutableStateOf<AccountBalanceSummary?>(null)
    val cartLines = mutableStateListOf<CartLineItem>()

    fun addItemToCart(product: ProductStockItem, asMajorUnit: Boolean = false) {
        val index = cartLines.indexOfFirst { it.productId == product.Id }
        if (index >= 0) {
            val existing = cartLines[index]
            cartLines[index] = if (asMajorUnit && product.UnitFactor > 1.0) existing.copy(majorQuantity = existing.majorQuantity + 1.0)
            else existing.copy(minorQuantity = existing.minorQuantity + 1.0)
        } else {
            val safeFactor = if (product.UnitFactor <= 0.0) 1.0 else product.UnitFactor
            val majorPrice = if (product.WholesalePrice > 0.0) product.WholesalePrice else (product.RetailPrice * safeFactor)
            cartLines.add(
                CartLineItem(
                    productId = product.Id,
                    name = product.Name,
                    barcode = product.Barcode,
                    smallUnit = product.SmallUnit,
                    largeUnit = product.LargeUnit,
                    unitFactor = safeFactor,
                    minorUnitPrice = product.RetailPrice,
                    majorUnitPrice = majorPrice,
                    minorUnitCost = product.CostPrice,
                    availableStockMinor = product.CurrentStockMinor,
                    majorQuantity = if (asMajorUnit && safeFactor > 1.0) 1.0 else 0.0,
                    minorQuantity = if (asMajorUnit && safeFactor > 1.0) 0.0 else 1.0
                )
            )
        }
    }

    fun findAndAddByBarcode(barcode: String): ProductStockItem? {
        val clean = barcode.trim()
        val match = products.value.firstOrNull { it.Barcode?.equals(clean, ignoreCase = true) == true }
        if (match != null) addItemToCart(match, asMajorUnit = false)
        return match
    }

    fun updateLineQuantities(productId: Long, majorQty: Double, minorQty: Double) {
        val index = cartLines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            if (majorQty <= 0.0 && minorQty <= 0.0) cartLines.removeAt(index)
            else cartLines[index] = cartLines[index].copy(majorQuantity = majorQty.coerceAtLeast(0.0), minorQuantity = minorQty.coerceAtLeast(0.0))
        }
    }

    fun updateLinePricing(productId: Long, newMinorPrice: Double, newMajorPrice: Double, lineDiscount: Double) {
        val index = cartLines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            cartLines[index] = cartLines[index].copy(minorUnitPrice = newMinorPrice.coerceAtLeast(0.0), majorUnitPrice = newMajorPrice.coerceAtLeast(0.0), lineDiscount = lineDiscount.coerceAtLeast(0.0))
        }
    }

    fun removeItemFromCart(productId: Long) { cartLines.removeAll { it.productId == productId } }
    fun clearCart() { cartLines.clear(); selectedCustomer = null }
    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> = invoiceRepo.getInvoiceLines(invoiceId)
    suspend fun reverseInvoice(invoiceId: Long, reason: String = "مرتجع مبيعات"): Result<Boolean> = invoiceRepo.reverseInvoice(invoiceId, reason)

    suspend fun checkoutWithDetails(
        paymentType: String,
        discount: Double = 0.0,
        paidAmount: Double = 0.0,
        notes: String? = null
    ): Result<Pair<InvoiceEntity, List<InvoiceLineEntity>>> {
        if (cartLines.isEmpty()) return Result.failure(Exception("سلة الفاتورة فارغة"))
        val now = Formatters.nowTimestamp()
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val invoiceNo = "INV-${dateCode}-${(System.currentTimeMillis() % 10000)}"
        val subtotal = cartLines.sumOf { it.calculatedLineTotal }
        val finalTotal = kotlin.math.max(0.0, subtotal - discount)
        val actualPaid = when (paymentType) {
            "Cash" -> finalTotal
            "Credit" -> 0.0
            else -> paidAmount.coerceIn(0.0, finalTotal)
        }
        val remaining = kotlin.math.max(0.0, finalTotal - actualPaid)

        if (remaining > 0.001 && (selectedCustomer == null || selectedCustomer!!.PersonId <= 0L)) {
            return Result.failure(Exception("لا يمكن إصدار فاتورة آجلة أو جزئية لعميل نقدي عام. يرجى اختيار عميل مسجل أولاً."))
        }

        val invoice = InvoiceEntity(
            InvoiceNumber = invoiceNo,
            Type = "Sale",
            PersonId = selectedCustomer?.PersonId ?: 0L,
            PersonNameSnapshot = selectedCustomer?.PersonName ?: "عميل نقدي عام",
            IssuedAt = now,
            Subtotal = subtotal,
            DiscountAmount = discount,
            TaxAmount = 0.0,
            FinalTotal = finalTotal,
            PaidAmount = actualPaid,
            RemainingAmount = remaining,
            PaymentType = paymentType,
            Notes = notes?.trim()?.ifBlank { null },
            CreatedAt = now
        )

        val lines = cartLines.mapIndexed { index, item ->
            val totalMinorQty = item.totalMinorQuantity
            val effectiveUnitPrice = if (totalMinorQty > 0) item.calculatedLineTotal / totalMinorQty else item.minorUnitPrice
            InvoiceLineEntity(
                InvoiceId = 0L,
                ProductId = item.productId,
                ProductNameSnapshot = item.name,
                BarcodeSnapshot = item.barcode,
                UnitName = item.formattedBreakdown,
                IsMajorUnit = item.majorQuantity > 0,
                ConversionFactor = item.unitFactor,
                QuantityMinor = totalMinorQty,
                UnitPrice = effectiveUnitPrice,
                LineDiscount = item.lineDiscount,
                LineCost = item.minorUnitCost,
                LineTotal = item.calculatedLineTotal,
                SortOrder = index + 1
            )
        }

        val result = invoiceRepo.createInvoice(invoice, lines)
        return if (result.isSuccess) {
            val savedInvoice = invoice.copy(Id = result.getOrThrow())
            clearCart()
            Result.success(Pair(savedInvoice, lines))
        } else Result.failure(result.exceptionOrNull() ?: Exception("تعذر حفظ الفاتورة"))
    }
}

data class CartLineItem(
    val productId: Long,
    val name: String,
    val barcode: String? = null,
    val smallUnit: String,
    val largeUnit: String?,
    val unitFactor: Double,
    val minorUnitPrice: Double,
    val majorUnitPrice: Double,
    val minorUnitCost: Double,
    val availableStockMinor: Double = 0.0,
    val majorQuantity: Double = 0.0,
    val minorQuantity: Double = 1.0,
    val lineDiscount: Double = 0.0
) {
    private val calc
        get() = UnitConversionEngine.calculateCompositeSale(majorQuantity, minorQuantity, unitFactor, minorUnitPrice, majorUnitPrice, minorUnitCost, lineDiscount)
    val totalMinorQuantity: Double get() = calc.totalMinorQuantity
    val calculatedLineTotal: Double get() = calc.totalLinePrice
    val isExceedingStock: Boolean get() = totalMinorQuantity > availableStockMinor
    val formattedBreakdown: String
        get() = UnitConversionEngine.breakdownStock(totalMinorQuantity, smallUnit, largeUnit, unitFactor).formattedText
}

class VouchersViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val voucherRepo = VoucherRepository(db)
    private val accountRepo = AccountRepository(db)

    val accounts: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentVouchers: StateFlow<List<OperationEntity>> = voucherRepo.getRecentVouchers(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createVoucher(personId: Long, type: String, amount: Double, statement: String, refNumber: String? = null): Result<Long> {
        return try { Result.success(voucherRepo.createVoucher(personId, type, amount, statement, refNumber)) }
        catch (e: Exception) { Result.failure(e) }
    }
}

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val expenseRepo = ExpenseRepository(db)

    val expenses: StateFlow<List<ExpenseEntity>> = expenseRepo.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveExpense(amount: Double, statement: String, category: String, source: String, photoUri: String? = null) {
        viewModelScope.launch {
            val now = Formatters.nowTimestamp()
            val fullStatement = if (category.isNotBlank() && !statement.startsWith("[$category]")) "[$category] ${statement.trim()}" else statement.trim()
            expenseRepo.saveExpense(ExpenseEntity(ExpenseDate = now, Amount = amount, Statement = fullStatement, PaymentSource = source.trim().ifBlank { "الخزينة الرئيسية" }, ReceiptPhotoUri = photoUri, CreatedAt = now))
        }
    }

    fun deleteExpense(id: Long, reason: String? = null) {
        viewModelScope.launch { expenseRepo.deleteExpense(id, reason) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val reportRepo = ReportRepository(db)
    private val inventoryRepo = InventoryRepository(db)
    private val accountRepo = AccountRepository(db)

    private val _selectedPeriod = MutableStateFlow("ALL")
    val selectedPeriod = _selectedPeriod.asStateFlow()

    val summary: StateFlow<FinancialReportSummary> = _selectedPeriod
        .flatMapLatest { period ->
            val today = Formatters.todayDate()
            when (period) {
                "TODAY" -> reportRepo.getFinancialReportSummaryByPeriod(today, today)
                "WEEK" -> reportRepo.getFinancialReportSummaryByPeriod(Formatters.startOfCurrentWeekDate(), today)
                "MONTH" -> reportRepo.getFinancialReportSummaryByPeriod(Formatters.startOfCurrentMonthDate(), today)
                "YEAR" -> reportRepo.getFinancialReportSummaryByPeriod(Formatters.startOfCurrentYearDate(), today)
                else -> reportRepo.getFinancialReportSummary()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialReportSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onPeriodSelected(period: String) { _selectedPeriod.value = period }
}
