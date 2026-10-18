package com.smartledger.pro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartledger.pro.ui.theme.SmartLedgerColors

/**
 * Optional inventory and point-of-sale input fields.
 *
 * This component owns only field-visibility presentation. It does not modify
 * product records, stock balances, invoice totals, or accounting transactions.
 */
@Composable
internal fun SettingsFieldVisibilityControls(
    inventoryBarcode: Boolean,
    productBarcode: Boolean,
    productShelf: Boolean,
    posBarcode: Boolean,
    onInventoryBarcodeChange: (Boolean) -> Unit,
    onProductBarcodeChange: (Boolean) -> Unit,
    onProductShelfChange: (Boolean) -> Unit,
    onPosBarcodeChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "تخصيص حقول الإدخال",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SmartLedgerColors.TextPrimary
        )
        Text(
            text = "أظهر الحقول التي يحتاجها نشاطك فقط. تغيير هذه الخيارات يؤثر على الواجهة ولا يحذف بيانات الأصناف المسجلة.",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingSwitchRow(
            title = "الباركود في المخزون",
            subtitle = "إظهار أو إخفاء حقل الباركود في شاشة المخزون",
            checked = inventoryBarcode,
            onCheckedChange = onInventoryBarcodeChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "الباركود في بطاقة الصنف",
            subtitle = "إظهار حقل الباركود عند إضافة الصنف أو تعديله",
            checked = productBarcode,
            onCheckedChange = onProductBarcodeChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "موقع الرف في بطاقة الصنف",
            subtitle = "إظهار موقع الرف للمتاجر التي تنظّم الأصناف على الرفوف",
            checked = productShelf,
            onCheckedChange = onProductShelfChange
        )
        Divider(color = SmartLedgerColors.BorderSubtle)
        SettingSwitchRow(
            title = "الباركود في نقطة البيع",
            subtitle = "إظهار حقل الباركود في شاشة البيع السريع",
            checked = posBarcode,
            onCheckedChange = onPosBarcodeChange
        )
    }
}
