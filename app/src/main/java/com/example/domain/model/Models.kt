package com.example.domain.model

import java.security.SecureRandom
import java.util.Locale

enum class AccountStatus(val displayLabel: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    CLOSED("Closed")
}

enum class ThemePreference(val storageValue: String, val displayLabel: String) {
    SYSTEM("SYSTEM", "System Default"),
    LIGHT("LIGHT", "Light Mode"),
    DARK("DARK", "Dark Mode");

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AppColorPalette(
    val storageValue: String,
    val displayLabel: String,
    val subtitle: String,
    val previewHex: Long
) {
    EMERALD("EMERALD", "Emerald Trust", "Deep Slate & Emerald", 0xFF10B981),
    SAPPHIRE("SAPPHIRE", "Sapphire Vault", "Royal Navy & Cobalt Blue", 0xFF3B82F6),
    AMETHYST("AMETHYST", "Amethyst Cyber", "Obsidian & Electric Violet", 0xFF8B5CF6),
    AMBER("AMBER", "Amber Gold", "Warm Charcoal & Amber", 0xFFF59E0B),
    ROSE("ROSE", "Crimson Rose", "Carbon & Ruby Rose", 0xFFF43F5E),
    SLATE("SLATE", "Monochrome Slate", "Minimalist Graphite & Silver", 0xFF64748B);

    companion object {
        fun fromStorage(value: String?): AppColorPalette =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: EMERALD
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

/**
 * Represents a standalone core Account (e.g., example123@gmail.com, example321@example.com)
 * independent of any Service, with its own encrypted password and notes.
 */
data class AccountRecord(
    val id: String,
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
    val status: AccountStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    val isArchived: Boolean
        get() = archivedAtEpochMillis != null

    val hasPassword: Boolean
        get() = !encryptedPassword.isNullOrBlank()

    val hasNotes: Boolean
        get() = !notes.isNullOrBlank()

    val primaryIdentifier: String
        get() = email ?: username ?: displayName
}

data class LinkedServiceSummary(
    val serviceAccountId: String,
    val service: ServiceRecord,
    val serviceAsset: ServiceAssetRecord?,
    val entryLabel: String,
    val identifier: String?,
    val hasOwnPassword: Boolean,
    val status: AccountStatus
)

data class AccountWithDetails(
    val account: AccountRecord,
    val category: CategoryRecord?,
    val linkedServices: List<LinkedServiceSummary> = emptyList()
)

/**
 * Represents a Service-specific Account entry created inside a particular Service.
 * Can either link to an existing standalone [AccountRecord] ([linkedAccountId]) or use
 * a custom unlinked email/username/handle, with an optional encrypted password.
 */
data class ServiceAccountRecord(
    val id: String,
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
    val status: AccountStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?
) {
    val isArchived: Boolean
        get() = archivedAtEpochMillis != null

    val hasPassword: Boolean
        get() = !encryptedPassword.isNullOrBlank()

    val hasNotes: Boolean
        get() = !notes.isNullOrBlank()
}

data class ServiceAccountWithDetails(
    val serviceAccount: ServiceAccountRecord,
    val service: ServiceRecord,
    val serviceAsset: ServiceAssetRecord?,
    val linkedAccount: AccountRecord?,
    val category: CategoryRecord?
) {
    val effectiveEmail: String?
        get() = serviceAccount.email ?: linkedAccount?.email

    val effectiveUsername: String?
        get() = serviceAccount.username ?: linkedAccount?.username

    val effectiveIdentifier: String
        get() = effectiveEmail ?: effectiveUsername ?: linkedAccount?.displayName ?: serviceAccount.displayName
}

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

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+?"
    private val random = SecureRandom()

    fun generatePassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val safeLength = length.coerceIn(8, 64)
        val pools = mutableListOf(LOWER)
        if (includeUppercase) pools.add(UPPER)
        if (includeDigits) pools.add(DIGITS)
        if (includeSymbols) pools.add(SYMBOLS)
        val combined = pools.joinToString("")

        val chars = CharArray(safeLength)
        // Guarantee at least one character from each enabled pool
        for (i in pools.indices) {
            val pool = pools[i]
            chars[i] = pool[random.nextInt(pool.length)]
        }
        for (i in pools.size until safeLength) {
            chars[i] = combined[random.nextInt(combined.length)]
        }
        // Fisher-Yates shuffle
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    fun estimateStrengthLabel(password: String): String {
        if (password.isEmpty()) return "None"
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score <= 2 -> "Weak"
            score <= 4 -> "Good"
            score == 5 -> "Strong"
            else -> "Vault-Grade"
        }
    }
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
