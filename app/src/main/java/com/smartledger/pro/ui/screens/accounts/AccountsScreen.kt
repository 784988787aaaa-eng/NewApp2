package com.smartledger.pro.ui.screens.accounts

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.dao.AccountBalanceSummary
import com.smartledger.pro.data.local.entity.BookEntity
import com.smartledger.pro.data.local.entity.OperationEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.AccountsViewModel
import com.smartledger.pro.util.AppSettingsManager
import com.smartledger.pro.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    privacyMode: Boolean,
    onOpenVoucherForAccount: ((accountId: Long, isReceipt: Boolean) -> Unit)? = null,
    viewModel: AccountsViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val books by viewModel.books.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val balanceFilter by viewModel.balanceFilter.collectAsState()
    val settings by AppSettingsManager.settings.collectAsState()

    var showAddOrEditSheet by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountBalanceSummary?>(null) }
    var selectedAccountForStatement by remember { mutableStateOf<AccountBalanceSummary?>(null) }
    var accountPendingDelete by remember { mutableStateOf<AccountBalanceSummary?>(null) }
    var quickVoucherTarget by remember { mutableStateOf<Pair<AccountBalanceSummary, Boolean>?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val booksMap = remember(books) { books.associate { it.Id to it.Name } }

    val bookTabs = remember(books) {
        buildList {
            add(FilterTabItem<Long?>(null, "الكل"))
            books.forEach { b -> add(FilterTabItem(b.Id, b.Name)) }
        }
    }

    val balanceStatusTabs = remember {
        listOf(
            FilterTabItem("ALL", "جميع الأرصدة"),
            FilterTabItem("DEBIT", "لنا (مدينون / عليه)"),
            FilterTabItem("CREDIT", "علينا (دائنون / له)"),
            FilterTabItem("SETTLED", "متزن (0)")
        )
    }

    val safeDialPhone: (String?) -> Unit = { phone ->
        val clean = phone?.trim().orEmpty()
        if (clean.isEmpty()) {
            feedbackBanner = "لا يوجد رقم هاتف مسجل لهذا الحساب. يمكنك إضافة الرقم من «تعديل الحساب»."
        } else {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
            } catch (_: Exception) {
                feedbackBanner = "تعذر فتح تطبيق الاتصال على هذا الجهاز."
            }
        }
    }

    val safeShareStatement: (AccountBalanceSummary) -> Unit = { account ->
        val statusDesc = when {
            account.NetBalance > 0.001 -> "مستحق لنا عليكم (مدين): ${Formatters.formatCurrency(account.NetBalance, settings.currencySymbol, settings.decimalPlaces)}"
            account.NetBalance < -0.001 -> "رصيد دائن لكم (علينا): ${Formatters.formatCurrency(kotlin.math.abs(account.NetBalance), settings.currencySymbol, settings.decimalPlaces)}"
            else -> "الرصيد متزن (0 ${settings.currencySymbol})"
        }
        val shareText = "كشف حساب: ${account.PersonName}\n$statusDesc\nتاريخ الإشعار: ${Formatters.todayDate()}\n— الدفتر الذكي Pro"
        val cleanPhone = account.Phone?.trim().orEmpty()

        try {
            if (cleanPhone.isNotEmpty()) {
                val intlPhone = if (cleanPhone.startsWith("+") || cleanPhone.startsWith("00")) {
                    cleanPhone.removePrefix("+").removePrefix("00")
                } else {
                    "967$cleanPhone"
                }
                val waUrl = "https://api.whatsapp.com/send?phone=$intlPhone&text=${Uri.encode(shareText)}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(sendIntent, "مشاركة الرصيد"))
            }
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "مشاركة الرصيد"))
            } catch (_: Exception) {
                feedbackBanner = "لا يوجد تطبيق مشاركة متاح حالياً على الجهاز."
            }
        }
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SmartLedgerColors.SurfacePanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            text = "${Formatters.formatQuantity(accounts.size.toDouble())} حساب نشط",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                    }
                    Button(
                        onClick = rememberDebouncedClick {
                            editingAccount = null
                            showAddOrEditSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حساب جديد", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "ابحث بالاسم، رقم الهاتف، أو رمز الحساب..."
                )

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableFilterTabs(
                    tabs = bookTabs,
                    selectedTabId = selectedBookId,
                    onTabSelected = { viewModel.onBookSelected(it) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ScrollableFilterTabs(
                    tabs = balanceStatusTabs,
                    selectedTabId = balanceFilter,
                    onTabSelected = { viewModel.onBalanceFilterChanged(it) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!feedbackBanner.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.AccentSubtle)
                            .border(1.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = feedbackBanner!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.AccentText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { feedbackBanner = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    SmartEmptyState(
                        icon = Icons.Default.PersonSearch,
                        title = if (searchQuery.isNotBlank() || selectedBookId != null || balanceFilter != "ALL") {
                            "لا توجد حسابات مطابقة للفلتر الحالي"
                        } else {
                            "لا توجد حسابات مسجلة بعد"
                        },
                        description = "يمكنك إضافة عميل أو مورد أو موظف وتحديد رصيده الافتتاحي وسقف الائتمان بسهولة.",
                        actionText = if (searchQuery.isNotBlank() || selectedBookId != null || balanceFilter != "ALL") {
                            "عرض جميع الحسابات"
                        } else {
                            "إضافة حساب جديد"
                        },
                        onActionClick = {
                            if (searchQuery.isNotBlank() || selectedBookId != null || balanceFilter != "ALL") {
                                viewModel.onSearchQueryChanged("")
                                viewModel.onBookSelected(null)
                                viewModel.onBalanceFilterChanged("ALL")
                            } else {
                                editingAccount = null
                                showAddOrEditSheet = true
                            }
                        }
                    )
                }
            } else {
                items(accounts, key = { it.PersonId }) { account ->
                    AccountCardItem(
                        account = account,
                        bookName = booksMap[account.BookId] ?: "حساب",
                        currencySymbol = settings.currencySymbol,
                        decimals = settings.decimalPlaces,
                        privacyMode = privacyMode,
                        showPhoneQuickAction = settings.showPhoneInAccounts,
                        onOpenStatement = { selectedAccountForStatement = account },
                        onQuickReceipt = {
                            if (onOpenVoucherForAccount != null) {
                                onOpenVoucherForAccount(account.PersonId, true)
                            } else {
                                quickVoucherTarget = Pair(account, true)
                            }
                        },
                        onQuickPayment = {
                            if (onOpenVoucherForAccount != null) {
                                onOpenVoucherForAccount(account.PersonId, false)
                            } else {
                                quickVoucherTarget = Pair(account, false)
                            }
                        },
                        onCallClick = { safeDialPhone(account.Phone) },
                        onShareClick = { safeShareStatement(account) },
                        onEditClick = {
                            editingAccount = account
                            showAddOrEditSheet = true
                        },
                        onDeleteClick = { accountPendingDelete = account }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (selectedAccountForStatement != null) {
            val liveAcc = accounts.firstOrNull { it.PersonId == selectedAccountForStatement!!.PersonId }
                ?: selectedAccountForStatement!!
            ModalBottomSheet(
                onDismissRequest = { selectedAccountForStatement = null },
                containerColor = SmartLedgerColors.SurfacePanel,
                tonalElevation = 16.dp
            ) {
                AccountStatementSheetContent(
                    account = liveAcc,
                    bookName = booksMap[liveAcc.BookId] ?: "حساب",
                    currencySymbol = settings.currencySymbol,
                    decimals = settings.decimalPlaces,
                    privacyMode = privacyMode,
                    viewModel = viewModel,
                    onQuickReceipt = {
                        selectedAccountForStatement = null
                        quickVoucherTarget = Pair(liveAcc, true)
                    },
                    onQuickPayment = {
                        selectedAccountForStatement = null
                        quickVoucherTarget = Pair(liveAcc, false)
                    },
                    onEditAccount = {
                        selectedAccountForStatement = null
                        editingAccount = liveAcc
                        showAddOrEditSheet = true
                    },
                    onCall = { safeDialPhone(liveAcc.Phone) },
                    onShare = { safeShareStatement(liveAcc) },
                    onClose = { selectedAccountForStatement = null }
                )
            }
        }

        if (showAddOrEditSheet) {
            AddOrEditAccountDialog(
                books = books,
                existingAccount = editingAccount,
                defaultBookId = selectedBookId ?: books.firstOrNull()?.Id ?: 1L,
                currencySymbol = settings.currencySymbol,
                onDismiss = { showAddOrEditSheet = false },
                onSave = { id, name, bookId, phone, limit, opening, isDebit, note, updateOpening ->
                    viewModel.saveAccount(
                        id = id,
                        name = name,
                        bookId = bookId,
                        phone = phone,
                        creditLimit = limit,
                        openingBalance = opening,
                        isDebit = isDebit,
                        note = note,
                        updateOpeningIfEdited = updateOpening
                    )
                    showAddOrEditSheet = false
                    feedbackBanner = if (id > 0) "تم تحديث بيانات الحساب «$name» بنجاح." else "تم إنشاء الحساب «$name» بنجاح."
                }
            )
        }

        if (quickVoucherTarget != null) {
            val (targetAcc, isReceipt) = quickVoucherTarget!!
            QuickAccountVoucherDialog(
                account = targetAcc,
                isReceipt = isReceipt,
                currencySymbol = settings.currencySymbol,
                decimals = settings.decimalPlaces,
                onConfirm = { amount, statement, refNo ->
                    val type = if (isReceipt) "Receive" else "Payment"
                    viewModel.recordQuickVoucher(targetAcc.PersonId, type, amount, statement, refNo)
                    quickVoucherTarget = null
                    feedbackBanner = "تم قيد ${if (isReceipt) "سند قبض" else "سند صرف"} بمبلغ ${Formatters.formatCurrency(amount, settings.currencySymbol)} للحساب «${targetAcc.PersonName}»."
                },
                onDismiss = { quickVoucherTarget = null }
            )
        }

        if (accountPendingDelete != null) {
            ConfirmActionDialog(
                title = "حذف الحساب",
                message = "هل أنت متأكد من أرشفة وحذف الحساب «${accountPendingDelete!!.PersonName}»؟ يمكنك استعادته لاحقاً من سلة المحذوفات.",
                confirmText = "نعم، حذف الحساب",
                onConfirm = {
                    viewModel.deleteAccount(accountPendingDelete!!.PersonId, "حذف من قائمة الحسابات")
                    accountPendingDelete = null
                    feedbackBanner = "تم نقل الحساب إلى سلة المحذوفات."
                },
                onDismiss = { accountPendingDelete = null }
            )
        }
    }
}

@Composable
fun AccountCardItem(
    account: AccountBalanceSummary,
    bookName: String,
    currencySymbol: String,
    decimals: Int,
    privacyMode: Boolean,
    showPhoneQuickAction: Boolean,
    onOpenStatement: () -> Unit,
    onQuickReceipt: () -> Unit,
    onQuickPayment: () -> Unit,
    onCallClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDebitOnThem = account.NetBalance > 0.001
    val isCreditToThem = account.NetBalance < -0.001
    val isOverCreditLimit = isDebitOnThem && account.CreditLimit > 0.0 && account.NetBalance > account.CreditLimit

    val balanceColor = when {
        isDebitOnThem -> SmartLedgerColors.StateDanger
        isCreditToThem -> SmartLedgerColors.StateSuccess
        else -> SmartLedgerColors.TextMuted
    }
    val balanceLabel = when {
        isDebitOnThem -> "لنا (مدين عليه)"
        isCreditToThem -> "علينا (دائن له)"
        else -> "متزن (0)"
    }
    val badgeBg = when {
        isDebitOnThem -> SmartLedgerColors.StateDangerSubtle
        isCreditToThem -> SmartLedgerColors.StateSuccessSubtle
        else -> SmartLedgerColors.SurfaceCardElevated
    }

    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onOpenStatement),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isOverCreditLimit) SmartLedgerColors.StateWarningBorder else SmartLedgerColors.BorderSubtle
            )
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = account.PersonName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SmartLedgerColors.SurfaceCardElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = bookName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = SmartLedgerColors.AccentLight
                            )
                        }
                    }

                    val subInfo = buildList {
                        if (!account.Phone.isNullOrBlank()) add(account.Phone)
                        if (isOverCreditLimit) add("تجاوز سقف الائتمان (${Formatters.formatAmountOnly(account.CreditLimit)})!")
                    }
                    if (subInfo.isNotEmpty()) {
                        Text(
                            text = subInfo.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverCreditLimit) SmartLedgerColors.StateWarning else SmartLedgerColors.TextMuted
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = balanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showContextMenu = true },
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
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                            modifier = Modifier.background(SmartLedgerColors.SurfaceDialog)
                        ) {
                            DropdownMenuItem(
                                text = { Text("كشف الحساب التفصيلي", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SmartLedgerColors.AccentLight) },
                                onClick = { showContextMenu = false; onOpenStatement() }
                            )
                            DropdownMenuItem(
                                text = { Text("تسجيل سند قبض (استلام)", color = SmartLedgerColors.StateSuccess) },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SmartLedgerColors.StateSuccess) },
                                onClick = { showContextMenu = false; onQuickReceipt() }
                            )
                            DropdownMenuItem(
                                text = { Text("تسجيل سند صرف (سداد)", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SmartLedgerColors.StateDanger) },
                                onClick = { showContextMenu = false; onQuickPayment() }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة الرصيد / واتساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = SmartLedgerColors.AccentLight) },
                                onClick = { showContextMenu = false; onShareClick() }
                            )
                            if (!account.Phone.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("اتصال هاتفي (${account.Phone})", color = SmartLedgerColors.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SmartLedgerColors.AccentLight) },
                                    onClick = { showContextMenu = false; onCallClick() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("تعديل بيانات الحساب", color = SmartLedgerColors.TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SmartLedgerColors.TextSecondary) },
                                onClick = { showContextMenu = false; onEditClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف الحساب", color = SmartLedgerColors.StateDanger) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SmartLedgerColors.StateDanger) },
                                onClick = { showContextMenu = false; onDeleteClick() }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "الرصيد الصافي",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.TextMuted
                    )
                    Text(
                        text = Formatters.formatCurrency(
                            amount = kotlin.math.abs(account.NetBalance),
                            currencySymbol = currencySymbol,
                            decimals = decimals,
                            privacyMode = privacyMode
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showPhoneQuickAction && !account.Phone.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onCallClick,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اتصال", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                        }
                    }

                    Button(
                        onClick = onOpenStatement,
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentSubtle),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "كشف الحساب",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.AccentLight
                        )
                    }
                }
            }
        }
    }
}

