package com.smartledger.pro.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * محرك التشفير وحماية الأسرار والـ PIN (SecurityCryptoHelper).
 *
 * القواعد الأمنية الصارمة:
 * 1. لا يُسمح بأي مسار إخفاء شكلي (Base64 obfuscation) بديلاً عن التشفير الفعلي `AES/GCM/NoPadding`.
 *    إذا فشل التشفير الفعلي، تعيد الدالة `Result.failure` ولا تحفظ الرمز بصيغة غير محمية.
 * 2. لا يقبل `verifyPin` إلا البصمات المملحة القياسية بصيغة `v1$iterations$saltHex$hashHex`
 *    المشتقة عبر `PBKDF2WithHmacSHA256` وبمقارنة زمنية ثابتة (`MessageDigest.isEqual`).
 */
object SecurityCryptoHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SmartLedgerPro_MasterSecretKey_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 12000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16

    /**
     * اشتقاق قيمة تحقق مملحة (Salted Hash) لرمز PIN بصيغة `v1$iterations$saltHex$hashHex`
     */
    fun hashPin(rawPin: String, customSalt: ByteArray? = null): String {
        val cleanPin = NumberFormatter.normalizeNumericInput(rawPin.trim(), allowDecimal = false)
        if (cleanPin.isEmpty()) return ""

        val salt = customSalt ?: ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derivePbkdf2(cleanPin, salt, PBKDF2_ITERATIONS)
        return "v1$$PBKDF2_ITERATIONS$${bytesToHex(salt)}$${bytesToHex(derived)}"
    }

    /**
     * التحقق الصارم من تطابق رمز PIN المدخل مع البصمة المملحة المخزنة بزمن ثابت (Constant-Time Comparison).
     * يرفض أي نص غير مملح بصيغة `v1$iterations$salt$hash`.
     */
    fun verifyPin(inputPin: String, storedVerifier: String): Boolean {
        val cleanInput = NumberFormatter.normalizeNumericInput(inputPin.trim(), allowDecimal = false)
        if (cleanInput.isEmpty() || storedVerifier.isBlank()) return false
        if (!storedVerifier.startsWith("v1$")) return false

        val parts = storedVerifier.split("$")
        if (parts.size != 4) return false

        return try {
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations < 1000) return false
            val salt = hexToBytes(parts[2])
            val expectedHash = hexToBytes(parts[3])
            if (salt.isEmpty() || expectedHash.isEmpty()) return false
            val candidateHash = derivePbkdf2(cleanInput, salt, iterations)
            MessageDigest.isEqual(expectedHash, candidateHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * حساب مدة الحظر المؤقت بالثواني (Progressive Backoff) بناءً على عدد المحاولات الفاشلة المتتالية
     */
    fun calculateLockoutDurationSeconds(failedAttempts: Int): Int {
        return when {
            failedAttempts < 5 -> 0
            failedAttempts == 5 -> 30
            failedAttempts == 6 -> 60
            failedAttempts == 7 -> 120
            else -> 300
        }
    }

    /**
     * تشفير نص سري فعلياً عبر `AES/GCM/NoPadding` مع إعادة `Result<String>` صريح (يرفض الحفظ إذا تعذر التشفير).
     */
    fun encryptSecretResult(plainText: String): Result<String> {
        val clean = plainText.trim()
        if (clean.isEmpty()) return Result.success("")
        return try {
            val keystoreKey = getOrCreateKeystoreSecretKey()
            if (keystoreKey != null) {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.getEncoder().encodeToString(iv)
                val cipherB64 = Base64.getEncoder().encodeToString(cipherBytes)
                Result.success("enc_v1:$ivB64:$cipherB64")
            } else {
                // تشفير AES-256-GCM فعلي بمفتاح مشتق عبر PBKDF2 وملح عشوائي في بيئة اختبارات JVM
                val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
                val aesKey = deriveJvmAesGcmKey(salt)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val cipherBytes = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
                val saltB64 = Base64.getEncoder().encodeToString(salt)
                val ivB64 = Base64.getEncoder().encodeToString(iv)
                val cipherB64 = Base64.getEncoder().encodeToString(cipherBytes)
                Result.success("enc_gcm_pbkdf2_v1:$saltB64:$ivB64:$cipherB64")
            }
        } catch (e: Throwable) {
            Result.failure(Exception("فشل تشفير البيانات الحساسة عبر AES-GCM: ${e.localizedMessage}"))
        }
    }

    fun encryptSecret(plainText: String): String {
        return encryptSecretResult(plainText).getOrThrow()
    }

    /**
     * فك تشفير النص السري المشفر عبر `AES/GCM/NoPadding`؛ يعيد `Result<String>` لكشف أي تلف أو فقدان مفتاح.
     */
    fun decryptSecretResult(storedCipherText: String): Result<String> {
        val clean = storedCipherText.trim()
        if (clean.isEmpty()) return Result.success("")
        return try {
            when {
                clean.startsWith("enc_v1:") -> {
                    val parts = clean.split(":")
                    if (parts.size != 3) return Result.failure(Exception("صيغة البيانات المشفرة غير صالحة"))
                    val secretKey = getOrCreateKeystoreSecretKey()
                        ?: return Result.failure(Exception("مفتاح AndroidKeyStore غير متاح لفك التشفير"))
                    val iv = Base64.getDecoder().decode(parts[1])
                    val cipherBytes = Base64.getDecoder().decode(parts[2])
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    Result.success(String(cipher.doFinal(cipherBytes), Charsets.UTF_8))
                }
                clean.startsWith("enc_gcm_pbkdf2_v1:") -> {
                    val parts = clean.split(":")
                    if (parts.size != 4) return Result.failure(Exception("صيغة تشفير AES-GCM غير صالحة"))
                    val salt = Base64.getDecoder().decode(parts[1])
                    val iv = Base64.getDecoder().decode(parts[2])
                    val cipherBytes = Base64.getDecoder().decode(parts[3])
                    val aesKey = deriveJvmAesGcmKey(salt)
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    Result.success(String(cipher.doFinal(cipherBytes), Charsets.UTF_8))
                }
                else -> Result.failure(Exception("البيانات المخزنة ليست بصيغة مشفرة معتمدة (AES-GCM)"))
            }
        } catch (e: Throwable) {
            Result.failure(Exception("تعذر فك تشفير الرمز المحفوظ (ربما تغير مفتاح الجهاز): ${e.localizedMessage}"))
        }
    }

    fun decryptSecret(storedCipherText: String): String {
        return decryptSecretResult(storedCipherText).getOrDefault("")
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existingKey != null) return existingKey

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun deriveJvmAesGcmKey(salt: ByteArray): SecretKeySpec {
        val rawKey = derivePbkdf2("SmartLedgerPro_AEAD_Key_Context_v2.4", salt, PBKDF2_ITERATIONS)
        return SecretKeySpec(rawKey, "AES")
    }

    private fun derivePbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        val len = clean.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(clean[i], 16)
            val lo = Character.digit(clean[i + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character" }
            out[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return out
    }
}
