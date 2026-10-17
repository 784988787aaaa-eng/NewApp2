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
    private val financialAccountRepo = container.financialAccountRepository
    private val warehouseRepo = container.warehouseRepository
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun completeOnboarding(
        tradeName: String,
        currencySymbol: String,
        initialCashboxName: String?,
        initialWarehouseName: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanTrade = tradeName.trim().ifBlank { "نشاطي التجاري" }
            val cleanCurrency = currencySymbol.trim().ifBlank { "ر.ي" }
            identityRepo.saveBusinessIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = cleanTrade,
                    LegalName = cleanTrade,
                    Phone1 = "",
                    Address = "",
                    ReceiptHeader = "مرحباً بكم في $cleanTrade",
                    ReceiptFooter = "شكراً لتعاملكم معنا"
                )
            )
            if (!initialCashboxName.isNullOrBlank()) {
                financialAccountRepo.saveFinancialAccount(
                    name = initialCashboxName.trim(),
                    accountType = "CASH_BOX",
                    currencySymbol = cleanCurrency,
                    isDefault = true
                )
            }
            if (!initialWarehouseName.isNullOrBlank()) {
                warehouseRepo.saveWarehouse(
                    name = initialWarehouseName.trim(),
                    isDefault = true
                )
            }
            prefsRepo.updateSettings {
                it.copy(
                    currencySymbol = cleanCurrency,
                    onboardingCompleted = true
                )
            }
            onComplete()
        }
    }

    fun skipOnboarding() {
        prefsRepo.updateSettings { it.copy(onboardingCompleted = true) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val accountRepo = container.accountRepository
    private val voucherRepo = container.voucherRepository
    private val invoiceRepo = container.invoiceRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val identityRepo = container.businessIdentityRepository
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings
    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    suspend fun saveBookAsync(name: String, id: Long = 0L): Result<Long> {
        return accountRepo.saveBook(name, id)
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

    fun restoreAccount(id: Long, onResult: ((Result<Boolean>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = accountRepo.restoreAccount(id)
            onResult?.invoke(res)
        }
    }

    fun getOperationsForAccount(personId: Long): Flow<List<OperationEntity>> {
        return accountRepo.getAccountOperations(personId)
    }

    fun getOpenInvoicesForAccount(personId: Long): Flow<List<InvoiceEntity>> {
        return invoiceRepo.getOpenInvoicesForPerson(personId)
    }

    fun deleteOperation(
        operationId: Long,
        reason: String? = null,
        onResult: ((Result<Long>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val res = accountRepo.deleteOperation(operationId, reason)
            onResult?.invoke(res)
        }
    }

    suspend fun createQuickVoucherAsync(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null,
        financialAccountId: Long? = null,
        targetInvoiceId: Long? = null
    ): Result<Long> {
        return voucherRepo.createVoucher(
            personId = personId,
            type = type,
            amount = amount,
            statement = statement,
            refNumber = refNumber,
            financialAccountId = financialAccountId,
            targetInvoiceId = targetInvoiceId
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val inventoryRepo = container.inventoryRepository
    private val categoryRepo = container.categoryRepository
    private val unitRepo = container.measurementUnitRepository
    private val warehouseRepo = container.warehouseRepository
    private val prefsRepo = container.preferencesRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _stockFilter = MutableStateFlow("ALL")
    val stockFilter = _stockFilter.asStateFlow()

    val productCategories: StateFlow<List<CategoryEntity>> =
        categoryRepo.observeCategories(domain = "PRODUCT", onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val units: StateFlow<List<MeasurementUnitEntity>> =
        unitRepo.observeUnits(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> =
        warehouseRepo.observeWarehouses(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val cleanWarehouse = warehouse.trim().ifBlank { "المستودع الرئيسي" }
        val cleanSmallUnit = smallUnit.trim().ifBlank { "قطعة" }
        val cleanLargeUnit = largeUnit?.trim()?.ifBlank { null }

        unitRepo.ensureUnitExists(cleanSmallUnit)
        if (cleanLargeUnit != null) {
            unitRepo.ensureUnitExists(cleanLargeUnit)
        }
        val matchedWarehouseId = warehouses.value.firstOrNull { it.Name.equals(cleanWarehouse, ignoreCase = true) }?.Id
            ?: warehouseRepo.saveWarehouse(name = cleanWarehouse).getOrNull()

        val product = ProductEntity(
            Id = id,
            Barcode = barcode?.trim()?.ifBlank { null },
            Name = name.trim(),
            CategoryId = categoryId,
            Warehouse = cleanWarehouse,
            ShelfLocation = shelfLocation?.trim()?.ifBlank { null },
            SmallUnit = cleanSmallUnit,
            LargeUnit = cleanLargeUnit,
            UnitFactor = safeFactor,
            CostPrice = NumberFormatter.roundCurrency(costPrice.coerceAtLeast(0.0)),
            RetailPrice = NumberFormatter.roundCurrency(retailPrice.coerceAtLeast(0.0)),
            WholesalePrice = NumberFormatter.roundCurrency(effectiveMajorPrice.coerceAtLeast(0.0)),
            AlertThreshold = alertThreshold.coerceAtLeast(0.0),
            OpeningQuantityMinor = totalOpeningMinor,
            OpeningCost = NumberFormatter.roundCurrency(costPrice.coerceAtLeast(0.0)),
            CreatedAt = now
        )
        return inventoryRepo.saveProduct(product, totalOpeningMinor, matchedWarehouseId)
    }

    fun deleteProduct(id: Long, reason: String? = null) {
        viewModelScope.launch {
            inventoryRepo.softDeleteProduct(id, reason)
        }
    }

    fun restoreProduct(id: Long, onResult: ((Result<Boolean>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = inventoryRepo.restoreProduct(id)
            onResult?.invoke(res)
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

    suspend fun transferStockBetweenWarehousesAsync(
        productId: Long,
        fromWarehouseId: Long,
        toWarehouseId: Long,
        quantityMinor: Double,
        notes: String? = null
    ): Result<Boolean> {
        return warehouseRepo.transferStockBetweenWarehouses(
            productId = productId,
            fromWarehouseId = fromWarehouseId,
            toWarehouseId = toWarehouseId,
            quantityMinor = quantityMinor,
            allowNegativeStock = prefsRepo.settings.value.allowNegativeStock,
            notes = notes
        )
    }
}

/**
 * نموذج عرض نقطة البيع السريعة (`PosViewModel`) مع حماية كاملة ضد قتل العملية في الخلفية (`Process Death`)
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
    private val financialAccountRepo = container.financialAccountRepository
    private val prefsRepo = container.preferencesRepository
    private val createSaleUseCase = container.createSaleUseCase

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = productRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
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

    fun restoreRemovedCartItem(item: CartLineItem) {
        val existingIdx = cartLines.indexOfFirst { it.productId == item.productId }
        if (existingIdx >= 0) {
            cartLines[existingIdx] = item
        } else {
            cartLines.add(item)
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
        notes: String? = null,
        financialAccountId: Long? = null
    ): Result<InvoiceEntity> {
        val activeSettings = prefsRepo.settings.value
        val defaultAccountId = financialAccountId
            ?: financialAccounts.value.firstOrNull { it.IsDefault }?.Id
            ?: financialAccounts.value.firstOrNull()?.Id

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
            invoicePrefix = activeSettings.invoicePrefix,
            financialAccountId = defaultAccountId
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
    private val financialAccountRepo = container.financialAccountRepository
    private val createPurchaseUseCase = container.createPurchaseUseCase
    private val prefsRepo = container.preferencesRepository

    val suppliers: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductStockItem>> = inventoryRepo.getProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
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
        notes: String?,
        financialAccountId: Long? = null
    ): Result<Long> {
        val defaultAccountId = financialAccountId
            ?: financialAccounts.value.firstOrNull { it.IsDefault }?.Id
            ?: financialAccounts.value.firstOrNull()?.Id
        return createPurchaseUseCase.execute(
            supplier = supplier,
            product = product,
            useMajorUnit = useMajorUnit,
            quantity = quantity,
            unitPurchaseCost = unitPurchaseCost,
            paidCash = paidCash,
            notes = notes,
            purchasePrefix = prefsRepo.settings.value.purchasePrefix,
            financialAccountId = defaultAccountId
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
    private val invoiceRepo = container.invoiceRepository
    private val financialAccountRepo = container.financialAccountRepository

    val accounts: StateFlow<List<AccountBalanceSummary>> = accountRepo.getAccountSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentVouchers: StateFlow<List<RecentOperationWithPerson>> = voucherRepo.observeRecentOperations(limit = 30)
        .map { list -> list.filter { it.Type == "Receive" || it.Type == "Payment" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getOpenInvoicesForPerson(personId: Long): Flow<List<InvoiceEntity>> {
        return invoiceRepo.getOpenInvoicesForPerson(personId)
    }

    suspend fun createVoucher(
        personId: Long,
        type: String,
        amount: Double,
        statement: String,
        refNumber: String? = null,
        financialAccountId: Long? = null,
        targetInvoiceId: Long? = null
    ): Result<Long> {
        val defaultAccountId = financialAccountId
            ?: financialAccounts.value.firstOrNull { it.IsDefault }?.Id
            ?: financialAccounts.value.firstOrNull()?.Id
        return voucherRepo.createVoucher(
            personId = personId,
            type = type,
            amount = amount,
            statement = statement,
            refNumber = refNumber,
            financialAccountId = defaultAccountId,
            targetInvoiceId = targetInvoiceId
        )
    }
}

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val expenseRepo = container.expenseRepository
    private val categoryRepo = container.categoryRepository
    private val financialAccountRepo = container.financialAccountRepository

    val expenses: StateFlow<List<ExpenseEntity>> = expenseRepo.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepo.observeCategories(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun saveExpenseAsync(
        amount: Double,
        statement: String,
        category: String,
        source: String,
        photoUri: String? = null,
        entryType: String = "EXPENSE",
        categoryId: Long? = null,
        financialAccountId: Long? = null
    ): Result<Long> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val cleanCatName = category.trim()
        val cleanDomain = if (entryType == "REVENUE") "REVENUE" else "EXPENSE"

        // إذا أدخل المستخدم اسم تصنيف جديد غير موجود بعد، يُحفظ تلقائياً في جدول التصنيفات المرن
        val resolvedCategoryId = categoryId ?: if (cleanCatName.isNotEmpty()) {
            categories.value.firstOrNull {
                it.Domain.equals(cleanDomain, ignoreCase = true) && it.Name.equals(cleanCatName, ignoreCase = true)
            }?.Id ?: categoryRepo.saveCategory(domain = cleanDomain, name = cleanCatName).getOrNull()
        } else null

        val resolvedAccountId = financialAccountId ?: if (source.isNotBlank()) {
            financialAccounts.value.firstOrNull { it.Name.equals(source.trim(), ignoreCase = true) }?.Id
        } else null

        val fullStatement = if (cleanCatName.isNotBlank() && !statement.startsWith("[$cleanCatName]")) {
            "[$cleanCatName] ${statement.trim()}"
        } else {
            statement.trim()
        }
        val entity = ExpenseEntity(
            ExpenseDate = now,
            CategoryId = resolvedCategoryId,
            Amount = NumberFormatter.roundCurrency(amount),
            Statement = fullStatement,
            PaymentSource = source.trim().ifBlank { "صندوق عام" },
            ReceiptPhotoUri = photoUri,
            CreatedAt = now,
            EntryType = entryType,
            FinancialAccountId = resolvedAccountId,
            IsArchived = 0
        )
        return expenseRepo.saveExpense(entity)
    }

    fun deleteExpense(id: Long, reason: String? = null) {
        viewModelScope.launch {
            expenseRepo.deleteExpense(id, reason)
        }
    }

    fun restoreExpenseSnapshot(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseRepo.restoreExpenseSnapshot(expense)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.getInstance(application)
    private val reportRepo = container.reportRepository
    private val identityRepo = container.businessIdentityRepository
    private val financialAccountRepo = container.financialAccountRepository

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
 * نموذج عرض الإعدادات والفهارس المرنة والنسخ الاحتياطي والأمان (`SettingsViewModel`).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val container = AppContainer.getInstance(appContext)
    private val identityRepo = container.businessIdentityRepository
    private val accountRepo = container.accountRepository
    private val categoryRepo = container.categoryRepository
    private val unitRepo = container.measurementUnitRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val warehouseRepo = container.warehouseRepository
    val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deletedItems: StateFlow<List<DeletedItemEntity>> = accountRepo.getAllDeletedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryRepo.observeCategories(onlyActive = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val units: StateFlow<List<MeasurementUnitEntity>> = unitRepo.observeUnits(onlyActive = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransfers: StateFlow<List<FinancialTransferEntity>> =
        financialAccountRepo.observeRecentTransfers(40)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> =
        warehouseRepo.observeWarehouses(onlyActive = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<BookEntity>> =
        accountRepo.getAllBooks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    suspend fun saveCategoryAsync(
        id: Long = 0L,
        domain: String,
        name: String,
        parentId: Long? = null,
        description: String? = null
    ): Result<Long> {
        return categoryRepo.saveCategory(id = id, domain = domain, name = name, parentId = parentId, description = description)
    }

    suspend fun deleteOrDeactivateCategoryAsync(categoryId: Long): Result<String> {
        return categoryRepo.deleteOrDeactivateCategory(categoryId)
    }

    suspend fun reactivateCategoryAsync(categoryId: Long): Result<Boolean> {
        return categoryRepo.reactivateCategory(categoryId)
    }

    suspend fun saveUnitAsync(
        id: Long = 0L,
        name: String,
        symbol: String,
        decimalPrecision: Int,
        baseUnitId: Long?,
        conversionFactorToBase: Double
    ): Result<Long> {
        return unitRepo.saveUnit(id, name, symbol, decimalPrecision, baseUnitId, conversionFactorToBase)
    }

    suspend fun saveFinancialAccountAsync(
        id: Long = 0L,
        name: String,
        accountType: String,
        parentGroupId: Long? = null,
        currencySymbol: String = settings.value.currencySymbol,
        accountNumberOrPhone: String? = null,
        openingBalance: Double = 0.0,
        allowNegativeBalance: Boolean = false,
        isDefault: Boolean = false,
        notes: String? = null
    ): Result<Long> {
        return financialAccountRepo.saveFinancialAccount(
            id = id,
            name = name,
            accountType = accountType,
            parentGroupId = parentGroupId,
            currencySymbol = currencySymbol,
            accountNumberOrPhone = accountNumberOrPhone,
            openingBalance = openingBalance,
            allowNegativeBalance = allowNegativeBalance,
            isDefault = isDefault,
            notes = notes
        )
    }

    suspend fun createInternalTransferAsync(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        feeAmount: Double = 0.0,
        notes: String? = null
    ): Result<Long> {
        return financialAccountRepo.createInternalTransfer(fromAccountId, toAccountId, amount, feeAmount, notes)
    }

    suspend fun reconcileFinancialAccountAsync(
        accountId: Long,
        actualBalance: Double,
        notes: String? = null
    ): Result<Double> {
        return financialAccountRepo.reconcileAccountBalance(accountId, actualBalance, notes)
    }

    suspend fun saveWarehouseAsync(
        id: Long = 0L,
        name: String,
        code: String? = null,
        location: String? = null,
        description: String? = null,
        isDefault: Boolean = false
    ): Result<Long> {
        return warehouseRepo.saveWarehouse(id, name, code, location, description, isDefault)
    }

    suspend fun saveBookAsync(name: String, id: Long = 0L): Result<Long> {
        return accountRepo.saveBook(name, id)
    }

    fun restoreDeletedItem(item: DeletedItemEntity, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = accountRepo.restoreDeletedItem(item)
            onResult(res)
        }
    }

    fun deleteDeletedItemPermanently(id: Long, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val res = accountRepo.deleteDeletedItemPermanently(id)
            onResult(res)
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
                    TradeName = tradeName.trim().ifBlank { "نشاطي التجاري" },
                    LegalName = tradeName.trim().ifBlank { "نشاطي التجاري" },
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
                uriToRestore != null -> BackupRestoreHelper.restoreFromUri(
                    appContext,
                    uriToRestore
                )
                else -> Result.failure(Exception("لم يتم تحديد مصدر الاستعادة"))
            }
            if (res.isSuccess) {
                val insp = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastRestoreTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
                        lastLocalBackupSha256 = insp.actualSha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun uploadBackupToGoogleDrive(
        onProgress: (Int) -> Unit,
        onResult: (Result<CloudBackupInfo>) -> Unit
    ) {
        viewModelScope.launch {
            val currentSettings = prefsRepo.settings.value
            val localRes = BackupRestoreHelper.createStructuredArchiveBackup(
                context = appContext,
                sourceTag = "DriveCloudSync",
                keepMaxCount = currentSettings.keepBackupCount
            )
            if (localRes.isFailure) {
                onResult(Result.failure(localRes.exceptionOrNull() ?: Exception("تعذر تجهيز ملف النسخة للرفع")))
                return@launch
            }
            val rec = localRes.getOrThrow()
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)

            val uploadRes = GoogleDriveBackupHelper.uploadBackupToDrive(
                backupFile = File(rec.filePath),
                accessToken = currentSettings.googleDriveAccessToken,
                onProgress = onProgress
            )
            if (uploadRes.isSuccess) {
                val cloud = uploadRes.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastCloudBackupTime = cloud.modifiedTime,
                        lastCloudBackupSize = cloud.sizeBytes,
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
            }
            onResult(uploadRes)
        }
    }
}
