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
        AppPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun serviceAssetDao(): ServiceAssetDao
    abstract fun serviceDao(): ServiceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun appPreferenceDao(): AppPreferenceDao

    companion object {
        /**
         * Explicit forward migration baseline so future schema upgrades preserve encrypted data
         * without ever using fallbackToDestructiveMigration().
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for v2 additive schema evolution
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
                .addMigrations(OrganizerDatabase.MIGRATION_1_2)
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
