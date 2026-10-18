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
