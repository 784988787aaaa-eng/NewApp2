package com.smartledger.pro.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.smartledger.pro.data.local.SmartLedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * حالة التحقق الصريح من البصمة الرقمية للنسخة الاحتياطية (ChecksumVerificationStatus).
 * تميز بوضوح بين النسخ الموثقة ببصمة SHA-256 مطابقة، والنسخ القديمة التي لا تحتوي على بصمة، والنسخ التالفة.
 */
enum class ChecksumVerificationStatus(val arabicDescription: String) {
    VERIFIED_MATCH("بصمة SHA-256 مطابقة وموثقة 100%"),
    LEGACY_UNVERIFIED("نسخة قديمة بدون بصمة رقمية (تم فحص سلامة SQLite فقط)"),
    MISMATCH_CORRUPT("فشل التحقق الرقمي: البصمة غير مطابقة أو الملف تالف")
}

data class BackupRecord(
    val id: String,
    val fileName: String,
    val filePath: String,
    val yearFolder: String,
    val monthFolder: String,
    val createdAt: String,
    val sizeBytes: Long,
    val sha256: String,
    val schemaVersion: Int,
    val accountsCount: Int,
    val productsCount: Int,
    val invoicesCount: Long,
    val source: String // "LocalArchive", "ExternalSAF", "GoogleDrive", "PreRestoreSafety"
)

data class BackupInspectionResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val appVersion: String = "3.0.0",
    val schemaVersion: Int = 1,
    val createdAt: String = "",
    val dbSizeBytes: Long = 0L,
    val expectedSha256: String = "",
    val actualSha256: String = "",
    val isChecksumMatched: Boolean = false,
    val checksumStatus: ChecksumVerificationStatus = ChecksumVerificationStatus.MISMATCH_CORRUPT,
    val accountsCount: Int = 0,
    val productsCount: Int = 0,
    val invoicesCount: Long = 0L
)

/**
 * محرك النسخ الاحتياطي والاستعادة الذري الموثوق (BackupRestoreHelper).
 *
 * الضمانات الهندسية المطبقة:
 * 1. لا ينشئ أي نسخة احتياطية إذا فشل تفريغ `WAL` (`checkpointWal().isFailure`)، أو إذا لم تتجاوز النسخة المؤقتة
 *    فحص `PRAGMA integrity_check` قبل ضغطها في الأرشيف.
 * 2. يرفض عملية الاستعادة فوراً إذا فشل إنشاء نسخة الأمان الاستباقية (`PreRestoreSafety`) عندما يكون الخيار مفعلاً.
 * 3. يميز صراحةً بين النسخ الموثقة ببصمة `SHA-256` (`VERIFIED_MATCH`) والنسخ القديمة (`LEGACY_UNVERIFIED`).
 * 4. بعد إتمام الاستعادة بنجاح، يستدعي `SmartLedgerDatabase.notifyDatabaseRestored()` و`AppPreferencesRepository.onDatabaseRestored()`
 *    لإعادة ربط كافة المستودعات والـ ViewModels باتصال قاعدة البيانات المستعادة فوراً.
 */
