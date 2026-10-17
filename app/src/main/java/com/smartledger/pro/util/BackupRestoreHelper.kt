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
    val appVersion: String = "2.4.0",
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
object BackupRestoreHelper {

    const val BACKUP_EXTENSION = ".slprobackup"
    private const val INDEX_FILE_NAME = "backup_index_v2.json"

    fun generateBackupFileName(prefix: String = "SmartLedger_Backup"): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return "${prefix}_${stamp}$BACKUP_EXTENSION"
    }

    /**
     * إنشاء نسخة احتياطية منظمة تلقائياً داخل هيكلة العام والشهر (`filesDir/Backups/YYYY/MM/`)
     * مع اشتراط نجاح تفريغ WAL ونجاح فحص سلامة اللقطة المؤقتة قبل اعتمادها.
     */
    suspend fun createStructuredArchiveBackup(
        context: Context,
        sourceTag: String = "LocalArchive",
        keepMaxCount: Int = 20
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        val stagingDb = File(context.cacheDir, "backup_staging_${System.currentTimeMillis()}.db")
        try {
            val db = SmartLedgerDatabase.getInstance(context)

            // 1. تفريغ سجل WAL والتحقق الصارم من عدم انشغال القاعدة
            val checkpointResult = db.checkpointWal()
            if (checkpointResult.isFailure) {
                return@withContext Result.failure(
                    checkpointResult.exceptionOrNull()
                        ?: IllegalStateException("تعذر تفريغ معاملات قاعدة البيانات (WAL) قبل النسخ الاحتياطي.")
                )
            }

            val dbFile = context.getDatabasePath(SmartLedgerDatabase.DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("ملف قاعدة البيانات الأساسي غير موجود بعد؛ أضف سجلاً أو افتح التطبيق أولاً.")
                )
            }

            // 2. أخذ لقطة مستقرة وفحص سلامتها قبل كتابة الأرشيف
            dbFile.copyTo(stagingDb, overwrite = true)
            if (!hasValidSqliteHeader(stagingDb) || !verifySqliteIntegrity(stagingDb)) {
                return@withContext Result.failure(
                    IllegalStateException("فشل فحص سلامة لقطة قاعدة البيانات المؤقتة (PRAGMA integrity_check)؛ تم إيقاف النسخ لمنع حفظ نسخة تالفة.")
                )
            }

            val accountsCount = db.accountDao().getActiveAccountsCount()
            val productsCount = db.productDao().getActiveProductsCount()
            val invoicesCount = db.invoiceDao().getInvoicesCount()

            val now = Date()
            val yearFolder = SimpleDateFormat("yyyy", Locale.US).format(now)
            val monthFolder = SimpleDateFormat("MM", Locale.US).format(now)
            val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)

            val targetDir = File(context.filesDir, "Backups/$yearFolder/$monthFolder")
            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = generateBackupFileName(
                if (sourceTag == "PreRestoreSafety") "SmartLedger_SafetyBeforeRestore" else "SmartLedger_Backup"
            )
            val targetZipFile = File(targetDir, fileName)

            val dbSha256 = calculateSha256(stagingDb)

            FileOutputStream(targetZipFile).use { fos ->
                writeBackupZipStream(
                    outputStream = fos,
                    dbFile = stagingDb,
                    dbSha256 = dbSha256,
                    createdAt = createdAt,
                    accountsCount = accountsCount,
                    productsCount = productsCount,
                    invoicesCount = invoicesCount
                )
            }

            val record = BackupRecord(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                filePath = targetZipFile.absolutePath,
                yearFolder = yearFolder,
                monthFolder = monthFolder,
                createdAt = createdAt,
                sizeBytes = targetZipFile.length(),
                sha256 = dbSha256,
                schemaVersion = SmartLedgerDatabase.CURRENT_SCHEMA_VERSION,
                accountsCount = accountsCount,
                productsCount = productsCount,
                invoicesCount = invoicesCount,
                source = sourceTag
            )

            appendRecordToIndex(context, record, keepMaxCount)
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر إنشاء النسخة الاحتياطية: ${e.localizedMessage ?: "خطأ في وحدة التخزين"}"))
        } finally {
            stagingDb.delete()
        }
    }

    /**
     * تصدير نسخة احتياطية إلى مسار خارجي يختاره المستخدم عبر SAF (`Uri`)
     */
    suspend fun exportBackupToUri(
        context: Context,
        destinationUri: Uri
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            val archiveResult = createStructuredArchiveBackup(context, sourceTag = "ExternalSAF")
            if (archiveResult.isFailure) {
                return@withContext Result.failure(archiveResult.exceptionOrNull() ?: Exception("فشل تجهيز النسخة"))
            }
            val record = archiveResult.getOrThrow()
            val archiveFile = File(record.filePath)

            val outStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(Exception("تعذر فتح مسار الحفظ المختار"))

            outStream.use { os ->
                FileInputStream(archiveFile).use { fis ->
                    fis.copyTo(os)
                }
            }
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر تصدير النسخة للمسار الخارجي: ${e.localizedMessage}"))
        }
    }

    private fun writeBackupZipStream(
        outputStream: OutputStream,
        dbFile: File,
        dbSha256: String,
        createdAt: String,
        accountsCount: Int,
        productsCount: Int,
        invoicesCount: Long
    ) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            // 1. Metadata JSON Entry
            val metadataJson = JSONObject().apply {
                put("appName", "SmartLedger Pro")
                put("appVersion", "2.4.0")
                put("schemaVersion", SmartLedgerDatabase.CURRENT_SCHEMA_VERSION)
                put("createdAt", createdAt)
                put("dbFileName", "smartledger.db")
                put("dbSizeBytes", dbFile.length())
                put("sha256", dbSha256)
                put("accountsCount", accountsCount)
                put("productsCount", productsCount)
                put("invoicesCount", invoicesCount)
            }.toString(2)

            val metaEntry = ZipEntry("backup_metadata.json")
            zos.putNextEntry(metaEntry)
            zos.write(metadataJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Database Entry
            val dbEntry = ZipEntry("smartledger.db")
            zos.putNextEntry(dbEntry)
            FileInputStream(dbFile).use { fis ->
                fis.copyTo(zos, bufferSize = 16 * 1024)
            }
            zos.closeEntry()
        }
    }

    /**
     * فحص شامل لملف النسخة الاحتياطية قبل الاستعادة دون المساس بقاعدة البيانات الحالية.
     */
    suspend fun inspectBackupFile(
        context: Context,
        backupFile: File,
        allowLegacyUnverified: Boolean = true
    ): BackupInspectionResult = withContext(Dispatchers.IO) {
        if (!backupFile.exists() || backupFile.length() < 64L) {
            return@withContext BackupInspectionResult(
                isValid = false,
                errorMessage = "ملف النسخة الاحتياطية غير موجود أو فارغ"
            )
        }

        val tempDb = File(context.cacheDir, "inspect_temp_${System.currentTimeMillis()}.db")
        try {
            var metaObj: JSONObject? = null
            var dbExtracted = false

            if (hasValidSqliteHeader(backupFile)) {
                backupFile.copyTo(tempDb, overwrite = true)
                dbExtracted = true
            } else {
                ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        val name = entry!!.name
                        when {
                            name == "backup_metadata.json" -> {
                                val bytes = zis.readBytes()
                                metaObj = JSONObject(String(bytes, Charsets.UTF_8))
                            }
                            name == "smartledger.db" || name.endsWith(".db") -> {
                                FileOutputStream(tempDb).use { fos ->
                                    zis.copyTo(fos)
                                }
                                dbExtracted = true
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }

            if (!dbExtracted || !tempDb.exists() || tempDb.length() < 100L) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "الأرشيف لا يحتوي على ملف قاعدة بيانات صالح (smartledger.db)"
                )
            }

            if (!hasValidSqliteHeader(tempDb)) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "ترويسة الملف ليست قاعدة بيانات SQLite قياسية صالحة"
                )
            }

            val actualSha256 = calculateSha256(tempDb)
            val expectedSha256 = metaObj?.optString("sha256", "") ?: ""
            val schemaVersion = metaObj?.optInt("schemaVersion", 1) ?: 1
            val createdAt = metaObj?.optString("createdAt", "نسخة أرشيف خارجية") ?: "نسخة أرشيف خارجية"
            val appVersion = metaObj?.optString("appVersion", "2.4.0") ?: "2.4.0"
            val accountsCount = metaObj?.optInt("accountsCount", 0) ?: 0
            val productsCount = metaObj?.optInt("productsCount", 0) ?: 0
            val invoicesCount = metaObj?.optLong("invoicesCount", 0L) ?: 0L

            if (schemaVersion > SmartLedgerDatabase.CURRENT_SCHEMA_VERSION) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "إصدار مخطط النسخة ($schemaVersion) أحدث من إصدار التطبيق الحالي (${SmartLedgerDatabase.CURRENT_SCHEMA_VERSION})"
                )
            }

            val status = when {
                expectedSha256.isNotBlank() && expectedSha256.equals(actualSha256, ignoreCase = true) ->
                    ChecksumVerificationStatus.VERIFIED_MATCH
                expectedSha256.isBlank() ->
                    ChecksumVerificationStatus.LEGACY_UNVERIFIED
                else ->
                    ChecksumVerificationStatus.MISMATCH_CORRUPT
            }

            if (status == ChecksumVerificationStatus.MISMATCH_CORRUPT) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "فشل التحقق الرقمي (SHA-256): ملف النسخة تعرض للتلف أو التعديل",
                    expectedSha256 = expectedSha256,
                    actualSha256 = actualSha256,
                    isChecksumMatched = false,
                    checksumStatus = ChecksumVerificationStatus.MISMATCH_CORRUPT
                )
            }

            if (status == ChecksumVerificationStatus.LEGACY_UNVERIFIED && !allowLegacyUnverified) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "هذه النسخة لا تحتوي على بصمة تحقق رقمية (SHA-256) ومرفوضة وفق سياسة التحقق الصارم",
                    expectedSha256 = "",
                    actualSha256 = actualSha256,
                    isChecksumMatched = false,
                    checksumStatus = ChecksumVerificationStatus.LEGACY_UNVERIFIED
                )
            }

            val sqliteOk = verifySqliteIntegrity(tempDb)
            if (!sqliteOk) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "فشل فحص سلامة الجداول الداخلية (PRAGMA integrity_check)"
                )
            }

            BackupInspectionResult(
                isValid = true,
                appVersion = appVersion,
                schemaVersion = schemaVersion,
                createdAt = createdAt,
                dbSizeBytes = tempDb.length(),
                expectedSha256 = expectedSha256,
                actualSha256 = actualSha256,
                isChecksumMatched = (status == ChecksumVerificationStatus.VERIFIED_MATCH),
                checksumStatus = status,
                accountsCount = accountsCount,
                productsCount = productsCount,
                invoicesCount = invoicesCount
            )
        } catch (e: Exception) {
            BackupInspectionResult(
                isValid = false,
                errorMessage = "تعذر قراءة ملف النسخة: ${e.localizedMessage ?: "ملف غير مدعوم"}"
            )
        } finally {
            tempDb.delete()
        }
    }

    suspend fun inspectBackupUri(context: Context, sourceUri: Uri): BackupInspectionResult = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "uri_inspect_${System.currentTimeMillis()}.slprobackup")
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext BackupInspectionResult(isValid = false, errorMessage = "تعذر فتح الملف المختار")
            input.use { ins ->
                FileOutputStream(tempZip).use { fos -> ins.copyTo(fos) }
            }
            inspectBackupFile(context, tempZip)
        } catch (e: Exception) {
            BackupInspectionResult(isValid = false, errorMessage = "فشل قراءة الملف المختار: ${e.localizedMessage}")
        } finally {
            tempZip.delete()
        }
    }

    /**
     * استعادة آمنة ومحمية من ملف أرشيف محلي (.slprobackup / .zip).
     * تشترط نجاح إنشاء نسخة الأمان (`PreRestoreSafety`) للقاعدة الحالية قبل السماح بالاستبدال،
     * وتخطر جميع المستودعات والتدفقات التفاعلية (`notifyDatabaseRestored`) فور إعادة فتح القاعدة المستعادة.
     */
    suspend fun restoreFromBackupFile(
        context: Context,
        zipFile: File,
        createPreRestoreSafety: Boolean = true
    ): Result<BackupInspectionResult> = withContext(Dispatchers.IO) {
        val inspection = inspectBackupFile(context, zipFile)
        if (!inspection.isValid) {
            return@withContext Result.failure(Exception(inspection.errorMessage ?: "ملف النسخة غير صالح"))
        }

        val dbFile = context.getDatabasePath(SmartLedgerDatabase.DATABASE_NAME)
        val tempExtractedDb = File(context.cacheDir, "restore_extracted_${System.currentTimeMillis()}.db")
        try {
            // 1. شرط إلزامي: إذا كانت القاعدة الحالية موجودة، يجب نجاح إنشاء نسخة الأمان قبل المتابعة
            if (createPreRestoreSafety && dbFile.exists() && dbFile.length() > 0L) {
                val safetyResult = createStructuredArchiveBackup(context, sourceTag = "PreRestoreSafety")
                if (safetyResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("تم إيقاف الاستعادة لحماية بياناتك الحالية: تعذر إنشاء نسخة الأمان الاستباقية (${safetyResult.exceptionOrNull()?.message ?: "خطأ في الحفظ"}). لم يتم المساس ببياناتك الحالية.")
                    )
                }
            }

            // 2. استخراج قاعدة البيانات إلى المسار المؤقت والتحقق من سلامتها
            if (hasValidSqliteHeader(zipFile)) {
                zipFile.copyTo(tempExtractedDb, overwrite = true)
            } else {
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == "smartledger.db" || entry!!.name.endsWith(".db")) {
                            FileOutputStream(tempExtractedDb).use { fos -> zis.copyTo(fos) }
                        }
                        zis.closeEntry()
                    }
                }
            }

            if (!tempExtractedDb.exists() || !hasValidSqliteHeader(tempExtractedDb) || !verifySqliteIntegrity(tempExtractedDb)) {
                return@withContext Result.failure(Exception("ملف قاعدة البيانات المستخرج لم يجتز فحص السلامة؛ تم إلغاء الاستعادة."))
            }

            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            val emergencyRollbackFile = File(context.cacheDir, "emergency_rollback.db")

            if (dbFile.exists()) {
                dbFile.copyTo(emergencyRollbackFile, overwrite = true)
            }

            // 3. إغلاق اتصال Room الحالي بأمان
            SmartLedgerDatabase.closeAndResetInstance()

            try {
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                tempExtractedDb.copyTo(dbFile, overwrite = true)

                // 4. إعادة فتح القاعدة والتأكد من عملها وإشعار جميع المستودعات والتدفقات بالاتصال الجديد
                val reopenedDb = SmartLedgerDatabase.getInstance(context)
                reopenedDb.accountDao().getActiveAccountsCount()
                SmartLedgerDatabase.notifyDatabaseRestored()
            } catch (restoreErr: Exception) {
                if (emergencyRollbackFile.exists()) {
                    emergencyRollbackFile.copyTo(dbFile, overwrite = true)
                }
                SmartLedgerDatabase.getInstance(context)
                SmartLedgerDatabase.notifyDatabaseRestored()
                return@withContext Result.failure(Exception("تعذر تفعيل القاعدة المستعادة، تم الحفاظ على بياناتك السابقة بأمان."))
            } finally {
                emergencyRollbackFile.delete()
            }

            Result.success(inspection)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempExtractedDb.delete()
        }
    }

    suspend fun restoreFromUri(context: Context, sourceUri: Uri): Result<BackupInspectionResult> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "restore_uri_${System.currentTimeMillis()}.slprobackup")
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("تعذر فتح ملف النسخة الاحتياطية المختار"))
            input.use { ins ->
                FileOutputStream(tempZip).use { fos -> ins.copyTo(fos) }
            }
            restoreFromBackupFile(context, tempZip, createPreRestoreSafety = true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempZip.delete()
        }
    }

    suspend fun getIndexedBackups(context: Context): List<BackupRecord> = withContext(Dispatchers.IO) {
        val indexFile = File(context.filesDir, "Backups/$INDEX_FILE_NAME")
        if (!indexFile.exists()) return@withContext emptyList()
        return@withContext try {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            val list = mutableListOf<BackupRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath", "")
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        BackupRecord(
                            id = obj.optString("id"),
                            fileName = obj.optString("fileName"),
                            filePath = path,
                            yearFolder = obj.optString("yearFolder"),
                            monthFolder = obj.optString("monthFolder"),
                            createdAt = obj.optString("createdAt"),
                            sizeBytes = file.length(),
                            sha256 = obj.optString("sha256"),
                            schemaVersion = obj.optInt("schemaVersion", 1),
                            accountsCount = obj.optInt("accountsCount", 0),
                            productsCount = obj.optInt("productsCount", 0),
                            invoicesCount = obj.optLong("invoicesCount", 0L),
                            source = obj.optString("source", "LocalArchive")
                        )
                    )
                }
            }
            list.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteBackupRecord(context: Context, recordId: String): Boolean = withContext(Dispatchers.IO) {
        val current = getIndexedBackups(context).toMutableList()
        val target = current.find { it.id == recordId } ?: return@withContext false
        runCatching { File(target.filePath).delete() }
        current.removeAll { it.id == recordId }
        saveIndexList(context, current)
        true
    }

    private fun appendRecordToIndex(context: Context, record: BackupRecord, keepMaxCount: Int) {
        try {
            val backupsRoot = File(context.filesDir, "Backups")
            if (!backupsRoot.exists()) backupsRoot.mkdirs()

            val current = mutableListOf<BackupRecord>()
            val indexFile = File(backupsRoot, INDEX_FILE_NAME)
            if (indexFile.exists()) {
                val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("filePath", "")
                    if (File(path).exists()) {
                        current.add(
                            BackupRecord(
                                id = obj.optString("id"),
                                fileName = obj.optString("fileName"),
                                filePath = path,
                                yearFolder = obj.optString("yearFolder"),
                                monthFolder = obj.optString("monthFolder"),
                                createdAt = obj.optString("createdAt"),
                                sizeBytes = obj.optLong("sizeBytes"),
                                sha256 = obj.optString("sha256"),
                                schemaVersion = obj.optInt("schemaVersion", 1),
                                accountsCount = obj.optInt("accountsCount", 0),
                                productsCount = obj.optInt("productsCount", 0),
                                invoicesCount = obj.optLong("invoicesCount", 0L),
                                source = obj.optString("source", "LocalArchive")
                            )
                        )
                    }
                }
            }

            current.add(0, record)
            val safeLimit = keepMaxCount.coerceIn(5, 100)
            if (current.size > safeLimit) {
                val overflow = current.subList(safeLimit, current.size)
                overflow.forEach { rec -> runCatching { File(rec.filePath).delete() } }
            }
            val retained = current.take(safeLimit)
            saveIndexList(context, retained)
        } catch (_: Exception) {
        }
    }

    private fun saveIndexList(context: Context, list: List<BackupRecord>) {
        val backupsRoot = File(context.filesDir, "Backups")
        if (!backupsRoot.exists()) backupsRoot.mkdirs()
        val arr = JSONArray()
        list.forEach { rec ->
            arr.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("fileName", rec.fileName)
                    put("filePath", rec.filePath)
                    put("yearFolder", rec.yearFolder)
                    put("monthFolder", rec.monthFolder)
                    put("createdAt", rec.createdAt)
                    put("sizeBytes", rec.sizeBytes)
                    put("sha256", rec.sha256)
                    put("schemaVersion", rec.schemaVersion)
                    put("accountsCount", rec.accountsCount)
                    put("productsCount", rec.productsCount)
                    put("invoicesCount", rec.invoicesCount)
                    put("source", rec.source)
                }
            )
        }
        File(backupsRoot, INDEX_FILE_NAME).writeText(arr.toString(2), Charsets.UTF_8)
    }

    fun hasValidSqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        return try {
            val headerBytes = ByteArray(16)
            FileInputStream(file).use { it.read(headerBytes) }
            val headerStr = String(headerBytes, Charsets.US_ASCII)
            headerStr.startsWith("SQLite format 3")
        } catch (_: Exception) {
            false
        }
    }

    fun verifySqliteIntegrity(dbFile: File): Boolean {
        if (!hasValidSqliteHeader(dbFile)) return false
        var sqliteDb: SQLiteDatabase? = null
        return try {
            sqliteDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val isOk = sqliteDb.isDatabaseIntegrityOk
            sqliteDb.close()
            isOk
        } catch (_: Exception) {
            runCatching { sqliteDb?.close() }
            false
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
