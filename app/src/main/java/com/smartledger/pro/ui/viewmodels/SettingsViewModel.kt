package com.smartledger.pro.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.data.repository.AppSettingsSnapshot
import com.smartledger.pro.di.AppContainer
import com.smartledger.pro.domain.model.UnitConversionEngine
import com.smartledger.pro.domain.usecase.SaleLineDraft
import com.smartledger.pro.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val container = AppContainer.getInstance(appContext)
    private val identityRepo = container.businessIdentityRepository
    private val accountRepo = container.accountRepository
    private val categoryRepo = container.categoryRepository
    private val unitRepo = container.measurementUnitRepository
    private val financialAccountRepo = container.financialAccountRepository
    private val warehouseRepo = container.warehouseRepository
    val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettingsSnapshot> = prefsRepo.settings

    val businessIdentity: StateFlow<BusinessIdentityEntity?> = identityRepo.observeBusinessIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deletedItems: StateFlow<List<DeletedItemEntity>> = accountRepo.getAllDeletedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryRepo.observeCategories(onlyActive = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val units: StateFlow<List<MeasurementUnitEntity>> = unitRepo.observeUnits(onlyActive = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAccounts: StateFlow<List<FinancialAccountBalanceSummary>> =
        financialAccountRepo.observeAccountBalances(onlyActive = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransfers: StateFlow<List<FinancialTransferEntity>> =
        financialAccountRepo.observeRecentTransfers(40)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> =
        warehouseRepo.observeWarehouses(onlyActive = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<BookEntity>> =
        accountRepo.getAllBooks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _indexedBackups = MutableStateFlow<List<BackupRecord>>(emptyList())
    val indexedBackups: StateFlow<List<BackupRecord>> = _indexedBackups.asStateFlow()

    init {
        refreshIndexedBackups()
    }

    fun refreshIndexedBackups() {
        viewModelScope.launch {
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
        }
    }

    suspend fun saveCategoryAsync(
        id: Long = 0L,
        domain: String,
        name: String,
        parentId: Long? = null,
        description: String? = null
    ): Result<Long> {
        return categoryRepo.saveCategory(id = id, domain = domain, name = name, parentId = parentId, description = description)
    }

    suspend fun deleteOrDeactivateCategoryAsync(categoryId: Long): Result<String> {
        return categoryRepo.deleteOrDeactivateCategory(categoryId)
    }

    suspend fun reactivateCategoryAsync(categoryId: Long): Result<Boolean> {
        return categoryRepo.reactivateCategory(categoryId)
    }

    suspend fun saveUnitAsync(
        id: Long = 0L,
        name: String,
        symbol: String,
        decimalPrecision: Int,
        baseUnitId: Long?,
        conversionFactorToBase: Double
    ): Result<Long> {
        return unitRepo.saveUnit(id, name, symbol, decimalPrecision, baseUnitId, conversionFactorToBase)
    }

    suspend fun saveFinancialAccountAsync(
        id: Long = 0L,
        name: String,
        accountType: String,
        parentGroupId: Long? = null,
        currencySymbol: String = settings.value.currencySymbol,
        accountNumberOrPhone: String? = null,
        openingBalance: Double = 0.0,
        allowNegativeBalance: Boolean = false,
        isDefault: Boolean = false,
        notes: String? = null
    ): Result<Long> {
        return financialAccountRepo.saveFinancialAccount(
            id = id,
            name = name,
            accountType = accountType,
            parentGroupId = parentGroupId,
            currencySymbol = currencySymbol,
            accountNumberOrPhone = accountNumberOrPhone,
            openingBalance = openingBalance,
            allowNegativeBalance = allowNegativeBalance,
            isDefault = isDefault,
            notes = notes
        )
    }

    suspend fun createInternalTransferAsync(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        feeAmount: Double = 0.0,
        notes: String? = null
    ): Result<Long> {
        return financialAccountRepo.createInternalTransfer(fromAccountId, toAccountId, amount, feeAmount, notes)
    }

    suspend fun reconcileFinancialAccountAsync(
        accountId: Long,
        actualBalance: Double,
        notes: String? = null
    ): Result<Double> {
        return financialAccountRepo.reconcileAccountBalance(accountId, actualBalance, notes)
    }

    suspend fun saveWarehouseAsync(
        id: Long = 0L,
        name: String,
        code: String? = null,
        location: String? = null,
        description: String? = null,
        isDefault: Boolean = false
    ): Result<Long> {
        return warehouseRepo.saveWarehouse(id, name, code, location, description, isDefault)
    }

    suspend fun saveBookAsync(name: String, id: Long = 0L): Result<Long> {
        return accountRepo.saveBook(name, id)
    }

    fun restoreDeletedItem(item: DeletedItemEntity, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = accountRepo.restoreDeletedItem(item)
            onResult(res)
        }
    }

    fun deleteDeletedItemPermanently(id: Long, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val res = accountRepo.deleteDeletedItemPermanently(id)
            onResult(res)
        }
    }

    fun saveBusinessIdentity(
        tradeName: String,
        phone: String,
        address: String,
        taxNumber: String?,
        receiptHeader: String,
        receiptFooter: String,
        invoicePrefix: String,
        purchasePrefix: String
    ) {
        viewModelScope.launch {
            identityRepo.saveBusinessIdentity(
                BusinessIdentityEntity(
                    Id = 1,
                    TradeName = tradeName.trim().ifBlank { "نشاطي التجاري" },
                    LegalName = tradeName.trim().ifBlank { "نشاطي التجاري" },
                    TaxNumber = taxNumber?.trim()?.ifBlank { null },
                    Phone1 = phone.trim(),
                    Address = address.trim(),
                    ReceiptHeader = receiptHeader.trim(),
                    ReceiptFooter = receiptFooter.trim()
                )
            )
            prefsRepo.updateSettings {
                it.copy(
                    invoicePrefix = invoicePrefix.trim().ifBlank { "INV" },
                    purchasePrefix = purchasePrefix.trim().ifBlank { "PUR" }
                )
            }
        }
    }

    fun updateSettings(transform: (AppSettingsSnapshot) -> AppSettingsSnapshot) {
        prefsRepo.updateSettings(transform)
    }

    fun updateAppPin(rawPin: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val clean = rawPin.trim()
            val hashed = if (clean.isBlank()) {
                ""
            } else {
                withContext(Dispatchers.Default) {
                    SecurityCryptoHelper.hashPin(clean)
                }
            }
            prefsRepo.updateSettings { it.copy(appPinCode = hashed) }
            onComplete?.invoke()
        }
    }

    fun resetPreferencesToDefault() {
        prefsRepo.resetToDefaultsPreservingBackupHistory()
    }

    fun createLocalArchiveBackup(onResult: (Result<BackupRecord>) -> Unit) {
        viewModelScope.launch {
            val res = BackupRestoreHelper.createStructuredArchiveBackup(
                context = appContext,
                sourceTag = "LocalArchive",
                keepMaxCount = prefsRepo.settings.value.keepBackupCount
            )
            if (res.isSuccess) {
                val rec = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun exportBackupToSafUri(destUri: Uri, onResult: (Result<BackupRecord>) -> Unit) {
        viewModelScope.launch {
            val res = BackupRestoreHelper.exportBackupToUri(appContext, destUri)
            if (res.isSuccess) {
                val rec = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun inspectBackupFromSafUri(srcUri: Uri, onResult: (BackupInspectionResult) -> Unit) {
        viewModelScope.launch {
            val inspection = BackupRestoreHelper.inspectBackupUri(appContext, srcUri)
            onResult(inspection)
        }
    }

    fun inspectBackupArchiveFile(file: File, onResult: (BackupInspectionResult) -> Unit) {
        viewModelScope.launch {
            val inspection = BackupRestoreHelper.inspectBackupFile(appContext, file)
            onResult(inspection)
        }
    }

    fun deleteIndexedBackup(recordId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            BackupRestoreHelper.deleteBackupRecord(appContext, recordId)
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            onComplete()
        }
    }

    fun restoreDatabase(
        fileToRestore: File?,
        uriToRestore: Uri?,
        onResult: (Result<BackupInspectionResult>) -> Unit
    ) {
        viewModelScope.launch {
            val res = when {
                fileToRestore != null -> BackupRestoreHelper.restoreFromBackupFile(
                    appContext,
                    fileToRestore,
                    createPreRestoreSafety = true
                )
                uriToRestore != null -> BackupRestoreHelper.restoreFromUri(
                    appContext,
                    uriToRestore
                )
                else -> Result.failure(Exception("لم يتم تحديد مصدر الاستعادة"))
            }
            if (res.isSuccess) {
                val insp = res.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastRestoreTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
                        lastLocalBackupSha256 = insp.actualSha256
                    )
                }
                _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)
            }
            onResult(res)
        }
    }

    fun uploadBackupToGoogleDrive(
        onProgress: (Int) -> Unit,
        onResult: (Result<CloudBackupInfo>) -> Unit
    ) {
        viewModelScope.launch {
            val currentSettings = prefsRepo.settings.value
            val localRes = BackupRestoreHelper.createStructuredArchiveBackup(
                context = appContext,
                sourceTag = "DriveCloudSync",
                keepMaxCount = currentSettings.keepBackupCount
            )
            if (localRes.isFailure) {
                onResult(Result.failure(localRes.exceptionOrNull() ?: Exception("تعذر تجهيز ملف النسخة للرفع")))
                return@launch
            }
            val rec = localRes.getOrThrow()
            _indexedBackups.value = BackupRestoreHelper.getIndexedBackups(appContext)

            val uploadRes = GoogleDriveBackupHelper.uploadBackupToDrive(
                backupFile = File(rec.filePath),
                accessToken = currentSettings.googleDriveAccessToken,
                onProgress = onProgress
            )
            if (uploadRes.isSuccess) {
                val cloud = uploadRes.getOrThrow()
                prefsRepo.updateSettings {
                    it.copy(
                        lastCloudBackupTime = cloud.modifiedTime,
                        lastCloudBackupSize = cloud.sizeBytes,
                        lastLocalBackupTime = rec.createdAt,
                        lastLocalBackupSize = rec.sizeBytes,
                        lastLocalBackupSha256 = rec.sha256
                    )
                }
            }
            onResult(uploadRes)
        }
    }
}
