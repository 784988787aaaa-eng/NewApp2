package com.smartledger.pro.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.data.repository.AppSettingsSnapshot
import com.smartledger.pro.di.AppContainer
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.domain.usecase.SaleLineDraft
import com.smartledger.pro.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val reportRepo = container.reportRepository
    private val voucherRepo = container.voucherRepository
    private val identityRepo = container.businessIdentityRepository

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
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
    private val container = AppContainer.getInstance(application)
    private val accountRepo = container.accountRepository
    private val voucherRepo = container.voucherRepository

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
    private val container = AppContainer.getInstance(application)
    private val inventoryRepo = container.inventoryRepository

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

/**
 * نموذج عرض نقطة البيع السريعة (`PosViewModel`) مع حماية كاملة ضد قتل العملية في الخلفية (`Process Death`):
 * - يحفظ عناصر السلة (`cartLines`) ومعرّف العميل المختار (`selectedCustomer`) تلقائياً داخل `SavedStateHandle`
 *   بصيغة JSON مضغوطة وآمنة ضد `TransactionTooLargeException`.
 * - يستعيد السلة والعميل تلقائياً عند إعادة إنشاء العملية بواسطة نظام أندرويد.
 */
class PosViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, SavedStateHandle())

    private val container = AppContainer.getInstance(application)
    private val invoiceRepo = container.invoiceRepository
    private val productRepo = container.inventoryRepository
    private val accountRepo = container.accountRepository
    private val identityRepo = container.businessIdentityRepository
    private val prefsRepo = container.preferencesRepository
    private val createSaleUseCase = container.createSaleUseCase

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<ProductStockItem>> = productRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = 1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentInvoices: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var _selectedCustomer by mutableStateOf<AccountBalanceSummary?>(null)
    var selectedCustomer: AccountBalanceSummary?
        get() = _selectedCustomer
        set(value) {
            _selectedCustomer = value
            savedStateHandle[KEY_SAVED_CUSTOMER_ID] = value?.PersonId ?: 0L
        }

    val cartLines = mutableStateListOf<CartLineItem>()

    init {
        restoreCartFromSavedState()
        observeCustomersToRestoreSelection()
    }

    private fun observeCustomersToRestoreSelection() {
        viewModelScope.launch {
            customers.collect { list ->
                val savedId = savedStateHandle.get<Long>(KEY_SAVED_CUSTOMER_ID) ?: 0L
                if (savedId > 0L && _selectedCustomer == null) {
                    _selectedCustomer = list.firstOrNull { it.PersonId == savedId }
                }
            }
        }
    }

    private fun persistCartToSavedState() {
        runCatching {
            val arr = JSONArray()
            for (item in cartLines.take(MAX_SAVED_CART_LINES)) {
                arr.put(
                    JSONObject().apply {
                        put("id", item.productId)
                        put("n", item.name)
                        put("bc", item.barcode ?: "")
                        put("su", item.smallUnit)
                        put("lu", item.largeUnit ?: "")
                        put("uf", item.unitFactor)
                        put("st", item.availableStockMinor)
                        put("mp", item.minorUnitPrice)
                        put("jp", item.majorUnitPrice)
                        put("mc", item.minorUnitCost)
                        put("jq", item.majorQuantity)
                        put("mq", item.minorQuantity)
                    }
                )
            }
            savedStateHandle[KEY_SAVED_CART_JSON] = arr.toString()
        }
    }

    private fun restoreCartFromSavedState() {
        val rawJson = savedStateHandle.get<String>(KEY_SAVED_CART_JSON) ?: return
        if (rawJson.isBlank() || cartLines.isNotEmpty()) return
        runCatching {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                cartLines.add(
                    CartLineItem(
                        productId = obj.getLong("id"),
                        name = obj.optString("n"),
                        barcode = obj.optString("bc").ifBlank { null },
                        smallUnit = obj.optString("su", "قطعة"),
                        largeUnit = obj.optString("lu").ifBlank { null },
                        unitFactor = obj.optDouble("uf", 1.0),
                        availableStockMinor = obj.optDouble("st", 0.0),
                        minorUnitPrice = obj.optDouble("mp", 0.0),
                        majorUnitPrice = obj.optDouble("jp", 0.0),
                        minorUnitCost = obj.optDouble("mc", 0.0),
                        majorQuantity = obj.optDouble("jq", 0.0),
                        minorQuantity = obj.optDouble("mq", 1.0)
                    )
                )
            }
        }
    }

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
        persistCartToSavedState()
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
            persistCartToSavedState()
        }
    }

    fun updateLineUnitPrice(productId: Long, newMinorPrice: Double, newMajorPrice: Double) {
        val index = cartLines.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            cartLines[index] = cartLines[index].copy(
                minorUnitPrice = NumberFormatter.roundCurrency(newMinorPrice.coerceAtLeast(0.0)),
                majorUnitPrice = NumberFormatter.roundCurrency(newMajorPrice.coerceAtLeast(0.0))
            )
            persistCartToSavedState()
        }
    }

    fun removeItemFromCart(productId: Long) {
        cartLines.removeAll { it.productId == productId }
        persistCartToSavedState()
    }

    fun clearCart() {
        cartLines.clear()
        selectedCustomer = null
        savedStateHandle[KEY_SAVED_CART_JSON] = ""
        savedStateHandle[KEY_SAVED_CUSTOMER_ID] = 0L
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
        val activeSettings = prefsRepo.settings.value
        val draftLines = cartLines.map { item ->
            SaleLineDraft(
                productId = item.productId,
                name = item.name,
                barcode = item.barcode,
                smallUnit = item.smallUnit,
                largeUnit = item.largeUnit,
                unitFactor = item.unitFactor,
                majorQuantity = item.majorQuantity,
                minorQuantity = item.minorQuantity,
                minorUnitPrice = item.minorUnitPrice,
                majorUnitPrice = item.majorUnitPrice,
                minorUnitCost = item.minorUnitCost,
                availableStockMinor = item.availableStockMinor
            )
        }

        val result = createSaleUseCase.execute(
            draftLines = draftLines,
            customer = selectedCustomer,
            paymentType = paymentType,
            discountAmount = discount,
            paidAmountInput = paidAmount,
            notes = notes,
            allowNegativeStock = activeSettings.allowNegativeStock,
            invoicePrefix = activeSettings.invoicePrefix
        )
        if (result.isSuccess) {
            clearCart()
        }
        return result
    }

    companion object {
        private const val KEY_SAVED_CART_JSON = "pos_saved_cart_json_v1"
        private const val KEY_SAVED_CUSTOMER_ID = "pos_saved_customer_id_v1"
        private const val MAX_SAVED_CART_LINES = 60
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

class PurchasesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val invoiceRepo = container.invoiceRepository
    private val accountRepo = container.accountRepository
    private val inventoryRepo = container.inventoryRepository
    private val createPurchaseUseCase = container.createPurchaseUseCase
    private val prefsRepo = container.preferencesRepository

    val suppliers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries(bookId = 2)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPurchases: StateFlow<List<InvoiceEntity>> = invoiceRepo.getRecentInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createPurchase(
        supplier: AccountBalanceSummary?,
        product: ProductStockItem,
        useMajorUnit: Boolean,
        quantity: Double,
        unitPurchaseCost: Double,
        paidCash: Double,
        notes: String?
    ): Result<Long> {
        return createPurchaseUseCase.execute(
            supplier = supplier,
            product = product,
            useMajorUnit = useMajorUnit,
            quantity = quantity,
            unitPurchaseCost = unitPurchaseCost,
            paidCash = paidCash,
            notes = notes,
            purchasePrefix = prefsRepo.settings.value.purchasePrefix
        )
    }

    suspend fun reversePurchaseInvoice(invoiceId: Long, reason: String?): Result<Boolean> {
        return invoiceRepo.reverseInvoice(invoiceId, reason)
    }
}

class VouchersViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val voucherRepo = container.voucherRepository
    private val accountRepo = container.accountRepository

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
    private val container = AppContainer.getInstance(application)
    private val expenseRepo = container.expenseRepository

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
    private val container = AppContainer.getInstance(application)
    private val reportRepo = container.reportRepository
    private val identityRepo = container.businessIdentityRepository

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
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

/**
 * نموذج عرض الإعدادات والنسخ الاحتياطي والأمان (`SettingsViewModel`).
 *
 * يدير العمليات الحرجة (النسخ الاحتياطي، التصدير، الفحص، الاستعادة، الرفع السحابي، وتشفير PIN)
 * داخل `viewModelScope` وبعيداً عن الخيط الرئيسي (`Main Thread`).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val container = AppContainer.getInstance(appContext)
    private val identityRepo = container.businessIdentityRepository
    val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _indexedBackups = MutableStateFlow<List<BackupRecord>>(emptyList())
    val indexedBackups: StateFlow<List<BackupRecord>> = _indexedBackups.asStateFlow()

    init {
        refreshIndexedBackups()
    }

    fun refreshIndexedBackups() {
        viewModelScope.launch {
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
        }
    }

    fun saveBusinessIdentity(
        tradeName: String,
        phone: String,
        address: String,
        taxNumber: String?,
        receiptHeader: String,
        receiptFooter: String,
        invoicePrefix: String,
        purchasePrefix: String
    ) {
        viewModelScope.launch {
            identityRepo.saveBusinessIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = tradeName.trim().ifBlank { "الدفتر الذكي للتجارة العامة" },
                    LegalName = tradeName.trim().ifBlank { "الدفتر الذكي" },
                    TaxNumber = taxNumber?.trim()?.ifBlank { null },
                    Phone1 = phone.trim(),
                    Address = address.trim(),
                    ReceiptHeader = receiptHeader.trim(),
                    ReceiptFooter = receiptFooter.trim()
                )
            )
            prefsRepo.updateSettings {
                it.copy(
                    invoicePrefix = invoicePrefix.trim().ifBlank { "INV" },
                    purchasePrefix = purchasePrefix.trim().ifBlank { "PUR" }
                )
            }
        }
    }

    fun updateSettings(transform: (AppSettingsSnapshot) -> AppSettingsSnapshot) {
        prefsRepo.updateSettings(transform)
    }

    /**
     * اشتقاق بصمة رمز الحماية (PBKDF2-SHA256) على خيط خلفي (`Dispatchers.Default`) لمنع تجميد الخيط الرئيسي
     */
    fun updateAppPin(rawPin: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val clean = rawPin.trim()
            val hashed = if (clean.isBlank()) {
                ""
            } else {
                withContext(Dispatchers.Default) {
                    SecurityCryptoHelper.hashPin(clean)
                }
            }
            prefsRepo.updateSettings { it.copy(appPinCode = hashed) }
            onComplete?.invoke()
        }
    }

    fun resetPreferencesToDefault() {
        prefsRepo.resetToDefaultsPreservingBackupHistory()
    }

    fun createLocalArchiveBackup(onResult: (Result<BackupRecord>) -> Unit) {
        viewModelScope.launch {
            val res = BackupRestoreHelper.createStructuredArchiveBackup(
                context = appContext,
                sourceTag = "LocalArchive",
                keepMaxCount = prefsRepo.settings.value.keepBackupCount
            )
            if (res.isSuccess) {
                val rec = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun exportBackupToSafUri(destUri: Uri, onResult: (Result<BackupRecord>) -> Unit) {
        viewModelScope.launch {
            val res = BackupRestoreHelper.exportBackupToUri(appContext, destUri)
            if (res.isSuccess) {
                val rec = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun inspectBackupFromSafUri(srcUri: Uri, onResult: (BackupInspectionResult) -> Unit) {
        viewModelScope.launch {
            val inspection = BackupRestoreHelper.inspectBackupUri(appContext, srcUri)
            onResult(inspection)
        }
    }

    fun inspectBackupArchiveFile(file: File, onResult: (BackupInspectionResult) -> Unit) {
        viewModelScope.launch {
            val inspection = BackupRestoreHelper.inspectBackupFile(appContext, file)
            onResult(inspection)
        }
    }

    fun deleteIndexedBackup(recordId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            BackupRestoreHelper.deleteBackupRecord(appContext, recordId)
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            onComplete()
        }
    }

    fun restoreDatabase(
        fileToRestore: File?,
        uriToRestore: Uri?,
        onResult: (Result<BackupInspectionResult>) -> Unit
    ) {
        viewModelScope.launch {
            val res = when {
                fileToRestore != null -> BackupRestoreHelper.restoreFromBackupFile(
                    appContext,
                    fileToRestore,
                    createPreRestoreSafety = true
                )
                uriToRestore != null -> BackupRestoreHelper.restoreFromUri(appContext, uriToRestore)
                else -> Result.failure(Exception("لم يتم تحديد مصدر الاستعادة"))
            }
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            onResult(res)
        }
    }

    fun uploadBackupToGoogleDrive(
        onProgress: (Int) -> Unit,
        onResult: (Result<CloudBackupInfo>) -> Unit
    ) {
        viewModelScope.launch {
            val archiveFile = GoogleDriveBackupHelper.createLocalArchive(appContext)
            if (archiveFile == null) {
                onResult(Result.failure(Exception("تعذر تجهيز ملف الأرشيف المحلي للرفع.")))
                return@launch
            }
            val uploadRes = GoogleDriveBackupHelper.uploadBackupToDrive(
                backupFile = archiveFile,
                accessToken = prefsRepo.settings.value.googleDriveAccessToken,
                onProgress = onProgress
            )
            if (uploadRes.isSuccess) {
                val info = uploadRes.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastCloudBackupTime = info.modifiedTime,
                        lastCloudBackupSize = info.sizeBytes,
                        lastCloudStatusMessage = "تم الرفع بنجاح (${info.fileName})"
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(uploadRes)
        }
    }
}
