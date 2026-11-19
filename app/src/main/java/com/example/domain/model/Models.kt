package com.example.domain.model

import java.security.SecureRandom
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AccountStatus(val displayLabel: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    CLOSED("Closed")
}

enum class AccountAvatarColor(
    val storageHex: String,
    val displayLabel: String,
    val containerHex: Long,
    val contentHex: Long
) {
    EMERALD("#10B981", "Emerald", 0xFF065F46, 0xFF6EE7B7),
    SAPPHIRE("#3B82F6", "Sapphire", 0xFF1E3A8A, 0xFF93C5FD),
    VIOLET("#8B5CF6", "Violet", 0xFF4C1D95, 0xFFC4B5FD),
    AMBER("#F59E0B", "Amber", 0xFF78350F, 0xFFFCD34D),
    ROSE("#F43F5E", "Crimson", 0xFF881337, 0xFFFDA4AF),
    CYAN("#06B6D4", "Cyan", 0xFF164E63, 0xFF67E8F9),
    INDIGO("#6366F1", "Indigo", 0xFF312E81, 0xFFA5B4FC),
    TEAL("#14B8A6", "Teal", 0xFF134E4A, 0xFF5EEAD4),
    CORAL("#FB7185", "Coral", 0xFF7F1D1D, 0xFFFECACA),
    SLATE("#64748B", "Slate", 0xFF1E293B, 0xFFCBD5E1);

    companion object {
        fun fromHex(hex: String?): AccountAvatarColor? {
            if (hex.isNullOrBlank()) return null
            return entries.firstOrNull { it.storageHex.equals(hex.trim(), ignoreCase = true) }
        }
    }
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
 * independent of any Service, with its own encrypted password, encrypted backup codes,
 * multiple connected phone numbers, date of birth, and personalized avatar color.
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
    val archivedAtEpochMillis: Long?,
    val phoneNumbers: List<String> = emptyList(),
    val dateOfBirth: String? = null,
    val encryptedBackupCodes: String? = null,
    val avatarColorHex: String? = null
) {
    val isArchived: Boolean
        get() = archivedAtEpochMillis != null

    val hasPassword: Boolean
        get() = !encryptedPassword.isNullOrBlank()

    val hasBackupCodes: Boolean
        get() = !encryptedBackupCodes.isNullOrBlank()

    val hasNotes: Boolean
        get() = !notes.isNullOrBlank()

    val primaryIdentifier: String
        get() = email ?: username ?: displayName

    /**
     * Single-letter avatar monogram: uses the first letter of the email address when present,
     * falling back to username or displayName.
     */
    val avatarSingleLetter: String
        get() = computeSingleAvatarLetter(email = email, identifier = username, displayName = displayName)

    fun calculateAgeYears(today: LocalDate = LocalDate.now()): Int? =
        DateOfBirthCalculator.calculateAgeYears(dateOfBirth, today)

    companion object {
        fun computeSingleAvatarLetter(
            email: String?,
            identifier: String?,
            displayName: String
        ): String {
            val source = when {
                !email.isNullOrBlank() -> email.trim()
                !identifier.isNullOrBlank() -> identifier.trim()
                else -> displayName.trim()
            }
            val ch = source.firstOrNull { it.isLetterOrDigit() } ?: source.firstOrNull()
            return ch?.uppercaseChar()?.toString() ?: "A"
        }
    }
}

object DateOfBirthCalculator {
    private val supportedFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    )

    fun parseDate(raw: String?): LocalDate? {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (formatter in supportedFormatters) {
            val parsed = runCatching { LocalDate.parse(cleaned, formatter) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    fun calculateAgeYears(raw: String?, today: LocalDate = LocalDate.now()): Int? {
        val birthDate = parseDate(raw) ?: return null
        if (birthDate.isAfter(today)) return null
        return Period.between(birthDate, today).years.coerceAtLeast(0)
    }

    fun formatDobWithAge(raw: String?, today: LocalDate = LocalDate.now()): String? {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val age = calculateAgeYears(cleaned, today)
        return if (age != null) {
            "$cleaned ($age yrs old)"
        } else {
            cleaned
        }
    }
}

object BackupCodesFormatter {
    /**
     * Parses raw user-entered or decrypted backup codes text into clean individual codes.
     */
    fun parseCodesList(raw: String?): List<String> {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return cleaned
            .split(Regex("[\\r\\n,;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun normalizeForStorage(raw: String?): String? {
        val codes = parseCodesList(raw)
        if (codes.isEmpty()) return null
        return codes.joinToString("\n")
    }
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