private data class StatementRowWithRunningBalance(
    val operation: OperationEntity,
    val isCreditToThem: Boolean,
    val runningBalanceAfter: Double
)

@Composable
fun AccountStatementSheetContent(
    account: AccountBalanceSummary,
    bookName: String,
    currencySymbol: String,
    decimals: Int,
    privacyMode: Boolean,
    viewModel: AccountsViewModel,
    onQuickReceipt: () -> Unit,
    onQuickPayment: () -> Unit,
    onEditAccount: () -> Unit,
    onCall: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    val operations by viewModel.getOperationsForAccount(account.PersonId).collectAsState(initial = emptyList())
    var statementSearch by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var periodFilter by remember { mutableStateOf("ALL") }
    var operationPendingDelete by remember { mutableStateOf<OperationEntity?>(null) }

    val rowsWithRunningBalance = remember(operations) {
        val chronological = operations.sortedWith(compareBy<OperationEntity> { it.OccurredAt }.thenBy { it.Id })
        var running = 0.0
        val computed = chronological.map { op ->
            val isCredit = op.Type in listOf("Receive", "DebtToUs", "Purchase", "SaleReturn")
            running += if (isCredit) -op.Amount else op.Amount
            StatementRowWithRunningBalance(
                operation = op,
                isCreditToThem = isCredit,
                runningBalanceAfter = running
            )
        }
        computed.reversed()
    }

    val filteredRows = remember(rowsWithRunningBalance, statementSearch, typeFilter, periodFilter) {
        val today = Formatters.todayDate()
        val monthStart = Formatters.startOfCurrentMonthDate()
        rowsWithRunningBalance.filter { row ->
            val op = row.operation
            val matchesQuery = statementSearch.isBlank() ||
                (op.Note?.contains(statementSearch, ignoreCase = true) == true) ||
                (op.ReferenceNumber?.contains(statementSearch, ignoreCase = true) == true)

            val matchesType = when (typeFilter) {
                "INVOICES" -> op.Type in listOf("Sale", "Purchase", "SaleReturn", "PurchaseReturn")
                "RECEIPTS" -> op.Type == "Receive"
                "PAYMENTS" -> op.Type == "Payment"
                else -> true
            }

            val opDate = op.OccurredAt.take(10)
            val matchesPeriod = when (periodFilter) {
                "TODAY" -> opDate == today
                "MONTH" -> opDate >= monthStart
                else -> true
            }

            matchesQuery && matchesType && matchesPeriod
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = account.PersonName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = "($bookName)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.AccentLight
                    )
                }
                Text(
                    text = "الهاتف: ${account.Phone ?: "غير مسجل"} • الرمز: ${account.AccountCode ?: "#${account.PersonId}"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onEditAccount,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل الحساب", tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي عليه (مدين)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateDangerText)
                    Text(
                        Formatters.formatCurrency(account.TotalDebit, currencySymbol, decimals, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateDanger
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي له (دائن)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccessText)
                    Text(
                        Formatters.formatCurrency(account.TotalCredit, currencySymbol, decimals, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.StateSuccess
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(34.dp).background(SmartLedgerColors.BorderSubtle))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val netLabel = when {
                        account.NetBalance > 0.001 -> "صافي لنا (عليه)"
                        account.NetBalance < -0.001 -> "صافي علينا (له)"
                        else -> "الرصيد متزن"
                    }
                    Text(netLabel, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    Text(
                        Formatters.formatCurrency(kotlin.math.abs(account.NetBalance), currencySymbol, decimals, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (account.NetBalance > 0.001) SmartLedgerColors.StateDanger else if (account.NetBalance < -0.001) SmartLedgerColors.StateSuccess else SmartLedgerColors.TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onQuickReceipt,
                modifier = Modifier.weight(1f).height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateSuccess),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp), tint = SmartLedgerColors.TextInverse)
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند قبض (له)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
            }

            Button(
                onClick = onQuickPayment,
                modifier = Modifier.weight(1f).height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp), tint = SmartLedgerColors.TextInverse)
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند صرف (عليه)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مشاركة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
            }

            if (!account.Phone.isNullOrBlank()) {
                IconButton(
                    onClick = onCall,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(SmartLedgerColors.SurfaceCardElevated)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        CompactSearchBar(
            query = statementSearch,
            onQueryChange = { statementSearch = it },
            placeholder = "ابحث في وصف الحركات أو رقم المرجع..."
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollableFilterTabs(
                tabs = listOf(
                    FilterTabItem("ALL", "كل الحركات (${filteredRows.size})"),
                    FilterTabItem("INVOICES", "الفواتير"),
                    FilterTabItem("RECEIPTS", "المقبوضات"),
                    FilterTabItem("PAYMENTS", "المدفوعات")
                ),
                selectedTabId = typeFilter,
                onTabSelected = { typeFilter = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SmartLedgerColors.SurfaceCardElevated)
                    .clickable {
                        periodFilter = when (periodFilter) {
                            "ALL" -> "MONTH"
                            "MONTH" -> "TODAY"
                            else -> "ALL"
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when (periodFilter) {
                        "TODAY" -> "اليوم"
                        "MONTH" -> "هذا الشهر"
                        else -> "كل الفترات"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.AccentLight,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredRows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد حركات مطابقة في كشف الحساب.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredRows, key = { it.operation.Id }) { item ->
                    val op = item.operation
                    val typeTitle = when (op.Type) {
                        "Receive" -> "سند قبض (له)"
                        "Payment" -> "سند صرف (عليه)"
                        "Sale" -> "فاتورة مبيعات (عليه)"
                        "Purchase" -> "فاتورة مشتريات (له)"
                        "SaleReturn" -> "مرتجع مبيعات (له)"
                        "PurchaseReturn" -> "مرتجع مشتريات (عليه)"
                        "DebtOnUs" -> "رصيد افتتاحي (عليه)"
                        "DebtToUs" -> "رصيد افتتاحي (له)"
                        else -> op.Type
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCard)
                            .border(1.dp, SmartLedgerColors.BorderGridLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                text = buildString {
                                    append(typeTitle)
                                    if (!op.ReferenceNumber.isNullOrBlank()) append(" • #${op.ReferenceNumber}")
                                    append(" • ${op.OccurredAt.take(16)}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (item.isCreditToThem) "-" else "+"}${Formatters.formatCurrency(op.Amount, currencySymbol, decimals, privacyMode)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isCreditToThem) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger
                                )
                                val runState = when {
                                    item.runningBalanceAfter > 0.001 -> "عليه"
                                    item.runningBalanceAfter < -0.001 -> "له"
                                    else -> "متزن"
                                }
                                Text(
                                    text = "الرصيد بعدها: ${Formatters.formatAmountOnly(kotlin.math.abs(item.runningBalanceAfter), decimals)} ($runState)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { operationPendingDelete = op },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "حذف القيد",
                                    tint = SmartLedgerColors.StateDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (operationPendingDelete != null) {
        ConfirmActionDialog(
            title = "حذف حركة مالية من كشف الحساب",
            message = "هل أنت متأكد من حذف القيد «${operationPendingDelete!!.Note ?: operationPendingDelete!!.Type}» بمبلغ ${Formatters.formatCurrency(operationPendingDelete!!.Amount, currencySymbol, decimals)}؟ سيتغير الرصيد الصافي للحساب فوراً ويُحفظ القيد في سلة المحذوفات.",
            confirmText = "تأكيد حذف القيد",
            onConfirm = {
                viewModel.deleteOperation(operationPendingDelete!!.Id, "حذف مؤكد من كشف الحساب")
                operationPendingDelete = null
            },
            onDismiss = { operationPendingDelete = null }
        )
    }
}

@Composable
fun AddOrEditAccountDialog(
    books: List<BookEntity>,
    existingAccount: AccountBalanceSummary?,
    defaultBookId: Long,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        name: String,
        bookId: Long,
        phone: String?,
        limit: Double,
        opening: Double,
        isDebit: Boolean,
        note: String?,
        updateOpening: Boolean
    ) -> Unit
) {
    val isEdit = existingAccount != null
    var name by remember { mutableStateOf(existingAccount?.PersonName ?: "") }
    var selectedBookId by remember { mutableStateOf(existingAccount?.BookId ?: defaultBookId) }
    var phone by remember { mutableStateOf(existingAccount?.Phone ?: "") }
    var openingBalanceText by remember { mutableStateOf("") }
    var isDebitOpening by remember {
        mutableStateOf((existingAccount?.BookId ?: defaultBookId) != 2L)
    }
    var showOptionalDetails by remember {
        mutableStateOf(isEdit && ((existingAccount?.CreditLimit ?: 0.0) > 0.0 || !existingAccount?.Note.isNullOrBlank()))
    }
    var creditLimitText by remember {
        mutableStateOf(
            if (existingAccount != null && existingAccount.CreditLimit > 0.0) {
                Formatters.formatQuantity(existingAccount.CreditLimit)
            } else ""
        )
    }
    var note by remember { mutableStateOf(existingAccount?.Note ?: "") }

    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val openingFocus = remember { FocusRequester() }

    val isFormValid = name.trim().isNotEmpty()
    val hasUnsavedChanges = if (isEdit) {
        name != existingAccount!!.PersonName ||
            selectedBookId != existingAccount.BookId ||
            phone != (existingAccount.Phone ?: "") ||
            note != (existingAccount.Note ?: "")
    } else {
        name.isNotBlank() || phone.isNotBlank() || openingBalanceText.isNotBlank()
    }

    AdaptiveFormHost(
        mode = FormWindowMode.BOTTOM_SHEET,
        title = if (isEdit) "تعديل بيانات الحساب" else "إضافة حساب جديد",
        subtitle = if (isEdit) "تحديث الاسم أو الدفتر أو بيانات التواصل" else "اختر نوع الحساب وأدخل الاسم والرصيد الافتتاحي إن وجد",
        onDismiss = onDismiss,
        primaryButtonText = if (isEdit) "حفظ التعديلات" else "إنشاء الحساب",
        isPrimaryEnabled = isFormValid,
        hasUnsavedChanges = hasUnsavedChanges,
        onPrimaryClick = {
            if (isFormValid) {
                val limit = Formatters.parseDoubleSafe(creditLimitText, 0.0)
                val opening = Formatters.parseDoubleSafe(openingBalanceText, 0.0)
                onSave(
                    existingAccount?.PersonId ?: 0L,
                    name,
                    selectedBookId,
                    phone,
                    limit,
                    opening,
                    isDebitOpening,
                    note,
                    false
                )
            }
        }
    ) {
        Text(
            text = "تصنيف الحساب (الدفتر) *",
            style = MaterialTheme.typography.labelSmall,
            color = SmartLedgerColors.TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books) { book ->
                val selected = selectedBookId == book.Id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.SurfaceCardElevated)
                        .border(
                            1.dp,
                            if (selected) SmartLedgerColors.AccentLight else SmartLedgerColors.BorderSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedBookId = book.Id
                            if (!isEdit) {
                                isDebitOpening = (book.Id != 2L)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = book.Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        SmartTextField(
            value = name,
            onValueChange = { name = it },
            label = "اسم العميل / المورد / الحساب",
            placeholder = "مثال: مؤسسة الأمل للتجارة",
            isRequired = true,
            focusRequester = nameFocus,
            nextFocusRequester = phoneFocus
        )

        SmartTextField(
            value = phone,
            onValueChange = { phone = Formatters.normalizeDigits(it) },
            label = "رقم الهاتف للتواصل والواتساب (اختياري)",
            placeholder = "مثال: 777123456",
            keyboardType = KeyboardType.Phone,
            focusRequester = phoneFocus,
            nextFocusRequester = if (!isEdit) openingFocus else null,
            imeAction = if (!isEdit) ImeAction.Next else ImeAction.Done
        )

        if (!isEdit) {
            SmartNumberField(
                value = openingBalanceText,
                onValueChange = { openingBalanceText = it },
                label = "الرصيد الافتتاحي السابق (إن وجد)",
                suffixText = currencySymbol,
                focusRequester = openingFocus,
                imeAction = ImeAction.Done
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SmartLedgerColors.SurfaceCardElevated)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDebitOpening) SmartLedgerColors.StateDanger else Color.Transparent)
                        .clickable { isDebitOpening = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لنا عليه (مدين)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebitOpening) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isDebitOpening) SmartLedgerColors.StateSuccess else Color.Transparent)
                        .clickable { isDebitOpening = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "له علينا (دائن)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (!isDebitOpening) SmartLedgerColors.TextInverse else SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SmartLedgerColors.SurfaceCardElevated)
                .clickable { showOptionalDetails = !showOptionalDetails }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "خيارات متقدمة (سقف الائتمان والعنوان والملاحظات)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.AccentLight
            )
            Icon(
                imageVector = if (showOptionalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SmartLedgerColors.AccentLight
            )
        }

        AnimatedVisibility(visible = showOptionalDetails) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmartNumberField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it },
                    label = "سقف الدين / الائتمان المسموح (اختياري)",
                    suffixText = currencySymbol,
                    helperText = "ينبهك النظام إذا تجاوزت مديونية العميل هذا السقف"
                )

                SmartTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "العنوان أو ملاحظات الحساب",
                    singleLine = false,
                    minLines = 2,
                    imeAction = ImeAction.Done
                )
            }
        }
    }
}

@Composable
fun QuickAccountVoucherDialog(
    account: AccountBalanceSummary,
    isReceipt: Boolean,
    currencySymbol: String,
    decimals: Int,
    onConfirm: (amount: Double, statement: String, refNo: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var statement by remember {
        mutableStateOf(if (isReceipt) "دفعة نقدية مستلمة من الحساب" else "دفعة نقدية مصروفة للحساب")
    }
    var refNo by remember { mutableStateOf("") }

    val amount = Formatters.parseDoubleSafe(amountText, 0.0)
    val projectedBalance = if (isReceipt) account.NetBalance - amount else account.NetBalance + amount

    AdaptiveFormHost(
        mode = FormWindowMode.COMPACT_DIALOG,
        title = if (isReceipt) "سند قبض سريع (له)" else "سند صرف سريع (عليه)",
        subtitle = "الحساب: ${account.PersonName}",
        onDismiss = onDismiss,
        primaryButtonText = if (isReceipt) "ترحيل سند القبض" else "ترحيل سند الصرف",
        primaryButtonColor = if (isReceipt) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateDanger,
        isPrimaryEnabled = amount > 0.0 && statement.isNotBlank(),
        hasUnsavedChanges = amountText.isNotBlank(),
        onPrimaryClick = {
            if (amount > 0.0) {
                onConfirm(amount, statement, refNo)
            }
        }
    ) {
        SmartNumberField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "المبلغ",
            suffixText = currencySymbol,
            isRequired = true
        )

        SmartTextField(
            value = statement,
            onValueChange = { statement = it },
            label = "البيان / سبب السند",
            isRequired = true
        )

        SmartTextField(
            value = refNo,
            onValueChange = { refNo = it },
            label = "رقم المرجع الورقي (اختياري)",
            imeAction = ImeAction.Done
        )

        if (amount > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("الرصيد بعد ترحيل السند:", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                    val projLabel = when {
                        projectedBalance > 0.001 -> "لنا (${Formatters.formatCurrency(projectedBalance, currencySymbol, decimals)})"
                        projectedBalance < -0.001 -> "علينا (${Formatters.formatCurrency(kotlin.math.abs(projectedBalance), currencySymbol, decimals)})"
                        else -> "متزن (0)"
                    }
                    Text(projLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.AccentLight)
                }
            }
        }
    }
}
