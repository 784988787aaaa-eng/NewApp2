package com.smartledger.pro.util

import android.content.Context
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
import com.smartledger.pro.ui.components.SmartTextField
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

enum class BiometricCapabilityStatus(val canUseBiometric: Boolean, val description: String) {
    READY(true, "القياسات الحيوية متاحة ومفعّلة على هذا الجهاز"),
    NO_HARDWARE(false, "الجهاز لا يحتوي على مستشعر بصمة أو قياسات حيوية"),
    HARDWARE_UNAVAILABLE(false, "مستشعر القياسات الحيوية غير متاح حالياً"),
    NONE_ENROLLED(false, "لم يتم تسجيل بصمة إصبع أو قفل شاشة في إعدادات الجهاز بعد")
}

object BiometricLockHelper {

    fun checkBiometricSupport(context: Context): BiometricCapabilityStatus {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
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
                        onError(errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("لم يتم التعرف على البصمة، حاول مجدداً")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("تعذر تشغيل نافذة المصادقة البيومترية: ${e.localizedMessage ?: "استخدم رمز الحماية البديل"}")
        }
    }
}

/**
 * شاشة قفل التطبيق الآمنة (AppSecurityLockScreen)
 * تدعم فتح القفل عبر BiometricPrompt الرسمي أو عبر رمز PIN الاحتياطي عند غياب المستشعر أو فشل القراءة.
 */
@Composable
fun AppSecurityLockScreen(
    expectedPin: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val capability = remember { BiometricLockHelper.checkBiometricSupport(context) }

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (activity != null && capability.canUseBiometric) {
            BiometricLockHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlocked,
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

                if (expectedPin.isNotBlank()) {
                    SmartTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.take(8)
                            errorMessage = null
                            if (pinInput == expectedPin) {
                                onUnlocked()
                            }
                        },
                        label = "رمز الحماية الشخصي (PIN)",
                        placeholder = "أدخل الرمز الرقمي...",
                        errorText = errorMessage,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onDoneAction = {
                            if (pinInput == expectedPin) {
                                onUnlocked()
                            } else {
                                errorMessage = "رمز الحماية غير صحيح"
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (pinInput == expectedPin) {
                                onUnlocked()
                            } else {
                                errorMessage = "رمز الحماية غير صحيح"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .bounceClick {
                                if (pinInput == expectedPin) onUnlocked()
                                else errorMessage = "رمز الحماية غير صحيح"
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text("فتح القفل", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
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
                                onSuccess = onUnlocked,
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المصادقة ببصمة الإصبع / قفل الجهاز", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                    }
                } else if (expectedPin.isBlank()) {
                    // إذا لم يكن هناك عتاد بيومتري ولا رمز PIN مضبوط، اسمح بالدخول الآمن مع تنبيه المستخدم
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarning,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onUnlocked,
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
