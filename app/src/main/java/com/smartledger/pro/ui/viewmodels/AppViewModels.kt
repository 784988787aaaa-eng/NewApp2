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
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val reportRepo = ReportRepository(db)
    private val voucherRepo = VoucherRepository(db)
    private val identityDao = db.businessIdentityDao()

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityDao.getIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val metrics: StateFlow<DashboardMetrics> = reportRepo.getDashboardMetrics()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
        )

    val recentOps: StateFlow<List<RecentOperationWithPerson>> = voucherRepo.observeRecentOperations(limit = 15)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
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

    val accounts: StateFlow<List<AccountBalanceSummary>> = combine(
        _searchQuery,
        _selectedBookId,
        _balanceFilter
    ) { query, bookId, balFilter ->
        Triple(query, bookId, balFilter)
    }.flatMapLatest { (query, bookId, balFilter) ->
        val baseFlow = if (query.isBlank()) {
            accountRepo.getAccountSummaries(bookId)
        } else {
            accountRepo.searchAccounts(query.trim(), bookId)
        }
        baseFlow.map { list ->
            when (balFilter) {
                "DEBIT" -> list.filter { it.NetBalance > 0.001 }
                "CREDIT" -> list.filter { it.NetBalance < -0.001 }
                else -> list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onBookSelected(bookId: Long?) {
        _selectedBookId.value = if (bookId == null || bookId <= 0) null else bookId
    }

    fun onBalanceFilterChanged(filter: String) {
        _balanceFilter.value = filter
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedBookId.value = null
        _balanceFilter.value = "ALL"
    }

    suspend fun saveAccountAsync(
        id: Long = 0,
        name: String,
        bookId: Long,
        phone: String?,
        creditLimit: Double,
        openingBalance: Double,
        isDebit: Boolean,
        note: String?
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val person = PersonEntity(
            Id = id,
            BookId = bookId,
            Name = name.trim(),
            Phone = phone?.trim()?.ifBlank { null },
            AccountCode = if (id > 0) null else "ACC-" + (System.currentTimeMillis() % 100000),
            CreditLimit = NumberFormatter.roundCurrency(creditLimit.coerceAtLeast(0.0)),
            Note = note?.trim()?.ifBlank { null },
            CreatedAt = now
        )
        return accountRepo.saveAccount(person, openingBalance, isDebit)
    }

    fun deleteAccount(id: Long, reason: String? = null) {
        viewModelScope.launch {
            accountRepo.softDeleteAccount(id, reason)
        }
    }

    fun getOperationsForAccount(personId: Long): Flow<List<OperationEntity>> {
        return accountRepo.getAccountOperations(personId)
    }

    fun deleteOperation(operationId: Long, reason: String? = null) {
        viewModelScope.launch {
            accountRepo.deleteOperation(operationId, reason)
        }
    }

    suspend fun createQuickVoucherAsync(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null
    ): Result<Long> {
        return voucherRepo.createVoucher(personId, type, amount, statement, refNumber)
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

    val products: StateFlow<List<ProductStockItem>> = combine(
        _searchQuery,
        _stockFilter
    ) { q, filter ->
        Pair(q, filter)
    }.flatMapLatest { (q, filter) ->
        val flow = if (q.isBlank()) inventoryRepo.getProducts() else inventoryRepo.searchProducts(q.trim())
        flow.map { list ->
            when (filter) {
                "LOW_STOCK" -> list.filter { it.CurrentStockMinor <= it.AlertThreshold && it.CurrentStockMinor > 0.0 }
                "OUT_OF_STOCK" -> list.filter { it.CurrentStockMinor <= 0.0 }
                "MULTI_UNIT" -> list.filter { !it.LargeUnit.isNullOrBlank() && it.UnitFactor > 1.0 }
                else -> list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onStockFilterChanged(filter: String) {
        _stockFilter.value = filter
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _stockFilter.value = "ALL"
    }

    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>> {
        return inventoryRepo.getProductMovements(productId)
    }

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
        openingMinorQty: Double = 0.0
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val safeFactor = if (unitFactor <= 0.0 || largeUnit.isNullOrBlank()) 1.0 else unitFactor
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
            LargeUnit = largeUnit?.trim()?.ifBlank { null },
            UnitFactor = safeFactor,
            CostPrice = NumberFormatter.roundCurrency(costPrice.coerceAtLeast(0.0)),
            RetailPrice = NumberFormatter.roundCurrency(retailPrice.coerceAtLeast(0.0)),
            WholesalePrice = NumberFormatter.roundCurrency(effectiveMajorPrice.coerceAtLeast(0.0)),
            AlertThreshold = alertThreshold.coerceAtLeast(0.0),
            OpeningQuantityMinor = totalOpeningMinor,
            OpeningCost = NumberFormatter.roundCurrency(costPrice.coerceAtLeast(0.0)),
            CreatedAt = now
        )
        return inventoryRepo.saveProduct(product, totalOpeningMinor)
    }

    fun deleteProduct(id: Long, reason: String? = null) {
        viewModelScope.launch {
            inventoryRepo.softDeleteProduct(id, reason)
        }
    }

    suspend fun commitStockAdjustmentAsync(
        productId: Long,
        actualQty: Double,
        currentQty: Double,
        notes: String? = null
    ): Result<Long> {
        return inventoryRepo.commitStockAdjustment(productId, actualQty, currentQty, notes)
    }
}

class PosViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val invoiceRepo = InvoiceRepository(db)
    private val productRepo = InventoryRepository(db)
    private val accountRepo = AccountRepository(db)
    private val identityDao = db.businessIdentityDao()
    private val prefsRepo = AppPreferencesRepository.getInstance(application)

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityDao.getIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<ProductStockItem>> = productRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = 1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentInvoices: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedCustomer by mutableStateOf<AccountBalanceSummary?>(null)
    val cartLines = mutableStateListOf<CartLineItem>()

    fun addItemToCart(product: ProductStockItem, asMajorUnit: Boolean = false) {
        val index = cartLines.indexOfFirst { it.productId == product.Id }
        if (index >= 0) {
            val existing = cartLines[index]
            cartLines[index] = if (asMajorUnit && product.UnitFactor > 1.0 && !product.LargeUnit.isNullOrBlank()) {
                existing.copy(majorQuantity = existing.majorQuantity + 1.0)
            } else {
                existing.copy(minorQuantity = existing.minorQuantity + 1.0)
            }
        } else {
            val safeFactor = if (product.UnitFactor <= 0.0 || product.LargeUnit.isNullOrBlank()) 1.0 else product.UnitFactor
            val majorPrice = if (product.WholesalePrice > 0.0) product.WholesalePrice else (product.RetailPrice * safeFactor)
            cartLines.add(
                CartLineItem(
                    productId = product.Id,
                    name = product.Name,
                    barcode = product.Barcode,
                    smallUnit = product.SmallUnit,
                    largeUnit = product.LargeUnit,
                    unitFactor = safeFactor,
                    availableStockMinor = product.CurrentStockMinor,
                    minorUnitPrice = product.RetailPrice,
                    majorUnitPrice = majorPrice,
                    minorUnitCost = product.CostPrice,
                    majorQuantity = if (asMajorUnit && safeFactor > 1.0) 1.0 else 0.0,
                    minorQuantity = if (asMajorUnit && safeFactor > 1.0) 0.0 else 1.0
                )
            )
        }
    }

    fun updateLineQuantities(productId: Long, majorQty: Double, minorQty: Double) {
        val index = cartLines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            if (majorQty <= 0.0 && minorQty <= 0.0) {
                cartLines.removeAt(index)
            } else {
                cartLines[index] = cartLines[index].copy(
                    majorQuantity = majorQty.coerceAtLeast(0.0),
                    minorQuantity = minorQty.coerceAtLeast(0.0)
                )
            }
        }
    }

    fun updateLineUnitPrice(productId: Long, newMinorPrice: Double, newMajorPrice: Double) {
        val index = cartLines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            cartLines[index] = cartLines[index].copy(
                minorUnitPrice = NumberFormatter.roundCurrency(newMinorPrice.coerceAtLeast(0.0)),
                majorUnitPrice = NumberFormatter.roundCurrency(newMajorPrice.coerceAtLeast(0.0))
            )
        }
    }

    fun removeItemFromCart(productId: Long) {
        cartLines.removeAll { it.productId == productId }
    }

    fun clearCart() {
        cartLines.clear()
        selectedCustomer = null
    }

    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> {
        return invoiceRepo.getInvoiceLines(invoiceId)
    }

    suspend fun reverseInvoice(invoiceId: Long, reason: String = "مرتجع مبيعات"): Result<Boolean> {
        return invoiceRepo.reverseInvoice(invoiceId, reason)
    }

    suspend fun checkout(
        paymentType: String, // Cash, Credit, Partial
        discount: Double = 0.0,
        paidAmount: Double = 0.0,
        notes: String? = null
    ): Result<InvoiceEntity> {
        if (cartLines.isEmpty()) return Result.failure(Exception("السلة فارغة"))

        val customer = selectedCustomer
        if ((paymentType == "Credit" || paymentType == "Partial") && customer == null) {
            return Result.failure(Exception("لا يمكن إصدار فاتورة آجلة أو جزئية بدون اختيار حساب عميل مسجل."))
        }

        val settings = prefsRepo.settings.value
        if (!settings.allowNegativeStock) {
            val insufficient = cartLines.firstOrNull { it.totalMinorQuantity > it.availableStockMinor }
            if (insufficient != null) {
                return Result.failure(
                    Exception("الكمية المطلوبة للصنف «${insufficient.name}» تتجاوز المتاح في المخزن (${insufficient.availableStockMinor.toInt()} ${insufficient.smallUnit}).")
                )
            }
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val prefix = settings.invoicePrefix.ifBlank { "INV" }
        val invoiceNo = "$prefix-$dateCode-${(System.currentTimeMillis() % 10000)}"

        val subtotal = NumberFormatter.roundCurrency(cartLines.sumOf { it.calculatedLineTotal })
        val safeDiscount = NumberFormatter.roundCurrency(discount.coerceIn(0.0, subtotal))
        val finalTotal = NumberFormatter.roundCurrency(kotlin.math.max(0.0, subtotal - safeDiscount))
        val actualPaid = NumberFormatter.roundCurrency(
            when (paymentType) {
                "Cash" -> finalTotal
                "Credit" -> 0.0
                else -> paidAmount.coerceIn(0.0, finalTotal)
            }
        )
        val remaining = NumberFormatter.roundCurrency(kotlin.math.max(0.0, finalTotal - actualPaid))

        val invoice = InvoiceEntity(
            InvoiceNumber = invoiceNo,
            Type = "Sale",
            PersonId = customer?.PersonId ?: 0L,
            PersonNameSnapshot = customer?.PersonName ?: "عميل نقدي عام",
            IssuedAt = now,
            Subtotal = subtotal,
            DiscountAmount = safeDiscount,
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
                UnitPrice = NumberFormatter.roundCurrency(effectiveUnitPrice, 4),
                LineCost = NumberFormatter.roundCurrency(item.minorUnitCost, 4),
                LineTotal = NumberFormatter.roundCurrency(item.calculatedLineTotal, 2),
                SortOrder = index + 1
            )
        }

        val result = invoiceRepo.createInvoice(invoice, lines)
        return if (result.isSuccess) {
            val savedId = result.getOrDefault(0L)
            clearCart()
            Result.success(invoice.copy(Id = savedId))
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("تعذر حفظ الفاتورة"))
        }
    }
}

