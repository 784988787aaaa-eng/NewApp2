package com.smartledger.pro.di

import android.content.Context
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.repository.*
import com.smartledger.pro.domain.usecase.CreatePurchaseUseCase
import com.smartledger.pro.domain.usecase.CreateSaleUseCase
import com.smartledger.pro.ui.components.AppSensoryConfig
import com.smartledger.pro.ui.theme.AppDesignState

/**
 * حاوية حقن الاعتماديات المركزية (AppContainer).
 *
 * الضمانات المعمارية:
 * 1. تمرر مزوداً ديناميكياً لقاعدة البيانات (`{ SmartLedgerDatabase.getInstance(appContext) }`) إلى كافة المستودعات
 *    (`Repositories`) بدلاً من تخزين نسخة `SmartLedgerDatabase` ثابتة، مما يضمن أنه عند إغلاق واستعادة قاعدة البيانات
 *    أثناء عمل التطبيق، تتحول جميع المستودعات والتدفقات تلقائياً إلى القاعدة المستعادة دون أخطاء `Connection pool closed`.
 * 2. توفر `businessIdentityRepository` لعزل جميع `ViewModels` عن الوصول المباشر إلى `DAO`.
 * 3. تربط مراقب الحالة الحية بين `AppPreferencesRepository` وطبقة التصميم (`AppDesignState` / `AppSensoryConfig`)
 *    من الطبقة العليا (`di`) بحيث لا تعتمد طبقة `data` على طبقة `ui`.
 */
class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: SmartLedgerDatabase
        get() = SmartLedgerDatabase.getInstance(appContext)

    val preferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository.getInstance(appContext).also { repo ->
            repo.registerRuntimeStateObserver { snapshot ->
                AppDesignState.syncFromSettings(snapshot)
                AppSensoryConfig.syncFromSettings(snapshot)
            }
        }
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val invoiceRepository: InvoiceRepository by lazy {
        InvoiceRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val voucherRepository: VoucherRepository by lazy {
        VoucherRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val reportRepository: ReportRepository by lazy {
        ReportRepository { SmartLedgerDatabase.getInstance(appContext) }
    }

    val businessIdentityRepository: BusinessIdentityRepository by lazy {
        BusinessIdentityRepository { SmartLedgerDatabase.getInstance(appContext) }
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
