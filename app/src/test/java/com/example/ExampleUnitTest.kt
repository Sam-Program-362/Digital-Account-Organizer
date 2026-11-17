package com.example

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.example.core.security.AesGcmEnvelopeCipher
import com.example.core.security.AutoLockTimeout
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.EnvelopeType
import com.example.core.security.FileKeyEnvelopeStore
import com.example.core.security.KdfParameters
import com.example.core.security.KeyEnvelope
import com.example.core.security.KeystoreKeyWrapper
import com.example.core.security.LockState
import com.example.core.security.SensitiveBytes
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var tempDir: File
    private lateinit var store: FileKeyEnvelopeStore
    private lateinit var fakeKeystore: InMemoryKeystoreWrapper
    private var currentClockMillis: Long = 1_700_000_000_000L
    private lateinit var keyManager: DatabaseKeyManager

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dao_sec_test").toFile()
        store = FileKeyEnvelopeStore(tempDir)
        fakeKeystore = InMemoryKeystoreWrapper()
        keyManager = DatabaseKeyManager(
            envelopeStore = store,
            keystoreWrapper = fakeKeystore,
            clockMillis = { currentClockMillis }
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun backupRulesAndManifest_enforceZeroPermissionsAndExcludeAllPrivateDomains() {
        val backupRulesFile = sequenceOf(
            File("src/main/res/xml/backup_rules.xml"),
            File("app/src/main/res/xml/backup_rules.xml")
        ).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(backupRulesFile)
        val excludes = doc.getElementsByTagName("exclude")
        val domains = (0 until excludes.length)
            .map { excludes.item(it).attributes.getNamedItem("domain").nodeValue }
            .toSet()

        assertEquals(
            setOf("database", "file", "sharedpref", "external", "root"),
            domains
        )

        val extractionFile = sequenceOf(
            File("src/main/res/xml/data_extraction_rules.xml"),
            File("app/src/main/res/xml/data_extraction_rules.xml")
        ).first { it.exists() }
        val extractionContent = extractionFile.readText()
        assertTrue(extractionContent.contains("<cloud-backup>"))
        assertTrue(extractionContent.contains("<device-transfer>"))

        val manifestFile = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }
        val manifestContent = manifestFile.readText()
        assertTrue(manifestContent.contains("android.permission.INTERNET"))
        assertTrue(manifestContent.contains("tools:node=\"remove\""))
        assertTrue(manifestContent.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun firstLaunch_createsDeviceEnvelopeAndUnlocks() {
        keyManager.initializeOrAutoUnlock()
        val state = keyManager.lockState.value
        assertTrue(state is LockState.Unlocked)
        assertFalse((state as LockState.Unlocked).isAppLockEnabled)

        val dek = keyManager.requireUnlockedDekCopy()
        assertEquals(32, dek.size)
    }

    @Test
    fun enableAppLock_removesDeviceEnvelopeAndEnforcesTimeoutPolicy() {
        keyManager.initializeOrAutoUnlock()
        val originalDek = keyManager.requireUnlockedDekCopy()

        val password = "correct-horse-battery-staple".toCharArray()
        val enableResult = keyManager.enableAppLock(
            newMasterPassword = password,
            autoLockTimeout = AutoLockTimeout.ONE_MINUTE
        )
        assertTrue(enableResult.recoveryCode.isNotEmpty())
        assertFalse(fakeKeystore.hasDeviceKey)

        // Backgrounding for 30s (less than 1m timeout) keeps workspace unlocked
        keyManager.onAppBackgrounded()
        currentClockMillis += 30_000L
        keyManager.onAppForegrounded()
        assertTrue(keyManager.lockState.value is LockState.Unlocked)

        // Backgrounding for 65s (>= 1m timeout) locks the workspace and clears DEK
        keyManager.onAppBackgrounded()
        currentClockMillis += 65_000L
        keyManager.onAppForegrounded()
        assertTrue(keyManager.lockState.value is LockState.Locked)
        try {
            keyManager.requireUnlockedDekCopy()
            fail("Expected IllegalStateException when requesting DEK while locked")
        } catch (_: IllegalStateException) {
            // Expected
        }

        // Wrong password must fail closed
        try {
            keyManager.unlockWithMasterPassword("wrong-password-12345".toCharArray())
            fail("Expected GeneralSecurityException on wrong master password")
        } catch (_: GeneralSecurityException) {
            // Expected
        }
        assertTrue(keyManager.lockState.value is LockState.Locked)

        // Correct password unlocks the exact same 256-bit DEK
        keyManager.unlockWithMasterPassword(password)
        assertTrue(keyManager.lockState.value is LockState.Unlocked)
        assertArrayEquals(originalDek, keyManager.requireUnlockedDekCopy())
    }

    @Test
    fun changeMasterPassword_rewrapsSameDekWithoutChangingIt() {
        keyManager.initializeOrAutoUnlock()
        val originalDek = keyManager.requireUnlockedDekCopy()

        val oldPassword = "initial-strong-passphrase".toCharArray()
        val newPassword = "updated-strong-passphrase".toCharArray()
        keyManager.enableAppLock(oldPassword)

        keyManager.changeMasterPassword(oldPassword, newPassword)
        keyManager.lockNow()

        try {
            keyManager.unlockWithMasterPassword(oldPassword)
            fail("Old password must not unlock after password change")
        } catch (_: GeneralSecurityException) {
            // Expected
        }

        keyManager.unlockWithMasterPassword(newPassword)
        assertArrayEquals(originalDek, keyManager.requireUnlockedDekCopy())
    }

    @Test
    fun recoveryKey_unlocksAndRotatesCredentialsWhilePreservingDek() {
        keyManager.initializeOrAutoUnlock()
        val originalDek = keyManager.requireUnlockedDekCopy()

        val enableResult = keyManager.enableAppLock("initial-master-password".toCharArray())
        keyManager.lockNow()

        val tamperedCode = enableResult.recoveryCode.copyOf()
        tamperedCode[0] = if (tamperedCode[0] == 'A') 'B' else 'A'
        try {
            keyManager.recoverAndResetMasterPassword(
                tamperedCode,
                "new-recovered-password".toCharArray()
            )
            fail("Expected checksum failure on tampered recovery key")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        val newPassword = "new-recovered-password".toCharArray()
        val newRecovery = keyManager.recoverAndResetMasterPassword(
            enableResult.recoveryCode,
            newPassword
        )
        assertNotEquals(
            String(enableResult.recoveryCode),
            String(newRecovery.recoveryCode)
        )
        assertArrayEquals(originalDek, keyManager.requireUnlockedDekCopy())
    }

    @Test
    fun tamperedEnvelopeAad_failsAuthentication() {
        val cipher = AesGcmEnvelopeCipher()
        val dek = SensitiveBytes(ByteArray(32) { it.toByte() })
        val kek = SensitiveBytes(ByteArray(32) { (it + 1).toByte() })
        val envelope = cipher.wrapDekWithRawKey(
            dek = dek,
            kek = kek,
            type = EnvelopeType.MASTER_PASSWORD,
            kdfParameters = KdfParameters()
        )

        val tampered = envelope.copy(
            kdfParameters = KdfParameters(iterations = 3)
        )
        try {
            cipher.unwrapDekWithRawKey(tampered, kek)
            fail("Expected AEADBadTagException when envelope header metadata is tampered")
        } catch (_: GeneralSecurityException) {
            // Expected
        }
    }

    @Test
    fun biometricInvalidation_disablesBiometricEnvelopeAndFallsBackToPassword() {
        keyManager.initializeOrAutoUnlock()
        val password = "biometric-fallback-password".toCharArray()
        keyManager.enableAppLock(password)

        val encryptCipher = keyManager.prepareBiometricEnrollmentCipher()
        keyManager.completeBiometricEnrollment(encryptCipher)
        keyManager.lockNow()

        assertTrue((keyManager.lockState.value as LockState.Locked).isBiometricEnabled)

        fakeKeystore.invalidateBiometricKey = true
        try {
            keyManager.prepareBiometricUnlockCipher()
            fail("Expected KeyPermanentlyInvalidatedException")
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Expected
        }

        val lockedState = keyManager.lockState.value as LockState.Locked
        assertFalse(lockedState.isBiometricEnabled)

        keyManager.unlockWithMasterPassword(password)
        assertTrue(keyManager.lockState.value is LockState.Unlocked)
    }

    private class InMemoryKeystoreWrapper : KeystoreKeyWrapper {
        private val secureRandom = SecureRandom()
        private var deviceKey: SecretKey? = null
        private var biometricKey: SecretKey? = null
        var invalidateBiometricKey: Boolean = false

        val hasDeviceKey: Boolean
            get() = deviceKey != null

        override fun wrapWithDeviceKey(dek: SensitiveBytes, createdAtEpochMillis: Long): KeyEnvelope {
            val key = deviceKey ?: generateAesKey().also { deviceKey = it }
            val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            val prototype = KeyEnvelope(
                type = EnvelopeType.DEVICE_KEYSTORE,
                nonce = nonce,
                ciphertextWithTag = ByteArray(0),
                keystoreAlias = "test_device_alias",
                createdAtEpochMillis = createdAtEpochMillis
            )
            cipher.updateAAD(prototype.canonicalAad())
            return prototype.copy(ciphertextWithTag = cipher.doFinal(dek.copyBytes()))
        }

        override fun unwrapWithDeviceKey(envelope: KeyEnvelope): SensitiveBytes {
            val key = deviceKey ?: throw IllegalStateException("No device key")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.nonce))
            cipher.updateAAD(envelope.canonicalAad())
            return SensitiveBytes(cipher.doFinal(envelope.ciphertextWithTag))
        }

        override fun deleteDeviceKey() {
            deviceKey = null
        }

        override fun createBiometricEncryptCipher(): Cipher {
            val key = generateAesKey().also { biometricKey = it }
            val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            }
        }

        override fun createBiometricDecryptCipher(nonce: ByteArray): Cipher {
            if (invalidateBiometricKey) {
                throw KeyPermanentlyInvalidatedException("Biometric enrollment changed")
            }
            val key = biometricKey ?: throw IllegalStateException("No biometric key")
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            }
        }

        override fun wrapWithBiometricCipher(
            authenticatedCipher: Cipher,
            dek: SensitiveBytes,
            createdAtEpochMillis: Long
        ): KeyEnvelope {
            val prototype = KeyEnvelope(
                type = EnvelopeType.BIOMETRIC_KEYSTORE,
                nonce = authenticatedCipher.iv,
                ciphertextWithTag = ByteArray(0),
                keystoreAlias = "test_bio_alias",
                createdAtEpochMillis = createdAtEpochMillis
            )
            authenticatedCipher.updateAAD(prototype.canonicalAad())
            return prototype.copy(ciphertextWithTag = authenticatedCipher.doFinal(dek.copyBytes()))
        }

        override fun unwrapWithBiometricCipher(
            authenticatedCipher: Cipher,
            envelope: KeyEnvelope
        ): SensitiveBytes {
            authenticatedCipher.updateAAD(envelope.canonicalAad())
            return SensitiveBytes(authenticatedCipher.doFinal(envelope.ciphertextWithTag))
        }

        override fun deleteBiometricKey() {
            biometricKey = null
        }

        private fun generateAesKey(): SecretKey {
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
    }
}
