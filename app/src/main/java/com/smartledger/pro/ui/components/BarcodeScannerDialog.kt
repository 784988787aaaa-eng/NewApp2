package com.smartledger.pro.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.util.Formatters
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerDialog(
    title: String = "مسح أو إدخال الباركود",
    subtitle: String = "وجّه الكاميرا نحو الباركود أو أدخل الرمز يدوياً",
    initialManualValue: String = "",
    onBarcodeResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasCameraHardware = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf(initialManualValue) }
    var manualError by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        permissionDeniedExplicitly = !granted
    }

    LaunchedEffect(hasCameraHardware) {
        if (hasCameraHardware && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).clip(RoundedCornerShape(24.dp)),
            color = SmartLedgerColors.SurfaceDialog,
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasCameraHardware && hasCameraPermission && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = { torchEnabled = !torchEnabled; activeCamera?.cameraControl?.enableTorch(torchEnabled) },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(if (torchEnabled) SmartLedgerColors.StateWarningSubtle else SmartLedgerColors.SurfaceCardElevated)
                            ) {
                                Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "الفلاش", tint = if (torchEnabled) SmartLedgerColors.StateWarning else SmartLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp).clip(CircleShape).background(SmartLedgerColors.SurfaceCardElevated)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = SmartLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    !hasCameraHardware -> {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SmartLedgerColors.SurfaceCardElevated).padding(14.dp)) {
                            Text("لا تتوفر كاميرا على هذا الجهاز. يمكنك إدخال رقم الباركود يدوياً في الحقل أدناه.", style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.TextSecondary)
                        }
                    }
                    !hasCameraPermission -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SmartLedgerColors.SurfaceCardElevated).padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("إذن الكاميرا مطلوب لمسح الباركود ضوئياً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                            Text("يُستخدم الإذن حصرياً لقراءة رمز الباركود وتوفير الوقت.", style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(if (permissionDeniedExplicitly) "إعادة طلب الإذن" else "منح إذن الكاميرا", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    cameraError != null -> {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SmartLedgerColors.StateDangerSubtle).padding(12.dp)) {
                            Text(cameraError!!, style = MaterialTheme.typography.bodyMedium, color = SmartLedgerColors.StateDangerText)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(SmartLedgerColors.SurfaceWindow).border(1.5.dp, SmartLedgerColors.AccentPrimary, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CameraBarcodePreview(
                                onCameraBound = { activeCamera = it },
                                onCameraError = { cameraError = it },
                                onBarcodeDetected = { code ->
                                    if (AppSensoryConfig.enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBarcodeResult(code)
                                }
                            )
                            Box(modifier = Modifier.fillMaxWidth(0.75f).height(86.dp).border(1.5.dp, SmartLedgerColors.StateSuccess.copy(alpha = 0.85f), RoundedCornerShape(8.dp)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SmartTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = Formatters.normalizeDigits(it).trim(); manualError = null },
                    label = "رقم الباركود أو رمز الصنف (إدخال يدوي)",
                    placeholder = "مثال: 6281000123456",
                    errorMessage = manualError,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    onImeDone = {
                        if (manualBarcode.isNotBlank()) onBarcodeResult(manualBarcode.trim())
                        else manualError = "يرجى كتابة رقم الباركود أولاً"
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = rememberDebouncedClick {
                            if (manualBarcode.isBlank()) manualError = "يرجى إدخال رمز الباركود أو مسحه بالكاميرا"
                            else onBarcodeResult(manualBarcode.trim())
                        },
                        modifier = Modifier.weight(1.5f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اعتماد الرمز", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("إلغاء", color = SmartLedgerColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraBarcodePreview(
    onCameraBound: (Camera) -> Unit,
    onCameraError: (String) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isScanLocked = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            try { barcodeScanner.close(); cameraExecutor.shutdown() } catch (_: Exception) {}
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !isScanLocked.get()) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val validCode = barcodes.mapNotNull { it.rawValue?.trim() }.firstOrNull { it.isNotEmpty() }
                                    if (validCode != null && isScanLocked.compareAndSet(false, true)) {
                                        ContextCompat.getMainExecutor(ctx).execute { onBarcodeDetected(Formatters.normalizeDigits(validCode)) }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else imageProxy.close()
                    }
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    onCameraBound(camera)
                } catch (_: Exception) {
                    onCameraError("تعذر تشغيل الكاميرا: يمكنك استخدام الإدخال اليدوي أدناه.")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
