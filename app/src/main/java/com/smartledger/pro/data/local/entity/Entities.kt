package com.smartledger.pro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * جدول تصنيفات الدفاتر والحسابات (العملاء، الموردين، الموظفين، الحسابات العامة، ...)
 */
@Entity(tableName = "Books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Name: String,
    val IsSystem: Boolean = false,
    val SortOrder: Int = 0
)

/**
 * جدول بطاقات الحسابات المالية (العملاء، الموردين، الموظفين، الحسابات المدينة والدائنة)
 */
@Entity(
    tableName = "People",
    indices = [
        Index("Name"),
        Index("Phone"),
        Index("BookId", "Name"),
        Index(value = ["AccountCode"], unique = true)
    ]
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val BookId: Long,
    val Name: String,
    val Phone: String? = null,
    val AccountCode: String? = null,
    val CreditLimit: Double = 0.0,
    val Note: String? = null,
    val IsActive: Boolean = true,
    val CreatedAt: String,
    val LastOperationAt: String? = null
)

/**
 * جدول القيود والحركات المالية اليومية (سندات قبض، سندات صرف، قيود افتتاحية، مبيعات، مشتريات)
 * اتجاه التأثير:
 *  - مدين (عليه): DebtOnUs, Sale, Payment, PurchaseReturn (+Amount)
 *  - دائن (له):   DebtToUs, Purchase, Receive, SaleReturn (-Amount)
 */
@Entity(
    tableName = "Operations",
    indices = [
        Index("PersonId", "OccurredAt"),
        Index("OccurredAt"),
        Index("PersonId", "Type", "Amount", "OccurredAt"),
        Index("SourceInvoiceId")
    ]
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val PersonId: Long,
    val Type: String, // Receive, Payment, DebtOnUs, DebtToUs, Sale, Purchase, SaleReturn, PurchaseReturn, Adjustment
    val Amount: Double,
    val OccurredAt: String,
    val Note: String? = null,
    val ReferenceNumber: String? = null,
    val SourceInvoiceId: Long? = null,
    val SourceStockMovementId: Long? = null,
    val CreatedAt: String,
    val FinancialAccountId: Long? = null,
    val TargetInvoiceId: Long? = null
)

/**
 * جدول الأصناف والمنتجات المخزنية
 */
