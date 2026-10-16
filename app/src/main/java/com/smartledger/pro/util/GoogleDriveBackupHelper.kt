package com.smartledger.pro.util

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

data class CloudBackupInfo(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val sha256: String
)

enum class DriveConnectionStatus(val code: String, val arabicLabel: String) {
    DISCONNECTED("DISCONNECTED", "غير مربوط"),
    NEEDS_CONFIG("NEEDS_CONFIG", "يتطلب تهيئة OAuth Client ID"),
    REQUIRES_AUTH("REQUIRES_AUTH", "يتطلب مصادقة حساب Google"),
    READY("READY", "جاهز ومربوط"),
    UPLOADING("UPLOADING", "جارٍ النسخ السحابي..."),
    SUCCESS("SUCCESS", "تم النسخ والتحقق بنجاح"),
    ERROR("ERROR", "فشل الاتصال أو الرفع")
}

object GoogleDriveBackupHelper {

    private const val GDRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val PREFS_AUTH_NAME = "smartledger_gdrive_auth"
    private const val KEY_ACCESS_TOKEN = "oauth_access_token"
    private const val KEY_TOKEN_EXPIRY = "oauth_token_expiry"
    const val AUTO_BACKUP_JOB_ID = 7401

    fun saveAccessToken(context: Context, token: String, expiresInSeconds: Long = 3600L) {
        val prefs = context.getSharedPreferences(PREFS_AUTH_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.trim())
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresInSeconds * 1000L))
            .apply()
    }

    fun getValidAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_AUTH_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        if (token.isNullOrBlank()) return null
        if (expiry > 0L && System.currentTimeMillis() > expiry) return null
        return token
    }

    fun disconnectAccount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_AUTH_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.DISCONNECTED.code)
        AppSettingsManager.updatePreference(context, "gdrive_email", "")
        AppSettingsManager.updatePreference(context, "auto_backup_enabled", "false")
        cancelAutoBackupJob(context)
    }

    fun buildGoogleOAuthUrl(clientId: String, redirectUri: String = "com.smartledger.pro:/oauth2redirect"): Uri {
        return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "$DRIVE_APPDATA_SCOPE email")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
    }

    suspend fun createLocalArchive(context: Context): File? = withContext(Dispatchers.IO) {
        val result = BackupRestoreHelper.createInternalBackupFile(context, "SmartLedgerPro_Cloud")
        result.getOrNull()?.first
    }

    suspend fun uploadBackupToDrive(
        backupFile: File,
        accessToken: String,
        onProgress: (Int) -> Unit
    ): Result<CloudBackupInfo> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) return@withContext Result.failure(IllegalStateException("يتطلب الربط والمصادقة مع حساب Google Drive أولاً"))
            val sha256 = BackupRestoreHelper.calculateSha256(backupFile)
            val boundary = "SmartLedgerBoundary_" + System.currentTimeMillis()
            val url = URL(GDRIVE_UPLOAD_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            }

            val outputStream = conn.outputStream
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            val cloudFileName = backupFile.name

            writer.write("--$boundary\r\n")
            writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            writer.write("""{"name": "$cloudFileName", "parents": ["appDataFolder"], "appProperties": {"sha256": "$sha256"}}""")
            writer.write("\r\n--$boundary\r\n")
            writer.write("Content-Type: application/octet-stream\r\n\r\n")
            writer.flush()

            val totalBytes = backupFile.length().coerceAtLeast(1L)
            var uploadedBytes = 0L
            val buffer = ByteArray(8192)

            FileInputStream(backupFile).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    uploadedBytes += bytesRead
                    onProgress(((uploadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100))
                }
            }
            outputStream.flush()

            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val fileId = BackupRestoreHelper.extractJsonString(response, "id") ?: "gdrive_file"
                val modified = BackupRestoreHelper.extractJsonString(response, "modifiedTime") ?: Formatters.nowTimestamp()
                Result.success(CloudBackupInfo(fileId, cloudFileName, backupFile.length(), modified, sha256))
            } else {
                val error = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(IOException("فشل الرفع السحابي: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromArchive(context: Context, zipFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        val res = BackupRestoreHelper.restoreFromArchiveFile(context, zipFile)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("فشل استعادة النسخة"))
    }

    fun scheduleAutoBackupJob(context: Context, frequencyHours: Int = 24, wifiOnly: Boolean = true): Boolean {
        return try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
            val component = ComponentName(context, AutoBackupJobService::class.java)
            val intervalMs = (frequencyHours.coerceAtLeast(1) * 3600L * 1000L)
            val networkType = if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            val jobInfo = JobInfo.Builder(AUTO_BACKUP_JOB_ID, component)
                .setPeriodic(intervalMs)
                .setRequiredNetworkType(networkType)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
        } catch (_: Exception) { false }
    }

    fun cancelAutoBackupJob(context: Context) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(AUTO_BACKUP_JOB_ID)
        } catch (_: Exception) {}
    }
}

class AutoBackupJobService : JobService() {
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            var needsReschedule = false
            try {
                val localRes = BackupRestoreHelper.createInternalBackupFile(applicationContext, prefix = "SmartLedgerPro_Auto")
                if (localRes.isSuccess) {
                    val (file, summary) = localRes.getOrThrow()
                    AppSettingsManager.updatePreference(
                        applicationContext,
                        "last_local_backup_info",
                        "${summary.createdAt} (${Formatters.formatBytes(summary.sizeBytes)})"
                    )
                    val token = GoogleDriveBackupHelper.getValidAccessToken(applicationContext)
                    if (!token.isNullOrBlank()) {
                        val cloudRes = GoogleDriveBackupHelper.uploadBackupToDrive(file, token) {}
                        if (cloudRes.isSuccess) {
                            val info = cloudRes.getOrThrow()
                            AppSettingsManager.updatePreference(applicationContext, "gdrive_status", DriveConnectionStatus.SUCCESS.code)
                            AppSettingsManager.updatePreference(applicationContext, "last_cloud_backup_info", "${Formatters.nowTimestamp()} (${Formatters.formatBytes(info.sizeBytes)})")
                        } else {
                            needsReschedule = true
                        }
                    }
                }
            } catch (_: Exception) {
                needsReschedule = true
            } finally {
                jobFinished(params, needsReschedule)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true
    }
}
