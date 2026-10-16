package com.smartledger.pro.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability(val isSupported: Boolean, val statusMessage: String) {
    AVAILABLE(true, "البصمة والقياسات الحيوية جاهزة ومفعلة على هذا الجهاز"),
    NO_HARDWARE(false, "هذا الجهاز لا يحتوي على مستشعر بصمة أو قياسات حيوية"),
    HARDWARE_UNAVAILABLE(false, "مستشعر البصمة غير متاح حالياً"),
    NONE_ENROLLED(false, "لم يتم تسجيل أي بصمة أو قفل شاشة في إعدادات الجهاز بعد"),
    UNKNOWN(false, "حالة القياسات الحيوية غير معروفة على هذا الجهاز")
}

object BiometricSecurityHelper {

    // BIOMETRIC_STRONG and BIOMETRIC_WEAK are mutually exclusive flags.
    // Device-credential fallback is supported alongside biometrics on Android 11+.
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    fun checkAvailability(context: Context): BiometricAvailability {
        return try {
            val manager = BiometricManager.from(context)
            when (manager.canAuthenticate(allowedAuthenticators())) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
                else -> BiometricAvailability.UNKNOWN
            }
        } catch (_: Exception) {
            BiometricAvailability.UNKNOWN
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "قفل الأمان — الدفتر الذكي Pro",
        subtitle: String = "تحقق من هويتك للوصول إلى السجلات المالية والمحاسبية",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val availability = checkAvailability(activity)
        if (!availability.isSupported) {
            onError(availability.statusMessage)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
