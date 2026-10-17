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

        if (initialSettings.biometricLockEnabled) {
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
                        onUnlocked = { needsUnlockState = false }
                    )
                } else {
                    MainAppScaffold()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTimestamp = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val settings = AppContainer.getInstance(applicationContext).preferencesRepository.settings.value
        if (settings.biometricLockEnabled && lastBackgroundTimestamp > 0L) {
            val elapsedMinutes = (SystemClock.elapsedRealtime() - lastBackgroundTimestamp) / 60000L
            if (elapsedMinutes >= settings.lockTimeoutMinutes) {
                needsUnlockState = true
            }
        }
    }
}
