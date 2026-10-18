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
    createAction: String? = null,
    onCreateActionConsumed: () -> Unit = {},
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
    LaunchedEffect(createAction) {
        if (createAction == "account") {
            editingAccountId = null
            showAddOrEditDialog = true
            onCreateActionConsumed()
        }
    }
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
