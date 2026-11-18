package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountRecord
import com.example.domain.model.AccountStatus
import com.example.domain.model.CategoryRecord
import com.example.domain.model.ServiceAccountRecord
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

/**
 * Standalone core Account entity (e.g., example123@gmail.com, example321@example.com)
 * independent of any Service, storing its own encrypted password, encrypted backup codes,
 * multiple connected phone numbers, date of birth, and personalized avatar color.
 */
@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
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
    val displayName: String,
    val normalizedDisplayName: String,
    val email: String?,
    val normalizedEmail: String?,
    val username: String?,
    val normalizedUsername: String?,
    val encryptedPassword: String?,
    val categoryId: String?,
    val purpose: String?,
    val recoveryEmailReference: String?,
    val recoveryPhoneReference: String?,
    val notes: String?,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
    val phoneNumbersJson: String = "[]",
    val dateOfBirth: String? = null,
    val encryptedBackupCodes: String? = null,
    val avatarColorHex: String? = null
) {
    fun toDomain(): AccountRecord = AccountRecord(
        id = id,
        displayName = displayName,
        normalizedDisplayName = normalizedDisplayName,
        email = email,
        normalizedEmail = normalizedEmail,
        username = username,
        normalizedUsername = normalizedUsername,
        encryptedPassword = encryptedPassword,
        categoryId = categoryId,
        purpose = purpose,
        recoveryEmailReference = recoveryEmailReference,
        recoveryPhoneReference = recoveryPhoneReference,
        notes = notes,
        status = runCatching { AccountStatus.valueOf(status) }.getOrDefault(AccountStatus.ACTIVE),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
        phoneNumbers = parsePhoneNumbersJson(phoneNumbersJson),
        dateOfBirth = dateOfBirth,
        encryptedBackupCodes = encryptedBackupCodes,
        avatarColorHex = avatarColorHex
    )

    companion object {
        fun encodePhoneNumbersJson(numbers: List<String>): String {
            val cleaned = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isEmpty()) return "[]"
            val arr = org.json.JSONArray()
            cleaned.forEach { arr.put(it) }
            return arr.toString()
        }

        fun parsePhoneNumbersJson(rawJson: String?): List<String> {
            val text = rawJson?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(text)
                buildList {
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i).trim()
                        if (v.isNotEmpty()) add(v)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Service-specific Account entry belonging to a particular [ServiceEntity].
 * Can optionally reference a standalone [AccountEntity] via [linkedAccountId] OR use a custom
 * unlinked email/username/handle, with its own optional encrypted password and notes.
 */
@Entity(
    tableName = "service_accounts",
    foreignKeys = [
        ForeignKey(
            entity = ServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedAccountId"],
            onDelete = ForeignKey.SET_NULL
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
        Index(value = ["linkedAccountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["status"]),
        Index(value = ["archivedAtEpochMillis"]),
        Index(value = ["updatedAtEpochMillis"]),
        Index(value = ["normalizedDisplayName"]),
        Index(value = ["normalizedEmail"]),
        Index(value = ["normalizedUsername"])
    ]
)
data class ServiceAccountEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val linkedAccountId: String?,
    val displayName: String,
    val normalizedDisplayName: String,
    val email: String?,
    val normalizedEmail: String?,
    val username: String?,
    val normalizedUsername: String?,
    val encryptedPassword: String?,
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
    fun toDomain(): ServiceAccountRecord = ServiceAccountRecord(
        id = id,
        serviceId = serviceId,
        linkedAccountId = linkedAccountId,
        displayName = displayName,
        normalizedDisplayName = normalizedDisplayName,
        email = email,
        normalizedEmail = normalizedEmail,
        username = username,
        normalizedUsername = normalizedUsername,
        encryptedPassword = encryptedPassword,
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
