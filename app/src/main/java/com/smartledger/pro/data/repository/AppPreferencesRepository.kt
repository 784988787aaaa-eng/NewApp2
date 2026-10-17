package com.smartledger.pro.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.AppPreferenceDao
import com.smartledger.pro.data.local.entity.AppPreferenceEntity
import com.smartledger.pro.domain.model.AppThemeMode
import com.smartledger.pro.domain.model.AppUiDensity
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

data class AppSettingsSnapshot(
    // 1. العملة والتنسيق
    val currencySymbol: String = "ر.ي",
    val decimalPlaces: Int = 0,
    val dateFormat: String = "yyyy-MM-dd",
    // 2. المخزون والوحدات
    val defaultSmallUnit: String = "قطعة",
    val defaultLargeUnit: String = "كرتون",
    val defaultUnitFactor: Double = 24.0,
    val defaultAlertThreshold: Double = 5.0,
    val showBarcodeField: Boolean = true,
    val showShelfLocation: Boolean = true,
    val allowNegativeStock: Boolean = true,
    // 3. المبيعات والدفع والحسابات
    val defaultPaymentType: String = "Cash",
    val warnOnLowStockInPos: Boolean = true,
    val warnOnCreditLimit: Boolean = true,
    val showPhoneInAccounts: Boolean = true,
    val invoicePrefix: String = "INV",
    val purchasePrefix: String = "PUR",
    // 4. الطباعة الحرارية
    val printerPaperWidthMm: Int = 80, // 80 or 58
    val printerCopies: Int = 1,
    val autoPrintAfterSale: Boolean = false,
    val savedPrinterName: String = "",
    val savedPrinterAddress: String = "",
    // 5. النسخ الاحتياطي المحلي والسحابي
    val autoBackupEnabled: Boolean = true,
    val autoBackupIntervalHours: Int = 24,
    val keepBackupCount: Int = 15,
    val lastLocalBackupTime: String = "",
    val lastLocalBackupSize: Long = 0L,
    val lastLocalBackupSha256: String = "",
    val googleDriveAccountEmail: String = "",
    val googleDriveAccessToken: String = "",
    val lastCloudBackupTime: String = "",
    val lastCloudBackupSize: Long = 0L,
    val lastCloudStatusMessage: String = "",
    // 6. الأمان والخصوصية (لا يُخزن رمز PIN كنص صريح؛ بل تُخزن بصمة PBKDF2 المملحة فقط)
    val biometricLockEnabled: Boolean = false,
    val appPinCode: String = "", // يحمل بصمة التحقق المملحة (v1$iterations$salt$hash) وليس الرمز الصريح
    val lockTimeoutMinutes: Int = 5,
    val hideSensitivePreviewInRecents: Boolean = false,
    // 7. المظهر وسهولة الاستخدام
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val uiDensity: AppUiDensity = AppUiDensity.COMFORTABLE,
    val fontScale: Float = 1.0f,
    val enableHaptics: Boolean = true,
    val reduceMotion: Boolean = false
) {
    val hasPinConfigured: Boolean
        get() = appPinCode.startsWith("v1$")
}