@Entity(
    tableName = "Products",
    indices = [
        Index("Name"),
        Index("CategoryId"),
        Index("Warehouse", "CategoryId"),
        Index(value = ["Barcode"], unique = true)
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Barcode: String? = null,
    val Name: String,
    val CategoryId: Long? = null,
    val Warehouse: String = "المستودع الرئيسي",
    val ShelfLocation: String? = null,
    val SmallUnit: String = "حبة",
    val LargeUnit: String? = "كرتون",
    val UnitFactor: Double = 1.0,
    val CostPrice: Double = 0.0,
    val RetailPrice: Double = 0.0,
    val WholesalePrice: Double = 0.0,
    val AlertThreshold: Double = 5.0,
    val OpeningQuantityMinor: Double = 0.0,
    val OpeningCost: Double = 0.0,
    val SupplierId: Long? = null,
    val IsActive: Boolean = true,
    val CreatedAt: String
)

/**
 * جدول رؤوس الفواتير التجارية (مبيعات، مشتريات، مرتجعات)
 */
@Entity(
    tableName = "Invoices",
    indices = [
        Index(value = ["InvoiceNumber"], unique = true),
        Index("PersonId"),
        Index("IssuedAt", "Type")
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val InvoiceNumber: String,
    val Type: String, // Sale, Purchase, SaleReturn, PurchaseReturn
    val PersonId: Long,
    val PersonNameSnapshot: String,
    val IssuedAt: String,
    val Subtotal: Double,
    val DiscountAmount: Double = 0.0,
    val TaxAmount: Double = 0.0,
    val FinalTotal: Double,
    val PaidAmount: Double = 0.0,
    val RemainingAmount: Double = 0.0,
    val PaymentType: String = "Cash", // Cash, Credit, Partial
    val Notes: String? = null,
    val Status: String = "Completed", // Completed, Suspended, Reversed
    val CreatedAt: String,
    val FinancialAccountId: Long? = null,
    val WarehouseId: Long? = null
)

/**
 * جدول بنود وتفاصيل الفاتورة التجارية
 */
@Entity(
    tableName = "InvoiceLines",
    indices = [
        Index("InvoiceId"),
        Index("ProductId")
    ]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val InvoiceId: Long,
    val ProductId: Long,
    val ProductNameSnapshot: String,
    val BarcodeSnapshot: String? = null,
    val UnitName: String,
    val IsMajorUnit: Boolean = false,
    val ConversionFactor: Double = 1.0,
    val QuantityMinor: Double,
    val UnitPrice: Double,
    val LineDiscount: Double = 0.0,
    val LineTotal: Double,
    val LineCost: Double = 0.0,
    val SortOrder: Int = 0
)

/**
 * جدول حركات وتغييرات المخزون المستندية (وارد، صادر، تسوية جرد، تحويل مخزني)
 */
@Entity(
    tableName = "StockMovements",
    indices = [
        Index("ProductId", "OccurredAt"),
        Index("InvoiceId")
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val ProductId: Long,
    val OccurredAt: String,
    val Type: String, // In, Out, AdjustmentIn, AdjustmentOut, Sale, Purchase, ReturnIn, ReturnOut, TransferIn, TransferOut
    val QuantityMinor: Double,
    val UnitCost: Double,
    val Notes: String? = null,
    val InvoiceId: Long? = null,
    val BatchId: Long? = null,
    val CreatedAt: String,
    val WarehouseId: Long? = null
)

/**
 * جدول المصروفات والنفقات التشغيلية والإيرادات الأخرى
 * - يدعم `EntryType`: `"EXPENSE"` (مصروف) أو `"REVENUE"` (إيراد تشغيلي آخر)
 * - يدعم `IsArchived`: `0` (نشط) أو `1` (مؤرشف في سلة المحذوفات مع الحفاظ على ثبات `Id`)
 */
@Entity(
    tableName = "Expenses",
    indices = [
        Index("ExpenseDate"),
        Index("CategoryId", "ExpenseDate")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val ExpenseDate: String,
    val CategoryId: Long? = null,
    val Amount: Double,
    val Statement: String,
    val PaymentSource: String = "الخزينة الرئيسية",
    val ReceiptPhotoUri: String? = null,
    val CreatedAt: String,
    val EntryType: String? = "EXPENSE", // EXPENSE or REVENUE
    val FinancialAccountId: Long? = null,
    val BeneficiaryName: String? = null,
    val ReferenceNumber: String? = null,
    val Notes: String? = null,
    val IsArchived: Int? = 0
)

/**
 * جدول سلة المحذوفات لتأمين استعادة السجلات المحذوفة خطأً وتوثيق سبب الإلغاء
 */
@Entity(
    tableName = "DeletedItems",
    indices = [
        Index("DeletedAt"),
        Index("EntityType", "DeletedAt")
    ]
)
data class DeletedItemEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val EntityType: String, // OperationReversal, Product, Account, Expense, Invoice
    val OriginalId: Long,
    val Title: String,
    val SnapshotJson: String,
    val DeletedAt: String,
    val Reason: String? = null
)

/**
 * جدول هوية المنشأة ورأس وتذييل الفواتير المطبوعة (يُضبط بواسطة المستخدم دون فرض بيانات وهمية)
 */
@Entity(tableName = "BusinessIdentity")
data class BusinessIdentityEntity(
    @PrimaryKey val Id: Int = 1,
    val TradeName: String = "نشاطي التجاري",
    val LegalName: String = "",
    val TaxNumber: String? = null,
    val CommercialRegistration: String? = null,
    val Phone1: String = "",
    val Phone2: String? = null,
    val Address: String = "",
    val ReceiptHeader: String = "",
    val ReceiptFooter: String = ""
)

/**
 * جدول إعدادات وتفضيلات التطبيق المحلية
 */
@Entity(tableName = "AppPreferences")
data class AppPreferenceEntity(
    @PrimaryKey val Key: String,
    val Value: String?
)

/**
 * جدول التصنيفات الهرمية المرنة متعدد المجالات (Categories)
 * يدعم شجرة غير محدودة المستويات (`ParentId`) للمصروفات، الإيرادات، الأصناف، العملاء، والموردين.
 */
@Entity(
    tableName = "Categories",
    indices = [
        Index("Domain", "ParentId"),
        Index("ParentId"),
        Index("Name")
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Domain: String, // EXPENSE, REVENUE, PRODUCT, CUSTOMER, SUPPLIER, ACCOUNT
    val Name: String,
    val ParentId: Long? = null,
    val Description: String? = null,
    val ColorHex: String? = null,
    val IsActive: Boolean = true,
    val SortOrder: Int = 0,
    val CreatedAt: String
)

/**
 * جدول وحدات القياس المرنة وقواعد التحويل (MeasurementUnits)
 */
@Entity(
    tableName = "MeasurementUnits",
    indices = [
        Index(value = ["Name"], unique = true),
        Index("BaseUnitId")
    ]
)
data class MeasurementUnitEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Name: String,
    val Symbol: String = "",
    val DecimalPrecision: Int = 0,
    val BaseUnitId: Long? = null,
    val ConversionFactorToBase: Double = 1.0,
    val IsActive: Boolean = true,
    val CreatedAt: String
)

/**
 * جدول الصناديق والحسابات المالية والمحافظ والبنوك (FinancialAccounts)
 * يشتق رصيده الفعلي من الحركات المرتبطة به كمصدر حقيقة واحد بالإضافة إلى الرصيد الافتتاحي الموثق.
 */
@Entity(
    tableName = "FinancialAccounts",
    indices = [
        Index("AccountType"),
        Index("ParentGroupId"),
        Index("Name")
    ]
)
data class FinancialAccountEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Name: String,
    val AccountType: String = "CASH_BOX", // CASH_BOX, E_WALLET, BANK_ACCOUNT, POS_TERMINAL, FUND_GROUP
    val ParentGroupId: Long? = null,
    val CurrencySymbol: String = "ر.ي",
    val AccountNumberOrPhone: String? = null,
    val OpeningBalance: Double = 0.0,
    val AllowNegativeBalance: Boolean = false,
    val IsDefault: Boolean = false,
    val IsActive: Boolean = true,
    val Notes: String? = null,
    val CreatedAt: String
)

/**
 * جدول التحويلات المالية الداخلية الذرية بين الصناديق والمحافظ والبنوك (FinancialTransfers)
 * لا يُصنف إيراداً ولا مصروفاً (ما عدا رسوم التحويل إن وُجدت).
 */
@Entity(
    tableName = "FinancialTransfers",
    indices = [
        Index("FromAccountId", "OccurredAt"),
        Index("ToAccountId", "OccurredAt"),
        Index("OccurredAt")
    ]
)
data class FinancialTransferEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val FromAccountId: Long,
    val ToAccountId: Long,
    val Amount: Double,
    val FeeAmount: Double = 0.0,
    val OccurredAt: String,
    val ReferenceNumber: String? = null,
    val Notes: String? = null,
    val Status: String = "Completed", // Completed, Reversed
    val CreatedAt: String
)

/**
 * جدول المخازن والفروع المستقلة (Warehouses)
 */
@Entity(
    tableName = "Warehouses",
    indices = [
        Index(value = ["Name"], unique = true)
    ]
)
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Name: String,
    val Code: String? = null,
    val Location: String? = null,
    val Description: String? = null,
    val IsDefault: Boolean = false,
    val IsActive: Boolean = true,
    val CreatedAt: String
)
