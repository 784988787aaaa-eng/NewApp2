package com.smartledger.pro.ui.screens.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.entity.BusinessIdentityEntity
import com.smartledger.pro.data.local.entity.DeletedItemEntity
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SmartLedgerDatabase.getInstance(application)
    private val identityDao = db.businessIdentityDao()
    private val deletedItemDao = db.deletedItemDao()

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityDao.getIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deletedItems: StateFlow<List<DeletedItemEntity>> = deletedItemDao.getAllDeletedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBusinessIdentity(
        tradeName: String,
        phone1: String,
        phone2: String?,
        address: String,
        taxNumber: String?,
        crNumber: String?,
        receiptHeader: String,
        receiptFooter: String
    ) {
        viewModelScope.launch {
            identityDao.saveIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = tradeName.trim().ifBlank { "الدفتر الذكي للتجارة العامة" },
                    LegalName = tradeName.trim().ifBlank { "الدفتر الذكي" },
                    TaxNumber = taxNumber?.trim()?.ifBlank { null },
                    CommercialRegistration = crNumber?.trim()?.ifBlank { null },
                    Phone1 = phone1.trim(),
                    Phone2 = phone2?.trim()?.ifBlank { null },
                    Address = address.trim(),
                    ReceiptHeader = receiptHeader.trim(),
                    ReceiptFooter = receiptFooter.trim()
                )
            )
        }
    }

    fun deleteFromTrashPermanently(id: Long) {
        viewModelScope.launch {
            deletedItemDao.deletePermanently(id)
        }
    }

    suspend fun runDatabaseDiagnostics(): String {
        return try {
            db.checkpointWal()
            val accCount = db.accountDao().getActiveAccountsCount()
            val prodCount = db.productDao().getActiveProductsCount()
            val invCount = db.invoiceDao().getInvoiceCount()
            "قاعدة البيانات سليمة ومتسقة (الإصدار التخطيطي v${SmartLedgerDatabase.SCHEMA_VERSION}) • الحسابات: $accCount • الأصناف: $prodCount • الفواتير: $invCount"
        } catch (e: Exception) {
            "تنبيه فحص قاعدة البيانات: ${e.message}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppSettingsManager.settings.collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val deletedItems by viewModel.deletedItems.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var isBannerError by remember { mutableStateOf(false) }

    var localBackups by remember { mutableStateOf<List<BackupSummary>>(emptyList()) }
    var showLocalBackupsHistory by remember { mutableStateOf(false) }
    var isWorkingOnBackup by remember { mutableStateOf(false) }
    var cloudUploadProgress by remember { mutableStateOf(0) }
    var pendingRestoreReport by remember { mutableStateOf<Pair<BackupInspectionReport, () -> Unit>?>(null) }
    var backupPendingDelete by remember { mutableStateOf<BackupSummary?>(null) }

    var showEditIdentitySheet by remember { mutableStateOf(false) }
    var showEditDefaultUnitsSheet by remember { mutableStateOf(false) }
    var showGoogleDriveSetupSheet by remember { mutableStateOf(false) }
    var showDisconnectDriveConfirm by remember { mutableStateOf(false) }
    var showPrinterPickerSheet by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<PairedPrinterDevice>>(emptyList()) }
    var showTrashSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val biometricAvailability = remember { BiometricSecurityHelper.checkAvailability(context) }

    LaunchedEffect(Unit) {
        localBackups = BackupRestoreHelper.listLocalBackups(context)
    }

    val createBackupSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            isWorkingOnBackup = true
            scope.launch {
                val res = BackupRestoreHelper.createBackupToUri(context, uri, settings.keepBackupCount)
                isWorkingOnBackup = false
                if (res.isSuccess) {
                    val summary = res.getOrThrow()
                    val infoText = "${summary.createdAt} (${Formatters.formatBytes(summary.sizeBytes)} • SHA-256: ${summary.sha256.take(10)}...)"
                    AppSettingsManager.updatePreference(context, "last_local_backup_info", infoText)
                    localBackups = BackupRestoreHelper.listLocalBackups(context)
                    isBannerError = false
                    statusBannerText = "تم حفظ النسخة (${summary.fileName}) في أرشيف (${summary.yearMonthFolder}) بنجاح."
                } else {
                    isBannerError = true
                    statusBannerText = "تعذر حفظ النسخة الاحتياطية: ${res.exceptionOrNull()?.message}"
                }
            }
        }
    }

    val openBackupSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isWorkingOnBackup = true
            scope.launch {
                val inspectRes = BackupRestoreHelper.inspectBackupUri(context, uri, "ملف خارجي مختار")
                isWorkingOnBackup = false
                if (inspectRes.isSuccess) {
                    val report = inspectRes.getOrThrow()
                    pendingRestoreReport = Pair(report) {
                        isWorkingOnBackup = true
                        scope.launch {
                            val restoreRes = BackupRestoreHelper.restoreFromUri(context, uri)
                            isWorkingOnBackup = false
                            if (restoreRes.isSuccess) {
                                localBackups = BackupRestoreHelper.listLocalBackups(context)
                                isBannerError = false
                                statusBannerText = "تمت استعادة البيانات بنجاح بعد التحقق من SHA-256 وإنشاء نسخة أمان."
                            } else {
                                isBannerError = true
                                statusBannerText = "تعذر استعادة النسخة: ${restoreRes.exceptionOrNull()?.message}"
                            }
                        }
                    }
                } else {
                    isBannerError = true
                    statusBannerText = "ملف النسخة غير صالح للاستعادة: ${inspectRes.exceptionOrNull()?.message}"
                }
            }
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val res = ThermalPrinterHelper.getPairedPrinters(context)
            if (res.isSuccess) {
                pairedPrinters = res.getOrThrow()
                showPrinterPickerSheet = true
            } else {
                isBannerError = true
                statusBannerText = res.exceptionOrNull()?.message
            }
        } else {
            isBannerError = true
            statusBannerText = "تم رفض إذن البلوتوث. يلزم منح الإذن لاختيار طابعة الفواتير الحرارية."
        }
    }

    val categoryTabs = remember {
        listOf(
            FilterTabItem("ALL", "الكل"),
            FilterTabItem("BUSINESS", "المنشأة والفواتير"),
            FilterTabItem("CURRENCY_INV", "العملة والمخزون"),
            FilterTabItem("POS_PRINT", "المبيعات والطباعة"),
            FilterTabItem("BACKUP", "النسخ وGoogle Drive"),
            FilterTabItem("SECURITY_UI", "الأمان والمظهر والصيانة")
        )
    }

    fun shouldShowSection(catKey: String, keywords: String): Boolean {
        val matchesCat = selectedCategory == "ALL" || selectedCategory == catKey
        val matchesQuery = searchQuery.isBlank() || keywords.contains(searchQuery.trim(), ignoreCase = true)
        return matchesCat && matchesQuery
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مركز التحكم والإعدادات",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = "${settings.currencySymbol} • ${if (settings.themeMode == "LIGHT") "فاتح" else "داكن"} • ${if (settings.compactDensity) "عرض كثيف" else "عرض مريح"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight
                        )
                    }

                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) searchQuery = ""
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSearchExpanded) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "ابحث عن إعداد",
                            tint = if (isSearchExpanded) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isSearchExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "ابحث عن إعداد (عملة، وحدات، طابعة، نسخ، بصمة، مظهر)..."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableFilterTabs(
                    tabs = categoryTabs,
                    selectedTabId = selectedCategory,
                    onTabSelected = { selectedCategory = it }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SmartLedgerTokens.SpaceSM)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!statusBannerText.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBannerError) SmartLedgerColors.StateDangerSubtle else SmartLedgerColors.StateSuccessSubtle
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(
                                if (isBannerError) SmartLedgerColors.StateDangerBorder else SmartLedgerColors.StateSuccessBorder
                            )
                        ),
                        shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusBannerText!!,
                                color = if (isBannerError) SmartLedgerColors.StateDangerText else SmartLedgerColors.StateSuccessText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { statusBannerText = null }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = if (isBannerError) SmartLedgerColors.StateDangerText else SmartLedgerColors.StateSuccessText
                                )
                            }
                        }
                    }
                }
            }

            if (shouldShowSection("BUSINESS", "المنشأة الاسم التجاري الهاتف العنوان الضريبي ترويسة تذييل الفاتورة الشعار")) {
                item {
                    SettingsCardSection(
                        title = "1. بيانات المنشأة وترويسة الفواتير",
                        statusSummary = "${identity?.TradeName ?: "الدفتر الذكي"} · ${identity?.Phone1 ?: "777000000"}"
                    ) {
                        Text(
                            text = "العنوان: ${identity?.Address ?: "صنعاء"}" +
                                if (!identity?.TaxNumber.isNullOrBlank()) " • الضريبي: ${identity?.TaxNumber}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showEditIdentitySheet = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                            shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "تعديل بيانات المنشأة وترويسة وتذييل الإيصال",
                                color = SmartLedgerColors.AccentLight,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (shouldShowSection("CURRENCY_INV", "العملة المنازل العشرية التقريب الأرقام الغربية 123 ر.ي ر.س دولار")) {
                item {
                    SettingsCardSection(
                        title = "2. العملة والتنسيق الرقمي (123)",
                        statusSummary = "العملة النشطة: ${settings.currencySymbol} · ${if (settings.decimalPlaces == 0) "بدون كسور" else "${settings.decimalPlaces} منازل عشرية"}"
                    ) {
                        Text("العملة الافتراضية:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        SegmentedButtonBar(
                            options = listOf("ر.ي" to "ر.ي", "ر.س" to "ر.س", "$" to "$", "د.إ" to "د.إ", "ج.م" to "ج.م"),
                            selectedOption = settings.currencySymbol,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "currency_symbol", it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("التقريب والمنازل العشرية:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        SegmentedButtonBar(
                            options = listOf(0 to "بدون كسور (0)", 2 to "منزلتان عشريتان (0.00)"),
                            selectedOption = settings.decimalPlaces,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "decimal_places", it.toString()) }
                        )
                    }
                }
            }

            if (shouldShowSection("CURRENCY_INV", "المخزون الوحدات الصغرى الكبرى كرتون قطعة الباركود موقع الرف الحد الأدنى النفاد")) {
                item {
                    SettingsCardSection(
                        title = "3. المخزون والوحدات والباركود",
                        statusSummary = "${settings.defaultSmallUnit} / ${settings.defaultLargeUnit} (معامل ${Formatters.formatQuantity(settings.defaultUnitFactor)})"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حد التنبيه الأدنى للنفاد: ${Formatters.formatQuantity(settings.defaultAlertThreshold)} ${settings.defaultSmallUnit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SmartLedgerColors.TextSecondary
                            )
                            OutlinedButton(
                                onClick = { showEditDefaultUnitsSheet = true },
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusSmall),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("تعديل الوحدات", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = SmartLedgerColors.BorderGridLine)

                        SettingSwitchRow(
                            title = "إظهار حقل وميزة مسح الباركود",
                            subtitle = "تفعيل أزرار مسح الباركود بالكاميرا وحقول الباركود في الأصناف",
                            checked = settings.showBarcodeField,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "show_barcode_field", it.toString()) }
                        )
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        SettingSwitchRow(
                            title = "إظهار موقع الرف في المخزن",
                            subtitle = "عرض وتحديد رقم الرف أو الممر لكل صنف",
                            checked = settings.showShelfLocation,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "show_shelf_location", it.toString()) }
                        )
                    }
                }
            }

            if (shouldShowSection("POS_PRINT", "المبيعات نقطة البيع الدفع العميل الافتراضي سقف الائتمان الهاتف واتساب")) {
                item {
                    SettingsCardSection(
                        title = "4. سياسات المبيعات والحسابات",
                        statusSummary = "الدفع الافتراضي: ${if (settings.defaultPaymentType == "Cash") "نقدي" else "آجل"}"
                    ) {
                        SegmentedButtonBar(
                            options = listOf("Cash" to "نقدي افتراضياً", "Credit" to "آجل افتراضياً"),
                            selectedOption = settings.defaultPaymentType,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "default_payment_type", it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = SmartLedgerColors.BorderGridLine)

                        SettingSwitchRow(
                            title = "التنبيه عند تجاوز الرصيد المتاح في السلة",
                            subtitle = "إظهار تحذير في نقطة البيع إذا تجاوزت الكمية المباعة رصيد المخزن",
                            checked = settings.warnOnLowStockInPos,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "warn_low_stock_pos", it.toString()) }
                        )
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        SettingSwitchRow(
                            title = "التنبيه عند تجاوز سقف ائتمان العميل",
                            subtitle = "تحذير محاسبي عند إصدار فاتورة آجلة تتجاوز سقف دين العميل",
                            checked = settings.warnOnCreditLimitExceeded,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "warn_credit_limit", it.toString()) }
                        )
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        SettingSwitchRow(
                            title = "إظهار زر الاتصال السريع في بطاقة الحساب",
                            subtitle = "إمكانية الاتصال المباشر بالعميل من قائمة الحسابات",
                            checked = settings.showPhoneInAccounts,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "show_phone_in_accounts", it.toString()) }
                        )
                    }
                }
            }

            if (shouldShowSection("POS_PRINT", "الطباعة طابعة بلوتوث حرارية ESC/POS 58mm 80mm نسخ اختبار اتصال")) {
                item {
                    SettingsCardSection(
                        title = "5. الطباعة الحرارية (ESC/POS Bluetooth)",
                        statusSummary = "${if (settings.printerName.isNotBlank()) settings.printerName else "غير مقترنة"} · ${settings.printerPaperWidthMm}mm · ${settings.printerCopiesCount} نسخة"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (settings.printerAddress.isNotBlank()) "العنوان: ${settings.printerAddress}" else "اختر طابعة مقترنة لإجراء اختبار اتصال حقيقي",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    if (ThermalPrinterHelper.hasBluetoothPermission(context)) {
                                        val res = ThermalPrinterHelper.getPairedPrinters(context)
                                        if (res.isSuccess) {
                                            pairedPrinters = res.getOrThrow()
                                            showPrinterPickerSheet = true
                                        } else {
                                            isBannerError = true
                                            statusBannerText = res.exceptionOrNull()?.message
                                        }
                                    } else {
                                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        } else {
                                            Manifest.permission.BLUETOOTH
                                        }
                                        bluetoothPermissionLauncher.launch(perm)
                                    }
                                },
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusSmall),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("اختيار طابعة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SegmentedButtonBar(
                            options = listOf(80 to "ورق 80mm (قياسي)", 58 to "ورق 58mm (مدمج)"),
                            selectedOption = settings.printerPaperWidthMm,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "printer_paper_width", it.toString()) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = rememberDebouncedClick {
                                scope.launch {
                                    val testRes = ThermalPrinterHelper.testPrinterConnection(
                                        context = context,
                                        macAddress = settings.printerAddress,
                                        paperWidthMm = settings.printerPaperWidthMm,
                                        tradeName = identity?.TradeName ?: "الدفتر الذكي Pro"
                                    )
                                    if (testRes.isSuccess) {
                                        isBannerError = false
                                        statusBannerText = testRes.getOrThrow()
                                    } else {
                                        isBannerError = true
                                        statusBannerText = testRes.exceptionOrNull()?.message ?: "تعذر الاتصال بالطابعة"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                            shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختبار اتصال الطابعة الفعلي (ESC/POS)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (shouldShowSection("BACKUP", "النسخ الاحتياطي استعادة محلي سحابي Google Drive تلقائي SHA-256 slprobackup أرشيف السنة الشهر")) {
                item {
                    val driveStatusEnum = DriveConnectionStatus.values().firstOrNull { it.code == settings.googleDriveStatus }
                        ?: DriveConnectionStatus.DISCONNECTED
                    val hasValidToken = GoogleDriveBackupHelper.getValidAccessToken(context) != null

                    SettingsCardSection(
                        title = "6. النسخ الاحتياطي (.slprobackup) وGoogle Drive",
                        statusSummary = "محلي: ${localBackups.size} نسخ (مرتبة بالسنة/الشهر) · سحابي: ${driveStatusEnum.arabicLabel}"
                    ) {
                        Text("أولاً: الأرشيف المحلي الموثق (Backups/YYYY/MM/)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                        Text("آخر نسخة: ${settings.lastLocalBackupInfo}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = rememberDebouncedClick {
                                    createBackupSafLauncher.launch(BackupRestoreHelper.generateBackupFileName())
                                },
                                enabled = !isWorkingOnBackup,
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ نسخة (.slprobackup)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = rememberDebouncedClick {
                                    openBackupSafLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !isWorkingOnBackup,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص واستعادة ملف...", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                                .background(SmartLedgerColors.SurfaceCardElevated)
                                .clickable { showLocalBackupsHistory = !showLocalBackupsHistory }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "فهرس الأرشيف المحلي حسب العام والشهر (${Formatters.formatQuantity(localBackups.size.toDouble())})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextPrimary
                            )
                            Icon(if (showLocalBackupsHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                        }

                        AnimatedVisibility(visible = showLocalBackupsHistory) {
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (localBackups.isEmpty()) {
                                    Text("لا توجد نسخ محلية محفوظة في الأرشيف بعد.", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                } else {
                                    localBackups.forEach { b ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                                                .background(SmartLedgerColors.SurfacePanel)
                                                .border(1.dp, SmartLedgerColors.BorderGridLine, RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(b.fileName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                                Text("المجلد: Backups/${b.yearMonthFolder} • ${Formatters.formatBytes(b.sizeBytes)} • ${b.createdAt}", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = SmartLedgerColors.TextMuted)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        val path = b.filePath ?: return@TextButton
                                                        val file = File(path)
                                                        scope.launch {
                                                            val rep = BackupRestoreHelper.inspectBackupFile(context, file)
                                                            if (rep.isSuccess) {
                                                                pendingRestoreReport = Pair(rep.getOrThrow()) {
                                                                    scope.launch {
                                                                        val r = BackupRestoreHelper.restoreFromArchiveFile(context, file)
                                                                        if (r.isSuccess) {
                                                                            localBackups = BackupRestoreHelper.listLocalBackups(context)
                                                                            isBannerError = false
                                                                            statusBannerText = "تمت استعادة النسخة (${b.fileName}) بنجاح."
                                                                        } else {
                                                                            isBannerError = true
                                                                            statusBannerText = r.exceptionOrNull()?.message
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                isBannerError = true
                                                                statusBannerText = rep.exceptionOrNull()?.message
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Text("استعادة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(
                                                    onClick = { backupPendingDelete = b },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف النسخة", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ثانياً: المزامنة السحابية مع Google Drive", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                Text(
                                    text = "الحالة: ${driveStatusEnum.arabicLabel}" +
                                        if (settings.googleDriveAccountEmail.isNotBlank()) " (${settings.googleDriveAccountEmail})" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasValidToken) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateWarning
                                )
                                Text(
                                    text = "آخر رفع سحابي: ${settings.lastCloudBackupInfo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextMuted
                                )
                            }

                            OutlinedButton(
                                onClick = { showGoogleDriveSetupSheet = true },
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusSmall),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (hasValidToken) "إدارة الربط" else "ربط الحساب",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (cloudUploadProgress in 1..99) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = cloudUploadProgress / 100f,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = SmartLedgerColors.AccentLight
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = rememberDebouncedClick {
                                    val token = GoogleDriveBackupHelper.getValidAccessToken(context)
                                    if (token.isNullOrBlank()) {
                                        showGoogleDriveSetupSheet = true
                                    } else {
                                        scope.launch {
                                            AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.UPLOADING.code)
                                            cloudUploadProgress = 5
                                            val archive = GoogleDriveBackupHelper.createLocalArchive(context)
                                            if (archive == null) {
                                                cloudUploadProgress = 0
                                                AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.ERROR.code)
                                                isBannerError = true
                                                statusBannerText = "تعذر تجهيز ملف قاعدة البيانات للرفع."
                                                return@launch
                                            }
                                            val uploadRes = GoogleDriveBackupHelper.uploadBackupToDrive(archive, token) { p ->
                                                cloudUploadProgress = p
                                            }
                                            cloudUploadProgress = 0
                                            if (uploadRes.isSuccess) {
                                                val info = uploadRes.getOrThrow()
                                                val summaryText = "${Formatters.nowTimestamp()} (${Formatters.formatBytes(info.sizeBytes)})"
                                                AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.SUCCESS.code)
                                                AppSettingsManager.updatePreference(context, "last_cloud_backup_info", summaryText)
                                                isBannerError = false
                                                statusBannerText = "تم رفع النسخة إلى Google Drive (appDataFolder) بنجاح."
                                            } else {
                                                val errMsg = uploadRes.exceptionOrNull()?.message ?: "تعذر الاتصال بالخادم"
                                                val nextState = if (errMsg.contains("401") || errMsg.contains("403")) {
                                                    DriveConnectionStatus.REQUIRES_AUTH.code
                                                } else {
                                                    DriveConnectionStatus.ERROR.code
                                                }
                                                AppSettingsManager.updatePreference(context, "gdrive_status", nextState)
                                                isBannerError = true
                                                statusBannerText = errMsg
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("رفع نسخة الآن إلى Drive", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                            }

                            if (settings.googleDriveAccountEmail.isNotBlank() || hasValidToken) {
                                OutlinedButton(
                                    onClick = { showDisconnectDriveConfirm = true },
                                    modifier = Modifier.height(40.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                                    shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                                ) {
                                    Text("فصل الحساب", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingSwitchRow(
                            title = "تفعيل النسخ الاحتياطي التلقائي المجدول",
                            subtitle = if (settings.autoBackupEnabled) {
                                "نشط: يتم النسخ كل ${settings.autoBackupFrequencyHours} ساعة"
                            } else {
                                "فعّل هذا الخيار لتحديد دورية النسخ التلقائي"
                            },
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                AppSettingsManager.updatePreference(context, "auto_backup_enabled", enabled.toString())
                                if (enabled) {
                                    GoogleDriveBackupHelper.scheduleAutoBackupJob(
                                        context = context,
                                        frequencyHours = settings.autoBackupFrequencyHours,
                                        wifiOnly = settings.autoBackupWifiOnly
                                    )
                                } else {
                                    GoogleDriveBackupHelper.cancelAutoBackupJob(context)
                                }
                            }
                        )

                        AnimatedVisibility(visible = settings.autoBackupEnabled) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                SegmentedButtonBar(
                                    options = listOf(24 to "يومياً (كل 24 ساعة)", 168 to "أسبوعياً (كل 7 أيام)"),
                                    selectedOption = settings.autoBackupFrequencyHours,
                                    onOptionSelected = { hrs ->
                                        AppSettingsManager.updatePreference(context, "auto_backup_frequency_hours", hrs.toString())
                                        GoogleDriveBackupHelper.scheduleAutoBackupJob(context, hrs, settings.autoBackupWifiOnly)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (shouldShowSection("SECURITY_UI", "الأمان الخصوصية البصمة قفل التطبيق مهلة القفل إخفاء الشاشة")) {
                item {
                    SettingsCardSection(
                        title = "7. الأمان والقياسات الحيوية",
                        statusSummary = if (settings.biometricEnabled && biometricAvailability.isSupported) "القفل البيومتري مفعّل" else "القفل غير مفعّل"
                    ) {
                        SettingSwitchRow(
                            title = "القفل البيومتري ببصمة الإصبع أو الوجه",
                            subtitle = biometricAvailability.statusMessage,
                            checked = settings.biometricEnabled && biometricAvailability.isSupported,
                            onCheckedChange = { wantEnable ->
                                if (!biometricAvailability.isSupported) {
                                    isBannerError = true
                                    statusBannerText = biometricAvailability.statusMessage
                                } else if (wantEnable) {
                                    val act = context as? FragmentActivity
                                    if (act != null) {
                                        BiometricSecurityHelper.authenticate(
                                            activity = act,
                                            title = "تأكيد تفعيل القفل البيومتري",
                                            subtitle = "امسح بصمتك لتأكيد تفعيل حماية التطبيق",
                                            onSuccess = {
                                                AppSettingsManager.updatePreference(context, "biometric_enabled", "true")
                                                isBannerError = false
                                                statusBannerText = "تم تفعيل القفل البيومتري بنجاح."
                                            },
                                            onError = { err ->
                                                isBannerError = true
                                                statusBannerText = "لم يتم تفعيل القفل: $err"
                                            }
                                        )
                                    }
                                } else {
                                    AppSettingsManager.updatePreference(context, "biometric_enabled", "false")
                                }
                            }
                        )

                        AnimatedVisibility(visible = settings.biometricEnabled && biometricAvailability.isSupported) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                SegmentedButtonBar(
                                    options = listOf(0 to "قفل فوري", 60 to "بعد دقيقة", 300 to "بعد 5 دقائق"),
                                    selectedOption = settings.lockTimeoutSeconds,
                                    onOptionSelected = { sec ->
                                        AppSettingsManager.updatePreference(context, "lock_timeout_seconds", sec.toString())
                                    }
                                )
                            }
                        }

                        Divider(color = SmartLedgerColors.BorderGridLine)

                        SettingSwitchRow(
                            title = "إخفاء الأرصدة من معاينات التطبيقات المفتوحة",
                            subtitle = "تفعيل حماية الشاشة (FLAG_SECURE) لمنع التقاط الأرصدة في الخلفية",
                            checked = settings.hideScreenInRecents,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "hide_screen_recents", it.toString()) }
                        )
                    }
                }
            }

            if (shouldShowSection("SECURITY_UI", "المظهر الوضع الداكن الفاتح الكثافة مريح مدمج حجم النص الخط الحركة الاهتزاز الصيانة قاعدة البيانات المحذوفات")) {
                item {
                    SettingsCardSection(
                        title = "8. المظهر، الكثافة البصرية، والصيانة",
                        statusSummary = "${if (settings.themeMode == "LIGHT") "فاتح" else if (settings.themeMode == "SYSTEM") "تلقائي" else "داكن"} · خط ${(settings.fontScale * 100).toInt()}% · ${if (settings.compactDensity) "كثيف" else "مريح"}"
                    ) {
                        Text("نمط المظهر والألوان:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        SegmentedButtonBar(
                            options = listOf("DARK" to "داكن (Velvet)", "LIGHT" to "فاتح (Daylight)", "SYSTEM" to "تلقائي"),
                            selectedOption = settings.themeMode,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "theme_mode", it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("حجم النص وسهولة القراءة:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        SegmentedButtonBar(
                            options = listOf(0.92f to "مدمج (92%)", 1.0f to "قياسي (100%)", 1.12f to "كبير (112%)"),
                            selectedOption = settings.fontScale,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "font_scale", it.toString()) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("الكثافة البصرية وتوزيع المسافات:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        SegmentedButtonBar(
                            options = listOf(false to "عرض مريح (مسافات أوسع)", true to "عرض كثيف (عناصر أكثر)"),
                            selectedOption = settings.compactDensity,
                            onOptionSelected = { AppSettingsManager.updatePreference(context, "compact_density", it.toString()) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = SmartLedgerColors.BorderGridLine)

                        SettingSwitchRow(
                            title = "الاهتزاز اللمسي الخفيف (Haptic Feedback)",
                            subtitle = "استجابة لمسية عند الضغط على الأزرار وقراءة الباركود",
                            checked = settings.enableHaptics,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "enable_haptics", it.toString()) }
                        )
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        SettingSwitchRow(
                            title = "تقليل الحركة والانتقالات (Reduce Motion)",
                            subtitle = "إيقاف تأثيرات الارتداد والانتقال لراحة العين وتسريع الأداء",
                            checked = settings.reduceMotion,
                            onCheckedChange = { AppSettingsManager.updatePreference(context, "reduce_motion", it.toString()) }
                        )
                        Divider(color = SmartLedgerColors.BorderGridLine)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isBannerError = false
                                        statusBannerText = viewModel.runDatabaseDiagnostics()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص سلامة البيانات", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.StateSuccess, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showTrashSheet = true },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("سلة المحذوفات (${deletedItems.size})", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                            shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                        ) {
                            Text("استعادة الإعدادات الافتراضية (دون مسح السجلات المالية)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (showEditIdentitySheet) {
            var tradeName by remember(identity) { mutableStateOf(identity?.TradeName ?: "الدفتر الذكي للتجارة العامة") }
            var phone1 by remember(identity) { mutableStateOf(identity?.Phone1 ?: "777000000") }
            var phone2 by remember(identity) { mutableStateOf(identity?.Phone2 ?: "") }
            var address by remember(identity) { mutableStateOf(identity?.Address ?: "صنعاء - شارع الزبيري") }
            var taxNo by remember(identity) { mutableStateOf(identity?.TaxNumber ?: "") }
            var crNo by remember(identity) { mutableStateOf(identity?.CommercialRegistration ?: "") }
            var header by remember(identity) { mutableStateOf(identity?.ReceiptHeader ?: "أهلاً وسهلاً بكم في متجرنا") }
            var footer by remember(identity) { mutableStateOf(identity?.ReceiptFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بفاتورة الشراء") }

            AdaptiveFormHost(
                mode = FormWindowMode.BOTTOM_SHEET,
                title = "تعديل هوية المنشأة والفواتير",
                subtitle = "تُطبع هذه البيانات في ترويسة وتذييل الإيصالات وكشوف الحساب",
                onDismiss = { showEditIdentitySheet = false },
                primaryButtonText = "حفظ",
                isPrimaryEnabled = tradeName.isNotBlank(),
                onPrimaryClick = {
                    viewModel.saveBusinessIdentity(tradeName, phone1, phone2, address, taxNo, crNo, header, footer)
                    showEditIdentitySheet = false
                    isBannerError = false
                    statusBannerText = "تم حفظ هوية المنشأة."
                }
            ) {
                SmartTextField(value = tradeName, onValueChange = { tradeName = it }, label = "الاسم التجاري للمنشأة", isRequired = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartTextField(value = phone1, onValueChange = { phone1 = it }, label = "رقم الهاتف الأساسي", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
                    SmartTextField(value = phone2, onValueChange = { phone2 = it }, label = "هاتف إضافي", keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f))
                }
                SmartTextField(value = address, onValueChange = { address = it }, label = "العنوان التجاري")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartTextField(value = taxNo, onValueChange = { taxNo = it }, label = "الرقم الضريبي (اختياري)", modifier = Modifier.weight(1f))
                    SmartTextField(value = crNo, onValueChange = { crNo = it }, label = "السجل التجاري (اختياري)", modifier = Modifier.weight(1f))
                }
                SmartTextField(value = header, onValueChange = { header = it }, label = "عبارة الترحيب أعلى الفاتورة")
                SmartTextField(value = footer, onValueChange = { footer = it }, label = "شروط الاستبدال أسفل الفاتورة", imeAction = ImeAction.Done)
            }
        }

        if (showEditDefaultUnitsSheet) {
            var sUnit by remember { mutableStateOf(settings.defaultSmallUnit) }
            var lUnit by remember { mutableStateOf(settings.defaultLargeUnit) }
            var factorTxt by remember { mutableStateOf(Formatters.formatQuantity(settings.defaultUnitFactor)) }
            var threshTxt by remember { mutableStateOf(Formatters.formatQuantity(settings.defaultAlertThreshold)) }

            AdaptiveFormHost(
                mode = FormWindowMode.COMPACT_DIALOG,
                title = "الوحدات الافتراضية للمخزون",
                subtitle = "تُقترح تلقائياً عند إضافة صنف جديد",
                onDismiss = { showEditDefaultUnitsSheet = false },
                primaryButtonText = "حفظ",
                isPrimaryEnabled = sUnit.isNotBlank() && Formatters.parseDoubleSafe(factorTxt, 1.0) >= 1.0,
                onPrimaryClick = {
                    AppSettingsManager.updatePreference(context, "default_small_unit", sUnit.trim())
                    AppSettingsManager.updatePreference(context, "default_large_unit", lUnit.trim())
                    AppSettingsManager.updatePreference(context, "default_unit_factor", Formatters.parseDoubleSafe(factorTxt, 24.0).toString())
                    AppSettingsManager.updatePreference(context, "default_alert_threshold", Formatters.parseDoubleSafe(threshTxt, 5.0).toString())
                    showEditDefaultUnitsSheet = false
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartTextField(value = sUnit, onValueChange = { sUnit = it }, label = "الوحدة الصغرى", modifier = Modifier.weight(1f))
                    SmartTextField(value = lUnit, onValueChange = { lUnit = it }, label = "الوحدة الكبرى", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartNumberField(value = factorTxt, onValueChange = { factorTxt = it }, label = "معامل التحويل", modifier = Modifier.weight(1f))
                    SmartNumberField(value = threshTxt, onValueChange = { threshTxt = it }, label = "حد تنبيه النفاد", imeAction = ImeAction.Done, modifier = Modifier.weight(1f))
                }
            }
        }

        if (showGoogleDriveSetupSheet) {
            var clientIdInput by remember { mutableStateOf(settings.googleDriveClientId) }
            var emailInput by remember { mutableStateOf(settings.googleDriveAccountEmail) }
            var accessTokenInput by remember { mutableStateOf("") }

            AdaptiveFormHost(
                mode = FormWindowMode.BOTTOM_SHEET,
                title = "إعداد وربط Google Drive السحابي",
                subtitle = "نطاق الصلاحيات المطلوب: drive.appdata فقط (معزول ولا يصل لملفاتك الشخصية)",
                onDismiss = { showGoogleDriveSetupSheet = false },
                primaryButtonText = "حفظ",
                isPrimaryEnabled = clientIdInput.isNotBlank() || accessTokenInput.isNotBlank(),
                onPrimaryClick = {
                    AppSettingsManager.updatePreference(context, "gdrive_client_id", clientIdInput.trim())
                    AppSettingsManager.updatePreference(context, "gdrive_email", emailInput.trim())
                    if (accessTokenInput.isNotBlank()) {
                        GoogleDriveBackupHelper.saveAccessToken(context, accessTokenInput.trim())
                        AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.READY.code)
                        isBannerError = false
                        statusBannerText = "تم حفظ رمز مصادقة Google Drive وأصبح الحساب جاهزاً."
                    } else {
                        AppSettingsManager.updatePreference(context, "gdrive_status", DriveConnectionStatus.REQUIRES_AUTH.code)
                    }
                    showGoogleDriveSetupSheet = false
                }
            ) {
                SmartTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = "البريد الإلكتروني لحساب Google (للعرض التعريفي)",
                    placeholder = "example@gmail.com",
                    keyboardType = KeyboardType.Email
                )
                SmartTextField(
                    value = clientIdInput,
                    onValueChange = { clientIdInput = it },
                    label = "معرف عميل OAuth 2.0 (Google Cloud Client ID)",
                    placeholder = "xxxx.apps.googleusercontent.com",
                    helperText = "مطلوب لفتح شاشة مصادقة Google الرسمية"
                )
                if (clientIdInput.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val authUri = GoogleDriveBackupHelper.buildGoogleOAuthUrl(clientIdInput)
                                context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح شاشة مصادقة Google الرسمية (OAuth 2.0)", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                SmartTextField(
                    value = accessTokenInput,
                    onValueChange = { accessTokenInput = it },
                    label = "رمز الوصول OAuth Access Token (اختياري)",
                    placeholder = "ya29....",
                    imeAction = ImeAction.Done
                )
            }
        }

        if (showDisconnectDriveConfirm) {
            ConfirmActionDialog(
                title = "فصل حساب Google Drive",
                message = "سيؤدي فصل الحساب إلى إيقاف النسخ السحابي المستقبلي ومسح رمز الجلسة من هذا الجهاز، دون حذف النسخ المحفوظة سابقاً في سحابة Google Drive.",
                confirmText = "فصل الحساب",
                onConfirm = {
                    GoogleDriveBackupHelper.disconnectAccount(context)
                    showDisconnectDriveConfirm = false
                    isBannerError = false
                    statusBannerText = "تم فصل حساب Google Drive."
                },
                onDismiss = { showDisconnectDriveConfirm = false }
            )
        }

        if (pendingRestoreReport != null) {
            val (report, executeRestore) = pendingRestoreReport!!
            AlertDialog(
                onDismissRequest = { pendingRestoreReport = null },
                containerColor = SmartLedgerColors.SurfaceDialog,
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusSheet),
                icon = {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = SmartLedgerColors.StateSuccess, modifier = Modifier.size(36.dp))
                },
                title = {
                    Text("تقرير فحص النسخة قبل الاستعادة", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("تم التحقق من سلامة الملف بنجاح:", fontWeight = FontWeight.Bold, color = SmartLedgerColors.StateSuccess)
                        Text("• الملف: ${report.fileName} (${Formatters.formatBytes(report.sizeBytes)})", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextPrimary)
                        Text("• تاريخ النسخة: ${report.createdAt}", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                        Text("• بصمة SHA-256: مطابقة (${report.actualSha256.take(12)}...)", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                        Text("• محتويات النسخة: ${report.accountsCount} حساب • ${report.productsCount} صنف • ${report.invoicesCount} فاتورة • ${report.operationsCount} قيد", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        Text(
                            "تنبيه: ستقوم الاستعادة باستبدال البيانات الحالية، وسيتم أخذ نسخة أمان تلقائية لبياناتك الحالية قبل الاستبدال.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.StateWarning
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingRestoreReport = null
                            executeRestore()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                    ) {
                        Text("استعادة البيانات", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestoreReport = null }) {
                        Text("إلغاء", color = SmartLedgerColors.TextMuted)
                    }
                }
            )
        }

        if (backupPendingDelete != null) {
            ConfirmActionDialog(
                title = "حذف ملف النسخة الاحتياطية",
                message = "هل تريد حذف ملف النسخة «${backupPendingDelete!!.fileName}» من الأرشيف المحلي؟",
                confirmText = "حذف النسخة",
                onConfirm = {
                    val path = backupPendingDelete!!.filePath
                    backupPendingDelete = null
                    if (path != null) {
                        scope.launch {
                            BackupRestoreHelper.deleteLocalBackup(path)
                            localBackups = BackupRestoreHelper.listLocalBackups(context)
                        }
                    }
                },
                onDismiss = { backupPendingDelete = null }
            )
        }

        if (showPrinterPickerSheet) {
            AlertDialog(
                onDismissRequest = { showPrinterPickerSheet = false },
                containerColor = SmartLedgerColors.SurfaceDialog,
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusSheet),
                title = { Text("اختر طابعة بلوتوث مقترنة", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary) },
                text = {
                    if (pairedPrinters.isEmpty()) {
                        Text("لا توجد أجهزة بلوتوث مقترنة حالياً. قم بإقران الطابعة من إعدادات البلوتوث أولاً.", color = SmartLedgerColors.TextSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pairedPrinters, key = { it.address }) { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                                        .background(SmartLedgerColors.SurfaceCard)
                                        .clickable {
                                            AppSettingsManager.updatePreference(context, "printer_name", dev.name)
                                            AppSettingsManager.updatePreference(context, "printer_address", dev.address)
                                            showPrinterPickerSheet = false
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dev.name, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                    Text(dev.address, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrinterPickerSheet = false }) {
                        Text("إغلاق", color = SmartLedgerColors.TextMuted)
                    }
                }
            )
        }

        if (showTrashSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTrashSheet = false },
                containerColor = SmartLedgerColors.SurfacePanel
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Text("سلة المحذوفات وسجل الإلغاء (${deletedItems.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (deletedItems.isEmpty()) {
                        Text("سلة المحذوفات فارغة.", color = SmartLedgerColors.TextMuted)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(deletedItems, key = { it.Id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SmartLedgerTokens.RadiusSmall))
                                        .background(SmartLedgerColors.SurfaceCard)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.Title} (${item.EntityType})", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                        Text("${item.Reason ?: "حذف"} • ${item.DeletedAt.take(16)}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                    }
                                    IconButton(onClick = { viewModel.deleteFromTrashPermanently(item.Id) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "حذف نهائي", tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showResetConfirmDialog) {
            ConfirmActionDialog(
                title = "استعادة الإعدادات الافتراضية",
                message = "سيتم إعادة ضبط تفضيلات العرض والمظهر والوحدات الافتراضية إلى الوضع القياسي دون المساس بأي حسابات أو أصناف أو فواتير مسجلة.",
                confirmText = "استعادة الإعدادات",
                onConfirm = {
                    AppSettingsManager.resetUiPreferencesToDefault(context)
                    showResetConfirmDialog = false
                    isBannerError = false
                    statusBannerText = "تمت استعادة الإعدادات الافتراضية."
                },
                onDismiss = { showResetConfirmDialog = false }
            )
        }
    }
}

@Composable
fun SettingsCardSection(
    title: String,
    statusSummary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        shape = RoundedCornerShape(SmartLedgerTokens.RadiusCard)
    ) {
        Column(modifier = Modifier.padding(SmartLedgerTokens.SpaceMD)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
            if (!statusSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(statusSummary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.AccentLight)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SmartLedgerColors.TextInverse,
                checkedTrackColor = SmartLedgerColors.AccentPrimary,
                uncheckedThumbColor = SmartLedgerColors.TextMuted,
                uncheckedTrackColor = SmartLedgerColors.SurfaceCardElevated
            )
        )
    }
}
