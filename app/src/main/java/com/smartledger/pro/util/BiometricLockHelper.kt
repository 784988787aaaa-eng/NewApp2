package com.smartledger.pro.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.smartledger.pro.ui.components.AppHaptics
import com.smartledger.pro.ui.components.SmartTextField
import com.smartledger.pro.ui.components.pressScaleEffect
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import kotlinx.coroutines.delay

enum class BiometricCapabilityStatus(val canUseBiometric: Boolean, val description: String) {
    READY(true, "القياسات الحيوية متاحة ومفعّلة على هذا الجهاز"),
    NO_HARDWARE(false, "الجهاز لا يحتوي على مستشعر بصمة أو قياسات حيوية"),
    HARDWARE_UNAVAILABLE(false, "مستشعر القياسات الحيوية غير متاح حالياً"),
    NONE_ENROLLED(false, "لم يتم تسجيل بصمة إصبع أو قفل شاشة في إعدادات الجهاز بعد")
}

object BiometricLockHelper {

    /**
     * اختيار تركيبة وسائل المصادقة المتوافقة رسمياً مع قواعد AndroidX Biometric حسب إصدار أندرويد:
     * - في Android 11+ (API 30+): يُسمح بدمج `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
     * - في Android 8–10 (API 26–29): ترفض مكتبة AndroidX Biometric جمع `BIOMETRIC_STRONG` مع `DEVICE_CREDENTIAL`
     *   في `PromptInfo.Builder`، لذا نستخدم `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` أو `BIOMETRIC_STRONG` مع زر بديل.
     */
    fun getAllowedAuthenticatorsForOs(sdkInt: Int = Build.VERSION.SDK_INT): Int {
        return if (sdkInt >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun checkBiometricSupport(context: Context): BiometricCapabilityStatus {
        return try {
            val manager = BiometricManager.from(context)
            val primaryAuth = getAllowedAuthenticatorsForOs()
            var result = manager.canAuthenticate(primaryAuth)
            if (result != BiometricManager.BIOMETRIC_SUCCESS) {
                result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            }
            when (result) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.READY
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NONE_ENROLLED
                else -> BiometricCapabilityStatus.HARDWARE_UNAVAILABLE
            }
        } catch (_: Exception) {
            BiometricCapabilityStatus.HARDWARE_UNAVAILABLE
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "تأكيد الهوية — الدفتر الذكي Pro",
        subtitle: String = "استخدم بصمة الإصبع أو رمز قفل الجهاز لفتح دفاترك المالية",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("لم يتم التعرف على البصمة، حاول مجدداً")
                    }
                }
            )

            val manager = BiometricManager.from(activity)
            val osAuthenticators = getAllowedAuthenticatorsForOs()
            val promptInfo = if (manager.canAuthenticate(osAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(osAuthenticators)
                    .build()
            } else {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                    .setNegativeButtonText("استخدام رمز الحماية (PIN)")
                    .build()
            }

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("تعذر تشغيل نافذة المصادقة البيومترية: ${e.localizedMessage ?: "استخدم رمز الحماية البديل"}")
        }
    }
}

/**
 * شاشة قفل التطبيق الآمنة (AppSecurityLockScreen)
 * - تتحقق من رمز PIN عبر `SecurityCryptoHelper.verifyPin` (مقارنة مملحة PBKDF2 بزمن ثابت).
 * - تفرض تأخيراً متدرجاً (`Progressive Lockout`) عند تكرار المحاولات الفاشلة لمنع التخمين.
 */
