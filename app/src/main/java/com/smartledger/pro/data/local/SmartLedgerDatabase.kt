package com.smartledger.pro.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    exportSchema = false
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
     * تفريغ معاملات WAL إلى ملف قاعدة البيانات الأساسي لضمان لقطة نسخ احتياطي متماسكة 100%
     */
    fun checkpointWal() {
        try {
            val db = openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) {
                    // WAL checkpoint executed
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SmartLedgerDatabase? = null

        fun getInstance(context: Context): SmartLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartLedgerDatabase::class.java,
                    "smartledger.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                seedInitialData(database)
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
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

        private suspend fun seedInitialData(db: SmartLedgerDatabase) {
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
