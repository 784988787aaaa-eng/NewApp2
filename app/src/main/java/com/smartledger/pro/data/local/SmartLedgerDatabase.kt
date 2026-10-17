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
 * قاعدة البيانات المركزية لتطبيق الدفتر الذكي Pro.
 *
 * القواعد الهندسية الصارمة:
 * 1. `exportSchema = true` لحفظ وتوثيق مخطط كل إصدار في `app/schemas/` ومراجعته قبل أي ترقية.
 * 2. يُحظر تماماً تفعيل أي مسار ترحيل تدميري يحذف الجداول لحماية سجلات العملاء والفواتير من الفقد الصامت.
 * 3. أي تعديل مستقبلي على الجداول يجب أن يمر عبر كائن `Migration(from, to)` صريح داخل `ALL_MIGRATIONS`.
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
        AppPreferenceEntity::class
    ],
    version = 1,
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
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * سجل الترحيلات المعتمدة (Migrations).
         * عند رفع رقم الإصدار مستقبلاً (مثلاً من 1 إلى 2)، يُضاف كائن `Migration(1, 2)` هنا ويُختبر قبل الإطلاق.
         */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()

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

        suspend fun seedInitialData(db: SmartLedgerDatabase) {
            val books = listOf(
                BookEntity(Id = 1, Name = "العملاء", IsSystem = true, SortOrder = 1),
                BookEntity(Id = 2, Name = "الموردين", IsSystem = true, SortOrder = 2),
                BookEntity(Id = 3, Name = "الموظفين", IsSystem = true, SortOrder = 3),
                BookEntity(Id = 4, Name = "حسابات عامة", IsSystem = true, SortOrder = 4),
                BookEntity(Id = 5, Name = "أخرى", IsSystem = true, SortOrder = 5)
            )
            for (b in books) {
                db.bookDao().insertBook(b)
            }

            db.businessIdentityDao().saveIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = "الدفتر الذكي للتجارة العامة",
                    LegalName = "الدفتر الذكي",
                    TaxNumber = null,
                    CommercialRegistration = null,
                    Phone1 = "777000000",
                    Phone2 = null,
                    Address = "صنعاء - شارع الزبيري",
                    ReceiptHeader = "أهلاً وسهلاً بكم في متجرنا",
                    ReceiptFooter = "البضاعة المباعة ترد وتستبدل خلال 3 أيام بفاتورة الشراء"
                )
            )
        }
    }
}
