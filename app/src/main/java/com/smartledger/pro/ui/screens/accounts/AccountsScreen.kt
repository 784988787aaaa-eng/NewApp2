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
fun AccountsScreen(
    privacyMode: Boolean,
    onNavigateToVouchers: () -> Unit = {},
    viewModel: AccountsViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val books by viewModel.books.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val balanceFilter by viewModel.balanceFilter.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val financialAccounts by viewModel.financialAccounts.collectAsState()
    var whatsAppTargetAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val whatsAppTargetAccount = remember(accounts, whatsAppTargetAccountId) { whatsAppTargetAccountId?.let { id -> accounts.firstOrNull { it.PersonId == id } } }
    val scope = rememberCoroutineScope()

    var showAddOrEditDialog by rememberSaveable { mutableStateOf(false) }
    var editingAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAccountIdForStatement by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickVoucherPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickVoucherInitialReceipt by rememberSaveable { mutableStateOf(true) }
    var accountIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val accountToDelete = remember(accounts, accountIdToDelete) { accountIdToDelete?.let { id -> accounts.firstOrNull { it.PersonId == id } } }
    var lastArchivedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastArchivedAccountName by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStatusError by rememberSaveable { mutableStateOf(false) }

    val editingAccount = remember(accounts, editingAccountId) {
        editingAccountId?.let { id -> accounts.firstOrNull { it.PersonId == id } }
    }
    val selectedAccountForStatement = remember(accounts, selectedAccountIdForStatement) {
        selectedAccountIdForStatement?.let { id -> accounts.firstOrNull { it.PersonId == id } }
    }
    val quickVoucherTargetAccount = remember(accounts, quickVoucherPersonId) {
        quickVoucherPersonId?.let { id -> accounts.firstOrNull { it.PersonId == id } }
    }

    val context = LocalContext.current
    val bookNameMap = remember(books) { books.associate { it.Id to it.Name } }

    val safeCallPhone: (String?) -> Unit = { phone ->
        if (phone.isNullOrBlank()) {
            isStatusError = true
            statusMessage = "لم يتم تسجيل رقم هاتف لهذا الحساب بعد. يمكنك إضافته من خيار تعديل الحساب."
        } else {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
            } catch (_: Exception) {
                isStatusError = true
                statusMessage = "تعذر فتح تطبيق الاتصال على هذا الجهاز."
            }
        }
    }

    val safeShareWhatsApp: (AccountBalanceSummary) -> Unit = { accItem ->
        whatsAppTargetAccountId = accItem.PersonId
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الحسابات والدفاتر",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${accounts.size} حساب نشط | كشوف الحساب والأرصدة",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onNavigateToVouchers,
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("السندات", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingAccountId = null
                                showAddOrEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حساب جديد", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "ابحث باسم العميل/المورد، رقم الهاتف، أو الكود...",
                    resultCount = accounts.size
                )

                val bookChips = remember(books) {
                    listOf<Pair<Long?, String>>(null to "كل الدفاتر") + books.map { it.Id to it.Name }
                }
                FilterChipRow(
                    items = bookChips,
                    selectedItem = selectedBookId,
                    onItemSelected = { viewModel.onBookSelected(it) }
                )

                SegmentedOptionRow(
                    options = listOf(
                        "ALL" to "الكل",
                        "DEBIT" to "عليه / لنا (مدين)",
                        "CREDIT" to "له / علينا (دائن)"
                    ),
                    selectedOption = balanceFilter,
                    onOptionSelected = { viewModel.onBalanceFilterChanged(it) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.itemSpacing)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusMessage,
                    isError = isStatusError,
                    actionLabel = if (lastArchivedAccountId != null) "تراجع عن الأرشفة" else null,
                    onAction = if (lastArchivedAccountId != null) {
                        {
                            val targetId = lastArchivedAccountId!!
                            val restoredName = lastArchivedAccountName ?: "الحساب"
                            viewModel.restoreAccount(targetId) {
                                lastArchivedAccountId = null
                                lastArchivedAccountName = null
                                isStatusError = false
                                statusMessage = "تمت استعادة الحساب «$restoredName» إلى القائمة النشطة."
                            }
                        }
                    } else null,
                    onDismiss = {
                        statusMessage = null
                        lastArchivedAccountId = null
                        lastArchivedAccountName = null
                    }
                )
            }

            if (accounts.isEmpty()) {
                item {
                    val isFiltered = searchQuery.isNotBlank() || selectedBookId != null || balanceFilter != "ALL"
                    SmartEmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "لا توجد حسابات مسجلة في هذا الدفتر",
                        subtitle = "أضف حسابات العملاء والموردين لمتابعة الأرصدة وإصدار كشوف الحساب والسندات.",
                        actionText = "تعريف حساب جديد",
                        onActionClick = {
                            editingAccountId = null
                            showAddOrEditDialog = true
                        },
                        isFiltered = isFiltered,
                        onClearFilters = { viewModel.clearFilters() }
                    )
                }
            } else {
                items(accounts, key = { it.PersonId }) { account ->
                    AccountCardItem(
                        account = account,
                        bookName = bookNameMap[account.BookId] ?: "دفتر عام",
                        privacyMode = privacyMode,
                        onOpenStatement = { selectedAccountIdForStatement = account.PersonId },
                        onQuickReceipt = {
                            quickVoucherPersonId = account.PersonId
                            quickVoucherInitialReceipt = true
                        },
                        onQuickPayment = {
                            quickVoucherPersonId = account.PersonId
                            quickVoucherInitialReceipt = false
                        },
                        onEdit = {
                            editingAccountId = account.PersonId
                            showAddOrEditDialog = true
                        },
                        onCall = { safeCallPhone(account.Phone) },
                        onShare = { safeShareWhatsApp(account) },
                        onDelete = { accountIdToDelete = account.PersonId }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // نافذة كشف الحساب العائمة
        if (selectedAccountForStatement != null) {
            val liveAccount = selectedAccountForStatement
            FloatingModalDialog(onDismissRequest = { selectedAccountIdForStatement = null }) {
                AccountStatementSheetContent(
                    account = liveAccount,
                    bookName = bookNameMap[liveAccount.BookId] ?: "دفتر عام",
                    privacyMode = privacyMode,
                    viewModel = viewModel,
                    onQuickVoucher = { isReceipt ->
                        selectedAccountIdForStatement = null
                        quickVoucherPersonId = liveAccount.PersonId
                        quickVoucherInitialReceipt = isReceipt
                    },
                    onOperationFeedback = { msg, isErr ->
                        lastArchivedAccountId = null
                        isStatusError = isErr
                        statusMessage = msg
                    },
                    onShareStatement = { safeShareWhatsApp(liveAccount) },
                    onCall = { safeCallPhone(liveAccount.Phone) },
                    onClose = { selectedAccountIdForStatement = null }
                )
            }
        }

        // نافذة سند قبض/صرف سريع العائمة فوق لوحة المفاتيح مع منع الإغلاق قبل نجاح الحفظ
        if (quickVoucherTargetAccount != null) {
            val targetAcc = quickVoucherTargetAccount
            val openInvsFlow = remember(targetAcc.PersonId) { viewModel.getOpenInvoicesForAccount(targetAcc.PersonId) }
            val openInvoices by openInvsFlow.collectAsState(initial = emptyList())
            FloatingModalDialog(onDismissRequest = { quickVoucherPersonId = null }) {
                var isSavingVoucher by remember { mutableStateOf(false) }
                var voucherError by remember { mutableStateOf<String?>(null) }

                QuickAccountVoucherSheet(
                    account = targetAcc,
                    initialIsReceipt = quickVoucherInitialReceipt,
                    financialAccounts = financialAccounts,
                    openInvoices = openInvoices,
                    isSaving = isSavingVoucher,
                    errorMessage = voucherError,
                    onDismiss = { quickVoucherPersonId = null },
                    onConfirm = { isReceipt, amount, statement, refNo, finAccId, targetInvId ->
                        isSavingVoucher = true
                        voucherError = null
                        scope.launch {
                            val res = viewModel.createQuickVoucherAsync(
                                personId = targetAcc.PersonId,
                                type = if (isReceipt) "Receive" else "Payment",
                                amount = amount,
                                statement = statement,
                                refNumber = refNo,
                                financialAccountId = finAccId,
                                targetInvoiceId = targetInvId
                            )
                            isSavingVoucher = false
                            if (res.isSuccess) {
                                quickVoucherPersonId = null
                                lastArchivedAccountId = null
                                isStatusError = false
                                statusMessage = "تم ترحيل ${if (isReceipt) "سند القبض" else "سند الصرف"} لحساب «${targetAcc.PersonName}» بنجاح."
                            } else {
                                voucherError = res.exceptionOrNull()?.message ?: "تعذر حفظ السند؛ يرجى التحقق والمحاولة مرة أخرى."
                            }
                        }
                    }
                )
            }
        }

        // نافذة إضافة أو تعديل حساب العائمة فوق لوحة المفاتيح مع حماية كاملة للبيانات
        if (showAddOrEditDialog) {
            FloatingModalDialog(onDismissRequest = { showAddOrEditDialog = false }) {
                var isSavingAccount by remember { mutableStateOf(false) }
                var accountSaveError by remember { mutableStateOf<String?>(null) }

                AddEditAccountDialog(
                    books = books,
                    defaultBookId = selectedBookId ?: 1L,
                    existingAccount = editingAccount,
                    isSaving = isSavingAccount,
                    errorMessage = accountSaveError,
                    onDismiss = { showAddOrEditDialog = false },
                    onSave = { id, name, bookId, phone, limit, opening, isDebit, note ->
                        isSavingAccount = true
                        accountSaveError = null
                        scope.launch {
                            val res = viewModel.saveAccountAsync(
                                id = id,
                                name = name,
                                bookId = bookId,
                                phone = phone,
                                creditLimit = limit,
                                openingBalance = opening,
                                isDebit = isDebit,
                                note = note
                            )
                            isSavingAccount = false
                            if (res.isSuccess) {
                                showAddOrEditDialog = false
                                lastArchivedAccountId = null
                                isStatusError = false
                                statusMessage = if (id > 0) "تم تحديث بيانات الحساب «$name» بنجاح." else "تم فتح الحساب «$name» بنجاح."
                            } else {
                                accountSaveError = res.exceptionOrNull()?.message ?: "تعذر حفظ بيانات الحساب؛ يرجى التحقق من المدخلات."
                            }
                        }
                    }
                )
            }
        }

        if (whatsAppTargetAccount != null) {
            val targetAcc = whatsAppTargetAccount!!
            val opsFlow = remember(targetAcc.PersonId) { viewModel.getOperationsForAccount(targetAcc.PersonId) }
            val recentOps by opsFlow.collectAsState(initial = emptyList())
            WhatsAppPreviewDialog(
                account = targetAcc,
                businessName = identity?.TradeName ?: "نشاطي التجاري",
                defaultCountryCode = settings.defaultCountryCode,
                recentOperations = recentOps,
                onDismiss = { whatsAppTargetAccountId = null }
            )
        }

        // حوار تأكيد أرشفة الحساب
        if (accountToDelete != null) {
            val target = accountToDelete!!
            ConfirmActionDialog(
                title = "أرشفة حساب «${target.PersonName}»؟",
                message = "سيتم نقل الحساب إلى الأرشيف وإخفاؤه من القائمة النشطة مع الاحتفاظ بجميع قيوده وفواتيره السابقة. يمكنك استعادته فوراً أو من سلة المحذوفات في الإعدادات.",
                confirmText = "أرشفة الحساب",
                requireReason = true,
                onConfirm = { reason ->
                    lastArchivedAccountId = target.PersonId
                    lastArchivedAccountName = target.PersonName
                    viewModel.deleteAccount(target.PersonId, reason)
                    accountIdToDelete = null
                    isStatusError = false
                    statusMessage = "تمت أرشفة الحساب «${target.PersonName}»."
                },
                onDismiss = { accountIdToDelete = null }
            )
        }
    }
}

@Composable
fun AccountCardItem(
    account: AccountBalanceSummary,
    bookName: String,
    privacyMode: Boolean,
    onOpenStatement: () -> Unit,
    onQuickReceipt: () -> Unit,
    onQuickPayment: () -> Unit,
    onEdit: () -> Unit,
    onCall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDebit = account.NetBalance > 0.001
    val isCredit = account.NetBalance < -0.001
    val overCreditLimit = AppDesignState.warnOnCreditLimit && account.CreditLimit > 0.0 && account.NetBalance > account.CreditLimit

    val balanceColor = when {
        isDebit -> SmartLedgerColors.StateDanger
        isCredit -> SmartLedgerColors.StateSuccess
        else -> SmartLedgerColors.TextMuted
    }
    val balanceLabel = when {
        isDebit -> "عليه / مستحق لنا (مدين)"
        isCredit -> "له / مستحق علينا (دائن)"
        else -> "رصيد متزن"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onOpenStatement() },
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (overCreditLimit) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderSubtle
            )
        ),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(modifier = Modifier.padding(SmartLedgerTokens.cardInternalPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SmartLedgerColors.AccentSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.PersonName.trim().take(1).ifBlank { "ح" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = account.PersonName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SmartLedgerColors.SurfaceCardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = bookName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.AccentLight
                                )
                            }
                            if (AppDesignState.showPhoneInAccounts && !account.Phone.isNullOrBlank()) {
                                Text(
                                    text = account.Phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = NumberFormatter.formatCurrency(
                                abs(account.NetBalance),
                                AppDesignState.currencySymbol,
                                AppDesignState.decimalPlaces,
                                privacyMode
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                        Text(
                            text = balanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = balanceColor
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات الحساب",
                                tint = SmartLedgerColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(SmartLedgerColors.SurfacePanel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("كشف الحساب التفصيلي", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, null, tint = SmartLedgerColors.AccentLight) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenStatement()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تعديل بيانات الحساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = SmartLedgerColors.TextSecondary) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة الرصيد عبر واتساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = SmartLedgerColors.StateSuccess) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                            if (!account.Phone.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("اتصال هاتفي", color = SmartLedgerColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = SmartLedgerColors.AccentLight) },
                                    onClick = {
                                        menuExpanded = false
                                        onCall()
                                    }
                                )
                            }
                            Divider(color = SmartLedgerColors.BorderSubtle)
                            DropdownMenuItem(
                                text = { Text("أرشفة الحساب", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = SmartLedgerColors.StateDanger) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (overCreditLimit) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SmartLedgerColors.StateWarningSubtle)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تجاوز سقف الائتمان المسموح (${NumberFormatter.formatCurrency(account.CreditLimit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarningText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط الإجراءات السريعة المباشرة على البطاقة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQuickReceipt,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateSuccessBorder)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("قبض (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onQuickPayment,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("صرف (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDanger, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenStatement,
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .pressScaleEffect(),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الكشف", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextPrimary)
                }

                if (AppDesignState.showPhoneInAccounts && !account.Phone.isNullOrBlank()) {
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "اتصال بالحساب", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AccountStatementSheetContent(
    account: AccountBalanceSummary,
    bookName: String,
    privacyMode: Boolean,
    viewModel: AccountsViewModel,
    onQuickVoucher: (isReceipt: Boolean) -> Unit,
    onOperationFeedback: (message: String, isError: Boolean) -> Unit = { _, _ -> },
    onShareStatement: () -> Unit,
    onCall: () -> Unit,
    onClose: () -> Unit
) {
    val operationsFlow = remember(account.PersonId) { viewModel.getOperationsForAccount(account.PersonId) }
    val allOperations by operationsFlow.collectAsState(initial = emptyList())
    var periodFilter by rememberSaveable { mutableStateOf("ALL") }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var operationIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val operationToDelete = remember(allOperations, operationIdToDelete) { operationIdToDelete?.let { id -> allOperations.firstOrNull { it.Id == id } } }

    val operationsWithRunningBalance = remember(allOperations) {
        val chronological = allOperations.sortedWith(compareBy<OperationEntity> { it.OccurredAt }.thenBy { it.Id })
        var running = 0.0
        chronological.map { op ->
            running += LedgerDirectionPolicy.signedOperationDelta(op.Type, op.Amount)
            Pair(op, running)
        }.reversed()
    }

    val filteredOperations = remember(operationsWithRunningBalance, periodFilter, typeFilter) {
        val today = NumberFormatter.todayDate()
        val monthPrefix = today.take(7)
        operationsWithRunningBalance.filter { (op, _) ->
            val matchesPeriod = when (periodFilter) {
                "TODAY" -> op.OccurredAt.startsWith(today)
                "MONTH" -> op.OccurredAt.startsWith(monthPrefix)
                else -> true
            }
            val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
            val matchesType = when (typeFilter) {
                "DEBIT" -> !isCredit
                "CREDIT" -> isCredit
                else -> true
            }
            matchesPeriod && matchesType
        }
    }

    FormSheetContainer(
        title = account.PersonName,
        subtitle = "$bookName • الهاتف: ${account.Phone ?: "غير مسجل"}",
        headerIcon = Icons.Default.AccountBalanceWallet,
        onDismiss = onClose,
        primaryButtonText = "إرسال كشف واتساب / مشاركة",
        secondaryButtonText = "إغلاق",
        onPrimaryClick = onShareStatement
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي مدين (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDangerText)
                    Text(
                        NumberFormatter.formatCurrency(account.TotalDebit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateDanger
                    )
                }
                Box(modifier = Modifier.width(1.dp).heightIn(min = 32.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي دائن (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccessText)
                    Text(
                        NumberFormatter.formatCurrency(account.TotalCredit, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                }
                Box(modifier = Modifier.width(1.dp).heightIn(min = 32.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (account.NetBalance >= 0) "الصافي لنا (عليه)" else "الصافي علينا (له)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        NumberFormatter.formatCurrency(abs(account.NetBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (account.NetBalance >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onQuickVoucher(true) },
                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateSuccess),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند قبض (له)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onQuickVoucher(false) },
                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SmartLedgerColors.TextInverse, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند صرف (عليه)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextInverse, fontWeight = FontWeight.Bold)
            }
            if (!account.Phone.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.heightIn(min = 38.dp).pressScaleEffect(),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(15.dp))
                }
            }
        }

        FilterChipRow(
            items = listOf(
                "ALL" to "كل الفترات (${allOperations.size})",
                "TODAY" to "اليوم",
                "MONTH" to "هذا الشهر"
            ),
            selectedItem = periodFilter,
            onItemSelected = { periodFilter = it }
        )

        SegmentedOptionRow(
            options = listOf(
                "ALL" to "جميع الحركات",
                "DEBIT" to "مدين (عليه +)",
                "CREDIT" to "دائن (له -)"
            ),
            selectedOption = typeFilter,
            onOptionSelected = { typeFilter = it }
        )

        if (filteredOperations.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد حركات مطابقة للفترة أو النوع المحدد.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredOperations, key = { it.first.Id }) { (op, runningBalance) ->
                    val isCredit = LedgerDirectionPolicy.isCreditOperation(op.Type)
                    val isAlreadyReversed = op.ReferenceNumber?.startsWith("REV-OP-") == true ||
                        allOperations.any { it.ReferenceNumber == "REV-OP-${op.Id}" }
                    val typeTitle = when (op.Type) {
                        "Receive" -> "سند قبض"
                        "Payment" -> "سند صرف"
                        "Sale" -> "فاتورة مبيعات آجل"
                        "Purchase" -> "فاتورة مشتريات آجل"
                        "SaleReturn" -> "مرتجع مبيعات"
                        "PurchaseReturn" -> "مرتجع مشتريات"
                        "DebtOnUs" -> "رصيد افتتاحي (مدين)"
                        "DebtToUs" -> "رصيد افتتاحي (دائن)"
                        else -> op.Type
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = op.Note ?: typeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Text(
                                text = "$typeTitle • ${op.OccurredAt.take(16)}" +
                                    if (!op.ReferenceNumber.isNullOrBlank()) " • #${op.ReferenceNumber}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (isCredit) "-" else "+"}${NumberFormatter.formatCurrency(op.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                )
                                Text(
                                    text = "الرصيد: ${NumberFormatter.formatCurrency(abs(runningBalance), AppDesignState.currencySymbol, AppDesignState.decimalPlaces, privacyMode)} ${if (runningBalance >= 0) "لنا" else "علينا"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                            if (!isAlreadyReversed) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { operationIdToDelete = op.Id },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "إلغاء بقيد عكسي", tint = SmartLedgerColors.StateWarning, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (operationToDelete != null) {
        val targetOp = operationToDelete!!
        ConfirmActionDialog(
            title = "إلغاء الحركة بقيد عكسي محاسبي؟",
            message = "حفاظاً على سلامة السجل المحاسبي، لا يتم مسح الحركة الأصلية، بل سيتم إدراج قيد عكسي موثق بقيمة ${NumberFormatter.formatCurrency(targetOp.Amount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} لتصحيح رصيد الحساب فوراً مع توثيق سبب الإلغاء.",
            confirmText = "اعتماد القيد العكسي",
            requireReason = true,
            reasonLabel = "سبب الإلغاء المحاسبي",
            onConfirm = { reason ->
                viewModel.deleteOperation(targetOp.Id, reason) { res ->
                    operationIdToDelete = null
                    if (res.isSuccess) {
                        onOperationFeedback("تم إدراج القيد العكسي للحركة #${targetOp.Id} وتصحيح رصيد الحساب بنجاح.", false)
                    } else {
                        onOperationFeedback(res.exceptionOrNull()?.message ?: "تعذر عكس الحركة المالية.", true)
                    }
                }
            },
            onDismiss = { operationIdToDelete = null }
        )
    }
}

@Composable
fun QuickAccountVoucherSheet(
    account: AccountBalanceSummary,
    initialIsReceipt: Boolean,
    financialAccounts: List<com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary> = emptyList(),
    openInvoices: List<com.smartledger.pro.data.local.entity.InvoiceEntity> = emptyList(),
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (isReceipt: Boolean, amount: Double, statement: String, refNumber: String?, financialAccountId: Long?, targetInvoiceId: Long?) -> Unit
) {
    var isReceipt by rememberSaveable(account.PersonId) { mutableStateOf(initialIsReceipt) }
    var amountText by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var statement by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var refNumber by rememberSaveable(account.PersonId) { mutableStateOf("") }
    var selectedFinAccountId by rememberSaveable(account.PersonId) { mutableStateOf<Long?>(null) }
    var selectedTargetInvoiceId by rememberSaveable(account.PersonId) { mutableStateOf<Long?>(null) }

    val focusStatement = remember { FocusRequester() }
    val focusRef = remember { FocusRequester() }

    val amountVal = NumberFormatter.parseDouble(amountText, 0.0)
    val isValid = amountVal > 0.0
    val currentNet = account.NetBalance
    val projectedNet = if (isReceipt) (currentNet - amountVal) else (currentNet + amountVal)

    FormSheetContainer(
        title = if (isReceipt) "سند قبض سريع (له)" else "سند صرف سريع (عليه)",
        subtitle = "الحساب: ${account.PersonName} • الرصيد الحالي: ${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)}",
        headerIcon = Icons.Default.ReceiptLong,
        onDismiss = onDismiss,
        primaryButtonText = if (isReceipt) "ترحيل سند القبض" else "ترحيل سند الصرف",
        primaryButtonColor = if (isReceipt) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
        isPrimaryEnabled = isValid,
        isSaving = isSaving,
        errorMessage = errorMessage,
        hasUnsavedChanges = amountText.isNotBlank() || statement.isNotBlank() || refNumber.isNotBlank(),
        onPrimaryClick = {
            if (isValid) {
                val finalStatement = statement.trim().ifBlank {
                    if (isReceipt) "سند قبض نقدية من ${account.PersonName}" else "سند صرف نقدية إلى ${account.PersonName}"
                }
                onConfirm(isReceipt, amountVal, finalStatement, refNumber.trim().ifBlank { null }, selectedFinAccountId, selectedTargetInvoiceId)
            }
        }
    ) {
        SegmentedOptionRow(
            options = listOf(
                true to "سند قبض / استلام (له)",
                false to "سند صرف / سداد (عليه)"
            ),
            selectedOption = isReceipt,
            onOptionSelected = { isReceipt = it },
            activeColor = if (isReceipt) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
        )

        // بطاقة المعاينة المالية الحية للرصيد قبل وبعد السند (UX-002 Financial Trust)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الرصيد قبل السند", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            text = "${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${
                                when {
                                    currentNet > 0.001 -> "(عليه)"
                                    currentNet < -0.001 -> "(له)"
                                    else -> "(متزن)"
                                }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentNet >= 0) SmartLedgerColors.StateDanger else SmartLedgerColors.StateSuccess
                        )
                    }
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الرصيد المتوقع بعد الترحيل", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                        Text(
                            text = "${NumberFormatter.formatCurrency(abs(projectedNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)} ${
                                when {
                                    projectedNet > 0.001 -> "(عليه)"
                                    projectedNet < -0.001 -> "(له)"
                                    else -> "(مسدد بالكامل)"
                                }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                projectedNet > 0.001 -> SmartLedgerColors.StateDanger
                                projectedNet < -0.001 -> SmartLedgerColors.StateSuccess
                                else -> SmartLedgerColors.AccentLight
                            }
                        )
                    }
                }

                if (abs(currentNet) > 0.001) {
                    OutlinedButton(
                        onClick = {
                            isReceipt = currentNet > 0.0
                            amountText = NumberFormatter.formatQuantity(abs(currentNet))
                            if (statement.isBlank()) {
                                statement = if (currentNet > 0.0) {
                                    "تصفية وسداد كامل الرصيد المستحق من ${account.PersonName}"
                                } else {
                                    "تصفية وسداد كامل الرصيد الدائن إلى ${account.PersonName}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).pressScaleEffect(),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تعبئة وتصفية كامل الرصيد (${NumberFormatter.formatCurrency(abs(currentNet), AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        FormSectionGroup(title = "تفاصيل السند المالي") {
            if (financialAccounts.isNotEmpty()) {
                FilterChipRow(
                    items = listOf<Pair<Long?, String>>(null to "الصندوق الافتراضي") + financialAccounts.map { it.Id as Long? to it.Name },
                    selectedItem = selectedFinAccountId,
                    onItemSelected = { selectedFinAccountId = it }
                )
            }
            if (openInvoices.isNotEmpty()) {
                FilterChipRow(
                    items = listOf<Pair<Long?, String>>(null to "دفعة عامة على الحساب") +
                        openInvoices.map { it.Id as Long? to "${it.InvoiceNumber} (${NumberFormatter.formatCurrency(it.RemainingAmount, AppDesignState.currencySymbol, AppDesignState.decimalPlaces)})" },
                    selectedItem = selectedTargetInvoiceId,
                    onItemSelected = { invId ->
                        selectedTargetInvoiceId = invId
                        val chosen = openInvoices.firstOrNull { it.Id == invId }
                        if (chosen != null && amountText.isBlank()) {
                            amountText = NumberFormatter.formatQuantity(chosen.RemainingAmount)
                            if (statement.isBlank()) statement = "سداد فاتورة #${chosen.InvoiceNumber}"
                        }
                    }
                )
            }
            SmartTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "مبلغ السند (${AppDesignState.currencySymbol})",
                placeholder = "0",
                isRequired = true,
                keyboardType = KeyboardType.Decimal,
                autoFocusOnAppear = amountText.isBlank(),
                imeAction = ImeAction.Next,
                nextFocusRequester = focusStatement
            )

            SmartTextField(
                value = statement,
                onValueChange = { statement = it },
                label = "البيان والشرح المحاسبي",
                placeholder = if (isReceipt) "دفعة نقدية من الحساب..." else "دفعة مسددة للحساب...",
                focusRequester = focusStatement,
                nextFocusRequester = focusRef,
                imeAction = ImeAction.Next
            )

            SmartTextField(
                value = refNumber,
                onValueChange = { refNumber = it },
                label = "رقم السند الورقي / الحوالة (اختياري)",
                placeholder = "مثال: RV-1042",
                focusRequester = focusRef,
                imeAction = ImeAction.Done
            )
        }
    }
}

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
        title = if (isEditing) "تعديل بيانات الحساب" else "فتح حساب مالي جديد",
        subtitle = "تحديد الدفتر وبيانات التواصل والرصيد الافتتاحي وسقف الائتمان",
        headerIcon = Icons.Default.PersonAdd,
        onDismiss = onDismiss,
        primaryButtonText = if (isEditing) "حفظ التعديلات" else "اعتماد وفتح الحساب",
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
        FormSectionGroup(title = "1. الدفتر والاسم والاتصال") {
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
            FormSectionGroup(title = "2. الرصيد الافتتاحي التأسيسي (اختياري)") {
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

        FormSectionGroup(title = if (isEditing) "2. الضوابط الائتمانية والملاحظات" else "3. الضوابط الائتمانية والملاحظات") {
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