/**
 * مستودع الإعدادات والأسرار الموحد (AppPreferencesRepository).
 *
 * الضمانات المعمارية والأمنية:
 * 1. مستقل تماماً عن طبقة واجهة المستخدم (`com.smartledger.pro.ui.*`)؛ لا يستورد أي كائنات Compose أو Theme،
 *    بل يبث التغييرات عبر `settings: StateFlow<AppSettingsSnapshot>` ومراقبي الحالة المسجلين (`registerRuntimeStateObserver`).
 * 2. يحصل على `AppPreferenceDao` ديناميكياً من `SmartLedgerDatabase.getInstance(appContext)` ويراقب
 *    `SmartLedgerDatabase.databaseEpoch` لإعادة قراءة التفضيلات المحمولة تلقائياً فور استعادة أي نسخة احتياطية.
 * 3. يستخدم `CoroutineScope(SupervisorJob() + Dispatchers.IO)` لضمان عدم تعطل المزامنة الخلفية عند حدوث أي استثناء عابر.
 */
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

    /**
     * تسجيل مراقب لتغييرات الإعدادات (تستخدمه طبقة العرض/الحاوية لمزامنة `AppDesignState` و`AppSensoryConfig`
     * دون كسر حدود الطبقات بين `data` و`ui`).
     */
    fun registerRuntimeStateObserver(observer: (AppSettingsSnapshot) -> Unit) {
        runtimeObservers.add(observer)
        observer(_settings.value)
    }

    private fun notifyRuntimeObservers(snapshot: AppSettingsSnapshot) {
        for (observer in runtimeObservers) {
            runCatching { observer(snapshot) }
        }
    }

    /**
     * يُستدعى فور نجاح استعادة قاعدة البيانات لإعادة قراءة التفضيلات المحمولة من القاعدة المستعادة الجديدة
     */
    fun onDatabaseRestored() {
        syncPortablePreferencesFromRoomIfNeeded(forceReloadFromRoom = true)
    }

    /**
     * ترحيل آمن ومتحقق منه لأي رمز PIN صريح أو رمز Google Drive قديم إلى الصيغة المحمية الجديدة،
     * مع التحقق من صحة فك التشفير/المطابقة قبل حذف القيمة القديمة.
     */
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
                    roomPrefs["invoice_prefix"]?.takeIf { it.isNotBlank() }?.let { editor.putString("invoicePrefix", it) }
                    roomPrefs["purchase_prefix"]?.takeIf { it.isNotBlank() }?.let { editor.putString("purchasePrefix", it) }
                    roomPrefs["allow_negative_stock"]?.toBooleanStrictOrNull()?.let { editor.putBoolean("allowNegativeStock", it) }
                    roomPrefs["theme_mode"]?.takeIf { it.isNotBlank() }?.let { editor.putString("themeMode", it) }
                    roomPrefs["ui_density"]?.takeIf { it.isNotBlank() }?.let { editor.putString("uiDensity", it) }
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

        val encryptedToken = securePrefs.getString("encryptedDriveToken", "") ?: ""
        val decryptedToken = SecurityCryptoHelper.decryptSecretResult(encryptedToken).getOrDefault("")
        val storedPinVerifier = (securePrefs.getString("appPinHash", "") ?: "").takeIf { it.startsWith("v1$") } ?: ""

        return AppSettingsSnapshot(
            currencySymbol = prefs.getString("currencySymbol", "ر.ي") ?: "ر.ي",
            decimalPlaces = prefs.getInt("decimalPlaces", 0),
            dateFormat = prefs.getString("dateFormat", "yyyy-MM-dd") ?: "yyyy-MM-dd",
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
            .putString("googleDriveAccountEmail", s.googleDriveAccountEmail)
            .putString("lastCloudBackupTime", s.lastCloudBackupTime)
            .putLong("lastCloudBackupSize", s.lastCloudBackupSize)
            .putString("lastCloudStatusMessage", s.lastCloudStatusMessage)
            .putBoolean("biometricLockEnabled", s.biometricLockEnabled)
            .putInt("lockTimeoutMinutes", s.lockTimeoutMinutes)
            .putBoolean("hideSensitivePreviewInRecents", s.hideSensitivePreviewInRecents)
            .putString("themeMode", s.themeMode.name)
            .putString("uiDensity", s.uiDensity.name)
            .putFloat("fontScale", s.fontScale)
            .putBoolean("enableHaptics", s.enableHaptics)
            .putBoolean("reduceMotion", s.reduceMotion)
            .apply()

        ioScope.launch {
            try {
                val activePrefDao = prefDao
                activePrefDao.setPreference(AppPreferenceEntity("currency_symbol", s.currencySymbol))
                activePrefDao.setPreference(AppPreferenceEntity("decimal_places", s.decimalPlaces.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("invoice_prefix", s.invoicePrefix))
                activePrefDao.setPreference(AppPreferenceEntity("purchase_prefix", s.purchasePrefix))
                activePrefDao.setPreference(AppPreferenceEntity("allow_negative_stock", s.allowNegativeStock.toString()))
                activePrefDao.setPreference(AppPreferenceEntity("theme_mode", s.themeMode.name))
                activePrefDao.setPreference(AppPreferenceEntity("ui_density", s.uiDensity.name))
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
            lastLocalBackupTime = current.lastLocalBackupTime,
            lastLocalBackupSize = current.lastLocalBackupSize,
            lastLocalBackupSha256 = current.lastLocalBackupSha256,
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
