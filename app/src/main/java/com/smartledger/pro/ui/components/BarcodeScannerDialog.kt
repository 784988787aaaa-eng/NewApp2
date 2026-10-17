package com.smartledger.pro.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
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
 * تعمل كنافذة عائمة فوق لوحة المفاتيح مع دعم الكاميرا الحية والإدخال اليدوي البديل.
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
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var useCameraMode by remember { mutableStateOf(hasCameraHardware && hasCameraPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        useCameraMode = granted && hasCameraHardware
        if (!granted) {
            permissionDeniedOnce = true
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
                        if (!hasCameraHardware) {
                            useCameraMode = false
                        } else if (hasCameraPermission) {
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
                                "تم رفض إذن الكاميرا. يمكنك منح الإذن لقراءة الرموز تلقائياً أو المتابعة بالإدخال اليدوي بالأسفل."
                            } else {
                                "يُستخدم إذن الكاميرا حصراً لقراءة باركود الأصناف أثناء البيع أو الجرد، ولا يتم التقاط أو حفظ أي صور."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("منح إذن الكاميرا وبدء المسح", fontWeight = FontWeight.Bold)
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

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun CameraBarcodePreviewBox(
    onDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var lastDetectedTime by remember { mutableStateOf(0L) }
    var lastDetectedCode by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            try {
                analysisExecutor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SmartLedgerColors.SurfaceInput)
            .border(1.5.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(14.dp))
    ) {
        if (cameraError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SmartLedgerColors.StateWarning)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = cameraError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextSecondary
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val scanner = BarcodeScanning.getClient()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val rawValue = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                            if (!rawValue.isNullOrBlank()) {
                                                val now = SystemClock.elapsedRealtime()
                                                if (rawValue != lastDetectedCode || (now - lastDetectedTime) > 1500L) {
                                                    lastDetectedCode = rawValue
                                                    lastDetectedTime = now
                                                    onDetected(rawValue)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraRef = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            cameraError = "تعذر تشغيل معاينة الكاميرا، يرجى استخدام الإدخال اليدوي بالأسفل."
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 190.dp, height = 88.dp)
                    .border(2.dp, SmartLedgerColors.AccentLight.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            )

            if (cameraRef?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        cameraRef?.cameraControl?.enableTorch(torchEnabled)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.SurfaceOverlay)
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "إضاءة الفلاش",
                        tint = if (torchEnabled) SmartLedgerColors.StateWarning else SmartLedgerColors.TextInverse,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
