package com.smartledger.pro.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.smartledger.pro.data.local.SmartLedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val fileName: String,
    val filePath: String? = null,
    val yearMonthFolder: String = "",
    val sizeBytes: Long,
    val createdAt: String,
    val schemaVersion: Int,
    val sha256: String,
    val accountsCount: Int = 0,
    val productsCount: Int = 0,
    val invoicesCount: Int = 0,
    val source: String = "محلي"
)

data class BackupInspectionReport(
    val isValid: Boolean,
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: String,
    val schemaVersion: Int,
    val expectedSha256: String,
    val actualSha256: String,
    val sha256Matched: Boolean,
    val sqliteIntegrityOk: Boolean,
    val accountsCount: Int,
    val productsCount: Int,
    val invoicesCount: Int,
    val operationsCount: Int,
    val errorMessage: String? = null
)

object BackupRestoreHelper {

    const val BACKUP_EXTENSION = ".slprobackup"
    private const val INTERNAL_BACKUP_DIR = "Backups"

    fun generateBackupFileName(prefix: String = "SmartLedgerPro_Backup"): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_${ts}$BACKUP_EXTENSION"
    }

    private fun getRootBackupsDir(context: Context): File {
        val dir = File(context.filesDir, INTERNAL_BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCurrentYearMonthBackupDir(context: Context): Pair<File, String> {
        val now = Date()
        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(now)
        val monthStr = SimpleDateFormat("MM-MMMM", Locale.US).format(now)
        val relativeFolder = "$yearStr/$monthStr"
        val dir = File(getRootBackupsDir(context), relativeFolder)
        if (!dir.exists()) dir.mkdirs()
        return Pair(dir, relativeFolder)
    }

    suspend fun createInternalBackupFile(
        context: Context,
        prefix: String = "SmartLedgerPro_Backup",
        maxKeepCount: Int = 5
    ): Result<Pair<File, BackupSummary>> = withContext(Dispatchers.IO) {
        try {
            val dbInstance = SmartLedgerDatabase.getInstance(context)
            dbInstance.checkpointWal()

            val dbFile = context.getDatabasePath("smartledger.db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("ملف قاعدة البيانات غير موجود أو فارغ"))
            }

            val accountsCount = dbInstance.accountDao().getActiveAccountsCount()
            val productsCount = dbInstance.productDao().getActiveProductsCount()
            val invoicesCount = dbInstance.invoiceDao().getInvoiceCount().toInt()

            val sha256 = calculateSha256(dbFile)
            val createdAt = Formatters.nowTimestamp()
            val fileName = generateBackupFileName(prefix)
            val (targetDir, yearMonthFolder) = getCurrentYearMonthBackupDir(context)
            val tempFile = File(targetDir, "$fileName.tmp")
            val finalFile = File(targetDir, fileName)

            writeBackupArchiveToStream(
                dbFile = dbFile,
                outputStream = FileOutputStream(tempFile),
                createdAt = createdAt,
                sha256 = sha256,
                accountsCount = accountsCount,
                productsCount = productsCount,
                invoicesCount = invoicesCount
            )

            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
            pruneOldBackups(context, maxKeepCount)

            val summary = BackupSummary(
                fileName = fileName,
                filePath = finalFile.absolutePath,
                yearMonthFolder = yearMonthFolder,
                sizeBytes = finalFile.length(),
                createdAt = createdAt,
                schemaVersion = SmartLedgerDatabase.SCHEMA_VERSION,
                sha256 = sha256,
                accountsCount = accountsCount,
                productsCount = productsCount,
                invoicesCount = invoicesCount,
                source = if (prefix.contains("PreRestore")) "نسخة أمان تلقائية" else "نسخة محلية ($yearMonthFolder)"
            )
            Result.success(Pair(finalFile, summary))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBackupToUri(
        context: Context,
        destUri: Uri,
        maxKeepCount: Int = 5
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val internalResult = createInternalBackupFile(context, "SmartLedgerPro_Backup", maxKeepCount)
            if (internalResult.isFailure) return@withContext Result.failure(internalResult.exceptionOrNull() ?: Exception("فشل تجهيز النسخة"))
            val (internalFile, summary) = internalResult.getOrThrow()
            val outStream = context.contentResolver.openOutputStream(destUri)
                ?: return@withContext Result.failure(IOException("تعذر فتح مسار الحفظ المختار"))
            outStream.use { out ->
                FileInputStream(internalFile).use { input -> input.copyTo(out) }
                out.flush()
            }
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createBackup(context: Context, destUri: Uri): Boolean {
        return try {
            val dbInstance = SmartLedgerDatabase.getInstance(context)
            dbInstance.checkpointWal()
            val dbFile = context.getDatabasePath("smartledger.db")
            if (!dbFile.exists()) return false
            val sha256 = calculateSha256(dbFile)
            val createdAt = Formatters.nowTimestamp()
            val outStream = context.contentResolver.openOutputStream(destUri) ?: return false
            writeBackupArchiveToStream(dbFile, outStream, createdAt, sha256, 0, 0, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun inspectBackupUri(
        context: Context,
        sourceUri: Uri,
        displayFileName: String = "نسخة مختارة"
    ): Result<BackupInspectionReport> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.slprobackup")
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IOException("تعذر قراءة الملف المختار"))
            FileOutputStream(tempZip).use { out -> inputStream.use { it.copyTo(out) } }
            inspectBackupFile(context, tempZip, displayFileName)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempZip.delete()
        }
    }

    suspend fun inspectBackupFile(
        context: Context,
        archiveFile: File,
        displayFileName: String = archiveFile.name
    ): Result<BackupInspectionReport> = withContext(Dispatchers.IO) {
        val tempDb = File(context.cacheDir, "verify_${System.currentTimeMillis()}.db")
        try {
            var metaJsonText: String? = null
            var hasDbEntry = false

            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    when (entry!!.name) {
                        "smartledger.db" -> {
                            hasDbEntry = true
                            FileOutputStream(tempDb).use { fos -> zis.copyTo(fos) }
                        }
                        "metadata.json" -> {
                            metaJsonText = zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                    }
                    zis.closeEntry()
                }
            }

            if (!hasDbEntry || !tempDb.exists() || tempDb.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("الملف المختار لا يحتوي على قاعدة بيانات صالحة"))
            }

            val actualSha256 = calculateSha256(tempDb)
            val expectedSha256 = extractJsonString(metaJsonText, "sha256") ?: actualSha256
            val schemaVersion = extractJsonInt(metaJsonText, "schemaVersion") ?: 1
            val createdAt = extractJsonString(metaJsonText, "createdAt") ?: "غير محدد"

            if (schemaVersion > SmartLedgerDatabase.SCHEMA_VERSION) {
                return@withContext Result.failure(IllegalStateException("إصدار مخطط النسخة أحدث من إصدار التطبيق الحالي"))
            }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                return@withContext Result.failure(SecurityException("فشل التحقق من بصمة SHA-256: الملف تالف أو معدل"))
            }

            var integrityOk = false
            var accountsCount = 0
            var productsCount = 0
            var invoicesCount = 0
            var operationsCount = 0

            SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
                sqlDb.rawQuery("PRAGMA integrity_check", null).use { c ->
                    if (c.moveToFirst()) integrityOk = c.getString(0).equals("ok", ignoreCase = true)
                }
                accountsCount = queryCountSafe(sqlDb, "SELECT COUNT(*) FROM People WHERE IsActive = 1")
                productsCount = queryCountSafe(sqlDb, "SELECT COUNT(*) FROM Products WHERE IsActive = 1")
                invoicesCount = queryCountSafe(sqlDb, "SELECT COUNT(*) FROM Invoices")
                operationsCount = queryCountSafe(sqlDb, "SELECT COUNT(*) FROM Operations")
            }

            if (!integrityOk) {
                return@withContext Result.failure(IllegalStateException("فشل فحص سلامة الجداول الداخلية"))
            }

            Result.success(
                BackupInspectionReport(
                    isValid = true,
                    fileName = displayFileName,
                    sizeBytes = archiveFile.length(),
                    createdAt = createdAt,
                    schemaVersion = schemaVersion,
                    expectedSha256 = expectedSha256,
                    actualSha256 = actualSha256,
                    sha256Matched = true,
                    sqliteIntegrityOk = true,
                    accountsCount = accountsCount,
                    productsCount = productsCount,
                    invoicesCount = invoicesCount,
                    operationsCount = operationsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDb.delete()
        }
    }

    suspend fun restoreFromUri(context: Context, sourceUri: Uri): Result<BackupInspectionReport> =
        withContext(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "restore_${System.currentTimeMillis()}.slprobackup")
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(IOException("تعذر فتح ملف النسخة المختارة"))
                FileOutputStream(tempZip).use { out -> inputStream.use { it.copyTo(out) } }
                restoreFromArchiveFile(context, tempZip)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempZip.delete()
            }
        }

    suspend fun restoreFromArchiveFile(context: Context, archiveFile: File): Result<BackupInspectionReport> =
        withContext(Dispatchers.IO) {
            val inspectionResult = inspectBackupFile(context, archiveFile, archiveFile.name)
            if (inspectionResult.isFailure) return@withContext inspectionResult
            val report = inspectionResult.getOrThrow()
            val tempExtractedDb = File(context.cacheDir, "extracted_restore_${System.currentTimeMillis()}.db")

            try {
                ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == "smartledger.db") {
                            FileOutputStream(tempExtractedDb).use { fos -> zis.copyTo(fos) }
                        }
                        zis.closeEntry()
                    }
                }

                createInternalBackupFile(context, "SmartLedgerPro_PreRestore_Safety", maxKeepCount = 10)

                val dbFile = context.getDatabasePath("smartledger.db")
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")
                val emergencyCopy = File(context.cacheDir, "emergency_rollback.db")

                if (dbFile.exists()) dbFile.copyTo(emergencyCopy, overwrite = true)

                try {
                    SmartLedgerDatabase.getInstance(context).close()
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                    tempExtractedDb.copyTo(dbFile, overwrite = true)

                    val reopenedDb = SmartLedgerDatabase.reopenAfterRestore(context)
                    reopenedDb.accountDao().getActiveAccountsCount()
                } catch (restoreError: Exception) {
                    if (emergencyCopy.exists()) {
                        emergencyCopy.copyTo(dbFile, overwrite = true)
                        SmartLedgerDatabase.reopenAfterRestore(context)
                    }
                    return@withContext Result.failure(IOException("فشل استبدال القاعدة وتمت استعادة الوضع السابق"))
                } finally {
                    emergencyCopy.delete()
                }
                Result.success(report)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempExtractedDb.delete()
            }
        }

    suspend fun listLocalBackups(context: Context): List<BackupSummary> = withContext(Dispatchers.IO) {
        val rootDir = getRootBackupsDir(context)
        val allBackupFiles = rootDir.walkTopDown()
            .filter { f -> f.isFile && (f.name.endsWith(BACKUP_EXTENSION) || f.name.endsWith(".zip")) }
            .sortedByDescending { it.lastModified() }
            .toList()

        allBackupFiles.map { file ->
            var sha = ""
            var created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified()))
            var schema = SmartLedgerDatabase.SCHEMA_VERSION
            val relParent = file.parentFile?.relativeToOrNull(rootDir)?.path ?: ""

            try {
                ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.name == "metadata.json") {
                            val json = zis.bufferedReader(Charsets.UTF_8).readText()
                            sha = extractJsonString(json, "sha256") ?: ""
                            created = extractJsonString(json, "createdAt") ?: created
                            schema = extractJsonInt(json, "schemaVersion") ?: schema
                            break
                        }
                        zis.closeEntry()
                    }
                }
            } catch (_: Exception) {}

            BackupSummary(
                fileName = file.name,
                filePath = file.absolutePath,
                yearMonthFolder = relParent,
                sizeBytes = file.length(),
                createdAt = created,
                schemaVersion = schema,
                sha256 = sha,
                source = if (file.name.contains("PreRestore")) "نسخة أمان قبل الاستعادة" else "أرشيف ($relParent)"
            )
        }
    }

    suspend fun deleteLocalBackup(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(filePath)
            f.exists() && f.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun pruneOldBackups(context: Context, maxKeepCount: Int) {
        val rootDir = getRootBackupsDir(context)
        val regularBackups = rootDir.walkTopDown()
            .filter { f -> f.isFile && !f.name.contains("PreRestore") && (f.name.endsWith(BACKUP_EXTENSION) || f.name.endsWith(".zip")) }
            .sortedByDescending { it.lastModified() }
            .toList()
        if (regularBackups.size > maxKeepCount) {
            regularBackups.drop(maxKeepCount).forEach { it.delete() }
        }
    }

    private fun writeBackupArchiveToStream(
        dbFile: File,
        outputStream: OutputStream,
        createdAt: String,
        sha256: String,
        accountsCount: Int,
        productsCount: Int,
        invoicesCount: Int
    ) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("smartledger.db"))
            FileInputStream(dbFile).use { it.copyTo(zipOut) }
            zipOut.closeEntry()

            val metaJson = """
            {
                "app": "SmartLedger Pro Android",
                "version": "2.2.0",
                "formatExtension": ".slprobackup",
                "formatVersion": 2,
                "schemaVersion": ${SmartLedgerDatabase.SCHEMA_VERSION},
                "createdAt": "$createdAt",
                "dbSizeBytes": ${dbFile.length()},
                "accountsCount": $accountsCount,
                "productsCount": $productsCount,
                "invoicesCount": $invoicesCount,
                "sha256": "$sha256"
            }
            """.trimIndent()
            zipOut.putNextEntry(ZipEntry("metadata.json"))
            zipOut.write(metaJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
            zipOut.finish()
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

    fun extractJsonString(json: String?, key: String): String? {
        if (json.isNullOrBlank()) return null
        return """"$key"\s*:\s*"([^"]*)"""".toRegex().find(json)?.groupValues?.getOrNull(1)
    }

    fun extractJsonInt(json: String?, key: String): Int? {
        if (json.isNullOrBlank()) return null
        return """"$key"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun queryCountSafe(db: SQLiteDatabase, sql: String): Int {
        return try {
            db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        } catch (_: Exception) { 0 }
    }
}
