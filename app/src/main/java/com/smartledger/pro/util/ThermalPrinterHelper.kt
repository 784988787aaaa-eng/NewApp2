package com.smartledger.pro.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

data class PairedPrinterDevice(
    val name: String,
    val address: String
)

object ThermalPrinterHelper {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): Result<List<PairedPrinterDevice>> {
        if (!hasBluetoothPermission(context)) {
            return Result.failure(SecurityException("يلزم منح إذن البلوتوث للوصول إلى الطابعات المقترنة"))
        }
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) return Result.failure(IllegalStateException("هذا الجهاز لا يدعم البلوتوث"))
            if (!adapter.isEnabled) return Result.failure(IllegalStateException("البلوتوث مغلق حالياً. يرجى تشغيل البلوتوث أولاً"))
            val list = adapter.bondedDevices?.map { PairedPrinterDevice(it.name ?: "طابعة بلوتوث", it.address) } ?: emptyList()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun testPrinterConnection(
        context: Context,
        macAddress: String,
        paperWidthMm: Int = 80,
        tradeName: String = "الدفتر الذكي Pro"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission(context)) return@withContext Result.failure(SecurityException("يرجى منح صلاحية البلوتوث أولاً"))
        if (macAddress.isBlank()) return@withContext Result.failure(IllegalArgumentException("يرجى اختيار طابعة بلوتوث مقترنة أولاً"))
        var socket: BluetoothSocket? = null
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(IllegalStateException("محول البلوتوث غير متاح"))
            if (!adapter.isEnabled) return@withContext Result.failure(IllegalStateException("البلوتوث غير مفعل على الجهاز"))
            val device = adapter.getRemoteDevice(macAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            val divider = "-".repeat(if (paperWidthMm == 58) 32 else 46)
            val out = socket.outputStream
            out.write(ESC_INIT)
            out.write(ESC_ALIGN_CENTER)
            out.write(ESC_BOLD_ON)
            out.write("$tradeName\n".toByteArray(charset("CP1256")))
            out.write(ESC_BOLD_OFF)
            out.write("اختبار اتصال طابعة ESC/POS (${paperWidthMm}mm)\n".toByteArray(charset("CP1256")))
            out.write("$divider\n".toByteArray())
            out.write("التاريخ: ${Formatters.nowTimestamp()}\n\n".toByteArray(charset("CP1256")))
            out.write(ESC_FEED_AND_CUT)
            out.flush()

            Result.success("تم الاتصال الفعلي بالطابعة (${device.name ?: macAddress}) وطباعة ورقة الاختبار بنجاح")
        } catch (e: Exception) {
            Result.failure(java.io.IOException("تعذر الاتصال بالطابعة ($macAddress): تأكد من تشغيلها واقترابها من الجهاز"))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun printInvoiceReceipt(
        device: BluetoothDevice,
        tradeName: String,
        invoiceNumber: String,
        customerName: String,
        items: List<Triple<String, Double, Double>>,
        finalTotal: Double,
        paid: Double,
        remaining: Double,
        footer: String,
        paperWidthMm: Int = 80,
        copies: Int = 1,
        currencySymbol: String = "ر.ي"
    ): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val out: OutputStream = socket.outputStream
            val lineChars = if (paperWidthMm == 58) 32 else 46
            val divider = "-".repeat(lineChars) + "\n"

            repeat(copies.coerceIn(1, 3)) {
                out.write(ESC_INIT)
                out.write(ESC_ALIGN_CENTER)
                out.write(ESC_BOLD_ON)
                out.write("$tradeName\n".toByteArray(charset("CP1256")))
                out.write(ESC_BOLD_OFF)
                out.write("فاتورة مبيعات\n".toByteArray(charset("CP1256")))
                out.write("رقم: $invoiceNumber\n".toByteArray(charset("CP1256")))
                out.write("العميل: $customerName\n".toByteArray(charset("CP1256")))
                out.write(divider.toByteArray())

                out.write(ESC_ALIGN_RIGHT)
                for (item in items) {
                    val nameMax = if (paperWidthMm == 58) 14 else 22
                    val line = String.format(
                        java.util.Locale.US,
                        "%-${nameMax}s %5s %10s\n",
                        item.first.take(nameMax),
                        Formatters.formatQuantity(item.second),
                        Formatters.formatAmountOnly(item.third)
                    )
                    out.write(line.toByteArray(charset("CP1256")))
                }

                out.write(divider.toByteArray())
                out.write(ESC_BOLD_ON)
                out.write("الإجمالي الصافي: ${Formatters.formatCurrency(finalTotal, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write(ESC_BOLD_OFF)
                out.write("المدفوع: ${Formatters.formatCurrency(paid, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write("المتبقي: ${Formatters.formatCurrency(remaining, currencySymbol)}\n".toByteArray(charset("CP1256")))
                out.write(divider.toByteArray())

                out.write(ESC_ALIGN_CENTER)
                if (footer.isNotBlank()) out.write("$footer\n\n".toByteArray(charset("CP1256")))
                out.write(ESC_FEED_AND_CUT)
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
