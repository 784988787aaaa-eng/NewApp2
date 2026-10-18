package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.SettingsViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

/**
 * معالج الإعداد الأولي الرشيق (FirstRunOnboardingDialog) — خطوة واحدة اختيارية دون فرض أي بيانات تجارية وهمية.
 */
@Composable
fun UnitsAndWarehousesManagerSheet(
    viewModel: SettingsViewModel,
    initialTab: String = "UNITS",
    onClose: () -> Unit
) {
    val units by viewModel.units.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val books by viewModel.books.collectAsState()
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableStateOf(initialTab) } // UNITS, WAREHOUSES, BOOKS
    var feedbackMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isFeedbackError by rememberSaveable { mutableStateOf(false) }

    var unitName by rememberSaveable { mutableStateOf("") }
    var unitSymbol by rememberSaveable { mutableStateOf("") }
    var unitPrecision by rememberSaveable { mutableStateOf(0) }
    var baseUnitId by rememberSaveable { mutableStateOf<Long?>(null) }
    var factorText by rememberSaveable { mutableStateOf("1") }

    var warehouseName by rememberSaveable { mutableStateOf("") }
    var warehouseLocation by rememberSaveable { mutableStateOf("") }

    var bookName by rememberSaveable { mutableStateOf("") }
    var editingBookId by rememberSaveable { mutableStateOf<Long?>(null) }

    FloatingModalDialog(onDismissRequest = onClose) {
        FormSheetContainer(
            title = "الوحدات والمخازن والدفاتر الحسابية",
            subtitle = "عرّف وحدات القياس وقواعد التحويل والمخازن والدفاتر الخاصة بنشاطك",
            headerIcon = Icons.Default.Straighten,
            onDismiss = onClose,
            primaryButtonText = when (activeTab) {
                "UNITS" -> "حفظ وحدة القياس"
                "WAREHOUSES" -> "حفظ المخزن"
                else -> if (editingBookId != null) "تحديث اسم الدفتر" else "حفظ الدفتر الجديد"
            },
            secondaryButtonText = "إغلاق",
            onPrimaryClick = {
                scope.launch {
                    when (activeTab) {
                        "UNITS" -> {
                            val res = viewModel.saveUnitAsync(
                                name = unitName,
                                symbol = unitSymbol,
                                decimalPrecision = unitPrecision,
                                baseUnitId = baseUnitId,
                                conversionFactorToBase = NumberFormatter.parseDouble(factorText, 1.0)
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ الوحدة «$unitName» بنجاح."
                                unitName = ""
                                unitSymbol = ""
                                factorText = "1"
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        "WAREHOUSES" -> {
                            val res = viewModel.saveWarehouseAsync(
                                name = warehouseName,
                                location = warehouseLocation,
                                isDefault = warehouses.isEmpty()
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ المخزن «$warehouseName» بنجاح."
                                warehouseName = ""
                                warehouseLocation = ""
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        else -> {
                            val res = viewModel.saveBookAsync(bookName, editingBookId ?: 0L)
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ الدفتر «$bookName» بنجاح."
                                bookName = ""
                                editingBookId = null
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                    }
                }
            }
        ) {
            StatusFeedbackBanner(
                message = feedbackMsg,
                isError = isFeedbackError,
                onDismiss = { feedbackMsg = null }
            )

            SegmentedOptionRow(
                options = listOf(
                    "UNITS" to "الوحدات (${units.size})",
                    "WAREHOUSES" to "المخازن (${warehouses.size})",
                    "BOOKS" to "الدفاتر (${books.size})"
                ),
                selectedOption = activeTab,
                onOptionSelected = { activeTab = it }
            )

            when (activeTab) {
                "UNITS" -> {
                    FormSectionGroup(title = "تعريف وحدة قياس جديدة") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SmartTextField(
                                value = unitName,
                                onValueChange = { unitName = it },
                                label = "اسم الوحدة",
                                placeholder = "مثال: كجم، كرتون، شدة، لتر...",
                                isRequired = true,
                                modifier = Modifier.weight(1f)
                            )
                            SmartTextField(
                                value = unitSymbol,
                                onValueChange = { unitSymbol = it },
                                label = "الرمز المختصر",
                                placeholder = "كجم",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FilterChipRow(
                            items = listOf(0 to "بدون كسور (0)", 2 to "منزلتان (0.00)", 3 to "3 منازل (0.000)"),
                            selectedItem = unitPrecision,
                            onItemSelected = { unitPrecision = it }
                        )
                        val baseOptions = remember(units) {
                            listOf<Pair<Long?, String>>(null to "وحدة أساسية مستقلة") +
                                units.filter { it.BaseUnitId == null }.map { it.Id to "مرتبطة بـ: ${it.Name}" }
                        }
                        FilterChipRow(
                            items = baseOptions,
                            selectedItem = baseUnitId,
                            onItemSelected = { baseUnitId = it }
                        )
                        if (baseUnitId != null) {
                            SmartTextField(
                                value = factorText,
                                onValueChange = { factorText = it },
                                label = "معامل التحويل إلى الوحدة الأساسية",
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            )
                        }
                    }
                }

                "WAREHOUSES" -> {
                    FormSectionGroup(title = "تعريف مخزن أو مستودع أو فرع") {
                        SmartTextField(
                            value = warehouseName,
                            onValueChange = { warehouseName = it },
                            label = "اسم المخزن أو الفرع",
                            placeholder = "مثال: معرض البيع، المخزن الخلفي...",
                            isRequired = true
                        )
                        SmartTextField(
                            value = warehouseLocation,
                            onValueChange = { warehouseLocation = it },
                            label = "الموقع أو الوصف (اختياري)",
                            imeAction = ImeAction.Done
                        )
                    }
                    if (warehouses.isNotEmpty()) {
                        FormSectionGroup(title = "المخازن المعرفة حالياً") {
                            warehouses.forEach { w ->
                                Text("• ${w.Name}" + (w.Location?.let { " ($it)" } ?: ""), color = SmartLedgerColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                "BOOKS" -> {
                    FormSectionGroup(title = "إضافة أو تعديل دفتر حسابات (العملاء، الموردين، المناديب...)") {
                        SmartTextField(
                            value = bookName,
                            onValueChange = { bookName = it },
                            label = if (editingBookId != null) "تعديل اسم الدفتر" else "اسم الدفتر الحسابي الجديد",
                            placeholder = "مثال: عملاء الجملة، المناديب، الشركاء...",
                            isRequired = true,
                            imeAction = ImeAction.Done
                        )
                        if (books.isNotEmpty()) {
                            books.forEach { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ${b.Name}", color = SmartLedgerColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    IconButton(
                                        onClick = {
                                            editingBookId = b.Id
                                            bookName = b.Name
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل الدفتر", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
