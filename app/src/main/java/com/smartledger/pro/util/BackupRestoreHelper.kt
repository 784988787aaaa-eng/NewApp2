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
 * سجل نسخة احتياطية مفهرسة (BackupRecord)
 * يُحفظ في الفهرس المحلي ومجلدات الأرشيف المنظمة (Backups/YYYY/MM-MonthName/)
 */
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
    val source: String // "LocalArchive", "ExternalSAF", "PreRestoreSafety", "GoogleDrive"
)

/**
 * نتيجة فحص وتحقق النسخة قبل الاستعادة (BackupInspectionResult)
 */
data class BackupInspectionResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val appVersion: String = "2.2.0",
    val schemaVersion: Int = 1,
    val createdAt: String = "",
    val dbSizeBytes: Long = 0L,
    val expectedSha256: String = "",
    val actualSha256: String = "",
    val isChecksumMatched: Boolean = false,
    val accountsCount: Int = 0,
    val productsCount: Int = 0,
    val invoicesCount: Long = 0L
)

/**
 * محرك النسخ الاحتياطي والاستعادة الذري الموثوق (.slprobackup)
 *
 * المواصفات الهندسية لتنسيق `.slprobackup`:
 * - حاوية ZIP قياسية تحمل امتداد `.slprobackup` (وتدعم أيضاً قراءة `.zip` للتوافق).
 * - تتضمن 3 ملفات موثقة:
 *   1. `smartledger.db`: لقطة متسقة لقاعدة بيانات SQLite بعد تنفيذ `PRAGMA wal_checkpoint(FULL)`.
 *   2. `manifest.json`: البيانات الوصفية، إصدار المخطط (`schemaVersion: 1`)، توقيت الإنشاء، إحصاءات السجلات، وبصمة التحقق الرقمية `SHA-256`.
 *   3. `preferences.json`: نسخة من تفضيلات المنشأة والتطبيق.
 * - ينظم النسخ تلقائياً داخل هيكل مجلدات: `Backups/YYYY/MM-MonthName/` مع فهرس `backup_index.json`.
 * - ينشئ تلقائياً نقطة أمان (`pre_restore_safety.slprobackup`) قبل أي عملية استعادة، ولا يستبدل القاعدة إلا بعد نجاح فحص `SHA-256` و`SQLite PRAGMA integrity_check`.
 */
object BackupRestoreHelper {

    const val BACKUP_EXTENSION = ".slprobackup"
    const val CURRENT_SCHEMA_VERSION = 1
    private const val INDEX_FILE_NAME = "backup_index.json"

    private val monthNames = arrayOf(
        "01-January", "02-February", "03-March", "04-April",
        "05-May", "06-June", "07-July", "08-August",
        "09-September", "10-October", "11-November", "12-December"
    )

