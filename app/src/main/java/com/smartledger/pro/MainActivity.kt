package com.smartledger.pro

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.smartledger.pro.ui.navigation.MainAppScaffold
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTheme
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.BiometricSecurityHelper

class MainActivity : FragmentActivity() {

    private var lastBackgroundTimestamp by mutableStateOf(0L)
    private var isLocked by mutableStateOf(false)
    private var lockErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettingsManager.initialize(applicationContext)

        setContent {
            val settings by AppSettingsManager.settings.collectAsState()

            LaunchedEffect(settings.hideScreenInRecents) {
                if (settings.hideScreenInRecents) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(settings.biometricEnabled) {
                if (settings.biometricEnabled) {
                    val availability = BiometricSecurityHelper.checkAvailability(this@MainActivity)
                    if (!availability.isSupported) {
                        isLocked = false
                    }
                } else {
                    isLocked = false
                }
            }

            SmartLedgerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainAppScaffold()

                    if (isLocked && settings.biometricEnabled) {
                        BiometricLockOverlay(
                            errorMessage = lockErrorMessage,
                            onUnlockRequest = { triggerBiometricUnlock() }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundTimestamp = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        val currentSettings = AppSettingsManager.settings.value
        if (currentSettings.biometricEnabled) {
            val availability = BiometricSecurityHelper.checkAvailability(this)
            if (availability.isSupported) {
                val elapsedSeconds = if (lastBackgroundTimestamp == 0L) {
                    Long.MAX_VALUE
                } else {
                    (SystemClock.elapsedRealtime() - lastBackgroundTimestamp) / 1000L
                }
                if (elapsedSeconds >= currentSettings.lockTimeoutSeconds) {
                    isLocked = true
                    triggerBiometricUnlock()
                }
            } else {
                isLocked = false
            }
        }
    }

    private fun triggerBiometricUnlock() {
        lockErrorMessage = null
        BiometricSecurityHelper.authenticate(
            activity = this,
            onSuccess = {
                isLocked = false
                lockErrorMessage = null
                lastBackgroundTimestamp = SystemClock.elapsedRealtime()
            },
            onError = { err ->
                lockErrorMessage = err
            }
        )
    }
}

@Composable
private fun BiometricLockOverlay(
    errorMessage: String?,
    onUnlockRequest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SmartLedgerColors.SurfaceWindow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.AccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "الدفتر الذكي Pro محمي",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "يرجى التحقق من هويتك بالبصمة أو رمز قفل الشاشة للوصول إلى البيانات المالية.",
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.TextMuted
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.StateDanger,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUnlockRequest,
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فتح القفل بالبصمة", fontWeight = FontWeight.Bold)
            }
        }
    }
}
