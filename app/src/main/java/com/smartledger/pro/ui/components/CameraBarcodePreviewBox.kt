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
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun CameraBarcodePreviewBox(
    onDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysisRef by remember { mutableStateOf<ImageAnalysis?>(null) }
    var lastDetectedTime by remember { mutableStateOf(0L) }
    var lastDetectedCode by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { imageAnalysisRef?.clearAnalyzer() }
            runCatching { cameraProviderRef?.unbindAll() }
            runCatching { barcodeScanner.close() }
            runCatching { analysisExecutor.shutdown() }
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
                            cameraProviderRef = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            imageAnalysisRef = imageAnalysis

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(image)
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
