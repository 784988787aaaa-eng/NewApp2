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
