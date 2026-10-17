package com.smartledger.pro.util

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

data class PairedPrinterDevice(
    val name: String,
    val address: String
)

/**
 * محرك الطباعة الحرارية الفعلي (ESC/POS Thermal Printer Helper)
 * - يدعم مقاسي الورق القياسيين: 58mm (32 حرفاً للسطر) و 80mm (48 حرفاً للسطر).
 * - يتحقق صراحةً من إذن `BLUETOOTH_CONNECT` على Android 12+ (API 31+) قبل الوصول إلى `bondedDevices` أو فتح المقبس.
 * - يضيف `FLAG_ACTIVITY_NEW_TASK` تلقائياً عند إطلاق نافذة المشاركة من سياق غير `Activity`.
 */
object ThermalPrinterHelper {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

    /**
     * التحقق من توفر إذن الاتصال بأجهزة البلوتوث المقترنة (`BLUETOOTH_CONNECT` على Android 12+ API 31+)
     */
    fun hasBluetoothConnectPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * اسم الإذن المطلوب طلبه وقت التشغيل على Android 12+ (أو `null` على الإصدارات الأقدم API 26–30)
     */
    fun requiredBluetoothRuntimePermission(sdkInt: Int = Build.VERSION.SDK_INT): String? {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            null
        }
    }

    /**
     * استرجاع قائمة أجهزة البلوتوث المقترنة لاختيار الطابعة الحرارية
     */
    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): Result<List<PairedPrinterDevice>> {
        if (!hasBluetoothConnectPermission(context)) {
            return Result.failure(
                SecurityException("يلزم منح إذن «الأجهزة القريبة / البلوتوث» (BLUETOOTH_CONNECT) لعرض الطابعات المقترنة.")
            )
        }
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                return Result.failure(Exception("هذا الجهاز لا يدعم تقنية البلوتوث"))
            }
            if (!adapter.isEnabled) {
                return Result.failure(Exception("البلوتوث مغلق حالياً. يرجى تشغيل البلوتوث من إعدادات الجهاز أولاً."))
            }
            val bonded = adapter.bondedDevices.orEmpty().map {
                PairedPrinterDevice(
                    name = it.name ?: "طابعة بلوتوث",
                    address = it.address
                )
            }
            Result.success(bonded)
        } catch (se: SecurityException) {
            Result.failure(Exception("يرجى منح إذن الاتصال بأجهزة البلوتوث القريبة للوصول إلى الطابعة"))
        } catch (e: Exception) {
            Result.failure(Exception("تعذر قراءة قائمة الطابعات: ${e.localizedMessage}"))
        }
    }

    /**
     * اختبار اتصال فعلي بطابعة بلوتوث عبر عنوان MAC وإرسال سطر اختبار حقيقي
     */
    @SuppressLint("MissingPermission")
    suspend fun testPrinterConnection(
        context: Context,
        printerAddress: String,
        paperWidthMm: Int = 80,
        tradeName: String = "الدفتر الذكي Pro"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext Result.failure(
                SecurityException("يلزم منح إذن البلوتوث (BLUETOOTH_CONNECT) للاتصال بالطابعة الحرارية.")
            )
        }
        if (printerAddress.isBlank()) {
            return@withContext Result.failure(Exception("لم يتم تحديد طابعة بلوتوث بعد. اختر طابعة مقترنة أولاً."))
        }
        var socket: BluetoothSocket? = null
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("البلوتوث غير مدعوم على هذا الجهاز"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(Exception("البلوتوث متوقف حالياً. شغّل البلوتوث ثم أعد المحاولة."))
            }

            if (adapter.isDiscovering) {
                runCatching { adapter.cancelDiscovery() }
            }

            val device: BluetoothDevice = adapter.getRemoteDevice(printerAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            val sepWidth = if (paperWidthMm <= 58) 32 else 48
            val separator = "-".repeat(sepWidth) + "\n"
            val out: OutputStream = socket.outputStream
            out.write(ESC_INIT)
            out.write(ESC_ALIGN_CENTER)
            out.write(ESC_BOLD_ON)
            out.write("$tradeName\n".toByteArray(charset("CP1256")))
            out.write(ESC_BOLD_OFF)
            out.write(separator.toByteArray())
            out.write("اختبار طباعة ناجح (${paperWidthMm}mm)\n".toByteArray(charset("CP1256")))
            out.write("${NumberFormatter.nowTimestamp()}\n".toByteArray())
            out.write(separator.toByteArray())
            out.write(ESC_FEED_AND_CUT)
            out.flush()
            Result.success("تم الاتصال بالطابعة (${device.name ?: printerAddress}) وطباعة ورقة الاختبار بنجاح.")
        } catch (se: SecurityException) {
            Result.failure(Exception("يلزم منح صلاحية البلوتوث للاتصال بالطابعة الحرارية."))
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بالطابعة ($printerAddress): تأكد من تشغيل الطابعة ووجودها في النطاق."))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * طباعة إيصال فاتورة فعلي عبر عنوان الطابعة المحفوظ مع دعم مقاس 58mm و 80mm وعدد النسخ
     */
    @SuppressLint("MissingPermission")
    suspend fun printInvoiceByAddress(
        context: Context,
        printerAddress: String,
        paperWidthMm: Int = 80,
        copies: Int = 1,
        tradeName: String,
        phone: String,
        header: String,
        invoiceNumber: String,
        issuedAt: String,
        customerName: String,
        items: List<Triple<String, String, Double>>, // Name, Qty/Unit text, LineTotal
        subtotal: Double,
        discount: Double,
        finalTotal: Double,
        paid: Double,
        remaining: Double,
        currencySymbol: String,
        footer: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext Result.failure(
                SecurityException("يلزم منح إذن البلوتوث (BLUETOOTH_CONNECT) للطباعة الحرارية.")
            )
        }
        if (printerAddress.isBlank()) {
            return@withContext Result.failure(Exception("لم يتم إعداد طابعة بلوتوث في الإعدادات"))
        }
        var socket: BluetoothSocket? = null
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("البلوتوث غير مدعوم"))
            if (!adapter.isEnabled) {
                return@withContext Result.failure(Exception("البلوتوث مغلق حالياً"))
            }

            if (adapter.isDiscovering) {
                runCatching { adapter.cancelDiscovery() }
            }

            val device = adapter.getRemoteDevice(printerAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val out = socket.outputStream

            val lineChars = if (paperWidthMm <= 58) 32 else 48
            val separator = "-".repeat(lineChars) + "\n"
            val safeCopies = copies.coerceIn(1, 3)

            repeat(safeCopies) {
                out.write(ESC_INIT)
                out.write(ESC_ALIGN_CENTER)
                out.write(ESC_BOLD_ON)
                out.write("$tradeName\n".toByteArray(charset("CP1256")))
                out.write(ESC_BOLD_OFF)
                if (phone.isNotBlank()) {
                    out.write("هاتف: $phone\n".toByteArray(charset("CP1256")))
                }
                if (header.isNotBlank()) {
                    out.write("$header\n".toByteArray(charset("CP1256")))
                }
                out.write(separator.toByteArray())
                out.write("فاتورة رقم: $invoiceNumber\n".toByteArray(charset("CP1256")))
                out.write("التاريخ: $issuedAt\n".toByteArray(charset("CP1256")))
                out.write("العميل: $customerName\n".toByteArray(charset("CP1256")))
                out.write(separator.toByteArray())

                out.write(ESC_ALIGN_RIGHT)
                for (item in items) {
                    val nameTake = if (paperWidthMm <= 58) 14 else 22
                    val line = String.format(
                        Locale.US,
                        "%-14s %6s %10s\n",
                        item.first.take(nameTake),
                        item.second.take(8),
                        NumberFormatter.formatAmount(item.third)
                    )
                    out.write(line.toByteArray(charset("CP1256")))
                }

                out.write(separator.toByteArray())
                if (discount > 0.0) {
                    out.write("الإجمالي قبل الخصم: ${NumberFormatter.formatCurrency(subtotal, currencySymbol)}\n".toByteArray(charset("CP1256")))
                    out.write("الخصم الممنوح: ${NumberFormatter.formatCurrency(discount, currencySymbol)}\n".toByteArray(charset("CP1256")))
                }
                out.write(ESC_BOLD_ON)
                out.write("الصافي النهائي: ${NumberFormatter.formatCurrency(finalTotal, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write(ESC_BOLD_OFF)
                out.write("المدفوع: ${NumberFormatter.formatCurrency(paid, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write("المتبقي آجل: ${NumberFormatter.formatCurrency(remaining, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write(separator.toByteArray())

                out.write(ESC_ALIGN_CENTER)
                if (footer.isNotBlank()) {
                    out.write("$footer\n\n".toByteArray(charset("CP1256")))
                }
                out.write(ESC_FEED_AND_CUT)
                out.flush()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الطباعة على الطابعة الحرارية: ${e.localizedMessage}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * توليد نص إيصال فاتورة منسق للمشاركة عبر واتساب أو تطبيقات النظام
     */
    fun buildFormattedReceiptText(
        tradeName: String,
        invoiceNumber: String,
        issuedAt: String,
        customerName: String,
        items: List<Triple<String, String, Double>>,
        subtotal: Double,
        discount: Double,
        finalTotal: Double,
        paid: Double,
        remaining: Double,
        currencySymbol: String,
        footer: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("🧾 *$tradeName*")
        sb.appendLine("فاتورة رقم: $invoiceNumber")
        sb.appendLine("التاريخ: $issuedAt")
        sb.appendLine("العميل: $customerName")
        sb.appendLine("────────────────────")
        items.forEachIndexed { idx, (name, qtyText, total) ->
            sb.appendLine("${idx + 1}. $name ($qtyText) = ${NumberFormatter.formatCurrency(total, currencySymbol)}")
        }
        sb.appendLine("────────────────────")
        if (discount > 0.0) {
            sb.appendLine("الإجمالي الفرعي: ${NumberFormatter.formatCurrency(subtotal, currencySymbol)}")
            sb.appendLine("الخصم: ${NumberFormatter.formatCurrency(discount, currencySymbol)}")
        }
        sb.appendLine("*الصافي النهائي: ${NumberFormatter.formatCurrency(finalTotal, currencySymbol)}*")
        sb.appendLine("المدفوع: ${NumberFormatter.formatCurrency(paid, currencySymbol)}")
        if (remaining > 0.0) {
            sb.appendLine("المتبقي آجل: ${NumberFormatter.formatCurrency(remaining, currencySymbol)}")
        }
        if (footer.isNotBlank()) {
            sb.appendLine("────────────────────")
            sb.appendLine(footer)
        }
        return sb.toString()
    }

    /**
     * مشاركة نص الفاتورة أو الكشف بأمان عبر نافذة المشاركة في أندرويد
     */
    fun shareTextSafely(context: Context, title: String, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }
}
