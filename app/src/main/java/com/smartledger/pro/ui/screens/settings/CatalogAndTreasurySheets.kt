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
fun FirstRunOnboardingDialog(
    defaultCurrency: String = "ر.ي",
    onComplete: (tradeName: String, currencySymbol: String, initialCashbox: String?, initialWarehouse: String?) -> Unit,
    onSkip: () -> Unit
) {
    var tradeName by rememberSaveable { mutableStateOf("") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrency) }
    var cashboxName by rememberSaveable { mutableStateOf("") }
    var warehouseName by rememberSaveable { mutableStateOf("") }

    FloatingModalDialog(onDismissRequest = onSkip) {
        FormSheetContainer(
            title = "مرحباً بك في الدفتر الذكي Pro",
            subtitle = "إعداد سريع في خطوة واحدة (اختياري بالكامل — يمكنك تخطيه أو تعديله لاحقاً)",
            headerIcon = Icons.Default.AutoAwesome,
            onDismiss = onSkip,
            primaryButtonText = "حفظ وبدء الاستخدام",
            secondaryButtonText = "تخطي الآن",
            onPrimaryClick = {
                onComplete(
                    tradeName.trim(),
                    currencySymbol.trim().ifBlank { "ر.ي" },
                    cashboxName.trim().ifBlank { null },
                    warehouseName.trim().ifBlank { null }
                )
            }
        ) {
            FormSectionGroup(title = "1. هوية نشاطك والعملة الأساسية") {
                SmartTextField(
                    value = tradeName,
                    onValueChange = { tradeName = it },
                    label = "اسم المتجر أو النشاط التجاري (اختياري)",
                    placeholder = "يظهر في ترويسة الفواتير والتقارير...",
                    leadingIcon = Icons.Default.Storefront
                )

                FilterChipRow(
                    items = listOf(
                        "ر.ي" to "ريال يمني (ر.ي)",
                        "ر.س" to "ريال سعودي (ر.س)",
                        "$" to "دولار أمريكي ($)",
                        "ج.م" to "جنيه مصري (ج.م)",
                        "د.ع" to "دينار عراقي (د.ع)"
                    ),
                    selectedItem = currencySymbol,
                    onItemSelected = { currencySymbol = it }
                )

                SmartTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = "رمز العملة المعتمد",
                    placeholder = "ر.ي"
                )
            }

            ExpandableAdvancedSection(
                title = "2. تأسيس أول صندوق ومخزن (اختياري)",
                subtitle = "اتركهما فارغين إذا كنت تفضل إضافتهما لاحقاً بأسمائك الخاصة",
                initiallyExpanded = true
            ) {
                SmartTextField(
                    value = cashboxName,
                    onValueChange = { cashboxName = it },
                    label = "اسم الصندوق النقدي أو المحفظة الأولى (اختياري)",
                    placeholder = "مثال: صندوق المحل، محفظة جوالي، كاشير 1...",
                    leadingIcon = Icons.Default.AccountBalanceWallet
                )
                SmartTextField(
                    value = warehouseName,
                    onValueChange = { warehouseName = it },
                    label = "اسم المخزن الأول (اختياري)",
                    placeholder = "مثال: معرض البيع، المستودع المركزي...",
                    leadingIcon = Icons.Default.Inventory2,
                    imeAction = ImeAction.Done
                )
            }
        }
    }
}

