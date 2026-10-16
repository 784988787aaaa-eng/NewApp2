package com.smartledger.pro.util

import android.content.Context
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.entity.AppPreferenceEntity
import com.smartledger.pro.ui.components.AppSensoryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSettings(
    val currencySymbol: String = "ر.ي",
    val decimalPlaces: Int = 2,
    val showTrailingZeros: Boolean = false,
    val defaultSmallUnit: String = "قطعة",
    val defaultLargeUnit: String = "كرتون",
    val defaultUnitFactor: Double = 24.0,
    val defaultAlertThreshold: Double = 5.0,
    val allowNegativeStock: Boolean = true,
    val showBarcodeField: Boolean = true,
    val showShelfLocation: Boolean = true,
    val defaultPaymentType: String = "Cash",
    val warnOnLowStockInPos: Boolean = true,
    val warnOnCreditLimitExceeded: Boolean = true,
    val showPhoneInAccounts: Boolean = true,
    val printerPaperWidthMm: Int = 80,
    val printerCopiesCount: Int = 1,
    val printerName: String = "",
    val printerAddress: String = "",
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequencyHours: Int = 24,
    val autoBackupWifiOnly: Boolean = true,
    val keepBackupCount: Int = 5,
    val lastLocalBackupInfo: String = "لم يتم إنشاء نسخة محلية بعد",
    val googleDriveStatus: String = "DISCONNECTED",
    val googleDriveAccountEmail: String = "",
    val googleDriveClientId: String = "",
    val lastCloudBackupInfo: String = "لم يتم الرفع السحابي بعد",
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val hideScreenInRecents: Boolean = false,
    val themeMode: String = "DARK",
    val fontScale: Float = 1.0f,
    val compactDensity: Boolean = false,
    val enableHaptics: Boolean = true,
    val reduceMotion: Boolean = false
)

object AppSettingsManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val db = SmartLedgerDatabase.getInstance(context.applicationContext)
        scope.launch {
            db.appPreferenceDao().getAllPreferences().collect { list ->
                val map = list.associate { it.Key to (it.Value ?: "") }
                val loaded = AppSettings(
                    currencySymbol = map["currency_symbol"]?.ifBlank { "ر.ي" } ?: "ر.ي",
                    decimalPlaces = map["decimal_places"]?.toIntOrNull()?.coerceIn(0, 3) ?: 2,
                    showTrailingZeros = map["show_trailing_zeros"]?.toBooleanStrictOrNull() ?: false,
                    defaultSmallUnit = map["default_small_unit"]?.ifBlank { "قطعة" } ?: "قطعة",
                    defaultLargeUnit = map["default_large_unit"]?.ifBlank { "كرتون" } ?: "كرتون",
                    defaultUnitFactor = map["default_unit_factor"]?.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 24.0,
                    defaultAlertThreshold = map["default_alert_threshold"]?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 5.0,
                    allowNegativeStock = map["allow_negative_stock"]?.toBooleanStrictOrNull() ?: true,
                    showBarcodeField = map["show_barcode_field"]?.toBooleanStrictOrNull() ?: true,
                    showShelfLocation = map["show_shelf_location"]?.toBooleanStrictOrNull() ?: true,
                    defaultPaymentType = map["default_payment_type"]?.ifBlank { "Cash" } ?: "Cash",
                    warnOnLowStockInPos = map["warn_low_stock_pos"]?.toBooleanStrictOrNull() ?: true,
                    warnOnCreditLimitExceeded = map["warn_credit_limit"]?.toBooleanStrictOrNull() ?: true,
                    showPhoneInAccounts = map["show_phone_in_accounts"]?.toBooleanStrictOrNull() ?: true,
                    printerPaperWidthMm = map["printer_paper_width"]?.toIntOrNull() ?: 80,
                    printerCopiesCount = map["printer_copies_count"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1,
                    printerName = map["printer_name"] ?: "",
                    printerAddress = map["printer_address"] ?: "",
                    autoBackupEnabled = map["auto_backup_enabled"]?.toBooleanStrictOrNull() ?: false,
                    autoBackupFrequencyHours = map["auto_backup_frequency_hours"]?.toIntOrNull() ?: 24,
                    autoBackupWifiOnly = map["auto_backup_wifi_only"]?.toBooleanStrictOrNull() ?: true,
                    keepBackupCount = map["keep_backup_count"]?.toIntOrNull()?.coerceIn(2, 20) ?: 5,
                    lastLocalBackupInfo = map["last_local_backup_info"]?.ifBlank { "لم يتم إنشاء نسخة محلية بعد" } ?: "لم يتم إنشاء نسخة محلية بعد",
                    googleDriveStatus = map["gdrive_status"]?.ifBlank { "DISCONNECTED" } ?: "DISCONNECTED",
                    googleDriveAccountEmail = map["gdrive_email"] ?: "",
                    googleDriveClientId = map["gdrive_client_id"] ?: "",
                    lastCloudBackupInfo = map["last_cloud_backup_info"]?.ifBlank { "لم يتم الرفع السحابي بعد" } ?: "لم يتم الرفع السحابي بعد",
                    biometricEnabled = map["biometric_enabled"]?.toBooleanStrictOrNull() ?: false,
                    lockTimeoutSeconds = map["lock_timeout_seconds"]?.toIntOrNull() ?: 60,
                    hideScreenInRecents = map["hide_screen_recents"]?.toBooleanStrictOrNull() ?: false,
                    themeMode = map["theme_mode"]?.ifBlank { "DARK" } ?: "DARK",
                    fontScale = map["font_scale"]?.toFloatOrNull()?.coerceIn(0.85f, 1.2f) ?: 1.0f,
                    compactDensity = map["compact_density"]?.toBooleanStrictOrNull() ?: false,
                    enableHaptics = map["enable_haptics"]?.toBooleanStrictOrNull() ?: true,
                    reduceMotion = map["reduce_motion"]?.toBooleanStrictOrNull() ?: false
                )
                _settings.value = loaded
                AppSensoryConfig.enableHaptics = loaded.enableHaptics
                AppSensoryConfig.reduceMotion = loaded.reduceMotion
                AppSensoryConfig.compactDensity = loaded.compactDensity
                AppSensoryConfig.currencySymbol = loaded.currencySymbol
                AppSensoryConfig.decimalPlaces = loaded.decimalPlaces
            }
        }
    }

    fun updatePreference(context: Context, key: String, value: String) {
        val db = SmartLedgerDatabase.getInstance(context.applicationContext)
        scope.launch {
            db.appPreferenceDao().setPreference(AppPreferenceEntity(Key = key, Value = value))
        }
    }

    fun resetUiPreferencesToDefault(context: Context) {
        val db = SmartLedgerDatabase.getInstance(context.applicationContext)
        scope.launch {
            val preservedKeys = listOf("last_local_backup_info", "last_cloud_backup_info", "gdrive_status", "gdrive_email", "gdrive_client_id")
            val saved = preservedKeys.mapNotNull { k ->
                db.appPreferenceDao().getPreference(k)?.let { v -> AppPreferenceEntity(k, v) }
            }
            db.appPreferenceDao().clearAllPreferences()
            saved.forEach { db.appPreferenceDao().setPreference(it) }
            AppSensoryConfig.enableHaptics = true
            AppSensoryConfig.reduceMotion = false
            AppSensoryConfig.compactDensity = false
            AppSensoryConfig.currencySymbol = "ر.ي"
            AppSensoryConfig.decimalPlaces = 2
        }
    }
}
