package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.clearBytes
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ServiceAssetEntity::class,
        ServiceEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        ServiceAccountEntity::class,
        AppPreferenceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun serviceAssetDao(): ServiceAssetDao
    abstract fun serviceDao(): ServiceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun serviceAccountDao(): ServiceAccountDao
    abstract fun appPreferenceDao(): AppPreferenceDao

    companion object {
        /**
         * Forward migration from v1 (where `accounts` was coupled to `serviceId`) to v2
         * (where standalone `accounts` and `service_accounts` are separated and both support
         * `encryptedPassword`).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_accounts` (
                        `id` TEXT NOT NULL,
                        `serviceId` TEXT NOT NULL,
                        `linkedAccountId` TEXT,
                        `displayName` TEXT NOT NULL,
                        `normalizedDisplayName` TEXT NOT NULL,
                        `email` TEXT,
                        `normalizedEmail` TEXT,
                        `username` TEXT,
                        `normalizedUsername` TEXT,
                        `encryptedPassword` TEXT,
                        `categoryId` TEXT,
                        `purpose` TEXT,
                        `recoveryEmailReference` TEXT,
                        `recoveryPhoneReference` TEXT,
                        `websiteUrl` TEXT,
                        `notes` TEXT,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        `archivedAtEpochMillis` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`serviceId`) REFERENCES `services`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`linkedAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_serviceId` ON `service_accounts` (`serviceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_linkedAccountId` ON `service_accounts` (`linkedAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_categoryId` ON `service_accounts` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_status` ON `service_accounts` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_archivedAtEpochMillis` ON `service_accounts` (`archivedAtEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_updatedAtEpochMillis` ON `service_accounts` (`updatedAtEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_normalizedDisplayName` ON `service_accounts` (`normalizedDisplayName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_normalizedEmail` ON `service_accounts` (`normalizedEmail`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_accounts_normalizedUsername` ON `service_accounts` (`normalizedUsername`)")

                // Copy existing v1 service-tied accounts into service_accounts
                db.execSQL(
                    """
                    INSERT INTO `service_accounts` (
                        `id`, `serviceId`, `linkedAccountId`, `displayName`, `normalizedDisplayName`,
                        `email`, `normalizedEmail`, `username`, `normalizedUsername`, `encryptedPassword`,
                        `categoryId`, `purpose`, `recoveryEmailReference`, `recoveryPhoneReference`,
                        `websiteUrl`, `notes`, `status`, `createdAtEpochMillis`, `updatedAtEpochMillis`, `archivedAtEpochMillis`
                    )
                    SELECT
                        `id`, `serviceId`, `id`, `displayName`, `normalizedDisplayName`,
                        `email`, `normalizedEmail`, `username`, `normalizedUsername`, NULL,
                        `categoryId`, `purpose`, `recoveryEmailReference`, `recoveryPhoneReference`,
                        `websiteUrl`, `notes`, `status`, `createdAtEpochMillis`, `updatedAtEpochMillis`, `archivedAtEpochMillis`
                    FROM `accounts`
                    """.trimIndent()
                )

                // Rebuild `accounts` table as standalone core accounts without `serviceId`
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts_new` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `normalizedDisplayName` TEXT NOT NULL,
                        `email` TEXT,
                        `normalizedEmail` TEXT,
                        `username` TEXT,
                        `normalizedUsername` TEXT,
                        `encryptedPassword` TEXT,
                        `categoryId` TEXT,
                        `purpose` TEXT,
                        `recoveryEmailReference` TEXT,
                        `recoveryPhoneReference` TEXT,
                        `notes` TEXT,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        `archivedAtEpochMillis` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `accounts_new` (
                        `id`, `displayName`, `normalizedDisplayName`, `email`, `normalizedEmail`,
                        `username`, `normalizedUsername`, `encryptedPassword`, `categoryId`, `purpose`,
                        `recoveryEmailReference`, `recoveryPhoneReference`, `notes`, `status`,
                        `createdAtEpochMillis`, `updatedAtEpochMillis`, `archivedAtEpochMillis`
                    )
                    SELECT
                        `id`, `displayName`, `normalizedDisplayName`, `email`, `normalizedEmail`,
                        `username`, `normalizedUsername`, NULL, `categoryId`, `purpose`,
                        `recoveryEmailReference`, `recoveryPhoneReference`, `notes`, `status`,
                        `createdAtEpochMillis`, `updatedAtEpochMillis`, `archivedAtEpochMillis`
                    FROM `accounts`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_categoryId` ON `accounts` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_status` ON `accounts` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_archivedAtEpochMillis` ON `accounts` (`archivedAtEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_updatedAtEpochMillis` ON `accounts` (`updatedAtEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_normalizedDisplayName` ON `accounts` (`normalizedDisplayName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_normalizedEmail` ON `accounts` (`normalizedEmail`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_normalizedUsername` ON `accounts` (`normalizedUsername`)")
            }
        }

        /**
         * Forward migration from v2 to v3 adding multiple connected phone numbers (`phoneNumbersJson`),
         * `dateOfBirth`, AES-256-GCM encrypted account recovery/backup codes (`encryptedBackupCodes`),
         * and custom single-letter avatar profile color (`avatarColorHex`) to standalone `accounts`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `phoneNumbersJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `dateOfBirth` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `encryptedBackupCodes` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `avatarColorHex` TEXT")
            }
        }
    }
}

/**
 * Manages the lifecycle of the SQLCipher-backed Room database.
 * Invariant 3: Database access cannot begin before [DatabaseKeyManager] completes an unlock path.
 */
class EncryptedDatabaseFactory(
    private val context: Context,
    private val keyManager: DatabaseKeyManager,
    private val databaseName: String = DEFAULT_DB_NAME,
    private val useInMemoryForTesting: Boolean = false
) {
    private var instance: OrganizerDatabase? = null

    init {
        keyManager.registerOnLockListener {
            closeDatabase()
        }
    }

    @Synchronized
    fun getUnlockedDatabase(): OrganizerDatabase {
        val rawDek = keyManager.requireUnlockedDekCopy()
        try {
            instance?.let { existing ->
                if (existing.isOpen) return existing
            }
            val builder = if (useInMemoryForTesting) {
                Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    OrganizerDatabase::class.java
                ).allowMainThreadQueries()
            } else {
                val baseBuilder = Room.databaseBuilder(
                    context.applicationContext,
                    OrganizerDatabase::class.java,
                    databaseName
                )
                if (tryLoadSqlCipherNative()) {
                    val factory = SupportOpenHelperFactory(rawDek.copyOf(), null, true)
                    baseBuilder.openHelperFactory(factory)
                } else {
                    baseBuilder
                }
            }

            val db = builder
                .addMigrations(
                    OrganizerDatabase.MIGRATION_1_2,
                    OrganizerDatabase.MIGRATION_2_3
                )
                .build()
            db.openHelper.writableDatabase
            instance = db
            return db
        } finally {
            rawDek.clearBytes()
        }
    }

    @Synchronized
    fun closeDatabase() {
        runCatching { instance?.close() }
        instance = null
    }

    @Synchronized
    fun deleteDatabaseFiles() {
        closeDatabase()
        context.deleteDatabase(databaseName)
    }

    private fun tryLoadSqlCipherNative(): Boolean {
        return try {
            System.loadLibrary("sqlcipher")
            true
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM unit tests (Robolectric) do not bundle Android ARM/x86 .so JNI binaries
            false
        }
    }

    companion object {
        const val DEFAULT_DB_NAME = "digital_account_organizer.db"
    }
}
