package com.smartledger.pro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

enum class FormWindowMode { COMPACT_DIALOG, BOTTOM_SHEET, FULL_SCREEN }

@Composable
fun FormSheetContainer(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    primaryButtonText: String = "حفظ",
    secondaryButtonText: String = "إلغاء",
    primaryButtonColor: Color = SmartLedgerColors.AccentPrimary,
    isPrimaryEnabled: Boolean = true,
    isSaving: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    errorMessage: String? = null,
    headerTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    onPrimaryClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val requestSafeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (hasUnsavedChanges && !isSaving) showDiscardConfirm = true
        else if (!isSaving) onDismiss()
    }

    BackHandler(enabled = hasUnsavedChanges || isSaving) { requestSafeDismiss() }

    val safePrimaryClick = rememberDebouncedClick(debounceMs = 500L) {
        if (isPrimaryEnabled && !isSaving) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onPrimaryClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                }
            }
            if (headerTrailingContent != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { headerTrailingContent() }
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = requestSafeDismiss,
                enabled = !isSaving,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = SmartLedgerColors.BorderSubtle.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(12.dp))

        if (!errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SmartLedgerTokens.RadiusButton))
                    .background(SmartLedgerColors.StateDangerSubtle)
                    .border(1.dp, SmartLedgerColors.StateDangerBorder, RoundedCornerShape(SmartLedgerTokens.RadiusButton))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SmartLedgerColors.StateDanger, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateDangerText, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = safePrimaryClick,
                enabled = isPrimaryEnabled && !isSaving,
                modifier = Modifier.weight(1.6f).height(SmartLedgerTokens.ButtonHeightPrimary),
                colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor, disabledContainerColor = SmartLedgerColors.SurfaceCardElevated),
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SmartLedgerColors.TextInverse)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جارٍ الحفظ...", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(primaryButtonText, fontWeight = FontWeight.Bold, color = if (isPrimaryEnabled) SmartLedgerColors.TextInverse else SmartLedgerColors.TextDisabled, style = MaterialTheme.typography.bodyLarge)
                }
            }

            OutlinedButton(
                onClick = requestSafeDismiss,
                enabled = !isSaving,
                modifier = Modifier.weight(1f).height(SmartLedgerTokens.ButtonHeightPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SmartLedgerColors.TextMuted),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
                shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
            ) {
                Text(secondaryButtonText, fontWeight = FontWeight.SemiBold, color = SmartLedgerColors.TextSecondary)
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = SmartLedgerColors.SurfaceDialog,
            shape = RoundedCornerShape(SmartLedgerTokens.RadiusSheet),
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SmartLedgerColors.StateWarning) },
            title = { Text("تعديلات غير محفوظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary) },
            text = { Text("لديك بيانات مدخلة لم يتم حفظها بعد. هل تريد إغلاق النافذة وتجاهل هذه التعديلات؟", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { showDiscardConfirm = false; onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.StateDanger),
                    shape = RoundedCornerShape(SmartLedgerTokens.RadiusButton)
                ) { Text("تجاهل وإغلاق", fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("متابعة التحرير", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.SemiBold) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveFormHost(
    mode: FormWindowMode = FormWindowMode.BOTTOM_SHEET,
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    primaryButtonText: String = "حفظ",
    primaryButtonColor: Color = SmartLedgerColors.AccentPrimary,
    isPrimaryEnabled: Boolean = true,
    isSaving: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    errorMessage: String? = null,
    headerTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    onPrimaryClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    when (mode) {
        FormWindowMode.COMPACT_DIALOG -> {
            Dialog(
                onDismissRequest = { if (!hasUnsavedChanges && !isSaving) onDismiss() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 560.dp),
                    shape = RoundedCornerShape(SmartLedgerTokens.RadiusSheet),
                    color = SmartLedgerColors.SurfaceDialog,
                    tonalElevation = 12.dp
                ) {
                    FormSheetContainer(title, subtitle, onDismiss, primaryButtonText, "إلغاء", primaryButtonColor, isPrimaryEnabled, isSaving, hasUnsavedChanges, errorMessage, headerTrailingContent, onPrimaryClick, content)
                }
            }
        }
        FormWindowMode.FULL_SCREEN -> {
            Dialog(
                onDismissRequest = { if (!hasUnsavedChanges && !isSaving) onDismiss() },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize().statusBarsPadding(), color = SmartLedgerColors.SurfaceWindow) {
                    FormSheetContainer(title, subtitle, onDismiss, primaryButtonText, "إلغاء", primaryButtonColor, isPrimaryEnabled, isSaving, hasUnsavedChanges, errorMessage, headerTrailingContent, onPrimaryClick, content)
                }
            }
        }
        FormWindowMode.BOTTOM_SHEET -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SmartLedgerColors.SurfacePanel, tonalElevation = 16.dp) {
                FormSheetContainer(title, subtitle, onDismiss, primaryButtonText, "إلغاء", primaryButtonColor, isPrimaryEnabled, isSaving, hasUnsavedChanges, errorMessage, headerTrailingContent, onPrimaryClick, content)
            }
        }
    }
}
