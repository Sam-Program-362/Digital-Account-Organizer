package com.example.domain.model

import java.util.Locale

enum class AccountStatus(val displayLabel: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    CLOSED("Closed")
}

enum class ThemePreference(val storageValue: String, val displayLabel: String) {
    SYSTEM("SYSTEM", "Follow System"),
    LIGHT("LIGHT", "Light Theme"),
    DARK("DARK", "Dark Theme");

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

data class ServiceAssetRecord(
    val id: String,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val sha256: String,
    val createdAtEpochMillis: Long
)

data class ServiceRecord(
    val id: String,
    val name: String,
    val normalizedName: String,
    val websiteUrl: String?,
    val description: String?,
    val iconAssetId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    val isArchived: Boolean
        get() = archivedAtEpochMillis != null
}

data class ServiceWithCount(
    val service: ServiceRecord,
    val iconAsset: ServiceAssetRecord?,
    val activeAccountCount: Int,
    val totalAccountCount: Int
)

data class CategoryRecord(
    val id: String,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class CategoryWithCount(
    val category: CategoryRecord,
    val activeAccountCount: Int,
    val totalAccountCount: Int
)

data class AccountRecord(
    val id: String,
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
    val status: AccountStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    val isArchived: Boolean
        get() = archivedAtEpochMillis != null
}

data class AccountWithDetails(
    val account: AccountRecord,
    val service: ServiceRecord,
    val serviceAsset: ServiceAssetRecord?,
    val category: CategoryRecord?
)

data class DashboardSummary(
    val totalActiveAccounts: Int,
    val totalArchivedAccounts: Int,
    val totalServices: Int,
    val totalCategories: Int,
    val recentAccounts: List<AccountWithDetails>,
    val topCategories: List<CategoryWithCount>,
    val topServices: List<ServiceWithCount>
)

enum class ServiceDeleteResolution {
    ARCHIVE_SERVICE_INSTEAD,
    REASSIGN_ACCOUNTS_TO_SERVICE,
    DELETE_SERVICE_AND_ACCOUNTS
}

object Normalizer {
    fun normalizeName(input: String): String =
        input.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun normalizeOptionalIdentifier(input: String?): String? {
        val trimmed = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.lowercase(Locale.ROOT)
    }

    fun cleanOptionalText(input: String?): String? =
        input?.trim()?.takeIf { it.isNotEmpty() }

    fun validateOptionalUrl(url: String?): Boolean {
        val cleaned = cleanOptionalText(url) ?: return true
        return cleaned.startsWith("https://", ignoreCase = true) ||
            cleaned.startsWith("http://", ignoreCase = true) ||
            cleaned.contains(".")
    }
}
