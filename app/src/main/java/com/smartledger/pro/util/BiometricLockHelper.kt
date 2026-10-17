package com.smartledger.pro.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricCapabilityStatus(val canUseBiometric: Boolean, val description: String) {
    READY(true, "القياسات الحيوية متاحة ومفعّلة على هذا الجهاز"),
    NO_HARDWARE(false, "الجهاز لا يحتوي على مستشعر بصمة أو قياسات حيوية"),
    HARDWARE_UNAVAILABLE(false, "مستشعر القياسات الحيوية غير متاح حالياً"),
    NONE_ENROLLED(false, "لم يتم تسجيل بصمة إصبع أو قفل شاشة في إعدادات الجهاز بعد")
}

/**
 * مساعد البنية التحتية للمصادقة البيومترية (`BiometricLockHelper`).
 *
 * معزول بالكامل عن طبقة واجهة المستخدم (`com.smartledger.pro.ui.*`) لمنع الاعتماديات الدائرية بين `util` و`ui`.
 * شاشة القفل المرئية (`AppSecurityLockScreen`) تعيش داخل طبقة العرض (`com.smartledger.pro.ui.components`).
 */
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
