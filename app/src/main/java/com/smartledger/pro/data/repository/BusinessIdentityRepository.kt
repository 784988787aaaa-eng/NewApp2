package com.smartledger.pro.data.repository

import androidx.room.withTransaction
import com.smartledger.pro.data.local.SmartLedgerDatabase
import com.smartledger.pro.data.local.dao.*
import com.smartledger.pro.data.local.entity.*
import com.smartledger.pro.domain.usecase.WeightedAverageCostCalculator
import com.smartledger.pro.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * امتداد مساعد يربط التدفقات التفاعلية (`Flow`) بمؤشر جيل قاعدة البيانات (`SmartLedgerDatabase.databaseEpoch`)،
 * بحيث يُعاد الاشتراك تلقائياً في نسخة `SmartLedgerDatabase` الجديدة فور إتمام استعادة النسخة الاحتياطية.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> observeAcrossDatabaseRestores(block: () -> Flow<T>): Flow<T> {
    return SmartLedgerDatabase.databaseEpoch.flatMapLatest { block() }
}

class BusinessIdentityRepository(private val dbProvider: () -> SmartLedgerDatabase) {
    constructor(db: SmartLedgerDatabase) : this({ db })

    private val db: SmartLedgerDatabase get() = dbProvider()
    private val identityDao: BusinessIdentityDao get() = db.businessIdentityDao()

    fun observeBusinessIdentity(): Flow<BusinessIdentityEntity?> =
        observeAcrossDatabaseRestores { identityDao.getIdentity() }

    suspend fun saveBusinessIdentity(identity: BusinessIdentityEntity) = withContext(Dispatchers.IO) {
        identityDao.saveIdentity(identity)
    }
}