/**
 * نافذة إدارة التصنيفات الهرمية متعددة المستويات (CategoryHierarchyManagerSheet)
 * تدعم: المصروفات (EXPENSE)، الإيرادات (REVENUE)، الأصناف (PRODUCT)، العملاء (CUSTOMER)، الموردين (SUPPLIER)
 * مع كشف الدورات المغلقة ومنع الحذف النهائي للتصنيفات المرتبطة.
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
@Composable
fun FinancialAccountsAndTransfersSheet(
    viewModel: SettingsViewModel,
    onClose: () -> Unit
) {
    val accounts by viewModel.financialAccounts.collectAsState()
    val transfers by viewModel.recentTransfers.collectAsState()
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableStateOf("ACCOUNTS") } // ACCOUNTS, TRANSFER, RECONCILE
    var feedbackMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isFeedbackError by rememberSaveable { mutableStateOf(false) }

    // حقول إنشاء صندوق/محفظة/بنك
    var accName by rememberSaveable { mutableStateOf("") }
    var accType by rememberSaveable { mutableStateOf("CASH_BOX") }
    var accOpeningText by rememberSaveable { mutableStateOf("0") }
    var accAllowNegative by rememberSaveable { mutableStateOf(false) }
    var accIsDefault by rememberSaveable { mutableStateOf(accounts.isEmpty()) }

    // حقول التحويل الداخلي الذري
    var fromAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var transferAmountText by rememberSaveable { mutableStateOf("") }
    var transferFeeText by rememberSaveable { mutableStateOf("0") }
    var transferNotes by rememberSaveable { mutableStateOf("") }

    // حقول الجرد والمطابقة النقدية
    var reconcileAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actualCountedBalanceText by rememberSaveable { mutableStateOf("") }
    var reconcileNotes by rememberSaveable { mutableStateOf("") }

    FloatingModalDialog(onDismissRequest = onClose) {
        FormSheetContainer(
            title = "الصناديق والمحافظ والتحويلات المالية",
            subtitle = "إدارة الخزائن النقدية والمحافظ الإلكترونية والحسابات البنكية والتحويل الذري بينها",
            headerIcon = Icons.Default.AccountBalanceWallet,
            onDismiss = onClose,
            primaryButtonText = when (activeTab) {
                "ACCOUNTS" -> "حفظ الحساب المالي"
                "TRANSFER" -> "تنفيذ التحويل الداخلي"
                else -> "اعتماد المطابقة"
            },
            secondaryButtonText = "إغلاق",
            onPrimaryClick = {
                scope.launch {
                    when (activeTab) {
                        "ACCOUNTS" -> {
                            val res = viewModel.saveFinancialAccountAsync(
                                name = accName,
                                accountType = accType,
                                openingBalance = NumberFormatter.parseDouble(accOpeningText, 0.0),
                                allowNegativeBalance = accAllowNegative,
                                isDefault = accIsDefault
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم حفظ الحساب المالي «$accName» بنجاح."
                                accName = ""
                                accOpeningText = "0"
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        "TRANSFER" -> {
                            val res = viewModel.createInternalTransferAsync(
                                fromAccountId = fromAccountId ?: 0L,
                                toAccountId = toAccountId ?: 0L,
                                amount = NumberFormatter.parseDouble(transferAmountText, 0.0),
                                feeAmount = NumberFormatter.parseDouble(transferFeeText, 0.0),
                                notes = transferNotes
                            )
                            if (res.isSuccess) {
                                isFeedbackError = false
                                feedbackMsg = "تم تنفيذ التحويل الداخلي الذري بنجاح دون التأثير على قائمة الدخل."
                                transferAmountText = ""
                                transferFeeText = "0"
                                transferNotes = ""
                            } else {
                                isFeedbackError = true
                                feedbackMsg = res.exceptionOrNull()?.message
                            }
                        }
                        "RECONCILE" -> {
                            val res = viewModel.reconcileFinancialAccountAsync(
                                accountId = reconcileAccountId ?: 0L,
                                actualBalance = NumberFormatter.parseDouble(actualCountedBalanceText, 0.0),
                                notes = reconcileNotes
                            )
                            if (res.isSuccess) {
                                val delta = res.getOrDefault(0.0)
                                isFeedbackError = false
                                feedbackMsg = if (kotlin.math.abs(delta) < 0.001) {
                                    "الرصيد الفعلي مطابق تماماً للرصيد الدفتري (لا يوجد فارق)."
                                } else {
                                    "تم تسجيل قيد تسوية المطابقة بفارق (${NumberFormatter.formatCurrency(delta, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})."
                                }
                                actualCountedBalanceText = ""
                                reconcileNotes = ""
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
                    "ACCOUNTS" to "الصناديق والمحافظ",
                    "TRANSFER" to "تحويل داخلي",
                    "RECONCILE" to "جرد ومطابقة"
                ),
                selectedOption = activeTab,
                onOptionSelected = { activeTab = it }
            )

            when (activeTab) {
                "ACCOUNTS" -> {
                    FormSectionGroup(title = "1. تعريف صندوق أو محفظة أو حساب بنكي جديد") {
                        FilterChipRow(
                            items = listOf(
                                "CASH_BOX" to "صندوق نقدي",
                                "E_WALLET" to "محفظة إلكترونية",
                                "BANK_ACCOUNT" to "حساب بنكي",
                                "POS_TERMINAL" to "شبكة / نقطة بيع"
                            ),
                            selectedItem = accType,
                            onItemSelected = { accType = it }
                        )
                        SmartTextField(
                            value = accName,
                            onValueChange = { accName = it },
                            label = "اسم الصندوق أو المحفظة أو البنك",
                            placeholder = "أدخل الاسم الذي تختاره...",
                            isRequired = true
                        )
                        SmartTextField(
                            value = accOpeningText,
                            onValueChange = { accOpeningText = it },
                            label = "الرصيد الافتتاحي الموثق (اختياري)",
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        )
                    }

                    FormSectionGroup(title = "2. أرصدة الصناديق والمحافظ الحية (${accounts.size})") {
                        if (accounts.isEmpty()) {
                            Text("لم يتم تعريف صناديق أو محافظ بعد.", style = MaterialTheme.typography.bodySmall, color = SmartLedgerColors.TextMuted)
                        } else {
                            accounts.forEach { acc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SmartLedgerColors.SurfaceCardElevated)
                                        .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.Name, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "وارد: ${NumberFormatter.formatCurrency(acc.TotalInflows, acc.CurrencySymbol)} • صادر: ${NumberFormatter.formatCurrency(acc.TotalOutflows, acc.CurrencySymbol)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextMuted
                                        )
                                    }
                                    Text(
                                        text = NumberFormatter.formatCurrency(acc.NetBalance, acc.CurrencySymbol, AppDesignState.decimalPlaces),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (acc.NetBalance >= 0.0) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                    )
                                }
                            }
                        }
                    }
                }

                "TRANSFER" -> {
                    val accountChips = remember(accounts) {
                        accounts.filter { it.IsActive }.map { it.Id as Long? to "${it.Name} (${NumberFormatter.formatCurrency(it.NetBalance, it.CurrencySymbol)})" }
                    }
                    FormSectionGroup(title = "تحويل داخلي ذري بين حسابين ماليين") {
                        if (accountChips.size < 2) {
                            Text("يرجى تعريف حسابين ماليين على الأقل (مثلاً صندوق ومحفظة) لتنفيذ التحويلات الداخلية.", color = SmartLedgerColors.StateWarning, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("من حساب (المصدر):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            FilterChipRow(
                                items = accountChips,
                                selectedItem = fromAccountId,
                                onItemSelected = { fromAccountId = it }
                            )
                            Text("إلى حساب (الوجهة):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            FilterChipRow(
                                items = accountChips,
                                selectedItem = toAccountId,
                                onItemSelected = { toAccountId = it }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmartTextField(
                                    value = transferAmountText,
                                    onValueChange = { transferAmountText = it },
                                    label = "مبلغ التحويل",
                                    isRequired = true,
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                                SmartTextField(
                                    value = transferFeeText,
                                    onValueChange = { transferFeeText = it },
                                    label = "عمولة/رسوم التحويل",
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SmartTextField(
                                value = transferNotes,
                                onValueChange = { transferNotes = it },
                                label = "بيان التحويل (اختياري)",
                                placeholder = "مثال: إيداع نقدي من الصندوق إلى المحفظة...",
                                imeAction = ImeAction.Done
                            )
                        }
                        if (transfers.isNotEmpty()) {
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            Text("أحدث التحويلات الداخلية (${transfers.size}):", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            transfers.take(5).forEach { tr ->
                                Text(
                                    text = "• ${tr.Notes ?: "تحويل داخلي"}: ${NumberFormatter.formatCurrency(tr.Amount, AppDesignState.currencySymbol)} (${tr.OccurredAt.take(16)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }
                    }
                }

                "RECONCILE" -> {
                    val accountChips = remember(accounts) {
                        accounts.filter { it.IsActive }.map { it.Id as Long? to "${it.Name} (الدفتري: ${NumberFormatter.formatCurrency(it.NetBalance, it.CurrencySymbol)})" }
                    }
                    FormSectionGroup(title = "جرد ومطابقة الرصيد الفعلي للصندوق أو المحفظة") {
                        FilterChipRow(
                            items = accountChips,
                            selectedItem = reconcileAccountId,
                            onItemSelected = { reconcileAccountId = it }
                        )
                        SmartTextField(
                            value = actualCountedBalanceText,
                            onValueChange = { actualCountedBalanceText = it },
                            label = "الرصيد الفعلي الموجود حالياً بعد الجرد",
                            isRequired = true,
                            keyboardType = KeyboardType.Decimal
                        )
                        SmartTextField(
                            value = reconcileNotes,
                            onValueChange = { reconcileNotes = it },
                            label = "سبب الفارق أو ملاحظة المطابقة (اختياري)",
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        }
    }
}

/**
 * نافذة إدارة الوحدات المرنة والمخازن المتعددة والدفاتر الحسابية (UnitsAndWarehousesManagerSheet)
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
