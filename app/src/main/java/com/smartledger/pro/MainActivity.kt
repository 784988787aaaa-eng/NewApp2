package com.smartledger.pro

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.smartledger.pro.di.AppContainer
import com.smartledger.pro.ui.components.AppSecurityLockScreen
import com.smartledger.pro.ui.components.AppSensoryConfig
import com.smartledger.pro.ui.navigation.MainAppScaffold
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerTheme

class MainActivity : FragmentActivity() {

    private var lastBackgroundTimestamp: Long = 0L
    private var needsUnlockState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(applicationContext)
        val prefsRepo = container.preferencesRepository
        val initialSettings = prefsRepo.settings.value

        AppDesignState.syncFromSettings(initialSettings)
        AppSensoryConfig.syncFromSettings(initialSettings)

        if (savedInstanceState != null) {
            // استعادة حالة القفل وطابع الخلفية عند تدوير الشاشة أو تغيير الإعدادات (Configuration Change)
            // لمنع إغلاق الجلسة النشطة خطأً أثناء عمل المستخدم
            needsUnlockState = savedInstanceState.getBoolean(KEY_NEEDS_UNLOCK, false)
            lastBackgroundTimestamp = savedInstanceState.getLong(KEY_LAST_BG_TIMESTAMP, 0L)
        } else if (initialSettings.biometricLockEnabled) {
            needsUnlockState = true
        }

        setContent {
            val settings by prefsRepo.settings.collectAsState()

            LaunchedEffect(settings) {
                AppDesignState.syncFromSettings(settings)
                AppSensoryConfig.syncFromSettings(settings)
            }

            // تطبيق حماية إخفاء المحتوى الحساس من معاينة التطبيقات الأخيرة (Recents Preview) عند التفعيل
            LaunchedEffect(settings.hideSensitivePreviewInRecents) {
                if (settings.hideSensitivePreviewInRecents) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            SmartLedgerTheme {
                if (needsUnlockState && settings.biometricLockEnabled) {
                    AppSecurityLockScreen(
                        expectedPinVerifier = settings.appPinCode,
                        onUnlocked = {
                            needsUnlockState = false
                            lastBackgroundTimestamp = 0L
                        }
                    )
                } else {
                    MainAppScaffold()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_NEEDS_UNLOCK, needsUnlockState)
        outState.putLong(KEY_LAST_BG_TIMESTAMP, lastBackgroundTimestamp)
    }

    override fun onStop() {
        super.onStop()
        // لا نسجل طابع مغادرة التطبيق إذا كان الإيقاف ناتجاً عن تدوير الشاشة أو تغيير كثافة/حجم النافذة
        if (!isChangingConfigurations) {
            lastBackgroundTimestamp = SystemClock.elapsedRealtime()
        }
    }

    override fun onStart() {
        super.onStart()
        val settings = AppContainer.getInstance(applicationContext).preferencesRepository.settings.value
        if (settings.biometricLockEnabled && lastBackgroundTimestamp > 0L) {
            val elapsedMs = (SystemClock.elapsedRealtime() - lastBackgroundTimestamp).coerceAtLeast(0L)
            val requiredTimeoutMs = if (settings.lockTimeoutMinutes <= 0) {
                IMMEDIATE_LOCK_GRACE_MS
            } else {
                settings.lockTimeoutMinutes * 60_000L
            }
            if (elapsedMs >= requiredTimeoutMs) {
                needsUnlockState = true
            }
            lastBackgroundTimestamp = 0L
        }
    }

    companion object {
        private const val KEY_NEEDS_UNLOCK = "smartledger_state_needs_unlock"
        private const val KEY_LAST_BG_TIMESTAMP = "smartledger_state_last_bg_timestamp"
        private const val IMMEDIATE_LOCK_GRACE_MS = 2_500L
    }
}
