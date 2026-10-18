package com.smartledger.pro.ui.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * نموذج الحالات الصريح لدورة حياة نافذة الإدخال (Explicit Window Lifecycle State Machine)
 * التدفق الأساسي:
 * - Open -> EDITING -> VALIDATING -> SAVING -> SAVE_SUCCESS -> Close
 * - عند فشل التحقق: EDITING -> VALIDATION_ERROR (لا تُغلق النافذة ولا تضيع القيم)
 * - عند فشل الحفظ: SAVING -> SAVE_ERROR -> EDITING (لا تُغلق النافذة وتُعرض الرسالة مع بقاء القيم)
 * - عند طلب الإلغاء أو الرجوع مع وجود تعديلات: EDITING -> CONFIRMING_DISCARD
 */
enum class FormWindowLifecycleState {
    CLOSED,
    EDITING,
    VALIDATING,
    VALIDATION_ERROR,
    SAVING,
    SAVE_ERROR,
    CONFIRMING_DISCARD,
    SAVE_SUCCESS
}

/**
 * موفر سياق داخلي لربط طلب الإغلاق الخارجي (مثل زر الرجوع في أندرويد أو النقر خارج النافذة)
 * بحارس حماية البيانات داخل FormSheetContainer.
 */
internal val LocalModalCloseInterceptor = compositionLocalOf<((() -> Boolean) -> Unit)?> { null }

/**
 * نافذة عائمة حديثة ومستقلة (FloatingModalDialog)
 * - تعمل فوق الشاشة الحالية وتستقر تلقائياً فوق لوحة المفاتيح عبر `decorFitsSystemWindows = false` و`imePadding()`.
 * - تعترض زر الرجوع في النظام (Back Press) والنقر خارج النافذة وتوجههما إلى آلة الحالات في `FormSheetContainer`
 *   بحيث يستحيل فقدان التعديلات غير المحفوظة بصمت أو إغلاق النافذة أثناء عملية الحفظ الجارية.
 */
