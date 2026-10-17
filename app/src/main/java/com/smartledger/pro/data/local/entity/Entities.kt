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
 *  - مدين (عليه): DebtOnUs, Sale, Payment  (+Amount)
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
    val Type: String, // Receive, Payment, DebtOnUs, DebtToUs, Sale, Purchase, SaleReturn, Adjustment
    val Amount: Double,
    val OccurredAt: String,
    val Note: String? = null,
    val ReferenceNumber: String? = null,
    val SourceInvoiceId: Long? = null,
    val SourceStockMovementId: Long? = null,
    val CreatedAt: String
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
    val CreatedAt: String
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
 * جدول حركات وتغييرات المخزون المستنديه (وارد، صادر، تسوية جرد)
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
    val Type: String, // In, Out, AdjustmentIn, AdjustmentOut, Sale, Purchase, ReturnIn, ReturnOut
    val QuantityMinor: Double,
    val UnitCost: Double,
    val Notes: String? = null,
    val InvoiceId: Long? = null,
    val BatchId: Long? = null,
    val CreatedAt: String
)

/**
 * جدول المصروفات والنفقات التشغيلية
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
    val CreatedAt: String
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
    val EntityType: String, // Operation, Product, Account, Expense, Invoice
    val OriginalId: Long,
    val Title: String,
    val SnapshotJson: String,
    val DeletedAt: String,
    val Reason: String? = null
)

/**
 * جدول هوية المنشأة ورأس وتذييل الفواتير المطبوعة
 */
@Entity(tableName = "BusinessIdentity")
data class BusinessIdentityEntity(
    @PrimaryKey val Id: Int = 1,
    val TradeName: String = "الدفتر الذكي للتجارة العامة",
    val LegalName: String = "الدفتر الذكي",
    val TaxNumber: String? = null,
    val CommercialRegistration: String? = null,
    val Phone1: String = "777000000",
    val Phone2: String? = null,
    val Address: String = "صنعاء - شارع الزبيري",
    val ReceiptHeader: String = "أهلاً وسهلاً بكم في متجرنا",
    val ReceiptFooter: String = "البضاعة المباعة ترد وتستبدل خلال 3 أيام بفاتورة الشراء"
)

/**
 * جدول إعدادات وتفضيلات التطبيق المحلية
 */
@Entity(tableName = "AppPreferences")
data class AppPreferenceEntity(
    @PrimaryKey val Key: String,
    val Value: String?
)
