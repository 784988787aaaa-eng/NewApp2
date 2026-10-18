package com.smartledger.pro.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenFieldVisibility(
    val inventoryBarcode: Boolean = true,
    val productBarcode: Boolean = true,
    val productShelf: Boolean = true,
    val posBarcode: Boolean = true
)

/**
 * Lightweight per-screen display preferences. These flags only control visibility;
 * they never alter product data, stock values, or accounting operations.
 */
object ScreenFieldVisibilityStore {
    private const val PREFS_NAME = "smartledger_pro_settings"
    private const val INVENTORY_BARCODE = "showBarcodeInInventory"
    private const val PRODUCT_BARCODE = "showBarcodeInProductForm"
    private const val PRODUCT_SHELF = "showShelfLocationInProductForm"
    private const val POS_BARCODE = "showBarcodeInPos"

    private val state = MutableStateFlow(ScreenFieldVisibility())
    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Synchronized
    fun observe(context: Context): StateFlow<ScreenFieldVisibility> {
        val prefs = preferences ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { loaded ->
                preferences = loaded
                state.value = read(loaded)
                val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
                    if (key in setOf(INVENTORY_BARCODE, PRODUCT_BARCODE, PRODUCT_SHELF, POS_BARCODE, "showBarcodeField", "showShelfLocation")) {
                        state.value = read(source)
                    }
                }
                listener = changeListener
                loaded.registerOnSharedPreferenceChangeListener(changeListener)
            }
        if (state.value == ScreenFieldVisibility()) state.value = read(prefs)
        return state.asStateFlow()
    }

    fun update(context: Context, transform: (ScreenFieldVisibility) -> ScreenFieldVisibility) {
        val prefs = synchronized(this) {
            observe(context)
            preferences!!
        }
        val next = transform(state.value)
        prefs.edit()
            .putBoolean(INVENTORY_BARCODE, next.inventoryBarcode)
            .putBoolean(PRODUCT_BARCODE, next.productBarcode)
            .putBoolean(PRODUCT_SHELF, next.productShelf)
            .putBoolean(POS_BARCODE, next.posBarcode)
            .apply()
        state.value = next
    }

    fun reset(context: Context) {
        update(context) { ScreenFieldVisibility() }
    }

    private fun read(prefs: SharedPreferences) = ScreenFieldVisibility(
        inventoryBarcode = prefs.getBoolean(INVENTORY_BARCODE, prefs.getBoolean("showBarcodeField", true)),
        productBarcode = prefs.getBoolean(PRODUCT_BARCODE, prefs.getBoolean("showBarcodeField", true)),
        productShelf = prefs.getBoolean(PRODUCT_SHELF, prefs.getBoolean("showShelfLocation", true)),
        posBarcode = prefs.getBoolean(POS_BARCODE, prefs.getBoolean("showBarcodeField", true))
    )
}
