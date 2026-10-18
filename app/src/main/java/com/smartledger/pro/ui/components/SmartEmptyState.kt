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
fun SmartEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    isFiltered: Boolean = false,
    onClearFilters: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SmartLedgerColors.BorderSubtle)),
        shape = RoundedCornerShape(SmartLedgerTokens.cardCorner)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SmartLedgerColors.SurfaceCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFiltered) Icons.Default.SearchOff else icon,
                    contentDescription = null,
                    tint = SmartLedgerColors.AccentLight,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isFiltered) "لا توجد نتائج مطابقة" else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SmartLedgerColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isFiltered) "جرب تعديل كلمات البحث أو مسح الفلاتر النشطة لعرض كافة السجلات." else subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SmartLedgerColors.TextMuted,
                textAlign = TextAlign.Center
            )

            if (isFiltered && onClearFilters != null) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SmartLedgerColors.AccentPrimary))
                ) {
                    Icon(Icons.Default.FilterAltOff, contentDescription = null, tint = SmartLedgerColors.AccentLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مسح البحث والفلاتر", color = SmartLedgerColors.AccentLight, fontWeight = FontWeight.Bold)
                }
            } else if (!actionText.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.AccentPrimary),
                    shape = RoundedCornerShape(SmartLedgerTokens.buttonCorner),
                    modifier = Modifier.pressScaleEffect()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionText, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextInverse)
                }
            }
        }
    }
}

/**
 * حوار تأكيد العمليات الحساسة أو المدمرة (ConfirmActionDialog) — نافذة قرار قصيرة ومستقلة
 */
