package com.smartledger.pro.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
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
    version = 2,
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

    fun checkpointWal() {
        try {
            val db = openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                cursor.moveToFirst()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SCHEMA_VERSION = 2

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE Products ADD COLUMN Notes TEXT")
                } catch (_: Exception) {
                }
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
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

        fun reopenAfterRestore(context: Context): SmartLedgerDatabase {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
                return getInstance(context)
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
            for (b in books) db.bookDao().insertBook(b)

            db.businessIdentityDao().saveIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = "الدفتر الذكي للتجارة العامة",
                    LegalName = "الدفتر الذكي",
                    Phone1 = "777000000",
                    Address = "صنعاء - شارع الزبيري",
                    ReceiptHeader = "أهلاً وسهلاً بكم في متجرنا",
                    ReceiptFooter = "البضاعة المباعة ترد وتستبدل خلال 3 أيام بفاتورة الشراء"
                )
            )
        }
    }
}
