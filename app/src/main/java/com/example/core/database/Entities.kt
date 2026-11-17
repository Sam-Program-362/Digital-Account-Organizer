package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountRecord
import com.example.domain.model.AccountStatus
import com.example.domain.model.CategoryRecord
import com.example.domain.model.ServiceAssetRecord
import com.example.domain.model.ServiceRecord

@Entity(
    tableName = "service_assets",
    indices = [Index(value = ["sha256"])]
)
data class ServiceAssetEntity(
    @PrimaryKey val id: String,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val sha256: String,
    val createdAtEpochMillis: Long
) {
    fun toDomain(): ServiceAssetRecord = ServiceAssetRecord(
        id = id,
        relativePath = relativePath,
        mimeType = mimeType,
        width = width,
        height = height,
        byteSize = byteSize,
        sha256 = sha256,
        createdAtEpochMillis = createdAtEpochMillis
    )
}

@Entity(
    tableName = "services",
    foreignKeys = [
        ForeignKey(
            entity = ServiceAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["iconAssetId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["iconAssetId"]),
        Index(value = ["archivedAtEpochMillis"])
    ]
)
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val websiteUrl: String?,
    val description: String?,
    val iconAssetId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    fun toDomain(): ServiceRecord = ServiceRecord(
        id = id,
        name = name,
        normalizedName = normalizedName,
        websiteUrl = websiteUrl,
        description = description,
        iconAssetId = iconAssetId,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis
    )
}

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["normalizedName"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    fun toDomain(): CategoryRecord = CategoryRecord(
        id = id,
        name = name,
        normalizedName = normalizedName,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = ServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["serviceId"]),
        Index(value = ["categoryId"]),
        Index(value = ["status"]),
        Index(value = ["archivedAtEpochMillis"]),
        Index(value = ["updatedAtEpochMillis"]),
        Index(value = ["normalizedDisplayName"]),
        Index(value = ["normalizedEmail"]),
        Index(value = ["normalizedUsername"])
    ]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val displayName: String,
    val normalizedDisplayName: String,
    val email: String?,
    val normalizedEmail: String?,
    val username: String?,
    val normalizedUsername: String?,
    val categoryId: String?,
    val purpose: String?,
    val recoveryEmailReference: String?,
    val recoveryPhoneReference: String?,
    val websiteUrl: String?,
    val notes: String?,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    fun toDomain(): AccountRecord = AccountRecord(
        id = id,
        serviceId = serviceId,
        displayName = displayName,
        normalizedDisplayName = normalizedDisplayName,
        email = email,
        normalizedEmail = normalizedEmail,
        username = username,
        normalizedUsername = normalizedUsername,
        categoryId = categoryId,
        purpose = purpose,
        recoveryEmailReference = recoveryEmailReference,
        recoveryPhoneReference = recoveryPhoneReference,
        websiteUrl = websiteUrl,
        notes = notes,
        status = runCatching { AccountStatus.valueOf(status) }.getOrDefault(AccountStatus.ACTIVE),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis
    )
}

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtEpochMillis: Long
)
