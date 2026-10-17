package com.smartledger.pro.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * قاعدة البيانات المركزية لتطبيق الدفتر الذكي Pro (الإصدار 2).
 *
 * القواعد الهندسية الصارمة:
 * 1. `exportSchema = true` لحفظ وتوثيق مخطط كل إصدار في `app/schemas/` ومراجعته قبل أي ترقية.
 * 2. يُحظر تماماً تفعيل أي مسار ترحيل تدميري يحذف الجداول لحماية سجلات العملاء والفواتير من الفقد الصامت.
 * 3. يمر التحديث من الإصدار 1 إلى الإصدار 2 عبر `MIGRATION_1_2` الصريح داخل `ALL_MIGRATIONS` دون فقد أي سجل.
 * 4. يوفر `databaseEpoch` تدفق حالة تفاعلياً (`StateFlow<Long>`) يتحدث تلقائياً عند إغلاق واستعادة قاعدة البيانات،
 *    مما يضمن إعادة ربط جميع المستودعات والتدفقات (`Flows`) بنسخة القاعدة الجديدة فوراً دون الحاجة لإعادة تشغيل التطبيق.
 */
@Database(
    entities = [
        BookEntity::class,
        PersonEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockMovementEntity::class,
        OperationEntity::class,
        ExpenseEntity::class,
        DeletedItemEntity::class,
        BusinessIdentityEntity::class,
        AppPreferenceEntity::class,
        CategoryEntity::class,
        MeasurementUnitEntity::class,
        FinancialAccountEntity::class,
        FinancialTransferEntity::class,
        WarehouseEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class SmartLedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun bookDao(): BookDao
    abstract fun productDao(): ProductDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun operationDao(): OperationDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deletedItemDao(): DeletedItemDao
    abstract fun businessIdentityDao(): BusinessIdentityDao
    abstract fun appPreferenceDao(): AppPreferenceDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun reportDao(): ReportDao
    abstract fun categoryDao(): CategoryDao
    abstract fun measurementUnitDao(): MeasurementUnitDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun warehouseDao(): WarehouseDao

    /**
     * تفريغ معاملات WAL إلى ملف قاعدة البيانات الأساسي والتحقق من عدم وجود صفحات محجوزة (`busy == 0`)
     * لضمان إنتاج لقطة نسخ احتياطي متماسكة 100%؛ يعيد `Result.failure` صريحاً إذا تعذر التفريغ الكامل.
     */
    fun checkpointWal(): Result<Unit> {
        return try {
            val db = openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) {
                    val busy = cursor.getInt(0)
                    if (busy != 0) {
                        return Result.failure(
                            IllegalStateException("قاعدة البيانات مشغولة بعملية كتابة جارية (WAL busy=$busy)؛ يرجى المحاولة بعد اكتمال الحفظ.")
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر تفريغ سجل معاملات قاعدة البيانات (WAL Checkpoint): ${e.localizedMessage}"))
        }
    }

    companion object {
        const val DATABASE_NAME = "smartledger.db"
        const val CURRENT_SCHEMA_VERSION = 2

        /**
         * الترحيل الآمن غير التدميري من الإصدار 1 إلى الإصدار 2 (`MIGRATION_1_2`):
         * - يحافظ على كافة سجلات الإصدار 1 (العملاء، الموردين، الفواتير، الحركات، الأصناف، المصروفات).
         * - يضيف الأعمدة الاختيارية الجديدة للجداول القائمة بقيم `NULL` متوافقة 100% مع Room `TableInfo`.
         * - ينشئ الجداول الخمسة الجديدة (`Categories`, `MeasurementUnits`, `FinancialAccounts`, `FinancialTransfers`, `Warehouses`) وفهارسها.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. توسيع الجداول القائمة بأعمدة اختيارية غير تدميرية
                db.execSQL("ALTER TABLE `Operations` ADD COLUMN `FinancialAccountId` INTEGER")
                db.execSQL("ALTER TABLE `Operations` ADD COLUMN `TargetInvoiceId` INTEGER")

                db.execSQL("ALTER TABLE `Invoices` ADD COLUMN `FinancialAccountId` INTEGER")
                db.execSQL("ALTER TABLE `Invoices` ADD COLUMN `WarehouseId` INTEGER")

                db.execSQL("ALTER TABLE `StockMovements` ADD COLUMN `WarehouseId` INTEGER")

                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `EntryType` TEXT")
                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `FinancialAccountId` INTEGER")
                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `BeneficiaryName` TEXT")
                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `ReferenceNumber` TEXT")
                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `Notes` TEXT")
                db.execSQL("ALTER TABLE `Expenses` ADD COLUMN `IsArchived` INTEGER")

                // 2. إنشاء جدول التصنيفات الهرمية (Categories)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Categories` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Domain` TEXT NOT NULL, `Name` TEXT NOT NULL, `ParentId` INTEGER, `Description` TEXT, `ColorHex` TEXT, `IsActive` INTEGER NOT NULL, `SortOrder` INTEGER NOT NULL, `CreatedAt` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Categories_Domain_ParentId` ON `Categories` (`Domain`, `ParentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Categories_ParentId` ON `Categories` (`ParentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Categories_Name` ON `Categories` (`Name`)")

                // 3. إنشاء جدول وحدات القياس المرنة (MeasurementUnits)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `MeasurementUnits` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Name` TEXT NOT NULL, `Symbol` TEXT NOT NULL, `DecimalPrecision` INTEGER NOT NULL, `BaseUnitId` INTEGER, `ConversionFactorToBase` REAL NOT NULL, `IsActive` INTEGER NOT NULL, `CreatedAt` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MeasurementUnits_Name` ON `MeasurementUnits` (`Name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_MeasurementUnits_BaseUnitId` ON `MeasurementUnits` (`BaseUnitId`)")

                // 4. إنشاء جدول الصناديق والحسابات المالية والمحافظ (FinancialAccounts)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `FinancialAccounts` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Name` TEXT NOT NULL, `AccountType` TEXT NOT NULL, `ParentGroupId` INTEGER, `CurrencySymbol` TEXT NOT NULL, `AccountNumberOrPhone` TEXT, `OpeningBalance` REAL NOT NULL, `AllowNegativeBalance` INTEGER NOT NULL, `IsDefault` INTEGER NOT NULL, `IsActive` INTEGER NOT NULL, `Notes` TEXT, `CreatedAt` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialAccounts_AccountType` ON `FinancialAccounts` (`AccountType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialAccounts_ParentGroupId` ON `FinancialAccounts` (`ParentGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialAccounts_Name` ON `FinancialAccounts` (`Name`)")

                // 5. إنشاء جدول التحويلات المالية الداخلية الذرية (FinancialTransfers)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `FinancialTransfers` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `FromAccountId` INTEGER NOT NULL, `ToAccountId` INTEGER NOT NULL, `Amount` REAL NOT NULL, `FeeAmount` REAL NOT NULL, `OccurredAt` TEXT NOT NULL, `ReferenceNumber` TEXT, `Notes` TEXT, `Status` TEXT NOT NULL, `CreatedAt` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialTransfers_FromAccountId_OccurredAt` ON `FinancialTransfers` (`FromAccountId`, `OccurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialTransfers_ToAccountId_OccurredAt` ON `FinancialTransfers` (`ToAccountId`, `OccurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FinancialTransfers_OccurredAt` ON `FinancialTransfers` (`OccurredAt`)")

                // 6. إنشاء جدول المخازن والفروع (Warehouses)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Warehouses` (`Id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `Name` TEXT NOT NULL, `Code` TEXT, `Location` TEXT, `Description` TEXT, `IsDefault` INTEGER NOT NULL, `IsActive` INTEGER NOT NULL, `CreatedAt` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Warehouses_Name` ON `Warehouses` (`Name`)")
            }
        }

        /**
         * سجل الترحيلات المعتمدة (Migrations).
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        private val databaseSeedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _databaseEpoch = MutableStateFlow(0L)

        /**
         * مؤشر الجيل الحالي لاتصال قاعدة البيانات؛ يزداد عند إغلاق واستعادة قاعدة البيانات
         * لإعادة ربط التدفقات التفاعلية (`Flows`) بالاتصال الجديد تلقائياً.
         */
        val databaseEpoch: StateFlow<Long> = _databaseEpoch.asStateFlow()

        @Volatile
        private var INSTANCE: SmartLedgerDatabase? = null

        fun getInstance(context: Context): SmartLedgerDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    appContext,
                    SmartLedgerDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            databaseSeedScope.launch {
                                val database = getInstance(appContext)
                                seedInitialData(database)
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * إغلاق الاتصال الحالي بقاعدة البيانات بأمان قبل استبدال ملف القاعدة أثناء الاستعادة
         */
        fun closeAndResetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.checkpointWal()
                    INSTANCE?.close()
                } catch (_: Exception) {
                } finally {
                    INSTANCE = null
                }
            }
        }

        /**
         * إشعار جميع المستودعات والتدفقات النشطة بأن قاعدة البيانات قد تمت استعادتها وإعادة فتحها
         */
        fun notifyDatabaseRestored() {
            _databaseEpoch.value = _databaseEpoch.value + 1L
        }

        /**
         * تهيئة هيكل الدفاتر التنظيمية فقط دون فرض أي بيانات تجارية وهمية (لا اسم متجر وهمي ولا هاتف ولا عنوان مفترض).
         */
        suspend fun seedInitialData(db: SmartLedgerDatabase) {
            val existingBooks = db.bookDao().getAllBooksOnce()
            if (existingBooks.isEmpty()) {
                val books = listOf(
                    BookEntity(Id = 1, Name = "العملاء", IsSystem = false, SortOrder = 1),
                    BookEntity(Id = 2, Name = "الموردين", IsSystem = false, SortOrder = 2),
                    BookEntity(Id = 3, Name = "الموظفين", IsSystem = false, SortOrder = 3),
                    BookEntity(Id = 4, Name = "حسابات عامة", IsSystem = false, SortOrder = 4)
                )
                for (b in books) {
                    db.bookDao().insertBook(b)
                }
            }
        }
    }
}
