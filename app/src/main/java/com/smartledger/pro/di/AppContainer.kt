package com.smartledger.pro.di

import android.content.Context
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.repository.*
import com.smartledger.pro.domain.usecase.CreatePurchaseUseCase
import com.smartledger.pro.domain.usecase.CreateSaleUseCase

/**
 * حاوية حقن الاعتماديات المركزية (AppContainer).
 *
 * تعالج الملاحظة المعمارية في التدقيق:
 * - تمنع إنشاء اتصالات `SmartLedgerDatabase` أو المستودعات (`Repositories`) بشكل مبعثر داخل كل `ViewModel`.
 * - توفر مصدراً واحداً للمستودعات وحالات الاستخدام (`UseCases`) يسهل اختباره وعزله.
 */
class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: SmartLedgerDatabase by lazy {
        SmartLedgerDatabase.getInstance(appContext)
    }

    val preferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository.getInstance(appContext)
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database)
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(database)
    }

    val invoiceRepository: InvoiceRepository by lazy {
        InvoiceRepository(database)
    }

    val voucherRepository: VoucherRepository by lazy {
        VoucherRepository(database)
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database)
    }

    val reportRepository: ReportRepository by lazy {
        ReportRepository(database)
    }

    val createSaleUseCase: CreateSaleUseCase by lazy {
        CreateSaleUseCase(invoiceRepository)
    }

    val createPurchaseUseCase: CreatePurchaseUseCase by lazy {
        CreatePurchaseUseCase(invoiceRepository)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                val instance = AppContainer(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
