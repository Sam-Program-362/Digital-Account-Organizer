package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.EncryptedDatabaseFactory
import com.example.core.database.OrganizerRepository
import com.example.core.database.SaveAccountOutcome
import com.example.core.database.SaveCategoryOutcome
import com.example.core.database.SaveServiceOutcome
import com.example.core.files.ServiceAssetStorage
import com.example.core.security.AndroidKeystoreKeyWrapper
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.FileKeyEnvelopeStore
import com.example.domain.model.AccountStatus
import com.example.domain.model.ServiceDeleteResolution
import com.example.feature.backup.BackupEngine
import com.example.feature.backup.ConflictKind
import com.example.feature.backup.ConflictResolution
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var tempSecurityDir: File
    private lateinit var keyManager: DatabaseKeyManager
    private lateinit var databaseFactory: EncryptedDatabaseFactory
    private lateinit var assetStorage: ServiceAssetStorage
    private lateinit var repository: OrganizerRepository
    private lateinit var backupEngine: BackupEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempSecurityDir = Files.createTempDirectory("robo_sec").toFile()
        val envelopeStore = FileKeyEnvelopeStore(tempSecurityDir)
        val keystoreWrapper = AndroidKeystoreKeyWrapper(fallbackDirectory = tempSecurityDir)
        keyManager = DatabaseKeyManager(
            envelopeStore = envelopeStore,
            keystoreWrapper = keystoreWrapper
        )
        keyManager.initializeOrAutoUnlock()

        databaseFactory = EncryptedDatabaseFactory(
            context = context,
            keyManager = keyManager,
            useInMemoryForTesting = true
        )
        assetStorage = ServiceAssetStorage(context)
        repository = OrganizerRepository(databaseFactory, assetStorage)
        backupEngine = BackupEngine(databaseFactory, assetStorage)
    }

    @After
    fun tearDown() {
        databaseFactory.closeDatabase()
        assetStorage.clearAllAssets()
        tempSecurityDir.deleteRecursively()
    }

    @Test
    fun appName_matchesDigitalAccountOrganizer() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Digital Account Organizer", appName)
    }

    @Test
    fun repository_supportsMultipleAccountsPerService_andSafeServiceAndCategoryDeletion() = runBlocking {
        // 1. Import a custom service icon and create two custom services
        val samplePng = createSamplePngBytes()
        val asset = repository.importServiceIconFromBytes(samplePng)
        assertEquals("image/png", asset.mimeType)

        val fastmailOutcome = repository.saveService(
            name = "Fastmail",
            websiteUrl = "https://fastmail.com",
            description = "Primary email provider",
            iconAssetId = asset.id
        ) as SaveServiceOutcome.Saved

        // Duplicate service name detection
        val duplicateCheck = repository.saveService(
            name = "  FASTMAIL  ",
            websiteUrl = null,
            description = null,
            iconAssetId = null
        )
        assertTrue(duplicateCheck is SaveServiceOutcome.DuplicateNameDetected)

        val backupServiceOutcome = repository.saveService(
            name = "Proton",
            websiteUrl = "https://proton.me",
            description = "Secondary provider",
            iconAssetId = null
        ) as SaveServiceOutcome.Saved

        // 2. Create a category
        val workCat = (repository.saveCategory(
            name = "Work",
            description = "Career accounts"
        ) as SaveCategoryOutcome.Saved).category

        // 3. Create two accounts under the same Fastmail service
        val acc1 = (repository.saveAccount(
            serviceId = fastmailOutcome.service.id,
            displayName = "Personal Inbox",
            email = "alex@fastmail.com",
            username = "alex_p",
            categoryId = workCat.id,
            purpose = "Personal correspondence",
            recoveryEmailReference = "recovery@example.org",
            recoveryPhoneReference = "+1-555-0100",
            websiteUrl = null,
            notes = "Created in 2021",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved).account

        val acc2 = (repository.saveAccount(
            serviceId = fastmailOutcome.service.id,
            displayName = "Work Alias",
            email = "alex.work@fastmail.com",
            username = null,
            categoryId = workCat.id,
            purpose = "Consulting invoices",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = null,
            notes = null,
            status = AccountStatus.INACTIVE
        ) as SaveAccountOutcome.Saved).account

        val allAccounts = repository.observeAccountsWithDetails().first()
        assertEquals(2, allAccounts.size)

        // 4. Deleting category preserves accounts and clears categoryId
        repository.deleteCategorySafely(workCat.id, reassignToCategoryId = null)
        val afterCategoryDelete = repository.observeAccountsWithDetails().first()
        assertEquals(2, afterCategoryDelete.size)
        assertTrue(afterCategoryDelete.all { it.account.categoryId == null })

        // 5. Reassigning accounts when deleting Fastmail moves both accounts to Proton
        repository.deleteOrResolveService(
            serviceId = fastmailOutcome.service.id,
            resolution = ServiceDeleteResolution.REASSIGN_ACCOUNTS_TO_SERVICE,
            reassignTargetServiceId = backupServiceOutcome.service.id
        )
        val afterServiceReassign = repository.observeAccountsWithDetails().first()
        assertEquals(2, afterServiceReassign.size)
        assertTrue(afterServiceReassign.all { it.service.id == backupServiceOutcome.service.id })
    }

    @Test
    fun backupEngine_roundTripAcrossFreshDatabase_andConflictResolutions() = runBlocking {
        val samplePng = createSamplePngBytes()
        val asset = repository.importServiceIconFromBytes(samplePng)

        val github = (repository.saveService(
            name = "GitHub",
            websiteUrl = "https://github.com",
            description = "Code hosting",
            iconAssetId = asset.id
        ) as SaveServiceOutcome.Saved).service

        val devCat = (repository.saveCategory(
            name = "Developer",
            description = "Engineering tools"
        ) as SaveCategoryOutcome.Saved).category

        val acc = (repository.saveAccount(
            serviceId = github.id,
            displayName = "Open Source",
            email = "dev@example.com",
            username = "devhandle",
            categoryId = devCat.id,
            purpose = "OSS maintenance",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = null,
            notes = "2FA hardware key",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved).account

        val backupPassphrase = "portable-backup-secret".toCharArray()
        val backupBytes = backupEngine.createEncryptedBackupBytes(backupPassphrase)
        assertTrue(backupBytes.isNotEmpty())

        // Wrong backup passphrase must fail closed
        try {
            backupEngine.stageAndPreviewEncryptedBackup(
                backupBytes,
                "wrong-passphrase-123".toCharArray()
            )
            fail("Expected GeneralSecurityException on wrong backup passphrase")
        } catch (_: GeneralSecurityException) {
            // Expected
        }

        // Modify local account display name so staging on same DB detects an ID_CONFLICT
        repository.saveAccount(
            existingId = acc.id,
            serviceId = github.id,
            displayName = "Locally Modified Label",
            email = acc.email,
            username = acc.username,
            categoryId = devCat.id,
            purpose = acc.purpose,
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = null,
            notes = acc.notes,
            status = AccountStatus.ACTIVE
        )

        val stagedWithConflict = backupEngine.stageAndPreviewEncryptedBackup(
            backupBytes,
            backupPassphrase
        )
        val accountConflict = stagedWithConflict.conflicts.firstOrNull { it.incomingId == acc.id }
        assertNotNull(accountConflict)
        assertEquals(ConflictKind.ID_CONFLICT, accountConflict?.kind)

        // Choose KEEP_BOTH for the account conflict while skipping service/category conflicts
        val resolutions = stagedWithConflict.conflicts.associate { c ->
            if (c.incomingId == acc.id) {
                c.incomingId to ConflictResolution.KEEP_BOTH
            } else {
                c.incomingId to ConflictResolution.SKIP
            }
        }
        val summary = backupEngine.commitStagedBackup(stagedWithConflict, resolutions)
        assertEquals(1, summary.importedAccounts)

        val accountsAfterKeepBoth = repository.observeAccountsWithDetails().first()
        assertEquals(2, accountsAfterKeepBoth.size)

        // Verify CSV export escapes fields and includes both records
        val csv = backupEngine.exportPlaintextCsv()
        assertTrue(csv.contains("Open Source (Imported)"))
        assertTrue(csv.contains("Locally Modified Label"))
    }

    private fun createSamplePngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(16, 185, 129))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
