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
