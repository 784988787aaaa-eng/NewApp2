package com.smartledger.pro.data.repository

import com.smartledger.pro.domain.model.AppAccentPalette
import com.smartledger.pro.domain.model.AppCornerStyle
import com.smartledger.pro.domain.model.AppFabStyle
import com.smartledger.pro.domain.model.AppNavStyle
import com.smartledger.pro.domain.model.AppThemeMode
import com.smartledger.pro.domain.model.AppUiDensity
import com.smartledger.pro.domain.model.AppUsageMode

data class AppSettingsSnapshot(
    // 1. العملة والتنسيق والإعداد الأولي
    val currencySymbol: String = "ر.ي",
    val decimalPlaces: Int = 0,
    val dateFormat: String = "yyyy-MM-dd",
    val onboardingCompleted: Boolean = false,
    val defaultCountryCode: String = "+967",
    // 2. المخزون والوحدات
    val defaultSmallUnit: String = "قطعة",
    val defaultLargeUnit: String = "كرتون",
    val defaultUnitFactor: Double = 24.0,
    val defaultAlertThreshold: Double = 5.0,
    val showBarcodeField: Boolean = true, // legacy shared preference
    val showShelfLocation: Boolean = true, // legacy shared preference
    // Per-screen display preferences. Hiding a field never removes stored values.
    val showBarcodeInInventory: Boolean = true,
    val showShelfLocationInProduct: Boolean = true,
    val showBarcodeInPos: Boolean = true,
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
    val lastRestoreTime: String = "",
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
    // 7. المظهر، نمط الاستخدام (كلاسيكي/عادي/احترافي)، وتخصيص التصميم والأزرار
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val uiDensity: AppUiDensity = AppUiDensity.COMFORTABLE,
    val usageMode: AppUsageMode = AppUsageMode.PRO,
    val accentPalette: AppAccentPalette = AppAccentPalette.ROYAL_SAPPHIRE,
    val navStyle: AppNavStyle = AppNavStyle.FLOATING_DOCK,
    val fabStyle: AppFabStyle = AppFabStyle.ORBITAL_RADIAL,
    val cornerStyle: AppCornerStyle = AppCornerStyle.ROUNDED,
    val fontScale: Float = 1.0f,
    val enableHaptics: Boolean = true,
    val reduceMotion: Boolean = false
) {
    val hasPinConfigured: Boolean
        get() = appPinCode.startsWith("v1$")
}
