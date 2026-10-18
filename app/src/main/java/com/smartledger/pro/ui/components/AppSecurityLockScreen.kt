package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens
import com.smartledger.pro.util.BiometricLockHelper
import com.smartledger.pro.util.NumberFormatter
import com.smartledger.pro.util.SecurityCryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * بوابة الدخول والأمان الفاخرة (Sovereign Security Gate - AppSecurityLockScreen)
 * - تدعم لوحة مفاتيح رقمية ذكية مدمجة (Tactile Glass Keypad) مع نقاط PIN متوهجة.
 * - تدعم التبديل السلس إلى حقل الإدخال العادي أو البصمة البيومترية بضغطة واحدة.
 * - تحافظ بالكامل على التحقق المشفر PBKDF2-SHA256 وقفل المحاولات المتدرج.
 */
@Composable
fun AppSecurityLockScreen(
    expectedPinVerifier: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val view = LocalView.current
    val capability = remember { BiometricLockHelper.checkBiometricSupport(context) }

    val scope = rememberCoroutineScope()
    var pinInput by rememberSaveable { mutableStateOf("") }
    var useTactilePad by rememberSaveable { mutableStateOf(true) }
    var isVerifyingPin by remember { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableStateOf(0) }
    var lockoutRemainingSeconds by rememberSaveable { mutableStateOf(0) }
    var lockoutUntilRealtime by rememberSaveable { mutableStateOf(0L) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(lockoutUntilRealtime) {
        while (lockoutUntilRealtime > SystemClock.elapsedRealtime()) {
            val remaining = ((lockoutUntilRealtime - SystemClock.elapsedRealtime() + 999L) / 1000L).toInt()
            lockoutRemainingSeconds = remaining.coerceAtLeast(0)
            delay(250L)
        }
        lockoutRemainingSeconds = 0
    }

    val verifyEnteredPin: () -> Unit = {
        if (lockoutRemainingSeconds > 0) {
            errorMessage = "تم إيقاف المحاولات مؤقتاً لحماية الحساب. انتظر $lockoutRemainingSeconds ثانية."
            AppHaptics.perform(view, AppHapticType.ERROR)
        } else if (!isVerifyingPin && pinInput.length >= 4) {
            isVerifyingPin = true
            val candidatePin = pinInput
            scope.launch {
                val matched = withContext(Dispatchers.Default) {
                    SecurityCryptoHelper.verifyPin(candidatePin, expectedPinVerifier)
                }
                isVerifyingPin = false
                if (matched) {
                    failedAttempts = 0
                    lockoutUntilRealtime = 0L
                    lockoutRemainingSeconds = 0
                    AppHaptics.perform(view, AppHapticType.CONFIRM)
                    onUnlocked()
                } else {
                    failedAttempts++
                    pinInput = ""
                    val penaltySec = SecurityCryptoHelper.calculateLockoutDurationSeconds(failedAttempts)
                    if (penaltySec > 0) {
                        lockoutUntilRealtime = SystemClock.elapsedRealtime() + (penaltySec * 1000L)
                        lockoutRemainingSeconds = penaltySec
                        errorMessage = "تجاوزت الحد المسموح للمحاولات ($failedAttempts). يرجى الانتظار $penaltySec ثانية."
                    } else {
                        val remainingTries = (5 - failedAttempts).coerceAtLeast(1)
                        errorMessage = "رمز الحماية غير صحيح (متبقي $remainingTries محاولات قبل الإيقاف المؤقت)"
                    }
                    AppHaptics.perform(view, AppHapticType.ERROR)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (activity != null && capability.canUseBiometric) {
            BiometricLockHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = {
                    failedAttempts = 0
                    lockoutUntilRealtime = 0L
                    onUnlocked()
                },
                onError = { err -> errorMessage = err }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SmartLedgerColors.AccentSubtle.copy(alpha = 0.45f),
                        SmartLedgerColors.SurfaceWindow,
                        SmartLedgerColors.SurfaceWindow
                    )
                )
            )
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfacePanel),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SmartLedgerColors.AccentLight.copy(alpha = 0.5f),
                        SmartLedgerColors.BorderSubtle
                    )
                )
            ),
            shape = RoundedCornerShape(SmartLedgerTokens.sheetCorner),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // أيقونة الدرع المالي المتوهجة
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SmartLedgerColors.AccentPrimary.copy(alpha = 0.3f),
                                    SmartLedgerColors.AccentSubtle
                                )
                            )
                        )
                        .border(1.5.dp, SmartLedgerColors.AccentLight.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SmartLedgerColors.AccentLight,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "بوابة الأمان — الدفتر الذكي Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SmartLedgerColors.TextPrimary
                    )
                    Text(
                        text = "بياناتك المالية والمخزنية مشفرة ومحمية. أدخل رمز المرور أو استخدم البصمة للدخول.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.TextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                if (expectedPinVerifier.isNotBlank()) {
                    // نقاط مؤشر الإدخال المتوهجة (Glowing Security Orbs)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        val orbCount = pinInput.length.coerceAtLeast(4).coerceAtMost(8)
                        for (i in 0 until orbCount) {
                            val filled = i < pinInput.length
                            val dotColor by animateColorAsState(
                                targetValue = when {
                                    lockoutRemainingSeconds > 0 -> SmartLedgerColors.StateDanger
                                    filled -> SmartLedgerColors.AccentLight
                                    else -> SmartLedgerColors.SurfaceCardElevated
                                },
                                label = "pinOrb"
                            )
                            Box(
                                modifier = Modifier
                                    .size(if (filled) 15.dp else 13.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        1.dp,
                                        if (filled) SmartLedgerColors.AccentPrimary else SmartLedgerColors.BorderStrong,
                                        CircleShape
                                    )
                            )
                        }
                    }

                    if (lockoutRemainingSeconds > 0 || !errorMessage.isNullOrBlank()) {
                        Surface(
                            color = SmartLedgerColors.StateDangerSubtle,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SmartLedgerColors.StateDangerBorder)
                        ) {
                            Text(
                                text = if (lockoutRemainingSeconds > 0) {
                                    "يرجى الانتظار $lockoutRemainingSeconds ثانية قبل المحاولة مجدداً"
                                } else errorMessage.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = SmartLedgerColors.StateDangerText,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (useTactilePad) {
                        // لوحة المفاتيح الرقمية الفاخرة المدمجة (Tactile Glass Keypad)
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rows.forEach { rowDigits ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowDigits.forEach { digit ->
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .bounceClick(enabled = lockoutRemainingSeconds == 0) {
                                                    if (pinInput.length < 8) {
                                                        pinInput += digit
                                                        errorMessage = null
                                                    }
                                                },
                                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                            color = SmartLedgerColors.SurfaceCardElevated,
                                            border = BorderStroke(1.dp, SmartLedgerColors.BorderSubtle)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = digit,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SmartLedgerColors.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // الصف الأخير: بصمة / 0 / مسح
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .bounceClick {
                                            if (activity != null && capability.canUseBiometric) {
                                                BiometricLockHelper.showBiometricPrompt(
                                                    activity = activity,
                                                    onSuccess = {
                                                        failedAttempts = 0
                                                        lockoutUntilRealtime = 0L
                                                        onUnlocked()
                                                    },
                                                    onError = { err -> errorMessage = err }
                                                )
                                            } else {
                                                useTactilePad = false
                                            }
                                        },
                                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                    color = SmartLedgerColors.AccentSubtle,
                                    border = BorderStroke(1.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.5f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (capability.canUseBiometric) Icons.Default.Fingerprint else Icons.Default.Keyboard,
                                            contentDescription = "البصمة أو لوحة المفاتيح",
                                            tint = SmartLedgerColors.AccentLight,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .bounceClick(enabled = lockoutRemainingSeconds == 0) {
                                            if (pinInput.length < 8) {
                                                pinInput += "0"
                                                errorMessage = null
                                            }
                                        },
                                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                    color = SmartLedgerColors.SurfaceCardElevated,
                                    border = BorderStroke(1.dp, SmartLedgerColors.BorderSubtle)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "0",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = SmartLedgerColors.TextPrimary
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .bounceClick(enabled = pinInput.isNotEmpty()) {
                                            if (pinInput.isNotEmpty()) {
                                                pinInput = pinInput.dropLast(1)
                                            }
                                        },
                                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                                    color = SmartLedgerColors.SurfaceCardElevated,
                                    border = BorderStroke(1.dp, SmartLedgerColors.BorderSubtle)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "حذف رقم",
                                            tint = SmartLedgerColors.StateWarning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        SmartTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = NumberFormatter.normalizeNumericInput(it, allowDecimal = false).take(8)
                                if (lockoutRemainingSeconds == 0) {
                                    errorMessage = null
                                }
                            },
                            label = "رمز الحماية الشخصي (PIN)",
                            placeholder = "أدخل الرمز الرقمي...",
                            enabled = lockoutRemainingSeconds == 0,
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                            onDoneAction = { verifyEnteredPin() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { verifyEnteredPin() },
                            enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .pressScaleEffect(enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0),
                            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                            shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lockoutRemainingSeconds > 0) "مقفل مؤقتاً ($lockoutRemainingSeconds ث)" else "فتح القفل والدخول",
                                fontWeight = FontWeight.Bold,
                                color = SmartLedgerColors.TextInverse
                            )
                        }
                    }
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SmartLedgerColors.StateDanger,
                        textAlign = TextAlign.Center
                    )
                }

                if (activity != null && capability.canUseBiometric) {
                    OutlinedButton(
                        onClick = {
                            BiometricLockHelper.showBiometricPrompt(
                                activity = activity,
                                onSuccess = {
                                    failedAttempts = 0
                                    lockoutUntilRealtime = 0L
                                    onUnlocked()
                                },
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .pressScaleEffect(),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SmartLedgerColors.AccentLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المصادقة السريعة بالبصمة / قفل الجهاز", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                    }
                } else if (expectedPinVerifier.isBlank()) {
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = SmartLedgerColors.StateWarning,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.pressScaleEffect(),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text("المتابعة إلى التطبيق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
