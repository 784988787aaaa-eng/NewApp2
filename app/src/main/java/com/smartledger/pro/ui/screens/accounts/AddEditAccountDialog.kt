package com.smartledger.pro.ui.screens.accounts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.entity.BookEntity
import com.smartledger.pro.data.local.entity.OperationEntity
import com.smartledger.pro.domain.usecase.LedgerDirectionPolicy
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.AccountsViewModel
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.ThermalPrinterHelper
import com.smartledger.pro.util.WhatsAppPreviewDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun AddEditAccountDialog(
    books: List<BookEntity>,
    defaultBookId: Long,
    existingAccount: AccountBalanceSummary?,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (id: Long, name: String, bookId: Long, phone: String?, limit: Double, opening: Double, isDebit: Boolean, note: String?) -> Unit
) {
    val isEditing = existingAccount != null
    val stateKey = existingAccount?.PersonId ?: -1L

    var name by rememberSaveable(stateKey) { mutableStateOf(existingAccount?.PersonName ?: "") }
    var selectedBookId by rememberSaveable(stateKey) { mutableStateOf(existingAccount?.BookId ?: defaultBookId.coerceAtLeast(1L)) }
    var phone by rememberSaveable(stateKey) { mutableStateOf(existingAccount?.Phone ?: "") }
    var openingBalanceText by rememberSaveable(stateKey) { mutableStateOf("") }
    var isDebitOpening by rememberSaveable(stateKey) {
        mutableStateOf(existingAccount?.BookId != 2L && defaultBookId != 2L)
    }
    var creditLimitText by rememberSaveable(stateKey) {
        mutableStateOf(
            if (existingAccount != null && existingAccount.CreditLimit > 0.0) NumberFormatter.formatQuantity(existingAccount.CreditLimit) else ""
        )
    }
    var note by rememberSaveable(stateKey) { mutableStateOf(existingAccount?.Note ?: "") }

    val focusPhone = remember { FocusRequester() }
    val focusOpening = remember { FocusRequester() }
    val focusLimit = remember { FocusRequester() }
    val focusNote = remember { FocusRequester() }

    val isFormValid = name.trim().length >= 2
    val hasUnsavedChanges = name != (existingAccount?.PersonName ?: "") ||
        phone != (existingAccount?.Phone ?: "") ||
        openingBalanceText.isNotBlank() ||
        note != (existingAccount?.Note ?: "")

    FormSheetContainer(
        title = if (isEditing) "تعديل الحساب" else "حساب جديد",
        
        headerIcon = Icons.Default.PersonAdd,
        onDismiss = onDismiss,
        primaryButtonText = "حفظ",
        isPrimaryEnabled = isFormValid,
        isSaving = isSaving,
        hasUnsavedChanges = hasUnsavedChanges,
        errorMessage = errorMessage,
        onPrimaryClick = {
            if (isFormValid) {
                val openVal = NumberFormatter.parseDouble(openingBalanceText, 0.0)
                val limitVal = NumberFormatter.parseDouble(creditLimitText, 0.0)
                onSave(
                    existingAccount?.PersonId ?: 0L,
                    name.trim(),
                    selectedBookId,
                    phone.trim().ifBlank { null },
                    limitVal,
                    openVal,
                    isDebitOpening,
                    note.trim().ifBlank { null }
                )
            }
        }
    ) {
        FormSectionGroup(title = "البيانات الأساسية") {
            if (books.isNotEmpty()) {
                FilterChipRow(
                    items = books.map { it.Id to it.Name },
                    selectedItem = selectedBookId,
                    onItemSelected = { bId ->
                        selectedBookId = bId
                        if (!isEditing) {
                            isDebitOpening = bId != 2L
                        }
                    }
                )
            }

            SmartTextField(
                value = name,
                onValueChange = { name = it },
                label = "الاسم الكامل للعميل / المورد / الحساب",
                placeholder = "مثال: مؤسسة النور التجارية",
                isRequired = true,
                leadingIcon = Icons.Default.Person,
                errorText = if (name.isNotEmpty() && name.trim().length < 2) "الاسم قصير جداً (حرفان على الأقل)" else null,
                autoFocusOnAppear = !isEditing && name.isBlank(),
                nextFocusRequester = focusPhone
            )

            SmartTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "رقم الهاتف / واتساب (اختياري)",
                placeholder = "777000000",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                focusRequester = focusPhone,
                nextFocusRequester = if (!isEditing) focusOpening else focusLimit
            )
        }

        if (!isEditing) {
            FormSectionGroup(title = "الرصيد الافتتاحي") {
                SegmentedOptionRow(
                    options = listOf(
                        true to "عليه / لنا (مدين)",
                        false to "له / علينا (دائن)"
                    ),
                    selectedOption = isDebitOpening,
                    onOptionSelected = { isDebitOpening = it },
                    activeColor = if (isDebitOpening) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                )

                SmartTextField(
                    value = openingBalanceText,
                    onValueChange = { openingBalanceText = it },
                    label = "مبلغ الرصيد الافتتاحي (${AppDesignState.currencySymbol})",
                    placeholder = "0 (اتركه فارغاً إذا كان الرصيد صفراً)",
                    keyboardType = KeyboardType.Decimal,
                    focusRequester = focusOpening,
                    nextFocusRequester = focusLimit
                )
            }
        }

        FormSectionGroup(title = "الائتمان والملاحظات") {
            SmartTextField(
                value = creditLimitText,
                onValueChange = { creditLimitText = it },
                label = "سقف الائتمان الأقصى المسموح (${AppDesignState.currencySymbol})",
                placeholder = "0 = بدون سقف ائتماني محدد",
                helperText = "ينبهك النظام عند تجاوز مديونية الحساب لهذا المبلغ",
                keyboardType = KeyboardType.Decimal,
                focusRequester = focusLimit,
                nextFocusRequester = focusNote
            )

            SmartTextField(
                value = note,
                onValueChange = { note = it },
                label = "عنوان أو ملاحظة تعريفية (اختياري)",
                placeholder = "العنوان، شروط السداد، أو اسم المندوب...",
                focusRequester = focusNote,
                imeAction = ImeAction.Done
            )
        }
    }
}
