package com.smartledger.pro.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import java.util.concurrent.Executors

/**
 * نافذة مسح الباركود الذكية العائمة (BarcodeScannerSheet)
 * - تعمل كنافذة عائمة فوق لوحة المفاتيح مع دعم الكاميرا الحية والإدخال اليدوي البديل.
 * - تحرر موارد CameraX (`unbindAll`) ومحلل ML Kit (`scanner.close`) فور إغلاق النافذة لمنع استنزاف البطارية أو قفل الكاميرا.
 * - تعالج حالة الرفض الدائم لإذن الكاميرا في أندرويد 11+ بتوفير زر مباشر لفتح إعدادات أذونات التطبيق في النظام.
 */
@Composable
fun BarcodeScannerSheet(
    title: String = "مسح أو إدخال الباركود",
    subtitle: String = "وجّه الكاميرا نحو الباركود أو أدخل الرمز يدوياً",
    knownBarcodes: List<Pair<String, String>> = emptyList(),
    onBarcodeResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by rememberSaveable { mutableStateOf(false) }
    var manualBarcode by rememberSaveable { mutableStateOf("") }
    var manualError by rememberSaveable { mutableStateOf<String?>(null) }
    var useCameraMode by rememberSaveable { mutableStateOf(hasCameraHardware && hasCameraPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        useCameraMode = granted && hasCameraHardware
        if (!granted) {
            permissionDeniedOnce = true
        }
    }

    val openSystemAppSettings: () -> Unit = {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    FloatingModalDialog(onDismissRequest = onDismiss) {
        FormSheetContainer(
            title = title,
            subtitle = subtitle,
            headerIcon = Icons.Default.QrCodeScanner,
            onDismiss = onDismiss,
            primaryButtonText = "تأكيد الرمز",
            isPrimaryEnabled = manualBarcode.trim().isNotEmpty(),
            onPrimaryClick = {
                val clean = manualBarcode.trim()
                if (clean.length < 2) {
                    manualError = "الرجاء إدخال رمز باركود صالح"
                } else {
                    if (AppSensoryConfig.enableHaptics) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onBarcodeResult(clean)
                }
            }
        ) {
            SegmentedOptionRow(
                options = listOf(
                    true to "المسح بالكاميرا",
                    false to "الإدخال اليدوي"
                ),
                selectedOption = useCameraMode,
                onOptionSelected = { wantCamera ->
                    if (wantCamera) {
                        val latestGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        hasCameraPermission = latestGranted
                        if (!hasCameraHardware) {
                            useCameraMode = false
                        } else if (latestGranted) {
                            useCameraMode = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        useCameraMode = false
                    }
                }
            )

            if (useCameraMode && hasCameraPermission && hasCameraHardware) {
                CameraBarcodePreviewBox(
                    onDetected = { code ->
                        if (AppSensoryConfig.enableHaptics) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onBarcodeResult(code)
                    }
                )
            } else if (!hasCameraHardware) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.StateWarningSubtle),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.StateWarningBorder))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null, tint = SmartLedgerColors.StateWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "لا تتوفر كاميرا نشطة على هذا الجهاز. يمكنك إدخال الباركود يدوياً أو اختياره من القائمة أدناه.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.TextPrimary
                        )
                    }
                }
            } else if (!hasCameraPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCardElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "نحتاج إذن الكاميرا لقراءة الباركود",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                        Text(
                            text = if (permissionDeniedOnce) {
                                "تم رفض إذن الكاميرا. يمكنك منح الإذن من نافذة النظام أو من إعدادات التطبيق، أو المتابعة بالإدخال اليدوي بالأسفل."
                            } else {
                                "يُستخدم إذن الكاميرا حصراً لقراءة باركود الأصناف أثناء البيع أو الجرد، ولا يتم التقاط أو حفظ أي صور."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                                shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("منح إذن الكاميرا", fontWeight = FontWeight.Bold)
                            }
                            if (permissionDeniedOnce) {
                                OutlinedButton(
                                    onClick = openSystemAppSettings,
                                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إعدادات التطبيق", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            SmartTextField(
                value = manualBarcode,
                onValueChange = {
                    manualBarcode = it
                    manualError = null
                },
                label = "رقم الباركود أو رمز الصنف",
                placeholder = "أدخل أرقام الباركود أو امسح بقارئ خارجي...",
                errorText = manualError,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                leadingIcon = Icons.Default.QrCode,
                onDoneAction = {
                    if (manualBarcode.isNotBlank()) {
                        onBarcodeResult(manualBarcode.trim())
                    }
                }
            )

            if (knownBarcodes.isNotEmpty()) {
                Text(
                    text = "رموز مسجلة في المخزن (اضغط للاختيار السريع):",
                    style = MaterialTheme.typography.labelSmall,
                    color = SmartLedgerColors.TextMuted
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(knownBarcodes) { (code, productName) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SmartLedgerColors.SurfaceCardElevated)
                                .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    manualBarcode = code
                                    onBarcodeResult(code)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$productName ($code)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SmartLedgerColors.AccentLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
