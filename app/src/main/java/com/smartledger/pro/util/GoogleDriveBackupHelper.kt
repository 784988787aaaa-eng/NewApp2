package com.smartledger.pro.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class CloudBackupInfo(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val sha256: String
)

/**
 * حالات اتصال ومزامنة Google Drive الفعلية (بدون أي حالات أو تواريخ وهمية)
 */
enum class DriveConnectionState(val arabicLabel: String) {
    DISCONNECTED("غير مربوط بحساب Google"),
    NEEDS_OAUTH_TOKEN("يتطلب رمز مصادقة OAuth 2.0 (أو مشاركة سحابية مباشرة)"),
    READY("متصل وجاهز للنسخ السحابي"),
    UPLOADING("جارٍ رفع النسخة والتحقق من البصمة..."),
    DOWNLOADING("جارٍ تنزيل النسخة وفحص السلامة..."),
    SUCCESS("اكتملت المزامنة السحابية بنجاح"),
    ERROR("تعذر إكمال العملية السحابية")
}

/**
 * محرك النسخ الاحتياطي والمزامنة السحابية مع Google Drive REST API v3 (appDataFolder).
 *
 * ملاحظة هندسية هامة (توثيق متطلبات التهيئة الخارجية):
 * - يعتمد الاتصال المباشر بـ Google Drive API v3 على رمز وصول OAuth 2.0 صالح للنطاق الضيق:
 *   `https://www.googleapis.com/auth/drive.appdata` (حفظ داخل مجلد التطبيق المعزول فقط دون الوصول لملفات المستخدم الأخرى).
 * - لتمكين تسجيل الدخول بضغطة واحدة عبر Google Identity Services في بيئة الإنتاج، يلزم تسجيل بصمة شهادة التوقيع `SHA-1`
 *   وتعريف `OAuth 2.0 Client ID (Android)` في Google Cloud Console للمزمة `com.smartledger.pro`.
 * - في حال عدم توفر رمز OAuth نشط، يعرض المحرك الحالة الحقيقية بوضوح ولا يدعي نجاحاً شكلياً،
 *   مع توفير إنشاء الأرشيف الموثق `.slprobackup` ومشاركته إلى تطبيق Google Drive المثبت في النظام أو عبر SAF.
 */
object GoogleDriveBackupHelper {

    private const val GDRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    private const val GDRIVE_FILES_URL =
        "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size,modifiedTime,appProperties)&pageSize=20"

    const val REQUIRED_OAUTH_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    /**
     * إنشاء أرشيف `.slprobackup` محلي متسق (مع تفريغ WAL وبصمة SHA-256) تمهيداً لرفعه إلى السحابة
     */
    suspend fun createLocalArchive(context: Context): File? = withContext(Dispatchers.IO) {
        val result = BackupRestoreHelper.createStructuredArchiveBackup(context, sourceTag = "GoogleDriveStaging")
        result.getOrNull()?.let { File(it.filePath) }
    }

    /**
     * رفع النسخة الاحتياطية فعلياً إلى Google Drive AppDataFolder باستخدام OAuth 2.0 Access Token
     */
    suspend fun uploadBackupToDrive(
        backupFile: File,
        accessToken: String,
        onProgress: (Int) -> Unit
    ): Result<CloudBackupInfo> = withContext(Dispatchers.IO) {
        val cleanToken = accessToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                Exception("لم يتم توفير رمز مصادقة OAuth 2.0 صالح لخدمة Google Drive. يرجى ربط الحساب أو استخدام الحفظ السحابي عبر النظام.")
            )
        }
        if (!backupFile.exists() || backupFile.length() == 0L) {
            return@withContext Result.failure(Exception("ملف النسخة الاحتياطية غير موجود أو فارغ"))
        }

        try {
            val sha256 = BackupRestoreHelper.calculateSha256(backupFile)
            val boundary = "SmartLedgerBoundary_" + System.currentTimeMillis()
            val url = URL(GDRIVE_UPLOAD_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Authorization", "Bearer $cleanToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val cloudFileName = "SmartLedger_Backup_${timestamp}${BackupRestoreHelper.BACKUP_EXTENSION}"

            conn.outputStream.use { outputStream ->
                val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))

                // 1. Metadata Part (appDataFolder + SHA-256 property)
                val metaObj = JSONObject().apply {
                    put("name", cloudFileName)
                    put("parents", org.json.JSONArray().put("appDataFolder"))
                    put("appProperties", JSONObject().put("sha256", sha256))
                }

                writer.write("--$boundary\r\n")
                writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                writer.write(metaObj.toString() + "\r\n")
                writer.flush()

                // 2. Media Part (.slprobackup Zip Content)
                writer.write("--$boundary\r\n")
                writer.write("Content-Type: application/zip\r\n\r\n")
                writer.flush()

                val totalBytes = backupFile.length().coerceAtLeast(1L)
                var uploadedBytes = 0L
                val buffer = ByteArray(8192)

                FileInputStream(backupFile).use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        uploadedBytes += bytesRead
                        val progress = ((uploadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
                outputStream.flush()

                writer.write("\r\n--$boundary--\r\n")
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = runCatching { JSONObject(response) }.getOrNull()
                val fileId = json?.optString("id", "gdrive_${System.currentTimeMillis()}") ?: ""
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                Result.success(
                    CloudBackupInfo(
                        fileId = fileId,
                        fileName = cloudFileName,
                        sizeBytes = backupFile.length(),
                        modifiedTime = nowStr,
                        sha256 = sha256
                    )
                )
            } else if (responseCode == 401 || responseCode == 403) {
                Result.failure(Exception("انتهت صلاحية جلسة Google Drive أو أن رمز المصادقة غير مصرح له (HTTP $responseCode). يرجى تحديث الربط."))
            } else {
                val error = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("فشل الرفع إلى Google Drive: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بخدمة Google Drive: ${e.localizedMessage ?: "تحقق من اتصال الإنترنت"}"))
        }
    }

    /**
     * جلب قائمة النسخ الاحتياطية المحفوظة في Google Drive AppDataFolder
     */
    suspend fun listCloudBackups(accessToken: String): Result<List<CloudBackupInfo>> = withContext(Dispatchers.IO) {
        val cleanToken = accessToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(Exception("رمز المصادقة غير متوفر"))
        }
        try {
            val conn = (URL(GDRIVE_FILES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $cleanToken")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(body)
                val filesArr = root.optJSONArray("files") ?: return@withContext Result.success(emptyList())
                val result = mutableListOf<CloudBackupInfo>()
                for (i in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(i)
                    val props = f.optJSONObject("appProperties")
                    result.add(
                        CloudBackupInfo(
                            fileId = f.optString("id"),
                            fileName = f.optString("name"),
                            sizeBytes = f.optLong("size", 0L),
                            modifiedTime = f.optString("modifiedTime"),
                            sha256 = props?.optString("sha256", "") ?: ""
                        )
                    )
                }
                Result.success(result)
            } else {
                Result.failure(Exception("تعذر جلب قائمة النسخ السحابية (HTTP $code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استعادة نسخة احتياطية من ملف أرشيف مع التحقق الصارم من بصمة SHA-256 وسلامة SQLite
     */
    suspend fun restoreFromArchive(context: Context, zipFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        val res = BackupRestoreHelper.restoreFromBackupFile(context, zipFile, createPreRestoreSafety = true)
        if (res.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("فشلت عملية الاستعادة"))
        }
    }
}
