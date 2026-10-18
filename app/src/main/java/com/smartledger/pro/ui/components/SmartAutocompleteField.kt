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
fun SmartAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isRequired: Boolean = false,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val filteredSuggestions = remember(value, suggestions) {
        val clean = value.trim()
        val distinct = suggestions.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) {
            distinct.take(6)
        } else {
            distinct.filter {
                it.contains(clean, ignoreCase = true) && !it.equals(clean, ignoreCase = true)
            }.take(6)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SmartTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            isRequired = isRequired,
            leadingIcon = leadingIcon,
            focusRequester = focusRequester,
            nextFocusRequester = nextFocusRequester,
            imeAction = imeAction
        )

        if (filteredSuggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredSuggestions, key = { it }) { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SmartLedgerColors.SurfaceCardElevated)
                            .border(1.dp, SmartLedgerColors.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onValueChange(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.AccentLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