data class CartLineItem(
    val productId: Long,
    val name: String,
    val barcode: String? = null,
    val smallUnit: String,
    val largeUnit: String?,
    val unitFactor: Double,
    val availableStockMinor: Double = 0.0,
    val minorUnitPrice: Double,
    val majorUnitPrice: Double,
    val minorUnitCost: Double,
    val majorQuantity: Double = 0.0,
    val minorQuantity: Double = 1.0
) {
    private val calc
        get() = UnitConversionEngine.calculateCompositeSale(
            majorQty = majorQuantity,
            minorQty = minorQuantity,
            unitFactor = unitFactor,
            minorSalePrice = minorUnitPrice,
            majorSalePrice = majorUnitPrice,
            minorUnitCost = minorUnitCost
        )

    val totalMinorQuantity: Double
        get() = calc.totalMinorQuantity

    val calculatedLineTotal: Double
        get() = calc.totalLinePrice

    val formattedBreakdown: String
        get() = UnitConversionEngine.breakdownStock(
            totalMinorQty = totalMinorQuantity,
            smallUnit = smallUnit,
            largeUnit = largeUnit,
            unitFactor = unitFactor
        ).formattedText
}

class VouchersViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val voucherRepo = VoucherRepository(db)
    private val accountRepo = AccountRepository(db)

    val accounts: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVouchers: StateFlow<List<RecentOperationWithPerson>> = voucherRepo.observeRecentOperations(limit = 30)
        .map { list -> list.filter { it.Type == "Receive" || it.Type == "Payment" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createVoucher(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null
    ): Result<Long> {
        return voucherRepo.createVoucher(personId, type, amount, statement, refNumber)
    }
}

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val expenseRepo = ExpenseRepository(db)

    val expenses: StateFlow<List<ExpenseEntity>> = expenseRepo.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun saveExpenseAsync(
        amount: Double,
        statement: String,
        category: String,
        source: String,
        photoUri: String? = null
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val fullStatement = if (category.isNotBlank() && !statement.startsWith("[$category]")) {
            "[$category] ${statement.trim()}"
        } else {
            statement.trim()
        }
        val entity = ExpenseEntity(
            ExpenseDate = now,
            Amount = NumberFormatter.roundCurrency(amount),
            Statement = fullStatement,
            PaymentSource = source.trim().ifBlank { "الخزينة الرئيسية" },
            ReceiptPhotoUri = photoUri,
            CreatedAt = now
        )
        return expenseRepo.saveExpense(entity)
    }

    fun deleteExpense(id: Long, reason: String? = null) {
        viewModelScope.launch {
            expenseRepo.deleteExpense(id, reason)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val reportRepo = ReportRepository(db)
    private val identityDao = db.businessIdentityDao()

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityDao.getIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedPeriod = MutableStateFlow("ALL")
    val selectedPeriod = _selectedPeriod.asStateFlow()

    val summary: StateFlow<FinancialReportSummary> = _selectedPeriod
        .flatMapLatest { period ->
            val fromDate = calculateFromDate(period)
            if (fromDate == null) {
                reportRepo.getFinancialReportSummary()
            } else {
                reportRepo.getFinancialReportFromDate(fromDate)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FinancialReportSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
    }

    private fun calculateFromDate(period: String): String? {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (period) {
            "TODAY" -> fmt.format(cal.time)
            "WEEK" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                fmt.format(cal.time)
            }
            "MONTH" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                fmt.format(cal.time)
            }
            "YEAR" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                fmt.format(cal.time)
            }
            else -> null
        }
    }
}
