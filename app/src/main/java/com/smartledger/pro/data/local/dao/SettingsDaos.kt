package com.smartledger.pro.data.local.dao

import androidx.room.*
import com.smartledger.pro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedItemDao {
    @Query("SELECT * FROM DeletedItems ORDER BY DeletedAt DESC, Id DESC")
    fun getAllDeletedItems(): Flow<List<DeletedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(item: DeletedItemEntity): Long

    @Query("DELETE FROM DeletedItems WHERE Id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM DeletedItems WHERE EntityType = :entityType AND OriginalId = :originalId")
    suspend fun deleteByEntityAndOriginalId(entityType: String, originalId: Long)

    @Query("SELECT * FROM DeletedItems WHERE Id = :id LIMIT 1")
    suspend fun getDeletedItemById(id: Long): DeletedItemEntity?
}

@Dao
interface BusinessIdentityDao {
    @Query("SELECT * FROM BusinessIdentity WHERE Id = 1 LIMIT 1")
    fun getIdentity(): Flow<BusinessIdentityEntity?>

    @Query("SELECT * FROM BusinessIdentity WHERE Id = 1 LIMIT 1")
    suspend fun getIdentityOnce(): BusinessIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIdentity(identity: BusinessIdentityEntity)
}

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM AppPreferences")
    fun getAllPreferences(): Flow<List<AppPreferenceEntity>>

    @Query("SELECT * FROM AppPreferences")
    suspend fun getAllPreferencesOnce(): List<AppPreferenceEntity>

    @Query("SELECT Value FROM AppPreferences WHERE Key = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: AppPreferenceEntity)

    @Query("DELETE FROM AppPreferences")
    suspend fun clearAllPreferences()
}
