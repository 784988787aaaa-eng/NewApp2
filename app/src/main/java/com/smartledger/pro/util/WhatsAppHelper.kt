package com.smartledger.pro.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.entity.OperationEntity
import com.smartledger.pro.ui.components.FilterChipRow
import com.smartledger.pro.ui.components.FloatingModalDialog
import com.smartledger.pro.ui.components.FormSectionGroup
import com.smartledger.pro.ui.components.FormSheetContainer
import com.smartledger.pro.ui.components.SmartTextField
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import kotlin.math.abs

enum class WhatsAppTemplateType(val arabicLabel: String) {
    CLAIM_REMINDER("مطالبة مالية مهذبة"),
    BALANCE_SUMMARY("إشعار رصيد الحساب"),
    STATEMENT_SUMMARY("ملخص كشف الحساب"),
    PAYMENT_RECEIPT("تأكيد استلام دفعة")
}

/**
 * مساعد تكامل WhatsApp الاحترافي (WhatsAppHelper)
 * - يدعم تنظيف وتوحيد أرقام الهواتف المحلية والدولية مع احترام مفتاح الدولة المحدد في الإعدادات.
 * - يوفر قوالب رسائل عربية واضحة قابلة للتخصيص والتعديل قبل الإرسال.
 */
object WhatsAppHelper {

    fun normalizePhoneForWhatsApp(rawPhone: String?, defaultCountryCode: String = "+967"): String {
        if (rawPhone.isNullOrBlank()) return ""
        val western = NumberFormatter.toWesternDigits(rawPhone)
        val digitsAndPlus = western.filter { it.isDigit() || it == '+' }
        if (digitsAndPlus.isEmpty()) return ""

        val cleanCode = NumberFormatter.toWesternDigits(defaultCountryCode)
            .filter { it.isDigit() }
            .ifBlank { "967" }

        return when {
            digitsAndPlus.startsWith("+") -> digitsAndPlus.removePrefix("+").filter { it.isDigit() }
            digitsAndPlus.startsWith("00") -> digitsAndPlus.removePrefix("00").filter { it.isDigit() }
            digitsAndPlus.startsWith(cleanCode) && digitsAndPlus.length > cleanCode.length + 5 -> digitsAndPlus.filter { it.isDigit() }
            else -> cleanCode + digitsAndPlus.filter { it.isDigit() }.trimStart('0')
        }
    }

    fun buildAccountMessage(
        account: AccountBalanceSummary,
        businessName: String,
        currencySymbol: String,
        decimalPlaces: Int,
        templateType: WhatsAppTemplateType,
        recentOperations: List<OperationEntity> = emptyList()
    ): String {
        val cleanBusiness = businessName.trim().ifBlank { "الدفتر الذكي Pro" }
        val absBalFormatted = NumberFormatter.formatCurrency(abs(account.NetBalance), currencySymbol, decimalPlaces)
        val balanceDirection = when {
            account.NetBalance > 0.001 -> "مستحق عليكم ($absBalFormatted)"
            account.NetBalance < -0.001 -> "رصيد دائن لصالحكم ($absBalFormatted)"
            else -> "متزن (0 $currencySymbol)"
        }

        return when (templateType) {
            WhatsAppTemplateType.CLAIM_REMINDER -> buildString {
                appendLine("مرحباً ${account.PersonName} المحترم،")
                appendLine("تحية طيبة من «$cleanBusiness».")
                if (account.NetBalance > 0.001) {
                    appendLine("نود تذكيركم بلطف بأن الرصيد المستحق في حسابكم حتى تاريخه هو: $absBalFormatted.")
                    appendLine("نسعد بتواصلكم لتسوية الرصيد أو مراجعة تفاصيل الفواتير في أي وقت.")
                } else {
                    appendLine("حالة رصيد حسابكم الحالي لدينا: $balanceDirection.")
                }
                append("شكراً لتعاملكم وثقتكم.")
            }

            WhatsAppTemplateType.BALANCE_SUMMARY -> buildString {
                appendLine("مرحباً ${account.PersonName}،")
                appendLine("إشعار رصيد حساب من «$cleanBusiness»:")
                appendLine("• إجمالي المدين (عليه): ${NumberFormatter.formatCurrency(account.TotalDebit, currencySymbol, decimalPlaces)}")
                appendLine("• إجمالي الدائن (له): ${NumberFormatter.formatCurrency(account.TotalCredit, currencySymbol, decimalPlaces)}")
                append("• صافي الرصيد الحالي: $balanceDirection")
            }

            WhatsAppTemplateType.STATEMENT_SUMMARY -> buildString {
                appendLine("كشف حساب مختصر — «$cleanBusiness»")
                appendLine("الحساب: ${account.PersonName}")
                appendLine("صافي الرصيد الحالي: $balanceDirection")
                if (recentOperations.isNotEmpty()) {
                    appendLine("آخر الحركات المسجلة:")
                    recentOperations.take(5).forEach { op ->
                        val amt = NumberFormatter.formatCurrency(op.Amount, currencySymbol, decimalPlaces)
                        val dateStr = op.OccurredAt.take(10)
                        appendLine("- [$dateStr] ${op.Note ?: op.Type}: $amt")
                    }
                }
                append("للاستفسار أو المطابقة يرجى التواصل معنا.")
            }

            WhatsAppTemplateType.PAYMENT_RECEIPT -> buildString {
                val lastReceive = recentOperations.firstOrNull { it.Type == "Receive" }
                appendLine("مرحباً ${account.PersonName}،")
                appendLine("إشعار تأكيد من «$cleanBusiness»:")
                if (lastReceive != null) {
                    appendLine("تم قيد دفعتكم بمبلغ ${NumberFormatter.formatCurrency(lastReceive.Amount, currencySymbol, decimalPlaces)} بتاريخ ${lastReceive.OccurredAt.take(16)}.")
                }
                append("الرصيد المتبقي بعد العملية: $balanceDirection.")
            }
        }
    }

