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
fun CategoryHierarchyManagerSheet(
    viewModel: SettingsViewModel,
    initialDomain: String = "EXPENSE",
    onClose: () -> Unit
) {
    val allCategories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedDomain by rememberSaveable { mutableStateOf(initialDomain) }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var selectedParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var feedbackMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isFeedbackError by rememberSaveable { mutableStateOf(false) }

    val domainCategories = remember(allCategories, selectedDomain) {
        allCategories.filter { it.Domain.equals(selectedDomain, ignoreCase = true) }
    }

    FloatingModalDialog(onDismissRequest = onClose) {
        FormSheetContainer(
            title = "محرك التصنيفات الهرمية المرن",
            subtitle = "أنشئ تصنيفات رئيسية وفرعية متعددة المستويات دون أي قوائم جامدة",
            headerIcon = Icons.Default.AccountTree,
            onDismiss = onClose,
            primaryButtonText = if (editingCategoryId != null) "تحديث التصنيف" else "إضافة التصنيف",
            secondaryButtonText = "إغلاق",
            isPrimaryEnabled = categoryName.trim().isNotEmpty(),
            onPrimaryClick = {
                scope.launch {
                    val res = viewModel.saveCategoryAsync(
                        id = editingCategoryId ?: 0L,
                        domain = selectedDomain,
                        name = categoryName.trim(),
                        parentId = selectedParentId
                    )
                    if (res.isSuccess) {
                        isFeedbackError = false
                        feedbackMsg = if (editingCategoryId != null) "تم تحديث التصنيف بنجاح." else "تمت إضافة التصنيف بنجاح."
                        categoryName = ""
                        editingCategoryId = null
                        selectedParentId = null
                    } else {
                        isFeedbackError = true
                        feedbackMsg = res.exceptionOrNull()?.message ?: "تعذر حفظ التصنيف"
                    }
                }
            }
        ) {
            StatusFeedbackBanner(
                message = feedbackMsg,
                isError = isFeedbackError,
                onDismiss = { feedbackMsg = null }
            )

            FormSectionGroup(title = "1. اختر مجال التصنيف") {
                FilterChipRow(
                    items = listOf(
                        "EXPENSE" to "المصروفات",
                        "REVENUE" to "الإيرادات",
                        "PRODUCT" to "الأصناف والمخزون",
                        "CUSTOMER" to "العملاء",
                        "SUPPLIER" to "الموردين"
                    ),
                    selectedItem = selectedDomain,
                    onItemSelected = {
                        selectedDomain = it
                        selectedParentId = null
                        editingCategoryId = null
                    }
                )

                SmartTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = if (editingCategoryId != null) "تعديل اسم التصنيف" else "اسم التصنيف الجديد",
                    placeholder = "أدخل الاسم الذي يناسب نشاطك...",
                    isRequired = true,
                    imeAction = ImeAction.Done
                )

                val parentOptions = remember(domainCategories, editingCategoryId) {
                    listOf<Pair<Long?, String>>(null to "تصنيف رئيسي (بدون أب)") +
                        domainCategories
                            .filter { it.IsActive && it.Id != editingCategoryId }
                            .map { it.Id to HierarchicalCategoryValidator.formatCategoryPath(it.Id, domainCategories) }
                }

                Text("التصنيف الأب (لإنشاء شجرة فرعية متعددة المستويات):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                FilterChipRow(
                    items = parentOptions,
                    selectedItem = selectedParentId,
                    onItemSelected = { selectedParentId = it }
                )
            }

            FormSectionGroup(
                title = "2. شجرة التصنيفات الحالية (${domainCategories.size})"
            ) {
                if (domainCategories.isEmpty()) {
                    Text(
                        text = "لا توجد تصنيفات في هذا المجال بعد. أضف أول تصنيف خاص بك من الحقل أعلاه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.TextMuted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(domainCategories, key = { it.Id }) { cat ->
                            val fullPath = HierarchicalCategoryValidator.formatCategoryPath(cat.Id, domainCategories)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fullPath,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cat.IsActive) SmartLedgerColors.TextPrimary else SmartLedgerColors.TextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!cat.IsActive) {
                                        Text("معطل (محتفظ به للتقارير التاريخية)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateWarning)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingCategoryId = cat.Id
                                            categoryName = cat.Name
                                            selectedParentId = cat.ParentId
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                    }
                                    if (cat.IsActive) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val res = viewModel.deleteOrDeactivateCategoryAsync(cat.Id)
                                                    isFeedbackError = res.isFailure
                                                    feedbackMsg = res.getOrNull() ?: res.exceptionOrNull()?.message
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "تعطيل/حذف", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    viewModel.reactivateCategoryAsync(cat.Id)
                                                    isFeedbackError = false
                                                    feedbackMsg = "تمت إعادة تفعيل التصنيف «${cat.Name}»."
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("تفعيل", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, maxLines = 1)
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
}

/**
 * نافذة إدارة الصناديق والحسابات المالية والمحافظ والتحويلات الداخلية الذرية (FinancialAccountsAndTransfersSheet)
 */
