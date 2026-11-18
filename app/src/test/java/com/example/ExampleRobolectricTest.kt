package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.EncryptedDatabaseFactory
import com.example.core.database.OrganizerRepository
import com.example.core.database.SaveAccountOutcome
import com.example.core.database.SaveCategoryOutcome
import com.example.core.database.SaveServiceAccountOutcome
import com.example.core.database.SaveServiceOutcome
import com.example.core.files.BackupVaultStorage
import com.example.core.files.ServiceAssetStorage
import com.example.core.security.AndroidKeystoreKeyWrapper
import com.example.core.security.AutoLockTimeout
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.FileKeyEnvelopeStore
import com.example.core.security.KdfParameters
import com.example.core.security.LockState
import com.example.domain.model.AccountStatus
import com.example.domain.model.AppColorPalette
import com.example.feature.backup.BackupEngine
import com.example.feature.backup.ConflictResolution
import com.example.feature.backup.ExportSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var testSecurityDir: File
    private lateinit var keyManager: DatabaseKeyManager
    private lateinit var databaseFactory: EncryptedDatabaseFactory
    private lateinit var assetStorage: ServiceAssetStorage
    private lateinit var repository: OrganizerRepository

    private val fastKdf = KdfParameters()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testSecurityDir = File(context.filesDir, "test_security_${System.nanoTime()}").apply {
            mkdirs()
        }
        val envelopeStore = FileKeyEnvelopeStore(testSecurityDir)
        val keystoreWrapper = AndroidKeystoreKeyWrapper(fallbackDirectory = testSecurityDir)
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
        repository = OrganizerRepository(
            databaseFactory = databaseFactory,
            assetStorage = assetStorage,
            keyManager = keyManager
        )
    }

    @After
    fun tearDown() {
        databaseFactory.closeDatabase()
        testSecurityDir.deleteRecursively()
    }

    @Test
    fun standaloneAccountsAndServiceAccountsStayDecoupledAndLinkProperly() = runBlocking {
        val catOutcome = repository.saveCategory(
            existingId = null,
            name = "Developer",
            description = "Coding & cloud accounts"
        ) as SaveCategoryOutcome.Saved

        // 1. Create two standalone Accounts (no service required)
        val gmailOutcome = repository.saveAccount(
            existingId = null,
            displayName = "Personal Gmail",
            email = "example123@gmail.com",
            username = "example123",
            password = "MySecretGmailPassword!99",
            categoryId = catOutcome.category.id,
            purpose = "Primary Personal Identity",
            recoveryEmailReference = "backup@example.com",
            recoveryPhoneReference = "+1-555-0100",
            notes = "Main Google identity notes",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved

        repository.saveAccount(
            existingId = null,
            displayName = "Work Identity",
            email = "example321@example.com",
            username = "work321",
            password = null,
            categoryId = catOutcome.category.id,
            purpose = "Corporate email",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            notes = null,
            status = AccountStatus.ACTIVE
        )

        // 2. Create a Service (GitHub) with two Service Account entries:
        //    one linked to Personal Gmail, and one unlinked custom service account
        val githubOutcome = repository.saveService(
            existingId = null,
            name = "GitHub",
            websiteUrl = "https://github.com",
            description = "Code hosting",
            iconAssetId = null
        ) as SaveServiceOutcome.Saved

        repository.saveServiceAccount(
            existingId = null,
            serviceId = githubOutcome.service.id,
            linkedAccountId = gmailOutcome.account.id,
            displayName = "Main OSS Profile",
            email = "example123@gmail.com",
            username = "example-oss",
            password = null, // Optional password left blank -> uses linked account
            categoryId = catOutcome.category.id,
            purpose = "Open source repos",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = "https://github.com/example-oss",
            notes = "Uses personal Gmail login",
            status = AccountStatus.ACTIVE
        ) as SaveServiceAccountOutcome.Saved

        repository.saveServiceAccount(
            existingId = null,
            serviceId = githubOutcome.service.id,
            linkedAccountId = null,
            displayName = "Bot Deployment Handle",
            email = "bot@ci.example.org",
            username = "ci-release-bot",
            password = "BotServicePassword#42",
            categoryId = catOutcome.category.id,
            purpose = "Automated releases",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = "https://github.com",
            notes = "Dedicated CI bot",
            status = AccountStatus.ACTIVE
        ) as SaveServiceAccountOutcome.Saved

        // Verify standalone accounts count is 2 (Service Accounts do NOT inflate standalone accounts)
        val standaloneAccounts = repository.observeAccountsWithDetails().first()
        assertEquals(2, standaloneAccounts.size)

        val summary = repository.observeDashboardSummary().first()
        assertEquals(2, summary.totalActiveAccounts)
        assertEquals(1, summary.totalServices)

        // Verify Personal Gmail shows GitHub under linkedServices and decrypts its password
        val gmailDetails = standaloneAccounts.first { it.account.id == gmailOutcome.account.id }
        assertTrue(gmailDetails.account.hasPassword)
        assertEquals(
            "MySecretGmailPassword!99",
            repository.decryptSecret(gmailDetails.account.encryptedPassword)
        )
        assertEquals(1, gmailDetails.linkedServices.size)
        assertEquals("GitHub", gmailDetails.linkedServices.first().service.name)
        assertEquals("Main OSS Profile", gmailDetails.linkedServices.first().entryLabel)

        // Verify Service Accounts list has 2 entries for GitHub
        val serviceAccounts = repository.observeServiceAccountsWithDetails().first()
        assertEquals(2, serviceAccounts.size)
        val botEntry = serviceAccounts.first { it.serviceAccount.username == "ci-release-bot" }
        assertTrue(botEntry.serviceAccount.hasPassword)
        assertEquals(
            "BotServicePassword#42",
            repository.decryptSecret(botEntry.serviceAccount.encryptedPassword)
        )
    }

    @Test
    fun editingAccountAndServiceAccountPreservesPasswordWhenBlankAndClearsWhenRequested() = runBlocking {
        val created = repository.saveAccount(
            existingId = null,
            displayName = "Primary Vault Mail",
            email = "vault@example.com",
            username = "vault_user",
            password = "InitialSecretPassword!1",
            categoryId = null,
            purpose = "Identity",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            notes = "Keep safe",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved

        // Edit display name while leaving password blank (editor does not pre-fill password)
        val editedKeepPassword = repository.saveAccount(
            existingId = created.account.id,
            displayName = "Updated Vault Mail",
            email = "vault@example.com",
            username = "vault_user",
            password = "",
            clearSavedPassword = false,
            categoryId = null,
            purpose = "Updated Identity",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            notes = "Keep safe",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved

        assertTrue(editedKeepPassword.account.hasPassword)
        assertEquals(
            "InitialSecretPassword!1",
            repository.decryptSecret(editedKeepPassword.account.encryptedPassword)
        )

        // Edit with clearSavedPassword = true -> removes saved encrypted password
        val editedCleared = repository.saveAccount(
            existingId = created.account.id,
            displayName = "Updated Vault Mail",
            email = "vault@example.com",
            username = "vault_user",
            password = "",
            clearSavedPassword = true,
            categoryId = null,
            purpose = "Updated Identity",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            notes = "Keep safe",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved

        assertFalse(editedCleared.account.hasPassword)
        assertNull(editedCleared.account.encryptedPassword)
    }

    @Test
    fun backupVaultStorageSavesBrowsesFoldersAndReadsExternalBackupFiles() = runBlocking {
        val vaultStorage = BackupVaultStorage(context)
        val sampleEncryptedBytes = "encrypted-dao-backup-payload".encodeToByteArray()

        val savedBackup = vaultStorage.saveBackupBytesToDownloadsAndVault(
            fileName = "test-vault.dao-backup",
            bytes = sampleEncryptedBytes
        )
        assertTrue(savedBackup.fileName.endsWith(".dao-backup"))

        val listed = vaultStorage.listSavedBackups()
        assertTrue(listed.any { it.fileName == savedBackup.fileName })

        val readBack = vaultStorage.readVaultFileBytes(savedBackup.vaultEntry.absolutePath)
        assertArrayEquals(sampleEncryptedBytes, readBack)

        // Simulate a .dao-backup file transferred from another device into an external folder
        val externalFolder = File(context.cacheDir, "transferred_from_other_phone").apply { mkdirs() }
        val externalBackupFile = File(externalFolder, "migrated_accounts.dao-backup").apply {
            writeBytes(sampleEncryptedBytes)
        }
        val folderListing = vaultStorage.browseDirectory(externalFolder.absolutePath)
        assertTrue(folderListing.readable)
        assertTrue(folderListing.entries.any { it.name == "migrated_accounts.dao-backup" && !it.isDirectory })
        assertArrayEquals(sampleEncryptedBytes, vaultStorage.readVaultFileBytes(externalBackupFile.absolutePath))

        val savedCsv = vaultStorage.saveCsvToDownloadsAndVault(
            fileName = "test-export.csv",
            csvContent = "record_type,name\nACCOUNT,example@gmail.com"
        )
        assertTrue(savedCsv.fileName.endsWith(".csv"))
        assertTrue(vaultStorage.deleteVaultFile(savedBackup.vaultEntry.absolutePath))
        assertTrue(vaultStorage.deleteVaultFile(savedCsv.vaultEntry.absolutePath))
        externalFolder.deleteRecursively()

        // Verify restrictScreenshotsEntireApp defaults to false and persists when toggled
        assertFalse(repository.observeRestrictScreenshotsEntireApp().first())
        repository.setRestrictScreenshotsEntireApp(true)
        assertTrue(repository.observeRestrictScreenshotsEntireApp().first())
        repository.setRestrictScreenshotsEntireApp(false)
        assertFalse(repository.observeRestrictScreenshotsEntireApp().first())
    }

    @Test
    fun eightCharMasterPasswordVaultSessionAndEncryptedRecoveryCodeWork() {
        val eightCharPassword = "MyPass8!".toCharArray()
        val result = keyManager.enableAppLock(
            newMasterPassword = eightCharPassword,
            autoLockTimeout = AutoLockTimeout.ONE_MINUTE,
            kdfParameters = fastKdf
        )
        val recoveryCodeStr = String(result.recoveryCode)
        assertTrue(recoveryCodeStr.isNotBlank())

        // Verify permanent encrypted recovery code is stored and retrievable
        assertEquals(recoveryCodeStr, keyManager.getDecryptedRecoveryCode())

        val initialUnlocked = keyManager.lockState.value as LockState.Unlocked
        assertFalse(initialUnlocked.recoveryCodesDownloadedOnce)

        keyManager.markRecoveryCodeFileDownloadedOnce()
        val afterDownload = keyManager.lockState.value as LockState.Unlocked
        assertTrue(afterDownload.recoveryCodesDownloadedOnce)

        // Verify 5-minute Password Manager verification session
        keyManager.clearVaultSessionVerification()
        assertTrue(keyManager.isVaultVerificationRequired())
        assertTrue(keyManager.verifyVaultWithMasterPassword("MyPass8!".toCharArray()))
        assertFalse(keyManager.isVaultVerificationRequired())

        // Verify recovery code resets Master Password with an 8-character password
        keyManager.lockNow()
        val resetResult = keyManager.recoverAndResetMasterPassword(
            recoveryCode = recoveryCodeStr.toCharArray(),
            newMasterPassword = "NewPass8".toCharArray(),
            kdfParameters = fastKdf
        )
        assertNotNull(resetResult.recoveryCode)
        keyManager.lockNow()
        keyManager.unlockWithMasterPassword("NewPass8".toCharArray())
        assertTrue(keyManager.lockState.value is LockState.Unlocked)
    }

    @Test
    fun colorPalettePreferencePersistsIndependentlyOfThemeMode() = runBlocking {
        assertEquals(AppColorPalette.EMERALD, repository.observeColorPalette().first())
        repository.setColorPalette(AppColorPalette.AMETHYST)
        assertEquals(AppColorPalette.AMETHYST, repository.observeColorPalette().first())
    }

    @Test
    fun autoLockedBackupExportAndRestorePreservesAccountsServiceAccountsPasswordsAndRecoveryCode() = runBlocking {
        val lockResult = keyManager.enableAppLock(
            newMasterPassword = "Backup88".toCharArray(),
            autoLockTimeout = AutoLockTimeout.IMMEDIATE,
            kdfParameters = fastKdf
        )
        val originalRecoveryCode = String(lockResult.recoveryCode)

        val savedAccount = repository.saveAccount(
            existingId = null,
            displayName = "Primary Proton",
            email = "alice@proton.me",
            username = "alice_sec",
            password = "ProtonVaultPassword!1",
            categoryId = null,
            purpose = "Encrypted Mail",
            recoveryEmailReference = "alice.rec@example.com",
            recoveryPhoneReference = null,
            notes = "Hardware 2FA enabled",
            status = AccountStatus.ACTIVE
        ) as SaveAccountOutcome.Saved

        val savedService = repository.saveService(
            existingId = null,
            name = "Proton",
            websiteUrl = "https://proton.me",
            description = "Swiss mail",
            iconAssetId = null
        ) as SaveServiceOutcome.Saved

        repository.saveServiceAccount(
            existingId = null,
            serviceId = savedService.service.id,
            linkedAccountId = savedAccount.account.id,
            displayName = "Proton Mail Inbox",
            email = "alice@proton.me",
            username = "alice_sec",
            password = "ServiceSpecificPass!2",
            categoryId = null,
            purpose = "Main inbox",
            recoveryEmailReference = null,
            recoveryPhoneReference = null,
            websiteUrl = "https://mail.proton.me",
            notes = "Linked to Primary Proton",
            status = AccountStatus.ACTIVE
        )

        val backupEngine = BackupEngine(
            databaseFactory = databaseFactory,
            assetStorage = assetStorage,
            keyManager = keyManager
        )

        val exportLockKey = backupEngine.generateExportLockKey()
        assertTrue(exportLockKey.startsWith("DAO-"))

        val backupBytes = backupEngine.createEncryptedBackupBytes(
            passphrase = exportLockKey.toCharArray(),
            selection = ExportSelection(),
            kdfParameters = fastKdf
        )

        // Wipe database tables to simulate restoring onto a fresh workspace
        databaseFactory.getUnlockedDatabase().clearAllTables()
        assertEquals(0, repository.observeAccountsWithDetails().first().size)
        assertEquals(0, repository.observeServiceAccountsWithDetails().first().size)

        val staged = backupEngine.stageAndPreviewEncryptedBackup(
            backupFileBytes = backupBytes,
            passphrase = exportLockKey.toCharArray()
        )
        val summary = backupEngine.commitStagedBackup(
            staged = staged,
            resolutionsByIncomingId = emptyMap<String, ConflictResolution>()
        )

        assertEquals(1, summary.importedServices)
        assertEquals(1, summary.importedAccounts)
        assertEquals(1, summary.importedServiceAccounts)

        val restoredAccounts = repository.observeAccountsWithDetails().first()
        assertEquals(1, restoredAccounts.size)
        assertEquals("alice@proton.me", restoredAccounts.first().account.email)
        assertEquals(
            "ProtonVaultPassword!1",
            repository.decryptSecret(restoredAccounts.first().account.encryptedPassword)
        )
        assertEquals(1, restoredAccounts.first().linkedServices.size)

        val restoredServiceAccounts = repository.observeServiceAccountsWithDetails().first()
        assertEquals(1, restoredServiceAccounts.size)
        assertEquals(
            "ServiceSpecificPass!2",
            repository.decryptSecret(restoredServiceAccounts.first().serviceAccount.encryptedPassword)
        )
        assertEquals(originalRecoveryCode, keyManager.getDecryptedRecoveryCode())
    }
}
