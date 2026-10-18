package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.FinancialAccountBalanceSummary
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.domain.usecase.FlexibleUnitValidator
import com.smartledger.pro.domain.usecase.HierarchicalCategoryValidator
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> observeAcrossRestores(block: () -> Flow<T>): Flow<T> {
    return SmartLedgerDatabase.databaseEpoch.flatMapLatest { block() }
}

/**
 * مستودع إدارة التصنيفات الهرمية المرنة متعدد المجالات (CategoryRepository)
 * يطبق كشف الدورات المغلقة ويمنع الحذف النهائي للتصنيفات المرتبطة بسجلات سابقة.
 */
class MeasurementUnitRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val writeMutex = Mutex()

    fun observeUnits(onlyActive: Boolean = false): Flow<List<MeasurementUnitEntity>> {
        return observeAcrossRestores {
            db.measurementUnitDao().observeUnits(if (onlyActive) 1 else 0)
        }
    }

    suspend fun saveUnit(
        id: Long = 0L,
        name: String,
        symbol: String = "",
        decimalPrecision: Int = 0,
        baseUnitId: Long? = null,
        conversionFactorToBase: Double = 1.0
    ): Result<Long> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val activeDb = db
                val unitDao = activeDb.measurementUnitDao()
                val existingUnits = unitDao.getAllUnitsOnce()
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val candidate = MeasurementUnitEntity(
                    Id = id,
                    Name = name.trim(),
                    Symbol = symbol.trim().ifBlank { name.trim() },
                    DecimalPrecision = decimalPrecision.coerceIn(0, 4),
                    BaseUnitId = if (baseUnitId != null && baseUnitId > 0L) baseUnitId else null,
                    ConversionFactorToBase = if (baseUnitId != null && baseUnitId > 0L) conversionFactorToBase else 1.0,
                    IsActive = true,
                    CreatedAt = now
                )
                val validation = FlexibleUnitValidator.validateUnit(candidate, existingUnits)
                if (validation.isFailure) {
                    return@withContext Result.failure(validation.exceptionOrNull()!!)
                }
                val savedId = if (id > 0L) {
                    unitDao.updateUnit(candidate)
                    id
                } else {
                    unitDao.insertUnit(candidate)
                }
                Result.success(savedId)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "تعذر حفظ وحدة القياس"))
            }
        }
    }

    suspend fun ensureUnitExists(name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext
        runCatching {
            val dao = db.measurementUnitDao()
            if (dao.findUnitByName(clean) == null) {
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                dao.insertUnit(
                    MeasurementUnitEntity(
                        Name = clean,
                        Symbol = clean,
                        DecimalPrecision = 0,
                        ConversionFactorToBase = 1.0,
                        IsActive = true,
                        CreatedAt = now
                    )
                )
            }
        }
    }

    suspend fun setUnitActive(id: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.measurementUnitDao().setUnitActive(id, isActive)
    }
}

/**
 * مستودع الصناديق والحسابات المالية والمحافظ والتحويلات الداخلية الذرية (FinancialAccountRepository)
 */
