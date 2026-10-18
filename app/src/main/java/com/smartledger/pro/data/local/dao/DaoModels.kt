package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class AccountBalanceSummary(
    val PersonId: Long,
    val PersonName: String,
    val Phone: String?,
    val BookId: Long,
    val TotalDebit: Double,
    val TotalCredit: Double,
    val NetBalance: Double,
    val LastOperationAt: String?,
    val AccountCode: String? = null,
    val CreditLimit: Double = 0.0,
    val Note: String? = null
)

data class DashboardMetrics(
    val TotalOwedToUs: Double,   // إجمالي لنا (المدين)
    val TotalWeOwe: Double,      // إجمالي علينا (الدائن)
    val TodaySales: Double,
    val TodayPurchases: Double,
    val TodayReceipts: Double,
    val TodayPayments: Double,
    val LowStockCount: Int,
    val ActiveAccountsCount: Int
)

data class ProductStockItem(
    val Id: Long,
    val Barcode: String?,
    val Name: String,
    val CategoryId: Long?,
    val Warehouse: String,
    val ShelfLocation: String? = null,
    val SmallUnit: String,
    val LargeUnit: String?,
    val UnitFactor: Double,
    val CostPrice: Double,
    val RetailPrice: Double,
    val WholesalePrice: Double,
    val AlertThreshold: Double,
    val CurrentStockMinor: Double
)

/**
 * ملخص رصيد الحساب المالي الفعلي (صندوق / محفظة / بنك) المشتق من مصدر حقيقة واحد
 */

data class FinancialAccountBalanceSummary(
    val Id: Long,
    val Name: String,
    val AccountType: String,
    val ParentGroupId: Long?,
    val CurrencySymbol: String,
    val AccountNumberOrPhone: String?,
    val OpeningBalance: Double,
    val AllowNegativeBalance: Boolean,
    val IsDefault: Boolean,
    val IsActive: Boolean,
    val Notes: String?,
    val TotalInflows: Double,
    val TotalOutflows: Double,
    val NetBalance: Double
)

/**
 * ملخص القوائم المالية الشامل:
 * يفصل بوضوح بين:
 * 1. قائمة الدخل المحاسبية (Accrual Income Statement): `TotalSales`, `CostOfGoodsSold`, `GrossProfit`, `TotalExpenses`, `NetProfit`.
 * 2. حركة الصندوق والسيولة النقدية الفعلية (Cashbox Statement): `CashInflows`, `CashOutflows`, `NetCashBalance`.
 * 3. المركز المالي والذمم والمخزون (Balance Sheet): `InventoryValuation`, `TotalDebtsToUs`, `TotalDebtsOnUs`.
 */

data class FinancialReportSummary(
    val TotalSales: Double,
    val CostOfGoodsSold: Double,
    val GrossProfit: Double,
    val TotalExpenses: Double,
    val NetProfit: Double,
    val InventoryValuation: Double,
    val TotalDebtsToUs: Double,
    val TotalDebtsOnUs: Double,
    val CashInflows: Double = 0.0,
    val CashOutflows: Double = 0.0,
    val NetCashBalance: Double = 0.0
)

data class RecentOperationWithPerson(
    val Id: Long,
    val PersonId: Long,
    val PersonName: String?,
    val Type: String,
    val Amount: Double,
    val OccurredAt: String,
    val Note: String?,
    val ReferenceNumber: String?,
    val SourceInvoiceId: Long?
)
