package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * حقل إدخال عصري وذكي (SmartTextField)
 * - يفصل التسمية العلوية (Label) فوق حقل الإدخال لمنع تشابك أو اقتصاص التسميات العربية.
 * - يدعم `BringIntoViewRequester` بحيث يتم تمرير الحقل النشط تلقائياً ليظهر كاملاً فوق لوحة المفاتيح وشريط الأزرار.
 * - يدعم التركيز التلقائي الاختياري (`autoFocusOnAppear`) للنماذج السريعة فقط دون إرباك المستخدم في الشاشات الأخرى.
 * - يضبط `ImeAction.Done` لإخفاء لوحة المفاتيح بأمان دون تنفيذ عملية حفظ مالية غير مقصودة.
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
        } else if (!isVerifyingPin) {
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
            .background(SmartLedgerColors.SurfaceWindow)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SmartLedgerColors.AccentSubtle)
                        .border(1.5.dp, SmartLedgerColors.AccentPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SmartLedgerColors.AccentLight,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "الدفتر الذكي Pro محمي",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SmartLedgerColors.TextPrimary
                )

                Text(
                    text = "لحماية سجلاتك المالية، يرجى تأكيد الهوية بالبصمة أو إدخال رمز المرور.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.TextMuted,
                    textAlign = TextAlign.Center
                )

                if (expectedPinVerifier.isNotBlank()) {
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
                        errorText = if (lockoutRemainingSeconds > 0) {
                            "يرجى الانتظار $lockoutRemainingSeconds ثانية قبل المحاولة مجدداً"
                        } else errorMessage,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onDoneAction = { verifyEnteredPin() }
                    )

                    Button(
                        onClick = { verifyEnteredPin() },
                        enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .pressScaleEffect(enabled = pinInput.length >= 4 && lockoutRemainingSeconds == 0),
                        colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                        shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner)
                    ) {
                        Text(
                            text = if (lockoutRemainingSeconds > 0) "مقفل مؤقتاً ($lockoutRemainingSeconds ث)" else "تحقق وفتح القفل",
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextInverse
                        )
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
                        Text("المصادقة ببصمة الإصبع / قفل الجهاز", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
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

/**
 * حقل الإدخال الذكي بالاقتراحات التلقائية من بيانات المستخدم (SmartAutocompleteField).
 * يعرض اقتراحات فورية أثناء الكتابة (للتصنيفات، الوحدات، المخازن، الصناديق، أو الأوصاف المتكررة)
 * دون فرض أي بيانات ثابتة مسبقة.
 */
