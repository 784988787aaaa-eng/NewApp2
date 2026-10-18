package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.ui.components.*
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.viewmodels.SettingsViewModel
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.launch

/**
 * معالج الإعداد الأولي الرشيق (FirstRunOnboardingDialog) — خطوة واحدة اختيارية دون فرض أي بيانات تجارية وهمية.
 */
@Composable
fun FirstRunOnboardingDialog(
    defaultCurrency: String = "ر.ي",
    onComplete: (tradeName: String, currencySymbol: String, initialCashbox: String?, initialWarehouse: String?) -> Unit,
    onSkip: () -> Unit
) {
    var tradeName by rememberSaveable { mutableStateOf("") }
    var currencySymbol by rememberSaveable { mutableStateOf(defaultCurrency) }
    var cashboxName by rememberSaveable { mutableStateOf("") }
    var warehouseName by rememberSaveable { mutableStateOf("") }

    FloatingModalDialog(onDismissRequest = onSkip) {
        FormSheetContainer(
            title = "مرحباً بك في الدفتر الذكي Pro",
            subtitle = "إعداد سريع في خطوة واحدة (اختياري بالكامل — يمكنك تخطيه أو تعديله لاحقاً)",
            headerIcon = Icons.Default.AutoAwesome,
            onDismiss = onSkip,
            primaryButtonText = "حفظ وبدء الاستخدام",
            secondaryButtonText = "تخطي الآن",
            onPrimaryClick = {
                onComplete(
                    tradeName.trim(),
                    currencySymbol.trim().ifBlank { "ر.ي" },
                    cashboxName.trim().ifBlank { null },
                    warehouseName.trim().ifBlank { null }
                )
            }
        ) {
            FormSectionGroup(title = "1. هوية نشاطك والعملة الأساسية") {
                SmartTextField(
                    value = tradeName,
                    onValueChange = { tradeName = it },
                    label = "اسم المتجر أو النشاط التجاري (اختياري)",
                    placeholder = "يظهر في ترويسة الفواتير والتقارير...",
                    leadingIcon = Icons.Default.Storefront
                )

                FilterChipRow(
                    items = listOf(
                        "ر.ي" to "ريال يمني (ر.ي)",
                        "ر.س" to "ريال سعودي (ر.س)",
                        "$" to "دولار أمريكي ($)",
                        "ج.م" to "جنيه مصري (ج.م)",
                        "د.ع" to "دينار عراقي (د.ع)"
                    ),
                    selectedItem = currencySymbol,
                    onItemSelected = { currencySymbol = it }
                )

                SmartTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = "رمز العملة المعتمد",
                    placeholder = "ر.ي"
                )
            }

            ExpandableAdvancedSection(
                title = "2. تأسيس أول صندوق ومخزن (اختياري)",
                subtitle = "اتركهما فارغين إذا كنت تفضل إضافتهما لاحقاً بأسمائك الخاصة",
                initiallyExpanded = true
            ) {
                SmartTextField(
                    value = cashboxName,
                    onValueChange = { cashboxName = it },
                    label = "اسم الصندوق النقدي أو المحفظة الأولى (اختياري)",
                    placeholder = "مثال: صندوق المحل، محفظة جوالي، كاشير 1...",
                    leadingIcon = Icons.Default.AccountBalanceWallet
                )
                SmartTextField(
                    value = warehouseName,
                    onValueChange = { warehouseName = it },
                    label = "اسم المخزن الأول (اختياري)",
                    placeholder = "مثال: معرض البيع، المستودع المركزي...",
                    leadingIcon = Icons.Default.Inventory2,
                    imeAction = ImeAction.Done
                )
            }
        }
    }
}

/**
 * نافذة إدارة التصنيفات الهرمية متعددة المستويات (CategoryHierarchyManagerSheet)
 * تدعم: المصروفات (EXPENSE)، الإيرادات (REVENUE)، الأصناف (PRODUCT)، العملاء (CUSTOMER)، الموردين (SUPPLIER)
 * مع كشف الدورات المغلقة ومنع الحذف النهائي للتصنيفات المرتبطة.
 */