@Composable
fun AppSecurityLockScreen(
    expectedPinVerifier: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val view = androidx.compose.ui.platform.LocalView.current
    val capability = remember { BiometricLockHelper.checkBiometricSupport(context) }

    var pinInput by rememberSaveable { mutableStateOf("") }
    var failedAttempts by rememberSaveable { mutableStateOf(0) }
    var lockoutRemainingSeconds by rememberSaveable { mutableStateOf(0) }
    var lockoutUntilRealtime by rememberSaveable { mutableStateOf(0L) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(lockoutUntilRealtime) {
        while (lockoutUntilRealtime > SystemClock.elapsedRealtime()) {
            val remaining = ((lockoutUntilRealtime - SystemClock.elapsedRealtime() + 999L) / 1000L).toInt()
            lockoutRemainingSeconds = remaining.coerceAtLeast(0)
            delay(250L)
        }
        lockoutRemainingSeconds = 0
    }

    val verifyEnteredPin: () -> Unit = {
        if (lockoutRemainingSeconds > 0) {
            errorMessage = "تم إيقاف المحاولات مؤقتاً لحماية الحساب. انتظر $lockoutRemainingSeconds ثانية."
            AppHaptics.perform(view, AppHaptics.FeedbackKind.ERROR)
        } else if (SecurityCryptoHelper.verifyPin(pinInput, expectedPinVerifier)) {
            failedAttempts = 0
            lockoutUntilRealtime = 0L
            lockoutRemainingSeconds = 0
            AppHaptics.perform(view, AppHaptics.FeedbackKind.CONFIRM)
            onUnlocked()
        } else {
            failedAttempts++
            val penaltySec = SecurityCryptoHelper.calculateLockoutDurationSeconds(failedAttempts)
            if (penaltySec > 0) {
                lockoutUntilRealtime = SystemClock.elapsedRealtime() + (penaltySec * 1000L)
                lockoutRemainingSeconds = penaltySec
                errorMessage = "تجاوزت الحد المسموح للمحاولات ($failedAttempts). يرجى الانتظار $penaltySec ثانية."
            } else {
                val remainingTries = (5 - failedAttempts).coerceAtLeast(1)
                errorMessage = "رمز الحماية غير صحيح (متبقي $remainingTries محاولات قبل الإيقاف المؤقت)"
            }
            AppHaptics.perform(view, AppHaptics.FeedbackKind.ERROR)
        }
    }

    LaunchedEffect(Unit) {
        if (activity != null && capability.canUseBiometric) {
            BiometricLockHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    failedAttempts = 0
                    lockoutUntilRealtime = 0L
                    onUnlocked()
                },
                onError = { err -> errorMessage = err }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.SurfaceWindow)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.AccentSubtle)
                        .border(1.5.dp, SmartLedgerColors.AccentPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SmartLedgerColors.AccentLight,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "الدفتر الذكي Pro محمي",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )

                Text(
                    text = "لحماية سجلاتك المالية، يرجى تأكيد الهوية بالبصمة أو إدخال رمز المرور.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextMuted,
                    textAlign = TextAlign.Center
                )

                if (expectedPinVerifier.isNotBlank()) {
                    SmartTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = NumberFormatter.normalizeNumericInput(it, allowDecimal = false).take(8)
                            if (lockoutRemainingSeconds == 0) {
                                errorMessage = null
                            }
                        },
                        label = "رمز الحماية الشخصي (PIN)",
                        placeholder = "أدخل الرمز الرقمي...",
                        enabled = lockoutRemainingSeconds == 0,
                        errorText = if (lockoutRemainingSeconds > 0) {
                            "يرجى الانتظار $lockoutRemainingSeconds ثانية قبل المحاولة مجدداً"
                        } else errorMessage,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onDoneAction = { verifyEnteredPin() }
                    )

                    Button(
                        onClick = { verifyEnteredPin() },
                        enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .pressScaleEffect(enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text(
                            text = if (lockoutRemainingSeconds > 0) "مقفل مؤقتاً ($lockoutRemainingSeconds ث)" else "تحقق وفتح القفل",
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextInverse
                        )
                    }
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.StateDanger,
                        textAlign = TextAlign.Center
                    )
                }

                if (activity != null && capability.canUseBiometric) {
                    OutlinedButton(
                        onClick = {
                            BiometricLockHelper.showBiometricPrompt(
                                activity = activity,
                                onSuccess = {
                                    failedAttempts = 0
                                    lockoutUntilRealtime = 0L
                                    onUnlocked()
                                },
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المصادقة ببصمة الإصبع / قفل الجهاز", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                    }
                } else if (expectedPinVerifier.isBlank()) {
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarning,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.pressScaleEffect(),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text("المتابعة إلى التطبيق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