    fun launchWhatsAppOrShare(
        context: Context,
        rawPhone: String?,
        defaultCountryCode: String,
        messageText: String,
        shareTitle: String = "مشاركة رسالة حساب"
    ) {
        val cleanPhone = normalizePhoneForWhatsApp(rawPhone, defaultCountryCode)
        if (cleanPhone.isNotBlank()) {
            try {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // في حال عدم تثبيت واتساب يتم التحويل الآمن للمشاركة النصية للنظام
            }
        }
        ThermalPrinterHelper.shareTextSafely(context, shareTitle, messageText)
    }
}

/**
 * نافذة معاينة وتعديل رسالة WhatsApp قبل الإرسال (WhatsAppPreviewDialog)
 * تتيح للمستخدم اختيار القالب، تعديل نص الرسالة بحرية، والتحقق من رقم الهاتف مع مفتاح الدولة قبل فتح واتساب.
 */
@Composable
fun WhatsAppPreviewDialog(
    account: AccountBalanceSummary,
    businessName: String,
    defaultCountryCode: String = "+967",
    recentOperations: List<OperationEntity> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTemplate by rememberSaveable {
        mutableStateOf(
            if (account.NetBalance > 0.001) WhatsAppTemplateType.CLAIM_REMINDER else WhatsAppTemplateType.BALANCE_SUMMARY
        )
    }
    var phoneInput by rememberSaveable(account.PersonId) { mutableStateOf(account.Phone ?: "") }
    var messageInput by rememberSaveable(account.PersonId, selectedTemplate) {
        mutableStateOf(
            WhatsAppHelper.buildAccountMessage(
                account = account,
                businessName = businessName,
                currencySymbol = AppDesignState.currencySymbol,
                decimalPlaces = AppDesignState.decimalPlaces,
                templateType = selectedTemplate,
                recentOperations = recentOperations
            )
        )
    }

    val normalizedPreviewPhone = remember(phoneInput, defaultCountryCode) {
        WhatsAppHelper.normalizePhoneForWhatsApp(phoneInput, defaultCountryCode)
    }

    FloatingModalDialog(onDismissRequest = onDismiss) {
        FormSheetContainer(
            title = "معاينة وإرسال رسالة واتساب",
            subtitle = "الحساب: ${account.PersonName} • يمكنك تعديل النص قبل الإرسال",
            headerIcon = Icons.Default.Chat,
            onDismiss = onDismiss,
            primaryButtonText = "إرسال عبر واتساب",
            primaryButtonColor = SmartLedgerColors.StateSuccess,
            isPrimaryEnabled = messageInput.trim().isNotEmpty(),
            onPrimaryClick = {
                WhatsAppHelper.launchWhatsAppOrShare(
                    context = context,
                    rawPhone = phoneInput,
                    defaultCountryCode = defaultCountryCode,
                    messageText = messageInput.trim(),
                    shareTitle = "رسالة حساب ${account.PersonName}"
                )
                onDismiss()
            }
        ) {
            FormSectionGroup(title = "1. نوع القالب ورقم المستلم") {
                FilterChipRow(
                    items = WhatsAppTemplateType.values().map { it to it.arabicLabel },
                    selectedItem = selectedTemplate,
                    onItemSelected = { selectedTemplate = it }
                )

                SmartTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = "رقم واتساب المستلم (مفتاح الدولة الافتراضي: $defaultCountryCode)",
                    placeholder = "مثال: 777123456 أو +967777123456",
                    helperText = if (normalizedPreviewPhone.isNotBlank()) "الرقم المنسق للإرسال الدولي: +$normalizedPreviewPhone" else "إن تُرك فارغاً سيتم فتح نافذة المشاركة لاختيار جهة الاتصال يدوياً",
                    keyboardType = KeyboardType.Phone,
                    leadingIcon = Icons.Default.Phone,
                    imeAction = ImeAction.Next
                )
            }

            FormSectionGroup(title = "2. معاينة وتعديل نص الرسالة") {
                SmartTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = "نص الرسالة القابل للتعديل",
                    singleLine = false,
                    minLines = 5,
                    imeAction = ImeAction.Done
                )

                OutlinedButton(
                    onClick = {
                        ThermalPrinterHelper.shareTextSafely(context, "رسالة حساب ${account.PersonName}", messageInput.trim())
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة كنص عام (SMS / تيليجرام / نسخ)", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
