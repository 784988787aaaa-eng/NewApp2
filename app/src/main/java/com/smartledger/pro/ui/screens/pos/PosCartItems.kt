package com.smartledger.pro.ui.screens.pos

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.Modifier
import com.smartledger.pro.ui.components.SmartEmptyState
import com.smartledger.pro.ui.viewmodels.CartLineItem

/**
 * Renders the POS cart body without owning cart state or business actions.
 * All mutations are delegated back to PosSalesScreen/ViewModel.
 */
internal fun LazyListScope.posCartItems(
    cartLines: List<CartLineItem>,
    onAddProducts: () -> Unit,
    onUpdateQuantities: (line: CartLineItem, major: Double, minor: Double) -> Unit,
    onEditLineDetails: (CartLineItem) -> Unit,
    onRemoveLine: (CartLineItem) -> Unit
) {
    if (cartLines.isEmpty()) {
        item {
            SmartEmptyState(
                icon = Icons.Default.ShoppingCart,
                title = "سلة المبيعات جاهزة",
                subtitle = "اضغط على أي صنف في الشريط العلوي، أو امسح الباركود، أو افتح قائمة الأصناف لبدء فاتورة جديدة.",
                actionText = "استعراض وإضافة الأصناف",
                onActionClick = onAddProducts
            )
        }
    } else {
        items(cartLines, key = { it.productId }) { line ->
            CartLineCard(
                line = line,
                onUpdateQuantities = { major, minor -> onUpdateQuantities(line, major, minor) },
                onEditLineDetails = { onEditLineDetails(line) },
                onRemoveLine = { onRemoveLine(line) }
            )
        }
    }
}
