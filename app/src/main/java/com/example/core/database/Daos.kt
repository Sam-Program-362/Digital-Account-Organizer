package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceAssetDao {
    @Query("SELECT * FROM service_assets")
    fun observeAllAssets(): Flow<List<ServiceAssetEntity>>

    @Query("SELECT * FROM service_assets")
    suspend fun getAllAssets(): List<ServiceAssetEntity>

    @Query("SELECT * FROM service_assets WHERE id = :id LIMIT 1")
    suspend fun getAssetById(id: String): ServiceAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: ServiceAssetEntity)

    @Query("DELETE FROM service_assets WHERE id = :id")
    suspend fun deleteAssetById(id: String)

    @Query(
        """
        SELECT * FROM service_assets 
        WHERE id NOT IN (SELECT iconAssetId FROM services WHERE iconAssetId IS NOT NULL)
        """
    )
    suspend fun getOrphanedAssets(): List<ServiceAssetEntity>
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY name COLLATE NOCASE ASC")
    fun observeAllServices(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllServices(): List<ServiceEntity>

    @Query("SELECT * FROM services WHERE id = :id LIMIT 1")
    suspend fun getServiceById(id: String): ServiceEntity?

    @Query("SELECT * FROM services WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): List<ServiceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertService(service: ServiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(service: ServiceEntity)

    @Update
    suspend fun updateService(service: ServiceEntity)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteServiceById(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY updatedAtEpochMillis DESC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY updatedAtEpochMillis DESC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE categoryId = :categoryId")
    suspend fun countAccountsForCategory(categoryId: String): Int

    @Query("UPDATE accounts SET categoryId = :targetCategoryId, updatedAtEpochMillis = :updatedAt WHERE categoryId = :sourceCategoryId")
    suspend fun reassignCategoryForAccounts(
        sourceCategoryId: String,
        targetCategoryId: String?,
        updatedAt: Long
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)
}

@Dao
interface ServiceAccountDao {
    @Query("SELECT * FROM service_accounts ORDER BY updatedAtEpochMillis DESC")
    fun observeAllServiceAccounts(): Flow<List<ServiceAccountEntity>>

    @Query("SELECT * FROM service_accounts ORDER BY updatedAtEpochMillis DESC")
    suspend fun getAllServiceAccounts(): List<ServiceAccountEntity>

    @Query("SELECT * FROM service_accounts WHERE id = :id LIMIT 1")
    suspend fun getServiceAccountById(id: String): ServiceAccountEntity?

    @Query("SELECT * FROM service_accounts WHERE serviceId = :serviceId ORDER BY updatedAtEpochMillis DESC")
    suspend fun getServiceAccountsForService(serviceId: String): List<ServiceAccountEntity>

    @Query("SELECT COUNT(*) FROM service_accounts WHERE serviceId = :serviceId")
    suspend fun countServiceAccountsForService(serviceId: String): Int

    @Query("SELECT COUNT(*) FROM service_accounts WHERE categoryId = :categoryId")
    suspend fun countServiceAccountsForCategory(categoryId: String): Int

    @Query("UPDATE service_accounts SET serviceId = :targetServiceId, updatedAtEpochMillis = :updatedAt WHERE serviceId = :sourceServiceId")
    suspend fun reassignServiceForServiceAccounts(
        sourceServiceId: String,
        targetServiceId: String,
        updatedAt: Long
    )

    @Query("UPDATE service_accounts SET categoryId = :targetCategoryId, updatedAtEpochMillis = :updatedAt WHERE categoryId = :sourceCategoryId")
    suspend fun reassignCategoryForServiceAccounts(
        sourceCategoryId: String,
        targetCategoryId: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM service_accounts WHERE serviceId = :serviceId")
    suspend fun deleteServiceAccountsForService(serviceId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertServiceAccount(serviceAccount: ServiceAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServiceAccount(serviceAccount: ServiceAccountEntity)

    @Update
    suspend fun updateServiceAccount(serviceAccount: ServiceAccountEntity)

    @Query("DELETE FROM service_accounts WHERE id = :id")
    suspend fun deleteServiceAccountById(id: String)
}

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences")
    fun observeAllPreferences(): Flow<List<AppPreferenceEntity>>

    @Query("SELECT * FROM app_preferences")
    suspend fun getAllPreferences(): List<AppPreferenceEntity>

    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): AppPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: AppPreferenceEntity)
}