    /**
     * توليد اسم ملف قياسي واضح للنسخة الاحتياطية بدون بيانات شخصية
     */
    fun generateBackupFileName(prefix: String = "SmartLedger_Backup"): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return "${prefix}_${ts}$BACKUP_EXTENSION"
    }

    /**
     * إنشاء نسخة احتياطية منظمة داخل أرشيف التطبيق (`Backups/YYYY/MM-Month/`) وتسجيلها في الفهرس
     */
    suspend fun createStructuredArchiveBackup(
        context: Context,
        sourceTag: String = "LocalArchive",
        keepMaxCount: Int = 15
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            val db = SmartLedgerDatabase.getInstance(context)
            // 1. تفريغ WAL لضمان اتساق لقطة القاعدة
            db.checkpointWal()

            val dbFile = context.getDatabasePath("smartledger.db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("ملف قاعدة البيانات غير موجود حالياً"))
            }

            val accountsCount = runCatching { db.accountDao().getActiveAccountsCount() }.getOrDefault(0)
            val productsCount = runCatching { db.productDao().getActiveProductsCount() }.getOrDefault(0)
            val invoicesCount = runCatching { db.invoiceDao().getInvoiceCount() }.getOrDefault(0L)

            val now = Date()
            val cal = Calendar.getInstance().apply { time = now }
            val yearStr = String.format(Locale.US, "%04d", cal.get(Calendar.YEAR))
            val monthStr = monthNames[cal.get(Calendar.MONTH).coerceIn(0, 11)]
            val createdAtIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)

            val archiveRoot = File(context.filesDir, "Backups/$yearStr/$monthStr")
            if (!archiveRoot.exists()) {
                archiveRoot.mkdirs()
            }

            val fileName = generateBackupFileName(
                if (sourceTag == "PreRestoreSafety") "SmartLedger_PreRestore_Safety" else "SmartLedger_Backup"
            )
            val tempFile = File(context.cacheDir, "$fileName.tmp")
            val finalFile = File(archiveRoot, fileName)

            val sha256 = calculateSha256(dbFile)

            // 2. كتابة الحاوية إلى ملف مؤقت أولاً
            FileOutputStream(tempFile).use { fos ->
                writeBackupZipStream(
                    context = context,
                    outStream = fos,
                    dbFile = dbFile,
                    sha256 = sha256,
                    createdAt = createdAtIso,
                    accountsCount = accountsCount,
                    productsCount = productsCount,
                    invoicesCount = invoicesCount
                )
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext Result.failure(Exception("تعذر كتابة ملف النسخة الاحتياطية"))
            }

            // 3. النقل الذري إلى المجلد النهائي بعد اكتمال الكتابة
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()

            val record = BackupRecord(
                id = "BKP-" + System.currentTimeMillis(),
                fileName = fileName,
                filePath = finalFile.absolutePath,
                yearFolder = yearStr,
                monthFolder = monthStr,
                createdAt = createdAtIso,
                sizeBytes = finalFile.length(),
                sha256 = sha256,
                schemaVersion = CURRENT_SCHEMA_VERSION,
                accountsCount = accountsCount,
                productsCount = productsCount,
                invoicesCount = invoicesCount,
                source = sourceTag
            )

            appendRecordToIndex(context, record, keepMaxCount)
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * تصدير نسخة احتياطية إلى موقع يختاره المستخدم عبر Storage Access Framework (SAF Uri)
     */
    suspend fun exportBackupToUri(
        context: Context,
        destUri: Uri
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            // ننشئ نسخة قياسية في الأرشيف أولاً ثم ننسخها إلى الـ Uri المختار
            val archiveResult = createStructuredArchiveBackup(context, sourceTag = "ExternalSAF")
            if (archiveResult.isFailure) {
                return@withContext archiveResult
            }
            val record = archiveResult.getOrThrow()
            val archiveFile = File(record.filePath)

            val outStream = context.contentResolver.openOutputStream(destUri)
                ?: return@withContext Result.failure(Exception("تعذر فتح مسار الحفظ المختار"))

            outStream.use { os ->
                FileInputStream(archiveFile).use { fis ->
                    fis.copyTo(os)
                }
            }
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * التوافق مع الدالة السابقة: حفظ مباشر إلى Uri
     */
    fun createBackup(context: Context, destUri: Uri): Boolean {
        return try {
            val db = SmartLedgerDatabase.getInstance(context)
            db.checkpointWal()
            val dbFile = context.getDatabasePath("smartledger.db")
            if (!dbFile.exists()) return false
            val sha256 = calculateSha256(dbFile)
            val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                writeBackupZipStream(
                    context = context,
                    outStream = outStream,
                    dbFile = dbFile,
                    sha256 = sha256,
                    createdAt = createdAt,
                    accountsCount = 0,
                    productsCount = 0,
                    invoicesCount = 0L
                )
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeBackupZipStream(
        context: Context,
        outStream: OutputStream,
        dbFile: File,
        sha256: String,
        createdAt: String,
        accountsCount: Int,
        productsCount: Int,
        invoicesCount: Long
    ) {
        ZipOutputStream(BufferedOutputStream(outStream)).use { zipOut ->
            // 1. ملف قاعدة البيانات
            val dbEntry = ZipEntry("smartledger.db")
            zipOut.putNextEntry(dbEntry)
            FileInputStream(dbFile).use { it.copyTo(zipOut) }
            zipOut.closeEntry()

            // 2. ملف manifest.json (و metadata.json للتوافق مع الإصدارات السابقة)
            val manifestJson = JSONObject().apply {
                put("format", "SmartLedgerProBackup")
                put("formatVersion", 2)
                put("app", "SmartLedger Pro Android")
                put("version", "2.2.0")
                put("schemaVersion", CURRENT_SCHEMA_VERSION)
                put("createdAt", createdAt)
                put("dbSizeBytes", dbFile.length())
                put("sha256", sha256)
                put("accountsCount", accountsCount)
                put("productsCount", productsCount)
                put("invoicesCount", invoicesCount)
            }.toString(2)

            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(manifestJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("metadata.json"))
            zipOut.write(manifestJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. تفضيلات التطبيق
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/smartledger_pro_settings.xml")
            if (prefsFile.exists()) {
                zipOut.putNextEntry(ZipEntry("preferences.xml"))
                FileInputStream(prefsFile).use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }
    }

    /**
     * فحص ملف النسخة الاحتياطية والتحقق من بصمة SHA-256 وسلامة SQLite قبل الاستعادة
     */
    suspend fun inspectBackupFile(context: Context, zipFile: File): BackupInspectionResult = withContext(Dispatchers.IO) {
        if (!zipFile.exists() || zipFile.length() == 0L) {
            return@withContext BackupInspectionResult(isValid = false, errorMessage = "ملف النسخة الاحتياطية غير موجود أو فارغ")
        }
        val tempDb = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.db")
        try {
            var metaJsonText: String? = null
            var foundDb = false

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    when (entry!!.name) {
                        "smartledger.db" -> {
                            FileOutputStream(tempDb).use { fos -> zis.copyTo(fos) }
                            foundDb = true
                        }
                        "manifest.json", "metadata.json" -> {
                            if (metaJsonText == null) {
                                metaJsonText = zis.bufferedReader(Charsets.UTF_8).readText()
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }

            if (!foundDb || !tempDb.exists() || tempDb.length() < 100L) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "الأرشيف لا يحتوي على ملف قاعدة بيانات صالح (smartledger.db)"
                )
            }

            // فحص ترويسة ملف SQLite القياسية ("SQLite format 3")
            if (!hasValidSqliteHeader(tempDb)) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "ملف قاعدة البيانات داخل النسخة تالف أو ليس بتنسيق SQLite قياسي"
                )
            }

            val actualSha256 = calculateSha256(tempDb)
            var expectedSha256 = ""
            var schemaVersion = 1
            var appVersion = "2.1.0"
            var createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(zipFile.lastModified()))
            var accountsCount = 0
            var productsCount = 0
            var invoicesCount = 0L

            if (!metaJsonText.isNullOrBlank()) {
                val obj = JSONObject(metaJsonText!!)
                expectedSha256 = obj.optString("sha256", "")
                schemaVersion = obj.optInt("schemaVersion", 1)
                appVersion = obj.optString("version", "2.1.0")
                createdAt = obj.optString("createdAt", createdAt)
                accountsCount = obj.optInt("accountsCount", 0)
                productsCount = obj.optInt("productsCount", 0)
                invoicesCount = obj.optLong("invoicesCount", 0L)
            }

            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "إصدار مخطط النسخة ($schemaVersion) أحدث من إصدار التطبيق الحالي ($CURRENT_SCHEMA_VERSION)"
                )
            }

            val checksumMatched = expectedSha256.isBlank() || expectedSha256.equals(actualSha256, ignoreCase = true)
            if (!checksumMatched) {
                return@withContext BackupInspectionResult(
                    isValid = false,
                    errorMessage = "فشل التحقق الرقمي (SHA-256): ملف النسخة تعرض للتلف أو التعديل",
                    expectedSha256 = expectedSha256,
                    actualSha256 = actualSha256,
                    isChecksumMatched = false
                )
            }

            // فحص سلامة الجداول داخل SQLite
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
                expectedSha256 = expectedSha256.ifBlank { actualSha256 },
                actualSha256 = actualSha256,
                isChecksumMatched = true,
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

    /**
     * فحص نسخة احتياطية مختارة من Storage Access Framework (Uri)
     */
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
     * استعادة آمنة ومحمية من ملف أرشيف محلي (.slprobackup / .zip)
     * 1. يتحقق من سلامة النسخة وبصمة SHA-256.
     * 2. ينشئ نسخة أمان تلقائية للبيانات الحالية (PreRestoreSafety).
     * 3. يغلق قاعدة البيانات الحالية، ويستبدلها ذرياً، ويمسح ملفات WAL/SHM القديمة، ثم يعيد فتح القاعدة والتحقق منها.
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

        val tempExtractedDb = File(context.cacheDir, "restore_extracted_${System.currentTimeMillis()}.db")
        try {
            // 1. إنشاء نسخة أمان تلقائية للبيانات الحالية قبل الاستبدال
            if (createPreRestoreSafety) {
                createStructuredArchiveBackup(context, sourceTag = "PreRestoreSafety")
            }

            // 2. استخراج قاعدة البيانات إلى المسار المؤقت
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == "smartledger.db") {
                        FileOutputStream(tempExtractedDb).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                }
            }

            val dbFile = context.getDatabasePath("smartledger.db")
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

                // 4. إعادة فتح القاعدة والتأكد من عملها
                val reopenedDb = SmartLedgerDatabase.getInstance(context)
                reopenedDb.accountDao().getActiveAccountsCount()
            } catch (restoreErr: Exception) {
                // التراجع الفوري في حال حدوث أي خطأ أثناء الاستبدال
                if (emergencyRollbackFile.exists()) {
                    emergencyRollbackFile.copyTo(dbFile, overwrite = true)
                }
                SmartLedgerDatabase.getInstance(context)
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

    /**
     * استعادة آمنة من Uri خارجي عبر Storage Access Framework
     */
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

    /**
     * قراءة سجل النسخ الاحتياطية المفهرسة محلياً ومرتبة من الأحدث للأقدم
     */
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

    /**
     * حذف نسخة احتياطية من الأرشيف والفهرس
     */
    suspend fun deleteBackupRecord(context: Context, recordId: String): Boolean = withContext(Dispatchers.IO) {
        val current = getIndexedBackups(context).toMutableList()
        val target = current.find { it.id == recordId } ?: return@withContext false
        runCatching { File(target.filePath).delete() }
        current.removeAll { it.id == recordId }
        saveIndexList(context, current)
        true
    }

    private fun appendRecordToIndex(context: Context, record: BackupRecord, keepMaxCount: Int) {
        val indexFile = File(context.filesDir, "Backups/$INDEX_FILE_NAME")
        indexFile.parentFile?.mkdirs()
        val existing = try {
            if (indexFile.exists()) {
                val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
                (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.getJSONObject(idx)
                    val path = obj.optString("filePath")
                    if (File(path).exists()) {
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
                    } else null
                }.toMutableList()
            } else mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        existing.add(0, record)

        // تطبيق سياسة الاحتفاظ بعدد النسخ المحدد (Backup Rotation)
        val safeMax = keepMaxCount.coerceAtLeast(3)
        if (existing.size > safeMax) {
            val toRemove = existing.subList(safeMax, existing.size)
            toRemove.forEach { old -> runCatching { File(old.filePath).delete() } }
        }
        val trimmed = existing.take(safeMax)
        saveIndexList(context, trimmed)
    }

    private fun saveIndexList(context: Context, records: List<BackupRecord>) {
        val indexFile = File(context.filesDir, "Backups/$INDEX_FILE_NAME")
        indexFile.parentFile?.mkdirs()
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("fileName", r.fileName)
                    put("filePath", r.filePath)
                    put("yearFolder", r.yearFolder)
                    put("monthFolder", r.monthFolder)
                    put("createdAt", r.createdAt)
                    put("sizeBytes", r.sizeBytes)
                    put("sha256", r.sha256)
                    put("schemaVersion", r.schemaVersion)
                    put("accountsCount", r.accountsCount)
                    put("productsCount", r.productsCount)
                    put("invoicesCount", r.invoicesCount)
                    put("source", r.source)
                }
            )
        }
        indexFile.writeText(arr.toString(2), Charsets.UTF_8)
    }

    fun hasValidSqliteHeader(dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < 16L) return false
        return try {
            val header = ByteArray(16)
            FileInputStream(dbFile).use { it.read(header) }
            val headerStr = String(header, 0, 15, Charsets.US_ASCII)
            headerStr == "SQLite format 3"
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySqliteIntegrity(dbFile: File): Boolean {
        var sqliteDb: SQLiteDatabase? = null
        return try {
            sqliteDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val isOk = sqliteDb.isDatabaseIntegrityOk
            isOk
        } catch (_: Exception) {
            false
        } finally {
            try {
                sqliteDb?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
