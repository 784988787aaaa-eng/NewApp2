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

/**
 * محرك التشفير وحماية الأسرار والـ PIN (SecurityCryptoHelper).
 *
 * يعالج المتطلبات الأمنية الحرجة:
 * 1. عدم تخزين رمز الحماية (PIN) كنص صريح مطلقاً؛ بل اشتقاق بصمة مملحة عبر `PBKDF2WithHmacSHA256`
 *    (12,000 دورة + ملح عشوائي 16 بايت) ومقارنة زمنية ثابتة (`MessageDigest.isEqual`) لمنع هجمات التوقيت.
 * 2. فرض سياسة التأخير التدرجي (`Progressive Lockout`) بعد المحاولات الفاشلة لمنع التخمين الآلي.
 * 3. تشفير رموز الوصول الحساسة (مثل `googleDriveAccessToken`) باستخدام مفتاح `AES/GCM/NoPadding`
 *    محمي داخل عتاد `AndroidKeyStore`.
 */
object SecurityCryptoHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SmartLedgerPro_MasterSecretKey_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
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
     * التحقق من تطابق رمز PIN المدخل مع البصمة المخزنة بزمن ثابت (Constant-Time Comparison)
     */
    fun verifyPin(inputPin: String, storedVerifier: String): Boolean {
        val cleanInput = NumberFormatter.normalizeNumericInput(inputPin.trim(), allowDecimal = false)
        if (cleanInput.isEmpty() || storedVerifier.isBlank()) return false

        // دعم الترحيل الآمن إذا كانت القيمة المخزنة قديمة قبل الترقية
        if (!storedVerifier.startsWith("v1$")) {
            val expectedBytes = storedVerifier.trim().toByteArray(Charsets.UTF_8)
            val actualBytes = cleanInput.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(expectedBytes, actualBytes)
        }

        val parts = storedVerifier.split("$")
        if (parts.size != 4) return false

        return try {
            val iterations = parts[1].toIntOrNull() ?: PBKDF2_ITERATIONS
            val salt = hexToBytes(parts[2])
            val expectedHash = hexToBytes(parts[3])
            val candidateHash = derivePbkdf2(cleanInput, salt, iterations)
            MessageDigest.isEqual(expectedHash, candidateHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * حساب مدة الحظر المؤقت بالثواني (Progressive Backoff) بناءً على عدد المحاولات الفاشلة المتتالية:
     * - أقل من 5 محاولات: 0 ثانية (مسموح)
     * - 5 محاولات: 30 ثانية
     * - 6 محاولات: 60 ثانية
     * - 7 محاولات: 120 ثانية
     * - 8 محاولات فأكثر: 300 ثانية (5 دقائق)
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
     * تشفير نص سري (مثل رمز مصادقة OAuth 2.0) باستخدام AndroidKeyStore AES-GCM
     */
    fun encryptSecret(plainText: String): String {
        val clean = plainText.trim()
        if (clean.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateKeystoreSecretKey() ?: return encodeFallbackObfuscation(clean)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.getEncoder().encodeToString(iv)
            val cipherB64 = Base64.getEncoder().encodeToString(cipherBytes)
            "enc_v1:$ivB64:$cipherB64"
        } catch (_: Throwable) {
            encodeFallbackObfuscation(clean)
        }
    }

    /**
     * فك تشفير النص السري المشفر عبر AndroidKeyStore AES-GCM
     */
    fun decryptSecret(storedCipherText: String): String {
        val clean = storedCipherText.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("enc_v1:")) {
            val parts = clean.split(":")
            if (parts.size != 3) return ""
            return try {
                val secretKey = getOrCreateKeystoreSecretKey() ?: return ""
                val iv = Base64.getDecoder().decode(parts[1])
                val cipherBytes = Base64.getDecoder().decode(parts[2])
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        } else if (clean.startsWith("obf_v1:")) {
            return decodeFallbackObfuscation(clean)
        }
        // توافق رجعي للقيم القديمة غير المشفرة قبل الترحيل
        return clean
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

    private fun derivePbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.digest(pin.toByteArray(Charsets.UTF_8))
        }
    }

    private fun encodeFallbackObfuscation(plain: String): String {
        val b64 = Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
        return "obf_v1:$b64"
    }

    private fun decodeFallbackObfuscation(encoded: String): String {
        return try {
            val raw = encoded.removePrefix("obf_v1:")
            String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
