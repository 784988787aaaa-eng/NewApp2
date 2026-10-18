package com.smartledger.pro.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppThemeMode
import com.smartledger.pro.ui.theme.AppUiDensity
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.ui.viewmodels.SettingsViewModel
import com.smartledger.pro.util.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val availableMeasurementUnits by viewModel.units.collectAsState()
    val fieldVisibility by ScreenFieldVisibilityStore.observe(context).collectAsState()
    val identity by viewModel.businessIdentity.collectAsState()
    val indexedBackups by viewModel.indexedBackups.collectAsState()
    val deletedItems by viewModel.deletedItems.collectAsState()
    val biometricCapability = remember { BiometricLockHelper.checkBiometricSupport(context) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusBannerText by rememberSaveable { mutableStateOf<String?>(null) }
    var isBannerError by rememberSaveable { mutableStateOf(false) }

    var showEditIdentitySheet by rememberSaveable { mutableStateOf(false) }
    var showGoogleDriveConfigSheet by rememberSaveable { mutableStateOf(false) }
    var showPrinterPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showPinSetupSheet by rememberSaveable { mutableStateOf(false) }
    var showBackupsArchiveSheet by rememberSaveable { mutableStateOf(false) }
    var showTrashRecoverySheet by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showCategoriesSheet by rememberSaveable { mutableStateOf(false) }
    var showFinancialAccountsSheet by rememberSaveable { mutableStateOf(false) }
    var showUnitsWarehousesSheet by rememberSaveable { mutableStateOf(false) }

    var isWorkingOnBackup by remember { mutableStateOf(false) }
    var driveState by remember(settings.googleDriveAccountEmail, settings.googleDriveAccessToken, settings.lastCloudBackupTime) {
        mutableStateOf(
            when {
                settings.googleDriveAccessToken.isNotBlank() && settings.lastCloudBackupTime.isNotBlank() -> DriveConnectionState.SUCCESS
                settings.googleDriveAccessToken.isNotBlank() -> DriveConnectionState.READY
                settings.googleDriveAccountEmail.isNotBlank() -> DriveConnectionState.NEEDS_OAUTH_TOKEN
                else -> DriveConnectionState.DISCONNECTED
            }
        )
    }
    var uploadProgress by remember { mutableStateOf(0) }

    var pendingRestoreInspection by remember { mutableStateOf<BackupInspectionResult?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        if (destUri != null) {
            isWorkingOnBackup = true
            viewModel.exportBackupToSafUri(destUri) { res ->
                isWorkingOnBackup = false
                if (res.isSuccess) {
                    val rec = res.getOrThrow()
                    isBannerError = false
                    statusBannerText = "تم تصدير النسخة (${rec.fileName}) بحجم ${NumberFormatter.formatFileSize(rec.sizeBytes)} والتحقق من بصمة SHA-256."
                } else {
                    isBannerError = true
                    statusBannerText = res.exceptionOrNull()?.message ?: "تعذر تصدير النسخة الاحتياطية"
                }
            }
        }
    }

    var pendingBluetoothAction by rememberSaveable { mutableStateOf<String?>(null) }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingBluetoothAction == "PICK_PRINTER") {
                showPrinterPickerSheet = true
            }
        } else {
            isBannerError = true
            statusBannerText = "تم رفض إذن البلوتوث (الأجهزة القريبة)؛ يلزم منح الإذن للاتصال بالطابعة الحرارية."
        }
        pendingBluetoothAction = null
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { srcUri ->
        if (srcUri != null) {
            isWorkingOnBackup = true
            viewModel.inspectBackupFromSafUri(srcUri) { inspection ->
                isWorkingOnBackup = false
                if (inspection.isValid) {
                    pendingRestoreFile = null
                    pendingRestoreUri = srcUri
                    pendingRestoreInspection = inspection
                } else {
                    isBannerError = true
                    statusBannerText = inspection.errorMessage ?: "ملف النسخة الاحتياطية المختار غير صالح"
                }
            }
        }
    }

    val matchesSearch: (String) -> Boolean = { keywords ->
        searchQuery.isBlank() || keywords.contains(searchQuery.trim(), ignoreCase = true)
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        topBar = {
            SettingsScreenHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onResetDefaults = { showResetConfirmDialog = true }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SmartLedgerTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusFeedbackBanner(
                    message = statusBannerText,
                    isError = isBannerError,
                    onDismiss = { statusBannerText = null }
                )
            }

            if (matchesSearch("التصنيفات الهرمية الصناديق المحافظ البنوك التحويلات الوحدات المخازن المستودعات الفروع")) {
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.AccountTree,
                        title = "0. الفهارس المرنة والصناديق والمحافظ والمخازن",
                        statusSummary = "شجرة التصنيفات • الصناديق والتحويلات • الوحدات والمخازن"
                    ) {
                        Text(
                            text = "أدِر تصنيفاتك الهرمية، وصناديقك ومحافظك المالية والتحويلات الداخلية، ووحدات القياس والمخازن بمرونة كاملة دون بيانات مفروضة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showCategoriesSheet = true },
                                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Text("التصنيفات الهرمية", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { showFinancialAccountsSheet = true },
                                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Text("الصناديق والمحافظ", color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { showUnitsWarehousesSheet = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).pressScaleEffect(),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                        ) {
                            Icon(Icons.Default.Straighten, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إدارة وحدات القياس والمخازن والفروع", color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (matchesSearch("بيانات المنشأة الفواتير الاسم التجاري الهاتف العنوان الضريبي الترويسة التذييل تسلسل الأرقام INV PUR")) {
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Storefront,
                        title = "1. المنشأة وترويسة الفواتير",
                        statusSummary = "${identity?.TradeName ?: "الدفتر الذكي"} • بادئة: ${settings.invoicePrefix}"
                    ) {
                        Text(
                            text = "الهاتف: ${identity?.Phone1 ?: ""} • العنوان: ${identity?.Address ?: "صنعاء"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.TextSecondary
                        )
                        if (!identity?.TaxNumber.isNullOrBlank()) {
                            Text(
                                text = "الرقم الضريبي: ${identity?.TaxNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { showEditIdentitySheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .pressScaleEffect(),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحرير بيانات المنشأة وترويسة وتذييل الإيصال", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (matchesSearch("النسخ الاحتياطي المحلي استعادة أرشيف slprobackup zip بصمة SHA-256 حفظ")) {
                item {
                    val lastLocalStatus = if (settings.lastLocalBackupTime.isNotBlank()) {
                        "آخر نسخة: ${settings.lastLocalBackupTime} (${NumberFormatter.formatFileSize(settings.lastLocalBackupSize)})"
                    } else {
                        "لم يتم إنشاء نسخة محلية بعد"
                    }
                    SettingsSectionCard(
                        icon = Icons.Default.Backup,
                        title = "2. النسخ الاحتياطي المحلي والأرشيف (.slprobackup)",
                        statusSummary = "${indexedBackups.size} نسخ مفهرسة • $lastLocalStatus"
                    ) {
                        Text(
                            text = "يحفظ لقطة متماسكة من قاعدة البيانات مع بصمة التحقق الرقمية SHA-256 داخل مجلدات السنة والشهر (Backups/YYYY/MM).",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )

                        if (settings.lastLocalBackupSha256.isNotBlank()) {
                            Text(
                                text = "بصمة SHA-256 لآخر نسخة: ${settings.lastLocalBackupSha256.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.StateSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isWorkingOnBackup = true
                                    viewModel.createLocalArchiveBackup { res ->
                                        isWorkingOnBackup = false
                                        if (res.isSuccess) {
                                            val rec = res.getOrThrow()
                                            isBannerError = false
                                            statusBannerText = "تم حفظ النسخة في أرشيف (${rec.yearFolder}/${rec.monthFolder}) بحجم ${NumberFormatter.formatFileSize(rec.sizeBytes)}."
                                        } else {
                                            isBannerError = true
                                            statusBannerText = res.exceptionOrNull()?.message ?: "تعذر إنشاء النسخة الاحتياطية"
                                        }
                                    }
                                },
                                enabled = !isWorkingOnBackup,
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(enabled = !isWorkingOnBackup),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ فوري للأرشيف", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { showBackupsArchiveSheet = true },
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                            ) {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("أرشيف النسخ (${indexedBackups.size})", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    exportSafLauncher.launch(BackupRestoreHelper.generateBackupFileName())
                                },
                                enabled = !isWorkingOnBackup,
                                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(enabled = !isWorkingOnBackup),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                            ) {
                                Icon(Icons.Default.IosShare, contentDescription = null, tint = SmartLedgerColors.TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصدير ملف خارجي", color = SmartLedgerColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    importSafLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !isWorkingOnBackup,
                                modifier = Modifier.weight(1f).heightIn(min = 38.dp).pressScaleEffect(enabled = !isWorkingOnBackup),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                            ) {
                                Icon(Icons.Default.RestorePage, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استعادة من ملف خارجي", color = SmartLedgerColors.AccentLight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (matchesSearch("Google Drive جوجل درايف سحابي مزامنة حساب ربط تلقائي")) {
                item {
                    val cloudStatusText = when {
                        settings.lastCloudBackupTime.isNotBlank() ->
                            "${driveState.arabicLabel} • آخر رفع: ${settings.lastCloudBackupTime} (${NumberFormatter.formatFileSize(settings.lastCloudBackupSize)})"
                        settings.googleDriveAccountEmail.isNotBlank() ->
                            "${driveState.arabicLabel} (${settings.googleDriveAccountEmail})"
                        else -> driveState.arabicLabel
                    }

                    SettingsSectionCard(
                        icon = Icons.Default.CloudSync,
                        title = "3. النسخ الاحتياطي السحابي (Google Drive)",
                        statusSummary = cloudStatusText
                    ) {
                        SettingSwitchRow(
                            title = "تفعيل الجدولة التلقائية للنسخ الاحتياطي",
                            subtitle = "إنشاء نسخة احتياطية دورية كل ${settings.autoBackupIntervalHours} ساعة عند استقرار البيانات",
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(autoBackupEnabled = enabled) }
                            }
                        )

                        if (driveState == DriveConnectionState.UPLOADING) {
                            LinearProgressIndicator(
                                progress = uploadProgress / 100f,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = SmartLedgerColors.AccentLight,
                                trackColor = SmartLedgerColors.SurfaceCardElevated
                            )
                            Text(
                                text = "جارٍ رفع النسخة الاحتياطية إلى Google Drive ($uploadProgress%)...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showGoogleDriveConfigSheet = true },
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (settings.googleDriveAccountEmail.isBlank()) "ربط حساب Google Drive" else "إدارة حساب Drive",
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Button(
                                onClick = {
                                    if (settings.googleDriveAccessToken.isBlank()) {
                                        showGoogleDriveConfigSheet = true
                                    } else {
                                        driveState = DriveConnectionState.UPLOADING
                                        uploadProgress = 0
                                        viewModel.uploadBackupToGoogleDrive(
                                            onProgress = { p -> uploadProgress = p },
                                            onResult = { uploadRes ->
                                                if (uploadRes.isSuccess) {
                                                    val info = uploadRes.getOrThrow()
                                                    driveState = DriveConnectionState.SUCCESS
                                                    isBannerError = false
                                                    statusBannerText = "تم رفع النسخة إلى Google Drive (${NumberFormatter.formatFileSize(info.sizeBytes)}) بنجاح."
                                                } else {
                                                    driveState = DriveConnectionState.ERROR
                                                    isBannerError = true
                                                    statusBannerText = uploadRes.exceptionOrNull()?.message ?: "فشل الرفع السحابي"
                                                }
                                            }
                                        )
                                    }
                                },
                                enabled = driveState != DriveConnectionState.UPLOADING,
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(enabled = driveState != DriveConnectionState.UPLOADING),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("رفع سحابي الآن", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (matchesSearch("العملة التنسيق المنازل العشرية التقريب الأرقام ريال دولار ر.ي ر.س")) {
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Payments,
                        title = "4. العملة والمنازل العشرية والأرقام",
                        statusSummary = "العملة: ${settings.currencySymbol} • المنازل العشرية: ${settings.decimalPlaces} • الأرقام: غربية (123)"
                    ) {
                        Text(
                            text = "ملاحظة: تغيير رمز العملة أو عدد المنازل العشرية يضبط طريقة العرض والطباعة فقط ولا يغير القيم المحاسبية التاريخية المسجلة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )

                        FilterChipRow(
                            items = listOf(
                                "ر.ي" to "ريال يمني (ر.ي)",
                                "ر.س" to "ريال سعودي (ر.س)",
                                "$" to "دولار أمريكي ($)",
                                "د.إ" to "درهم إماراتي (د.إ)",
                                "ج.م" to "جنيه مصري (ج.م)"
                            ),
                            selectedItem = settings.currencySymbol,
                            onItemSelected = { curr ->
                                viewModel.updateSettings { it.copy(currencySymbol = curr) }
                            }
                        )

                        SegmentedOptionRow(
                            options = listOf(
                                0 to "أرقام صحيحة (بدون كسور)",
                                2 to "منزلتان عشريتان (0.00)"
                            ),
                            selectedOption = settings.decimalPlaces,
                            onOptionSelected = { places ->
                                viewModel.updateSettings { it.copy(decimalPlaces = places) }
                            }
                        )
                    }
                }
            }

            if (matchesSearch("المظهر الوضع الداكن الفاتح الخط حجم النص الكثافة الحركة الاهتزاز")) {
                item {
                    val themeLabel = when (settings.themeMode) {
                        AppThemeMode.DARK -> "داكن احترافي"
                        AppThemeMode.LIGHT -> "فاتح نهاري"
                        AppThemeMode.AMOLED -> "أسود عميق AMOLED"
                    }
                    val densityLabel = if (settings.uiDensity == AppUiDensity.COMPACT) "كثيف" else "مريح"

                    SettingsSectionCard(
                        icon = Icons.Default.Palette,
                        title = "5. المظهر، الكثافة البصرية، وسهولة الاستخدام",
                        statusSummary = "$themeLabel • عرض $densityLabel • حجم النص ${(settings.fontScale * 100).toInt()}%"
                    ) {
                        Text("نمط الألوان والسطوع:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                        SegmentedOptionRow(
                            options = listOf(
                                AppThemeMode.DARK to "داكن احترافي",
                                AppThemeMode.LIGHT to "فاتح نهاري",
                                AppThemeMode.AMOLED to "أسود AMOLED"
                            ),
                            selectedOption = settings.themeMode,
                            onOptionSelected = { mode ->
                                viewModel.updateSettings { it.copy(themeMode = mode) }
                            }
                        )

                        Text("الكثافة البصرية للشاشات:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                        SegmentedOptionRow(
                            options = listOf(
                                AppUiDensity.COMFORTABLE to "عرض مريح (مسافات أوسع)",
                                AppUiDensity.COMPACT to "عرض كثيف (عناصر أكثر)"
                            ),
                            selectedOption = settings.uiDensity,
                            onOptionSelected = { density ->
                                viewModel.updateSettings { it.copy(uiDensity = density) }
                            }
                        )

                        Text("مقياس حجم الخط:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                        SegmentedOptionRow(
                            options = listOf(
                                0.92f to "صغير (92%)",
                                1.0f to "قياسي (100%)",
                                1.1f to "كبير (110%)"
                            ),
                            selectedOption = settings.fontScale,
                            onOptionSelected = { scale ->
                                viewModel.updateSettings { it.copy(fontScale = scale) }
                            }
                        )

                        Divider(color = SmartLedgerColors.BorderSubtle)

                        SettingSwitchRow(
                            title = "الاهتزاز اللمسي التفاعلي (Haptic Feedback)",
                            subtitle = "استجابة لمسية خفيفة عند تنفيذ الإجراءات وقراءة الباركود",
                            checked = settings.enableHaptics,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(enableHaptics = enabled) }
                            }
                        )

                        SettingSwitchRow(
                            title = "تقليل المؤثرات الحركية (Reduce Motion)",
                            subtitle = "إيقاف حركات الانتقال والارتداد لتسريع الاستجابة الفورية",
                            checked = settings.reduceMotion,
                            onCheckedChange = { reduced ->
                                viewModel.updateSettings { it.copy(reduceMotion = reduced) }
                            }
                        )
                    }
                }
            }

            if (matchesSearch("المخزون الباركود موقع الرف البيع بالسالب الحد الأدنى تنبيه الائتمان الهاتف الوحدات قطعة كرتون")) {
                item {
                    SettingsSectionCard(
                        icon = Icons.Default.Inventory2,
                        title = "6. سياسات المخزون ونقطة البيع والحقول",
                        statusSummary = "البيع بالسالب: ${if (settings.allowNegativeStock) "مسموح" else "ممنوع"} • الوحدات الافتراضية: ${settings.defaultSmallUnit} / ${settings.defaultLargeUnit} (${NumberFormatter.formatQuantity(settings.defaultUnitFactor)})"
                    ) {
                        val smallUnitOptions = remember(availableMeasurementUnits, settings.defaultSmallUnit) {
                            (availableMeasurementUnits.map { it.Name } + settings.defaultSmallUnit)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .map { it to it }
                        }
                        val largeUnitOptions = remember(availableMeasurementUnits, settings.defaultLargeUnit) {
                            (availableMeasurementUnits.map { it.Name } + settings.defaultLargeUnit)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .map { it to it }
                        }
                        var defaultUnitFactorText by remember(settings.defaultUnitFactor) {
                            mutableStateOf(NumberFormatter.formatQuantity(settings.defaultUnitFactor))
                        }

                        CompactDropdownPicker(
                            label = "الوحدة الصغرى الافتراضية",
                            options = smallUnitOptions,
                            selectedOption = settings.defaultSmallUnit,
                            onOptionSelected = { unit ->
                                viewModel.updateSettings { it.copy(defaultSmallUnit = unit) }
                            },
                            leadingIcon = Icons.Default.Straighten
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactDropdownPicker(
                            label = "الوحدة الكبرى الافتراضية",
                            options = largeUnitOptions,
                            selectedOption = settings.defaultLargeUnit,
                            onOptionSelected = { unit ->
                                viewModel.updateSettings { it.copy(defaultLargeUnit = unit) }
                            },
                            leadingIcon = Icons.Default.Inventory2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartTextField(
                                value = defaultUnitFactorText,
                                onValueChange = { defaultUnitFactorText = it },
                                label = "عدد الصغرى في الكبرى",
                                placeholder = "24",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val factor = NumberFormatter.parseDouble(defaultUnitFactorText, 0.0)
                                    if (factor >= 1.0) {
                                        viewModel.updateSettings { it.copy(defaultUnitFactor = factor) }
                                        isBannerError = false
                                        statusBannerText = "تم حفظ معامل التحويل"
                                    } else {
                                        isBannerError = true
                                        statusBannerText = "أدخل معامل تحويل أكبر من صفر"
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "حفظ معامل التحويل", tint = SmartLedgerColors.AccentLight)
                            }
                        }
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "السماح بالبيع عند نفاد الكمية (الرصيد السالب)",
                            subtitle = "عند التعطيل، سيرفض النظام إصدار فاتورة مبيعات تتجاوز الرصيد المتاح في المخزن",
                            checked = settings.allowNegativeStock,
                            onCheckedChange = { allow ->
                                viewModel.updateSettings { it.copy(allowNegativeStock = allow) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "الباركود في المخزون",
                            checked = fieldVisibility.inventoryBarcode,
                            onCheckedChange = { show ->
                                ScreenFieldVisibilityStore.update(context) { it.copy(inventoryBarcode = show) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "الباركود في بطاقة الصنف",
                            checked = fieldVisibility.productBarcode,
                            onCheckedChange = { show ->
                                ScreenFieldVisibilityStore.update(context) { it.copy(productBarcode = show) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "موقع الرف في بطاقة الصنف",
                            checked = fieldVisibility.productShelf,
                            onCheckedChange = { show ->
                                ScreenFieldVisibilityStore.update(context) { it.copy(productShelf = show) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "الباركود في نقطة البيع",
                            checked = fieldVisibility.posBarcode,
                            onCheckedChange = { show ->
                                ScreenFieldVisibilityStore.update(context) { it.copy(posBarcode = show) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "التنبيه عند تجاوز سقف الائتمان للعميل",
                            subtitle = "إظهار تحذير في بطاقة الحساب ونقطة البيع عند تجاوز الحد الائتماني",
                            checked = settings.warnOnCreditLimit,
                            onCheckedChange = { warn ->
                                viewModel.updateSettings { it.copy(warnOnCreditLimit = warn) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        SettingSwitchRow(
                            title = "إظهار زر الاتصال السريع في قائمة الحسابات",
                            subtitle = "عرض أيقونة الاتصال المباشر بجوار رصيد الحساب",
                            checked = settings.showPhoneInAccounts,
                            onCheckedChange = { show ->
                                viewModel.updateSettings { it.copy(showPhoneInAccounts = show) }
                            }
                        )
                        Divider(color = SmartLedgerColors.BorderSubtle)
                        Text(
                            text = "مفتاح الدولة الافتراضي لرسائل واتساب (${settings.defaultCountryCode}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = SmartLedgerColors.TextSecondary
                        )
                        FilterChipRow(
                            items = listOf(
                                "+967" to "اليمن (+967)",
                                "+966" to "السعودية (+966)",
                                "+20" to "مصر (+20)",
                                "+971" to "الإمارات (+971)",
                                "+964" to "العراق (+964)",
                                "+962" to "الأردن (+962)"
                            ),
                            selectedItem = settings.defaultCountryCode,
                            onItemSelected = { code ->
                                viewModel.updateSettings { it.copy(defaultCountryCode = code) }
                            }
                        )
                    }
                }
            }

            if (matchesSearch("الطباعة طابعة بلوتوث حرارية 80mm 58mm نسخ اختبار ESC POS")) {
                item {
                    val printerSummary = if (settings.savedPrinterName.isNotBlank()) {
                        "${settings.savedPrinterName} • مقاس ${settings.printerPaperWidthMm}mm • ${settings.printerCopies} نسخة"
                    } else {
                        "غير مقترنة بطابعة محددة • مقاس ${settings.printerPaperWidthMm}mm"
                    }

                    var isTestingPrinter by remember { mutableStateOf(false) }

                    SettingsSectionCard(
                        icon = Icons.Default.Print,
                        title = "7. طابعة الفواتير الحرارية (ESC/POS)",
                        statusSummary = printerSummary
                    ) {
                        Text("مقاس ورق الطابعة الحرارية:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                        SegmentedOptionRow(
                            options = listOf(
                                80 to "مقاس 80mm (48 حرفاً)",
                                58 to "مقاس 58mm (32 حرفاً)"
                            ),
                            selectedOption = settings.printerPaperWidthMm,
                            onOptionSelected = { width ->
                                viewModel.updateSettings { it.copy(printerPaperWidthMm = width) }
                            }
                        )

                        Text("عدد النسخ المطبوعة لكل فاتورة:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                        SegmentedOptionRow(
                            options = listOf(
                                1 to "نسخة واحدة (1)",
                                2 to "نسختان (2)",
                                3 to "3 نسخ"
                            ),
                            selectedOption = settings.printerCopies,
                            onOptionSelected = { copies ->
                                viewModel.updateSettings { it.copy(printerCopies = copies) }
                            }
                        )

                        SettingSwitchRow(
                            title = "الطباعة التلقائية فور حفظ فاتورة المبيعات",
                            subtitle = "إرسال الإيصال مباشرة للطابعة المحفوظة بعد الترحيل",
                            checked = settings.autoPrintAfterSale,
                            onCheckedChange = { auto ->
                                viewModel.updateSettings { it.copy(autoPrintAfterSale = auto) }
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val reqPerm = ThermalPrinterHelper.requiredBluetoothRuntimePermission()
                                    if (reqPerm != null && !ThermalPrinterHelper.hasBluetoothConnectPermission(context)) {
                                        pendingBluetoothAction = "PICK_PRINTER"
                                        bluetoothPermissionLauncher.launch(reqPerm)
                                    } else {
                                        showPrinterPickerSheet = true
                                    }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اختيار طابعة بلوتوث", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    val reqPerm = ThermalPrinterHelper.requiredBluetoothRuntimePermission()
                                    if (reqPerm != null && !ThermalPrinterHelper.hasBluetoothConnectPermission(context)) {
                                        pendingBluetoothAction = "TEST_PRINTER"
                                        bluetoothPermissionLauncher.launch(reqPerm)
                                        return@Button
                                    }
                                    isTestingPrinter = true
                                    scope.launch {
                                        val testRes = ThermalPrinterHelper.testPrinterConnection(
                                            context = context,
                                            printerAddress = settings.savedPrinterAddress,
                                            paperWidthMm = settings.printerPaperWidthMm,
                                            tradeName = identity?.TradeName ?: "الدفتر الذكي Pro"
                                        )
                                        isTestingPrinter = false
                                        if (testRes.isSuccess) {
                                            isBannerError = false
                                            statusBannerText = testRes.getOrThrow()
                                        } else {
                                            isBannerError = true
                                            statusBannerText = testRes.exceptionOrNull()?.message ?: "فشل اختبار الاتصال بالطابعة"
                                        }
                                    }
                                },
                                enabled = !isTestingPrinter,
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).pressScaleEffect(enabled = !isTestingPrinter),
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = SmartLedgerColors.TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTestingPrinter) "جارٍ الاختبار..." else "اختبار طباعة فعلي",
                                    color = SmartLedgerColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            if (matchesSearch("الأمان الخصوصية القفل البيومتري بصمة الإصبع رمز الحماية PIN مهلة القفل")) {
                item {
                    val securitySummary = if (settings.biometricLockEnabled) {
                        "القفل مفعّل (مهلة ${settings.lockTimeoutMinutes} د) • ${if (settings.hasPinConfigured) "بصمة رمز PIN مملحة (PBKDF2)" else "بصمة الجهاز"}"
                    } else {
                        "القفل غير مفعّل • ${biometricCapability.description}"
                    }

                    SettingsSectionCard(
                        icon = Icons.Default.Security,
                        title = "8. الأمان والخصوصية والقياسات الحيوية",
                        statusSummary = securitySummary
                    ) {
                        SettingSwitchRow(
                            title = "تفعيل قفل التطبيق (بصمة الإصبع / قفل الجهاز / PIN)",
                            subtitle = biometricCapability.description,
                            checked = settings.biometricLockEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(biometricLockEnabled = enabled) }
                                if (enabled && !settings.hasPinConfigured && !biometricCapability.canUseBiometric) {
                                    showPinSetupSheet = true
                                }
                            }
                        )

                        if (settings.biometricLockEnabled) {
                            Text("مهلة القفل التلقائي بعد مغادرة التطبيق:", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.TextSecondary)
                            SegmentedOptionRow(
                                options = listOf(
                                    0 to "فوري",
                                    1 to "1 دقيقة",
                                    5 to "5 دقائق",
                                    15 to "15 دقيقة"
                                ),
                                selectedOption = settings.lockTimeoutMinutes,
                                onOptionSelected = { mins ->
                                    viewModel.updateSettings { it.copy(lockTimeoutMinutes = mins) }
                                }
                            )

                            OutlinedButton(
                                onClick = { showPinSetupSheet = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).pressScaleEffect(),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                            ) {
                                Icon(Icons.Default.Password, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!settings.hasPinConfigured) "تعيين رمز حماية شخصي احتياطي (PIN)" else "تغيير أو إزالة رمز الحماية (PIN)",
                                    color = SmartLedgerColors.AccentLight,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Divider(color = SmartLedgerColors.BorderSubtle)

                        SettingSwitchRow(
                            title = "إخفاء شاشة التطبيق من معاينة التطبيقات الأخيرة",
                            subtitle = "تفعيل حماية النافذة (FLAG_SECURE) لمنع التقاط الشاشة أو ظهور الأرصدة في الخلفية",
                            checked = settings.hideSensitivePreviewInRecents,
                            onCheckedChange = { hide ->
                                viewModel.updateSettings { it.copy(hideSensitivePreviewInRecents = hide) }
                            }
                        )
                    }
                }
            }

            if (matchesSearch("سلة المحذوفات الأرشيف استعادة حساب مؤرشف صنف محذوف نفقة استرجاع سجل التدقيق")) {
                item {
                    val restorableCount = deletedItems.count { it.EntityType in listOf("Account", "Product", "Expense") }
                    SettingsSectionCard(
                        icon = Icons.Default.RestoreFromTrash,
                        title = "9. سلة المحذوفات والأرشيف واستعادة السجلات",
                        statusSummary = "${deletedItems.size} سجل محفوظ • $restorableCount قابل للاستعادة المباشرة"
                    ) {
                        Text(
                            text = "يعرض الحسابات والأصناف المؤرشفة والنفقات المحذوفة والقيود العكسية الموثقة، ويتيح استعادة أي حساب أو صنف أو نفقة بضغطة واحدة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextMuted
                        )
                        Button(
                            onClick = { showTrashRecoverySheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .pressScaleEffect(),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "فتح سلة المحذوفات والأرشيف (${deletedItems.size})",
                                color = SmartLedgerColors.AccentLight,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (showCategoriesSheet) {
            CategoryHierarchyManagerSheet(
                viewModel = viewModel,
                onClose = { showCategoriesSheet = false }
            )
        }

        if (showFinancialAccountsSheet) {
            FinancialAccountsAndTransfersSheet(
                viewModel = viewModel,
                onClose = { showFinancialAccountsSheet = false }
            )
        }

        if (showUnitsWarehousesSheet) {
            UnitsAndWarehousesManagerSheet(
                viewModel = viewModel,
                onClose = { showUnitsWarehousesSheet = false }
            )
        }

        // نافذة تعديل هوية المنشأة والفواتير العائمة فوق لوحة المفاتيح
        if (showEditIdentitySheet) {
            FloatingModalDialog(onDismissRequest = { showEditIdentitySheet = false }) {
                var tradeName by rememberSaveable { mutableStateOf(identity?.TradeName ?: "نشاطي التجاري") }
                var phone by rememberSaveable { mutableStateOf(identity?.Phone1 ?: "") }
                var address by rememberSaveable { mutableStateOf(identity?.Address ?: "") }
                var taxNo by rememberSaveable { mutableStateOf(identity?.TaxNumber ?: "") }
                var header by rememberSaveable { mutableStateOf(identity?.ReceiptHeader ?: "أهلاً وسهلاً بكم في متجرنا") }
                var footer by rememberSaveable { mutableStateOf(identity?.ReceiptFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بفاتورة الشراء") }
                var invPrefix by rememberSaveable { mutableStateOf(settings.invoicePrefix) }
                var purPrefix by rememberSaveable { mutableStateOf(settings.purchasePrefix) }

                FormSheetContainer(
                    title = "تحرير بيانات المنشأة والفواتير",
                    subtitle = "تظهر هذه البيانات في ترويسة وتذييل جميع الفواتير والتقارير",
                    headerIcon = Icons.Default.Storefront,
                    onDismiss = { showEditIdentitySheet = false },
                    primaryButtonText = "حفظ بيانات المنشأة",
                    isPrimaryEnabled = tradeName.trim().isNotEmpty(),
                    hasUnsavedChanges = tradeName != (identity?.TradeName ?: "نشاطي التجاري") ||
                        phone != (identity?.Phone1 ?: ""),
                    onPrimaryClick = {
                        viewModel.saveBusinessIdentity(
                            tradeName = tradeName,
                            phone = phone,
                            address = address,
                            taxNumber = taxNo,
                            receiptHeader = header,
                            receiptFooter = footer,
                            invoicePrefix = invPrefix,
                            purchasePrefix = purPrefix
                        )
                        showEditIdentitySheet = false
                        isBannerError = false
                        statusBannerText = "تم حفظ بيانات المنشأة وترويسة الفواتير بنجاح."
                    }
                ) {
                    FormSectionGroup(title = "1. الهوية التجارية وبيانات الاتصال") {
                        SmartTextField(
                            value = tradeName,
                            onValueChange = { tradeName = it },
                            label = "الاسم التجاري للمنشأة",
                            isRequired = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmartTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = "رقم الهاتف الأساسي",
                                keyboardType = KeyboardType.Phone,
                                modifier = Modifier.weight(1f)
                            )
                            SmartTextField(
                                value = taxNo,
                                onValueChange = { taxNo = it },
                                label = "الرقم الضريبي (اختياري)",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SmartTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = "العنوان التجاري"
                        )
                    }

                    FormSectionGroup(title = "2. تسلسل الفواتير وترويسة الإيصال") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmartTextField(
                                value = invPrefix,
                                onValueChange = { invPrefix = it },
                                label = "بادئة فواتير البيع",
                                placeholder = "INV",
                                modifier = Modifier.weight(1f)
                            )
                            SmartTextField(
                                value = purPrefix,
                                onValueChange = { purPrefix = it },
                                label = "بادئة فواتير الشراء",
                                placeholder = "PUR",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SmartTextField(
                            value = header,
                            onValueChange = { header = it },
                            label = "عبارة الترحيب أعلى الفاتورة"
                        )
                        SmartTextField(
                            value = footer,
                            onValueChange = { footer = it },
                            label = "شروط الاستبدال أسفل الفاتورة",
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        }

        // نافذة ربط وإدارة Google Drive العائمة فوق لوحة المفاتيح
        if (showGoogleDriveConfigSheet) {
            FloatingModalDialog(onDismissRequest = { showGoogleDriveConfigSheet = false }) {
                var emailText by rememberSaveable { mutableStateOf(settings.googleDriveAccountEmail) }
                var tokenText by rememberSaveable { mutableStateOf(settings.googleDriveAccessToken) }

                FormSheetContainer(
                    title = "إعداد وربط Google Drive",
                    subtitle = "النطاق الآمن: ${GoogleDriveBackupHelper.REQUIRED_OAUTH_SCOPE}",
                    headerIcon = Icons.Default.CloudSync,
                    onDismiss = { showGoogleDriveConfigSheet = false },
                    primaryButtonText = "حفظ إعدادات الربط",
                    onPrimaryClick = {
                        viewModel.updateSettings {
                            it.copy(
                                googleDriveAccountEmail = emailText.trim(),
                                googleDriveAccessToken = tokenText.trim()
                            )
                        }
                        showGoogleDriveConfigSheet = false
                        isBannerError = false
                        statusBannerText = if (tokenText.isNotBlank()) {
                            "تم ربط حساب Google Drive وتشفير رمز الوصول عبر AndroidKeyStore بنجاح."
                        } else if (emailText.isNotBlank()) {
                            "تم حفظ البريد الإلكتروني. يلزم توفير رمز OAuth 2.0 للرفع المباشر عبر API."
                        } else {
                            "تم فصل حساب Google Drive."
                        }
                    }
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "حول مصادقة Google Drive السحابية وتشفير الرموز:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.AccentLight
                            )
                            Text(
                                text = "• يستخدم التطبيق نطاق الصلاحية المعزول (drive.appdata) لحفظ نسخك داخل مجلد التطبيق الخاص دون الوصول لأي ملفات أخرى في حسابك.\n• يُشفّر رمز الوصول محلياً بواسطة مفتاح AES-GCM محمي داخل AndroidKeyStore ولا يُحفظ كنص صريح.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.TextSecondary
                            )
                        }
                    }

                    FormSectionGroup(title = "بيانات الربط والمصادقة") {
                        SmartTextField(
                            value = emailText,
                            onValueChange = { emailText = it },
                            label = "البريد الإلكتروني لحساب Google المرتبط",
                            placeholder = "example@gmail.com",
                            keyboardType = KeyboardType.Email
                        )

                        SmartTextField(
                            value = tokenText,
                            onValueChange = { tokenText = it },
                            label = "رمز الوصول OAuth 2.0 (Access Token - يُشفّر عبر Keystore)",
                            placeholder = "ya29.a0...",
                            helperText = "لا نطلب كلمة مرور Google مطلقاً. يُستخدم الرمز لمصادقة طلبات Drive API v3.",
                            imeAction = ImeAction.Done
                        )
                    }

                    if (settings.googleDriveAccountEmail.isNotBlank() || settings.googleDriveAccessToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        googleDriveAccountEmail = "",
                                        googleDriveAccessToken = ""
                                    )
                                }
                                showGoogleDriveConfigSheet = false
                                isBannerError = false
                                statusBannerText = "تم فصل الحساب السحابي مع الحفاظ على النسخ السابقة."
                            },
                            modifier = Modifier.fillMaxWidth().pressScaleEffect(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.StateDanger),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.StateDangerBorder))
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فصل الحساب السحابي", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // نافذة اختيار طابعة البلوتوث العائمة فوق لوحة المفاتيح
        if (showPrinterPickerSheet) {
            val pairedResult = remember { ThermalPrinterHelper.getPairedPrinters(context) }
            val printers = pairedResult.getOrDefault(emptyList())
            val errorMsg = pairedResult.exceptionOrNull()?.message

            FloatingModalDialog(onDismissRequest = { showPrinterPickerSheet = false }) {
                var manualAddress by rememberSaveable { mutableStateOf(settings.savedPrinterAddress) }
                var manualName by rememberSaveable { mutableStateOf(settings.savedPrinterName) }

                FormSheetContainer(
                    title = "اختيار طابعة الفواتير الحرارية",
                    subtitle = "اختر من أجهزة البلوتوث المقترنة أو أدخل عنوان MAC يدوياً",
                    headerIcon = Icons.Default.Print,
                    onDismiss = { showPrinterPickerSheet = false },
                    primaryButtonText = "حفظ الطابعة المختارة",
                    onPrimaryClick = {
                        viewModel.updateSettings {
                            it.copy(
                                savedPrinterName = manualName.trim().ifBlank { "طابعة حرارية" },
                                savedPrinterAddress = manualAddress.trim()
                            )
                        }
                        showPrinterPickerSheet = false
                        isBannerError = false
                        statusBannerText = "تم حفظ إعداد الطابعة الحرارية."
                    }
                ) {
                    if (!errorMsg.isNullOrBlank()) {
                        Text(errorMsg, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateWarning)
                    } else if (printers.isEmpty()) {
                        Text("لا توجد طابعات بلوتوث مقترنة حالياً. قم بإقران الطابعة من إعدادات البلوتوث في الجهاز أولاً.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextMuted)
                    } else {
                        printers.forEach { dev ->
                            val isSelected = manualAddress.equals(dev.address, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SmartLedgerColors.AccentSubtle else SmartLedgerColors.SurfaceCardElevated)
                                    .border(1.dp, if (isSelected) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        manualName = dev.name
                                        manualAddress = dev.address
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dev.name, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                    Text(dev.address, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                                }
                            }
                        }
                    }

                    FormSectionGroup(title = "تحديد الطابعة") {
                        SmartTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = "اسم الطابعة التعريفي",
                            placeholder = "مثال: Xprinter XP-80"
                        )
                        SmartTextField(
                            value = manualAddress,
                            onValueChange = { manualAddress = it },
                            label = "عنوان البلوتوث (MAC Address)",
                            placeholder = "00:11:22:33:44:55",
                            imeAction = ImeAction.Done
                        )
                    }
                }
            }
        }

        // نافذة إعداد رمز الحماية الشخصي العائمة فوق لوحة المفاتيح (يُحفظ كبصمة مملحة PBKDF2)
        if (showPinSetupSheet) {
            FloatingModalDialog(onDismissRequest = { showPinSetupSheet = false }) {
                var pinText by rememberSaveable { mutableStateOf("") }
                val isPinValid = pinText.isBlank() || pinText.length in 4..8

                FormSheetContainer(
                    title = "إعداد رمز الحماية الشخصي (PIN)",
                    subtitle = "يُحفظ كبصمة مشفرة مملحة (PBKDF2-SHA256) ولا يُخزن كنص صريح",
                    headerIcon = Icons.Default.Password,
                    onDismiss = { showPinSetupSheet = false },
                    primaryButtonText = if (pinText.isBlank()) "إزالة رمز PIN" else "تشفير وحفظ رمز الحماية",
                    isPrimaryEnabled = isPinValid,
                    onPrimaryClick = {
                        if (isPinValid) {
                            val cleanPin = pinText.trim()
                            viewModel.updateAppPin(cleanPin)
                            showPinSetupSheet = false
                            isBannerError = false
                            statusBannerText = if (cleanPin.isNotBlank()) {
                                "تم تشفير وحفظ بصمة رمز الحماية الشخصي (PBKDF2) بنجاح."
                            } else {
                                "تم إزالة رمز الحماية الشخصي."
                            }
                        }
                    }
                ) {
                    SmartTextField(
                        value = pinText,
                        onValueChange = { pinText = NumberFormatter.normalizeNumericInput(it, allowDecimal = false).take(8) },
                        label = "رمز الحماية الرقمي الجديد (4 إلى 8 أرقام)",
                        placeholder = "اتركه فارغاً لإزالة الرمز والاعتماد على البصمة فقط",
                        errorText = if (pinText.isNotEmpty() && pinText.length < 4) "يجب ألا يقل رمز PIN عن 4 أرقام" else null,
                        keyboardType = KeyboardType.NumberPassword,
                        autoFocusOnAppear = true,
                        imeAction = ImeAction.Done
                    )
                }
            }
        }

        // نافذة أرشيف النسخ الاحتياطية المفهرسة العائمة
        if (showBackupsArchiveSheet) {
            var recordIdToDelete by rememberSaveable { mutableStateOf<String?>(null) }
            val recordToDelete = remember(indexedBackups, recordIdToDelete) { recordIdToDelete?.let { id -> indexedBackups.firstOrNull { it.id == id } } }

            FloatingModalDialog(onDismissRequest = { showBackupsArchiveSheet = false }) {
                FormSheetContainer(
                    title = "أرشيف النسخ الاحتياطية المفهرسة",
                    subtitle = "منظمة تلقائياً حسب العام والشهر (Backups/YYYY/MM)",
                    headerIcon = Icons.Default.FolderSpecial,
                    onDismiss = { showBackupsArchiveSheet = false },
                    primaryButtonText = "إغلاق الأرشيف",
                    onPrimaryClick = { showBackupsArchiveSheet = false }
                ) {
                    if (indexedBackups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد نسخ محفوظة في الأرشيف بعد.", color = SmartLedgerColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(indexedBackups, key = { it.id }) { rec ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rec.yearFolder} / ${rec.monthFolder}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.AccentLight,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = NumberFormatter.formatFileSize(rec.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.StateSuccess,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(rec.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                        Text(
                                            text = "التاريخ: ${rec.createdAt} • حسابات: ${rec.accountsCount} • أصناف: ${rec.productsCount} • فواتير: ${rec.invoicesCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextMuted
                                        )
                                        Text(
                                            text = "SHA-256: ${rec.sha256.take(20)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SmartLedgerColors.TextSecondary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = { recordIdToDelete = rec.id },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("حذف", color = SmartLedgerColors.StateDanger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    val f = File(rec.filePath)
                                                    viewModel.inspectBackupArchiveFile(f) { insp ->
                                                        if (insp.isValid) {
                                                            showBackupsArchiveSheet = false
                                                            pendingRestoreFile = f
                                                            pendingRestoreUri = null
                                                            pendingRestoreInspection = insp
                                                        } else {
                                                            isBannerError = true
                                                            statusBannerText = insp.errorMessage ?: "تعذر التحقق من النسخة"
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp).pressScaleEffect()
                                            ) {
                                                Text("فحص واستعادة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (recordToDelete != null) {
                val targetRec = recordToDelete!!
                ConfirmActionDialog(
                    title = "حذف النسخة الاحتياطية من الأرشيف؟",
                    message = "سيتم حذف ملف النسخة (${targetRec.fileName}) نهائياً من أرشيف الجهاز.",
                    confirmText = "حذف النسخة",
                    onConfirm = {
                        viewModel.deleteIndexedBackup(targetRec.id) {
                            recordIdToDelete = null
                        }
                    },
                    onDismiss = { recordIdToDelete = null }
                )
            }
        }

        // نافذة سلة المحذوفات والأرشيف واستعادة السجلات العائمة (UX-001)
        if (showTrashRecoverySheet) {
            var trashFilter by rememberSaveable { mutableStateOf("ALL") }
            var trashItemIdToDeletePermanently by rememberSaveable { mutableStateOf<Long?>(null) }
            val itemToDeleteForever = remember(deletedItems, trashItemIdToDeletePermanently) {
                trashItemIdToDeletePermanently?.let { id -> deletedItems.firstOrNull { it.Id == id } }
            }
            val filteredDeleted = remember(deletedItems, trashFilter) {
                when (trashFilter) {
                    "Account" -> deletedItems.filter { it.EntityType == "Account" }
                    "Product" -> deletedItems.filter { it.EntityType == "Product" }
                    "Expense" -> deletedItems.filter { it.EntityType == "Expense" }
                    else -> deletedItems
                }
            }

            FloatingModalDialog(onDismissRequest = { showTrashRecoverySheet = false }) {
                FormSheetContainer(
                    title = "سلة المحذوفات والأرشيف",
                    subtitle = "استعادة الحسابات والأصناف المؤرشفة والمصروفات المحذوفة",
                    headerIcon = Icons.Default.RestoreFromTrash,
                    onDismiss = { showTrashRecoverySheet = false },
                    primaryButtonText = "إغلاق السلة",
                    onPrimaryClick = { showTrashRecoverySheet = false }
                ) {
                    FilterChipRow(
                        items = listOf(
                            "ALL" to "الكل (${deletedItems.size})",
                            "Account" to "الحسابات",
                            "Product" to "الأصناف",
                            "Expense" to "المصروفات"
                        ),
                        selectedItem = trashFilter,
                        onItemSelected = { trashFilter = it }
                    )

                    if (filteredDeleted.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد سجلات مؤرشفة أو محذوفة ضمن هذا التصنيف.", color = SmartLedgerColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDeleted, key = { it.Id }) { item ->
                                val canRestore = item.EntityType in listOf("Account", "Product", "Expense")
                                val typeLabel = when (item.EntityType) {
                                    "Account" -> "حساب مؤرشف"
                                    "Product" -> "صنف مؤرشف"
                                    "Expense" -> "نفقة محذوفة"
                                    "OperationReversal" -> "قيد عكسي محاسبي موثق"
                                    else -> item.EntityType
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = typeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (canRestore) SmartLedgerColors.AccentLight else SmartLedgerColors.StateWarning,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = item.DeletedAt.take(16),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.TextMuted
                                            )
                                        }

                                        Text(
                                            text = item.Title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.TextPrimary
                                        )
                                        if (!item.Reason.isNullOrBlank()) {
                                            Text(
                                                text = "السبب: ${item.Reason}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SmartLedgerColors.TextSecondary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = { trashItemIdToDeletePermanently = item.Id },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("حذف نهائي", color = SmartLedgerColors.StateDanger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                            if (canRestore) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = {
                                                        viewModel.restoreDeletedItem(item) { res ->
                                                            if (res.isSuccess) {
                                                                isBannerError = false
                                                                statusBannerText = res.getOrThrow()
                                                            } else {
                                                                isBannerError = true
                                                                statusBannerText = res.exceptionOrNull()?.message ?: "تعذر استعادة السجل"
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateSuccess),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(32.dp).pressScaleEffect()
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("استعادة", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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

            if (itemToDeleteForever != null) {
                val targetDel = itemToDeleteForever!!
                ConfirmActionDialog(
                    title = "حذف السجل نهائياً من سلة المحذوفات؟",
                    message = "سيتم مسح النسخة المؤرشفة لـ «${targetDel.Title}» من سجل المحذوفات.",
                    confirmText = "حذف نهائي",
                    onConfirm = {
                        viewModel.deleteDeletedItemPermanently(targetDel.Id) {
                            trashItemIdToDeletePermanently = null
                        }
                    },
                    onDismiss = { trashItemIdToDeletePermanently = null }
                )
            }
        }

        // نافذة تأكيد الاستعادة الآمنة العائمة
        if (pendingRestoreInspection != null) {
            val insp = pendingRestoreInspection!!
            FloatingModalDialog(
                onDismissRequest = {
                    pendingRestoreInspection = null
                    pendingRestoreFile = null
                    pendingRestoreUri = null
                },
                maxWidth = 460.dp
            ) {
                FormSheetContainer(
                    title = "اجتازت النسخة فحص السلامة بنجاح",
                    subtitle = "تاريخ النسخة: ${insp.createdAt}",
                    headerIcon = Icons.Default.VerifiedUser,
                    onDismiss = {
                        pendingRestoreInspection = null
                        pendingRestoreFile = null
                        pendingRestoreUri = null
                    },
                    primaryButtonText = "تأكيد وبدء الاستعادة الآمنة",
                    onPrimaryClick = {
                        val fileToRestore = pendingRestoreFile
                        val uriToRestore = pendingRestoreUri
                        pendingRestoreInspection = null
                        isWorkingOnBackup = true
                        viewModel.restoreDatabase(fileToRestore, uriToRestore) { res ->
                            isWorkingOnBackup = false
                            if (res.isSuccess) {
                                isBannerError = false
                                statusBannerText = "تمت استعادة قاعدة البيانات بنجاح بعد إنشاء نقطة أمان مسبقة."
                            } else {
                                isBannerError = true
                                statusBannerText = res.exceptionOrNull()?.message ?: "فشلت عملية الاستعادة"
                            }
                        }
                    }
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("حجم قاعدة البيانات: ${NumberFormatter.formatFileSize(insp.dbSizeBytes)}", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextSecondary)
                            Text(
                                text = if (insp.isChecksumMatched) {
                                    "${insp.checksumStatus.arabicDescription} (${insp.actualSha256.take(16)}...)"
                                } else {
                                    insp.checksumStatus.arabicDescription
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (insp.isChecksumMatched) SmartLedgerColors.StateSuccess else SmartLedgerColors.StateWarning,
                                fontWeight = FontWeight.Bold
                            )
                            if (insp.accountsCount > 0 || insp.productsCount > 0 || insp.invoicesCount > 0) {
                                Text("السجلات: ${insp.accountsCount} حساب • ${insp.productsCount} صنف • ${insp.invoicesCount} فاتورة", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.AccentLight)
                            }
                        }
                    }
                    Text(
                        text = "تنبيه: ستقوم الاستعادة باستبدال البيانات الحالية ببيانات هذه النسخة. سيقوم النظام تلقائياً بحفظ نسخة أمان (Pre-Restore Safety) لبياناتك الحالية قبل الاستبدال.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.TextSecondary
                    )
                }
            }
        }

        if (showResetConfirmDialog) {
            ConfirmActionDialog(
                title = "استعادة الإعدادات الافتراضية؟",
                message = "سيتم إعادة ضبط تفضيلات المظهر، العملة، الحقول، والطباعة إلى القيم الافتراضية الآمنة دون المساس بأي حسابات أو فواتير أو أصناف مسجلة أو قفل الحماية.",
                confirmText = "استعادة الافتراضيات",
                onConfirm = {
                    viewModel.resetPreferencesToDefault()
                    ScreenFieldVisibilityStore.reset(context)
                    showResetConfirmDialog = false
                    isBannerError = false
                    statusBannerText = "تمت استعادة الإعدادات الافتراضية مع الحفاظ على كافة السجلات المالية وإعدادات الحماية."
                },
                onDismiss = { showResetConfirmDialog = false }
            )
        }
    }
}
