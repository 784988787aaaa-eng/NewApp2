package com.smartledger.pro.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.AppPreferenceDao
import com.smartledger.pro.data.local.entity.AppPreferenceEntity
import com.smartledger.pro.domain.model.AppAccentPalette
import com.smartledger.pro.domain.model.AppCornerStyle
import com.smartledger.pro.domain.model.AppFabStyle
import com.smartledger.pro.domain.model.AppNavStyle
import com.smartledger.pro.domain.model.AppThemeMode
import com.smartledger.pro.domain.model.AppUiDensity
import com.smartledger.pro.domain.model.AppUsageMode
import com.smartledger.pro.util.SecurityCryptoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class AppPreferencesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("smartledger_pro_settings", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences =
        appContext.getSharedPreferences("smartledger_secure_secrets", Context.MODE_PRIVATE)
    private val prefDao: AppPreferenceDao
        get() = SmartLedgerDatabase.getInstance(appContext).appPreferenceDao()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeObservers = CopyOnWriteArrayList<(AppSettingsSnapshot) -> Unit>()

    init {
        migrateLegacyPlaintextSecretsIfNeeded()
    }

    private val _settings = MutableStateFlow(loadFromPreferences())
    val settings: StateFlow<AppSettingsSnapshot> = _settings.asStateFlow()

    init {
        notifyRuntimeObservers(_settings.value)
        syncPortablePreferencesFromRoomIfNeeded(forceReloadFromRoom = false)
        observeDatabaseRestoreEpochs()
    }

    private fun observeDatabaseRestoreEpochs() {
        ioScope.launch {
            SmartLedgerDatabase.databaseEpoch.collect { epoch ->
                if (epoch > 0L) {
                    syncPortablePreferencesFromRoomIfNeeded(forceReloadFromRoom = true)
                }
            }
        }
    }

    fun registerRuntimeStateObserver(observer: (AppSettingsSnapshot) -> Unit) {
        runtimeObservers.add(observer)
        observer(_settings.value)
    }

    private fun notifyRuntimeObservers(snapshot: AppSettingsSnapshot) {
        for (observer in runtimeObservers) {
            runCatching { observer(snapshot) }
        }
    }

    fun onDatabaseRestored() {
        syncPortablePreferencesFromRoomIfNeeded(forceReloadFromRoom = true)
    }

    private fun migrateLegacyPlaintextSecretsIfNeeded() {
        val legacyPin = prefs.getString("appPinCode", null)
        if (!legacyPin.isNullOrBlank()) {
            val hashed = if (legacyPin.startsWith("v1$")) legacyPin else SecurityCryptoHelper.hashPin(legacyPin)
            val verified = if (legacyPin.startsWith("v1$")) true else SecurityCryptoHelper.verifyPin(legacyPin, hashed)
            if (verified && hashed.startsWith("v1$")) {
                val committed = securePrefs.edit().putString("appPinHash", hashed).commit()
                if (committed) {
                    prefs.edit().remove("appPinCode").apply()
                }
            }
        }

        val existingPinHash = securePrefs.getString("appPinHash", null)
        if (!existingPinHash.isNullOrBlank() && !existingPinHash.startsWith("v1$")) {
            val upgradedHash = SecurityCryptoHelper.hashPin(existingPinHash)
            if (SecurityCryptoHelper.verifyPin(existingPinHash, upgradedHash)) {
                securePrefs.edit().putString("appPinHash", upgradedHash).apply()
            }
        }

        val legacyToken = prefs.getString("googleDriveAccessToken", null)
        if (!legacyToken.isNullOrBlank()) {
            val encResult = SecurityCryptoHelper.encryptSecretResult(legacyToken)
            if (encResult.isSuccess) {
                val cipherText = encResult.getOrThrow()
                val roundTrip = SecurityCryptoHelper.decryptSecretResult(cipherText)
                if (roundTrip.isSuccess && roundTrip.getOrNull() == legacyToken.trim()) {
                    val committed = securePrefs.edit().putString("encryptedDriveToken", cipherText).commit()
                    if (committed) {
                        prefs.edit().remove("googleDriveAccessToken").apply()
                    }
                }
            }
        }
    }

    private fun syncPortablePreferencesFromRoomIfNeeded(forceReloadFromRoom: Boolean) {
        ioScope.launch {
            try {
                val roomPrefs = prefDao.getAllPreferencesOnce().associate { it.Key to (it.Value ?: "") }
                if (roomPrefs.isNotEmpty() && (forceReloadFromRoom || !prefs.getBoolean("_initialized", false))) {
                    val editor = prefs.edit().putBoolean("_initialized", true)
                    roomPrefs["currency_symbol"]?.takeIf { it.isNotBlank() }?.let { editor.putString("currencySymbol", it) }
                    roomPrefs["decimal_places"]?.toIntOrNull()?.let { editor.putInt("decimalPlaces", it) }
                    roomPrefs["onboarding_completed"]?.toBooleanStrictOrNull()?.let { editor.putBoolean("onboardingCompleted", it) }
                    roomPrefs["default_country_code"]?.takeIf { it.isNotBlank() }?.let { editor.putString("defaultCountryCode", it) }
                    roomPrefs["invoice_prefix"]?.takeIf { it.isNotBlank() }?.let { editor.putString("invoicePrefix", it) }
                    roomPrefs["purchase_prefix"]?.takeIf { it.isNotBlank() }?.let { editor.putString("purchasePrefix", it) }
                    roomPrefs["allow_negative_stock"]?.toBooleanStrictOrNull()?.let { editor.putBoolean("allowNegativeStock", it) }
                    roomPrefs["theme_mode"]?.takeIf { it.isNotBlank() }?.let { editor.putString("themeMode", it) }
                    roomPrefs["ui_density"]?.takeIf { it.isNotBlank() }?.let { editor.putString("uiDensity", it) }
                    roomPrefs["usage_mode"]?.takeIf { it.isNotBlank() }?.let { editor.putString("usageMode", it) }
                    roomPrefs["accent_palette"]?.takeIf { it.isNotBlank() }?.let { editor.putString("accentPalette", it) }
                    roomPrefs["nav_style"]?.takeIf { it.isNotBlank() }?.let { editor.putString("navStyle", it) }
                    roomPrefs["fab_style"]?.takeIf { it.isNotBlank() }?.let { editor.putString("fabStyle", it) }
                    roomPrefs["corner_style"]?.takeIf { it.isNotBlank() }?.let { editor.putString("cornerStyle", it) }
                    roomPrefs["font_scale"]?.toFloatOrNull()?.let { editor.putFloat("fontScale", it) }
                    roomPrefs["enable_haptics"]?.toBooleanStrictOrNull()?.let { editor.putBoolean("enableHaptics", it) }
                    roomPrefs["reduce_motion"]?.toBooleanStrictOrNull()?.let { editor.putBoolean("reduceMotion", it) }
                    editor.apply()
                    val updated = loadFromPreferences()
                    _settings.value = updated
                    notifyRuntimeObservers(updated)
                } else {
                    prefs.edit().putBoolean("_initialized", true).apply()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFromPreferences(): AppSettingsSnapshot {
        val themeStr = prefs.getString("themeMode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val densityStr = prefs.getString("uiDensity", AppUiDensity.COMFORTABLE.name) ?: AppUiDensity.COMFORTABLE.name
        val usageStr = prefs.getString("usageMode", AppUsageMode.PRO.name) ?: AppUsageMode.PRO.name
        val paletteStr = prefs.getString("accentPalette", AppAccentPalette.ROYAL_SAPPHIRE.name) ?: AppAccentPalette.ROYAL_SAPPHIRE.name
        val navStr = prefs.getString("navStyle", AppNavStyle.FLOATING_DOCK.name) ?: AppNavStyle.FLOATING_DOCK.name
        val fabStr = prefs.getString("fabStyle", AppFabStyle.ORBITAL_RADIAL.name) ?: AppFabStyle.ORBITAL_RADIAL.name
        val cornerStr = prefs.getString("cornerStyle", AppCornerStyle.ROUNDED.name) ?: AppCornerStyle.ROUNDED.name

        val encryptedToken = securePrefs.getString("encryptedDriveToken", "") ?: ""
        val decryptedToken = SecurityCryptoHelper.decryptSecretResult(encryptedToken).getOrDefault("")
        val storedPinVerifier = (securePrefs.getString("appPinHash", "") ?: "").takeIf { it.startsWith("v1$") } ?: ""

        return AppSettingsSnapshot(
            currencySymbol = prefs.getString("currencySymbol", "ر.ي") ?: "ر.ي",
            decimalPlaces = prefs.getInt("decimalPlaces", 0),
            dateFormat = prefs.getString("dateFormat", "yyyy-MM-dd") ?: "yyyy-MM-dd",
            onboardingCompleted = prefs.getBoolean("onboardingCompleted", false),
            defaultCountryCode = prefs.getString("defaultCountryCode", "+967") ?: "+967",
            defaultSmallUnit = prefs.getString("defaultSmallUnit", "قطعة") ?: "قطعة",
            defaultLargeUnit = prefs.getString("defaultLargeUnit", "كرتون") ?: "كرتون",
            defaultUnitFactor = prefs.getFloat("defaultUnitFactor", 24f).toDouble(),
            defaultAlertThreshold = prefs.getFloat("defaultAlertThreshold", 5f).toDouble(),
            showBarcodeField = prefs.getBoolean("showBarcodeField", true),
            showShelfLocation = prefs.getBoolean("showShelfLocation", true),
            allowNegativeStock = prefs.getBoolean("allowNegativeStock", true),
            defaultPaymentType = prefs.getString("defaultPaymentType", "Cash") ?: "Cash",
            warnOnLowStockInPos = prefs.getBoolean("warnOnLowStockInPos", true),
            warnOnCreditLimit = prefs.getBoolean("warnOnCreditLimit", true),
            showPhoneInAccounts = prefs.getBoolean("showPhoneInAccounts", true),
            invoicePrefix = prefs.getString("invoicePrefix", "INV") ?: "INV",
            purchasePrefix = prefs.getString("purchasePrefix", "PUR") ?: "PUR",
            printerPaperWidthMm = prefs.getInt("printerPaperWidthMm", 80),
            printerCopies = prefs.getInt("printerCopies", 1),
            autoPrintAfterSale = prefs.getBoolean("autoPrintAfterSale", false),
            savedPrinterName = prefs.getString("savedPrinterName", "") ?: "",
            savedPrinterAddress = prefs.getString("savedPrinterAddress", "") ?: "",
            autoBackupEnabled = prefs.getBoolean("autoBackupEnabled", true),
            autoBackupIntervalHours = prefs.getInt("autoBackupIntervalHours", 24),
            keepBackupCount = prefs.getInt("keepBackupCount", 15),
            lastLocalBackupTime = prefs.getString("lastLocalBackupTime", "") ?: "",
            lastLocalBackupSize = prefs.getLong("lastLocalBackupSize", 0L),
            lastLocalBackupSha256 = prefs.getString("lastLocalBackupSha256", "") ?: "",
            lastRestoreTime = prefs.getString("lastRestoreTime", "") ?: "",
            googleDriveAccountEmail = prefs.getString("googleDriveAccountEmail", "") ?: "",
            googleDriveAccessToken = decryptedToken,
            lastCloudBackupTime = prefs.getString("lastCloudBackupTime", "") ?: "",
            lastCloudBackupSize = prefs.getLong("lastCloudBackupSize", 0L),
            lastCloudStatusMessage = prefs.getString("lastCloudStatusMessage", "") ?: "",
            biometricLockEnabled = prefs.getBoolean("biometricLockEnabled", false),
            appPinCode = storedPinVerifier,
            lockTimeoutMinutes = prefs.getInt("lockTimeoutMinutes", 5),
            hideSensitivePreviewInRecents = prefs.getBoolean("hideSensitivePreviewInRecents", false),
            themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.DARK),
            uiDensity = runCatching { AppUiDensity.valueOf(densityStr) }.getOrDefault(AppUiDensity.COMFORTABLE),
            usageMode = runCatching { AppUsageMode.valueOf(usageStr) }.getOrDefault(AppUsageMode.PRO),
            accentPalette = runCatching { AppAccentPalette.valueOf(paletteStr) }.getOrDefault(AppAccentPalette.ROYAL_SAPPHIRE),
            navStyle = runCatching { AppNavStyle.valueOf(navStr) }.getOrDefault(AppNavStyle.FLOATING_DOCK),
            fabStyle = runCatching { AppFabStyle.valueOf(fabStr) }.getOrDefault(AppFabStyle.ORBITAL_RADIAL),
            cornerStyle = runCatching { AppCornerStyle.valueOf(cornerStr) }.getOrDefault(AppCornerStyle.ROUNDED),
            fontScale = prefs.getFloat("fontScale", 1.0f),
            enableHaptics = prefs.getBoolean("enableHaptics", true),
            reduceMotion = prefs.getBoolean("reduceMotion", false)
        )
    }

    fun updateSettings(transform: (AppSettingsSnapshot) -> AppSettingsSnapshot) {
        val rawNext = transform(_settings.value)
        val normalizedPinVerifier = when {
            rawNext.appPinCode.isBlank() -> ""
            rawNext.appPinCode.startsWith("v1$") -> rawNext.appPinCode
            else -> SecurityCryptoHelper.hashPin(rawNext.appPinCode)
        }
        val next = rawNext.copy(appPinCode = normalizedPinVerifier)
        saveSnapshotToDisk(next)
        _settings.value = next
        notifyRuntimeObservers(next)
    }

    private fun saveSnapshotToDisk(s: AppSettingsSnapshot) {
        val secureEditor = securePrefs.edit()
            .putString("appPinHash", s.appPinCode)

        if (s.googleDriveAccessToken.isBlank()) {
            secureEditor.putString("encryptedDriveToken", "")
        } else {
            val encResult = SecurityCryptoHelper.encryptSecretResult(s.googleDriveAccessToken)
            if (encResult.isSuccess) {
                secureEditor.putString("encryptedDriveToken", encResult.getOrThrow())
            }
        }
        secureEditor.apply()

        prefs.edit()
            .putBoolean("_initialized", true)
            .remove("appPinCode")
            .remove("googleDriveAccessToken")
            .putString("currencySymbol", s.currencySymbol)
            .putInt("decimalPlaces", s.decimalPlaces)
            .putString("dateFormat", s.dateFormat)
            .putBoolean("onboardingCompleted", s.onboardingCompleted)
            .putString("defaultCountryCode", s.defaultCountryCode)
            .putString("defaultSmallUnit", s.defaultSmallUnit)
            .putString("defaultLargeUnit", s.defaultLargeUnit)
            .putFloat("defaultUnitFactor", s.defaultUnitFactor.toFloat())
            .putFloat("defaultAlertThreshold", s.defaultAlertThreshold.toFloat())
            .putBoolean("showBarcodeField", s.showBarcodeField)
            .putBoolean("showShelfLocation", s.showShelfLocation)
            .putBoolean("allowNegativeStock", s.allowNegativeStock)
            .putString("defaultPaymentType", s.defaultPaymentType)
            .putBoolean("warnOnLowStockInPos", s.warnOnLowStockInPos)
            .putBoolean("warnOnCreditLimit", s.warnOnCreditLimit)
            .putBoolean("showPhoneInAccounts", s.showPhoneInAccounts)
            .putString("invoicePrefix", s.invoicePrefix)
            .putString("purchasePrefix", s.purchasePrefix)
            .putInt("printerPaperWidthMm", s.printerPaperWidthMm)
            .putInt("printerCopies", s.printerCopies)
            .putBoolean("autoPrintAfterSale", s.autoPrintAfterSale)
            .putString("savedPrinterName", s.savedPrinterName)
            .putString("savedPrinterAddress", s.savedPrinterAddress)
            .putBoolean("autoBackupEnabled", s.autoBackupEnabled)
            .putInt("autoBackupIntervalHours", s.autoBackupIntervalHours)
            .putInt("keepBackupCount", s.keepBackupCount)
            .putString("lastLocalBackupTime", s.lastLocalBackupTime)
            .putLong("lastLocalBackupSize", s.lastLocalBackupSize)
            .putString("lastLocalBackupSha256", s.lastLocalBackupSha256)
            .putString("lastRestoreTime", s.lastRestoreTime)
            .putString("googleDriveAccountEmail", s.googleDriveAccountEmail)
            .putString("lastCloudBackupTime", s.lastCloudBackupTime)
            .putLong("lastCloudBackupSize", s.lastCloudBackupSize)
            .putString("lastCloudStatusMessage", s.lastCloudStatusMessage)
            .putBoolean("biometricLockEnabled", s.biometricLockEnabled)
            .putInt("lockTimeoutMinutes", s.lockTimeoutMinutes)
            .putBoolean("hideSensitivePreviewInRecents", s.hideSensitivePreviewInRecents)
            .putString("themeMode", s.themeMode.name)
            .putString("uiDensity", s.uiDensity.name)
            .putString("usageMode", s.usageMode.name)
            .putString("accentPalette", s.accentPalette.name)
            .putString("navStyle", s.navStyle.name)
            .putString("fabStyle", s.fabStyle.name)
            .putString("cornerStyle", s.cornerStyle.name)
            .putFloat("fontScale", s.fontScale)
            .putBoolean("enableHaptics", s.enableHaptics)
            .putBoolean("reduceMotion", s.reduceMotion)
            .apply()

        ioScope.launch {
            try {
                val activePrefDao = prefDao
                activePrefDao.setPreference(AppPreferenceEntity("currency_symbol", s.currencySymbol))
                activePrefDao.setPreference(AppPreferenceEntity("decimal_places", s.decimalPlaces.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("onboarding_completed", s.onboardingCompleted.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("default_country_code", s.defaultCountryCode))
                activePrefDao.setPreference(AppPreferenceEntity("invoice_prefix", s.invoicePrefix))
                activePrefDao.setPreference(AppPreferenceEntity("purchase_prefix", s.purchasePrefix))
                activePrefDao.setPreference(AppPreferenceEntity("allow_negative_stock", s.allowNegativeStock.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("theme_mode", s.themeMode.name))
                activePrefDao.setPreference(AppPreferenceEntity("ui_density", s.uiDensity.name))
                activePrefDao.setPreference(AppPreferenceEntity("usage_mode", s.usageMode.name))
                activePrefDao.setPreference(AppPreferenceEntity("accent_palette", s.accentPalette.name))
                activePrefDao.setPreference(AppPreferenceEntity("nav_style", s.navStyle.name))
                activePrefDao.setPreference(AppPreferenceEntity("fab_style", s.fabStyle.name))
                activePrefDao.setPreference(AppPreferenceEntity("corner_style", s.cornerStyle.name))
                activePrefDao.setPreference(AppPreferenceEntity("font_scale", s.fontScale.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("enable_haptics", s.enableHaptics.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("reduce_motion", s.reduceMotion.toString()))
            } catch (_: Exception) {
            }
        }
    }

    fun resetToDefaultsPreservingBackupHistory() {
        val current = _settings.value
        val resetSnapshot = AppSettingsSnapshot(
            onboardingCompleted = current.onboardingCompleted,
            lastLocalBackupTime = current.lastLocalBackupTime,
            lastLocalBackupSize = current.lastLocalBackupSize,
            lastLocalBackupSha256 = current.lastLocalBackupSha256,
            lastRestoreTime = current.lastRestoreTime,
            googleDriveAccountEmail = current.googleDriveAccountEmail,
            googleDriveAccessToken = current.googleDriveAccessToken,
            lastCloudBackupTime = current.lastCloudBackupTime,
            lastCloudBackupSize = current.lastCloudBackupSize,
            lastCloudStatusMessage = current.lastCloudStatusMessage,
            biometricLockEnabled = current.biometricLockEnabled,
            appPinCode = current.appPinCode
        )
        saveSnapshotToDisk(resetSnapshot)
        _settings.value = resetSnapshot
        notifyRuntimeObservers(resetSnapshot)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppPreferencesRepository? = null

        fun getInstance(context: Context): AppPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AppPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
