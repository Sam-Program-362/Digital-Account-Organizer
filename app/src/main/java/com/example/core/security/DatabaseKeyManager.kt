package com.example.core.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LockState {
    data object Uninitialized : LockState
    data class Locked(
        val isBiometricEnabled: Boolean,
        val hasRecoveryEnvelope: Boolean,
        val autoLockTimeout: AutoLockTimeout
    ) : LockState
    data class Unlocked(
        val isAppLockEnabled: Boolean,
        val isBiometricEnabled: Boolean,
        val autoLockTimeout: AutoLockTimeout,
        val isPasswordManagerLockEnabled: Boolean = true,
        val useMasterPasswordForVault: Boolean = false,
        val hasDismissedInitialLockSuggestion: Boolean = false,
        val hasStoredRecoveryCode: Boolean = false,
        val recoveryCodesDownloadedOnce: Boolean = false
    ) : LockState
}

data class EnableAppLockResult(
    val recoveryCode: CharArray
)

class DatabaseKeyManager(
    private val envelopeStore: KeyEnvelopeStore,
    private val keystoreWrapper: KeystoreKeyWrapper,
    private val argon2Deriver: Argon2idKeyDeriver = Argon2idKeyDeriver(),
    private val envelopeCipher: AesGcmEnvelopeCipher = AesGcmEnvelopeCipher(),
    private val recoveryKeyManager: RecoveryKeyManager = RecoveryKeyManager(),
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val _lockState = MutableStateFlow<LockState>(LockState.Uninitialized)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private var activeDek: SensitiveBytes? = null
    private var backgroundEnteredAtMillis: Long? = null
    private var lastVaultVerifiedAtMillis: Long? = null
    private val lockCallbacks = mutableListOf<() -> Unit>()

    @Synchronized
    fun registerOnLockListener(listener: () -> Unit) {
        lockCallbacks.add(listener)
    }

    @Synchronized
    fun initializeOrAutoUnlock() {
        val persisted = envelopeStore.readState()
        if (persisted == null) {
            val freshDekBytes = ByteArray(KeyEnvelope.DEK_BYTES).also { secureRandom.nextBytes(it) }
            val freshDek = SensitiveBytes(freshDekBytes)
            val deviceEnvelope = keystoreWrapper.wrapWithDeviceKey(freshDek, clockMillis())
            keystoreWrapper.unwrapWithDeviceKey(deviceEnvelope).close()
            val initialState = PersistedSecurityState(
                isAppLockEnabled = false,
                autoLockTimeout = AutoLockTimeout.IMMEDIATE,
                envelopes = mapOf(EnvelopeType.DEVICE_KEYSTORE to deviceEnvelope),
                isPasswordManagerLockEnabled = true,
                useMasterPasswordForVault = false,
                hasDismissedInitialLockSuggestion = false
            )
            envelopeStore.writeStateAtomically(initialState)
            setUnlockedDek(
                dek = freshDek,
                persisted = initialState
            )
            return
        }

        if (!persisted.isAppLockEnabled) {
            val deviceEnvelope = persisted.envelopes[EnvelopeType.DEVICE_KEYSTORE]
                ?: throw IllegalStateException("Lock-disabled state missing DEVICE_KEYSTORE envelope.")
            val dek = keystoreWrapper.unwrapWithDeviceKey(deviceEnvelope)
            setUnlockedDek(
                dek = dek,
                persisted = persisted
            )
        } else {
            clearActiveDek()
            lastVaultVerifiedAtMillis = null
            _lockState.value = LockState.Locked(
                isBiometricEnabled = persisted.envelopes.containsKey(EnvelopeType.BIOMETRIC_KEYSTORE),
                hasRecoveryEnvelope = persisted.envelopes.containsKey(EnvelopeType.RECOVERY_KEY),
                autoLockTimeout = persisted.autoLockTimeout
            )
        }
    }

    @Synchronized
    fun requireUnlockedDekCopy(): ByteArray {
        val dek = activeDek
            ?: throw IllegalStateException("Database key is unavailable while organizer is locked.")
        return dek.copyBytes()
    }

    /**
     * Encrypts an individual sensitive field (such as an Account or Service Account password)
     * with AES-256-GCM under the active 256-bit DEK before storing in SQLCipher.
     */
    @Synchronized
    fun encryptSecretField(plaintext: String?): String? {
        val clean = plaintext?.takeIf { it.isNotEmpty() } ?: return null
        if (clean.startsWith(FIELD_CIPHER_PREFIX)) return clean
        val rawDek = requireUnlockedDekCopy()
        try {
            val nonce = ByteArray(KeyEnvelope.GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(rawDek, "AES"),
                GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(FIELD_CIPHER_AAD)
            val ciphertext = cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(nonce.size + ciphertext.size)
            System.arraycopy(nonce, 0, combined, 0, nonce.size)
            System.arraycopy(ciphertext, 0, combined, nonce.size, ciphertext.size)
            return FIELD_CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined)
        } finally {
            rawDek.clearBytes()
        }
    }

    /**
     * Decrypts a field previously encrypted with [encryptSecretField].
     */
    @Synchronized
    fun decryptSecretField(storedValue: String?): String? {
        val raw = storedValue?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith(FIELD_CIPHER_PREFIX)) return raw
        val rawDek = requireUnlockedDekCopy()
        try {
            val decoded = Base64.getDecoder().decode(raw.removePrefix(FIELD_CIPHER_PREFIX))
            require(decoded.size > KeyEnvelope.GCM_NONCE_BYTES) { "Invalid encrypted field payload." }
            val nonce = decoded.copyOfRange(0, KeyEnvelope.GCM_NONCE_BYTES)
            val ciphertext = decoded.copyOfRange(KeyEnvelope.GCM_NONCE_BYTES, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(rawDek, "AES"),
                GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(FIELD_CIPHER_AAD)
            val plainBytes = cipher.doFinal(ciphertext)
            return String(plainBytes, StandardCharsets.UTF_8)
        } finally {
            rawDek.clearBytes()
        }
    }

    // --- Password Manager 5-Minute Session Verification ---

    @Synchronized
    fun isVaultVerificationRequired(): Boolean {
        val persisted = envelopeStore.readState() ?: return false
        if (!persisted.isPasswordManagerLockEnabled && !persisted.isAppLockEnabled) {
            return false
        }
        val verifiedAt = lastVaultVerifiedAtMillis ?: return true
        val elapsed = clockMillis() - verifiedAt
        return elapsed >= VAULT_VERIFICATION_WINDOW_MILLIS
    }

    @Synchronized
    fun recordVaultSessionVerified() {
        lastVaultVerifiedAtMillis = clockMillis()
    }

    @Synchronized
    fun clearVaultSessionVerification() {
        lastVaultVerifiedAtMillis = null
    }

    @Synchronized
    fun verifyVaultWithMasterPassword(password: CharArray): Boolean {
        val persisted = envelopeStore.readState() ?: return false
        val masterEnvelope = persisted.envelopes[EnvelopeType.MASTER_PASSWORD] ?: return false
        val salt = masterEnvelope.salt ?: return false
        val params = masterEnvelope.kdfParameters ?: return false
        return try {
            argon2Deriver.deriveKey(password, salt, params).use { kek ->
                envelopeCipher.unwrapDekWithRawKey(masterEnvelope, kek).close()
            }
            recordVaultSessionVerified()
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun setPasswordManagerLockEnabled(enabled: Boolean) {
        val persisted = envelopeStore.readState() ?: return
        val updated = persisted.copy(
            isPasswordManagerLockEnabled = enabled,
            hasDismissedInitialLockSuggestion = true
        )
        envelopeStore.writeStateAtomically(updated)
        if (!enabled) {
            lastVaultVerifiedAtMillis = null
        }
        refreshUnlockedState(updated)
    }

    @Synchronized
    fun setUseMasterPasswordForVault(useMasterPassword: Boolean) {
        val persisted = envelopeStore.readState() ?: return
        val updated = persisted.copy(useMasterPasswordForVault = useMasterPassword)
        envelopeStore.writeStateAtomically(updated)
        refreshUnlockedState(updated)
    }

    @Synchronized
    fun dismissInitialLockSuggestion(disableAllLocks: Boolean = false) {
        val persisted = envelopeStore.readState() ?: return
        val updated = persisted.copy(
            hasDismissedInitialLockSuggestion = true,
            isPasswordManagerLockEnabled = if (disableAllLocks) false else persisted.isPasswordManagerLockEnabled
        )
        envelopeStore.writeStateAtomically(updated)
        refreshUnlockedState(updated)
    }

    @Synchronized
    fun enableAppLock(
        newMasterPassword: CharArray,
        autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
        kdfParameters: KdfParameters = KdfParameters()
    ): EnableAppLockResult {
        require(newMasterPassword.size >= MIN_MASTER_PASSWORD_CHARS) {
            "Master password must be at least $MIN_MASTER_PASSWORD_CHARS characters."
        }
        val dek = activeDek
            ?: throw IllegalStateException("Must be unlocked before enabling app lock.")
        val previous = envelopeStore.readState()

        val salt = argon2Deriver.generateSalt()
        val masterEnvelope = argon2Deriver.deriveKey(newMasterPassword, salt, kdfParameters).use { kek ->
            val env = envelopeCipher.wrapDekWithRawKey(
                dek = dek,
                kek = kek,
                type = EnvelopeType.MASTER_PASSWORD,
                kdfParameters = kdfParameters,
                salt = salt,
                createdAtEpochMillis = clockMillis()
            )
            envelopeCipher.unwrapDekWithRawKey(env, kek).close()
            env
        }

        val recoveryCode = recoveryKeyManager.generateRecoveryCode()
        val recoveryEnvelope = recoveryKeyManager.deriveKekFromRecoveryCode(recoveryCode).use { recKek ->
            val env = envelopeCipher.wrapDekWithRawKey(
                dek = dek,
                kek = recKek,
                type = EnvelopeType.RECOVERY_KEY,
                createdAtEpochMillis = clockMillis()
            )
            envelopeCipher.unwrapDekWithRawKey(env, recKek).close()
            env
        }

        val (encRecPayload, encRecNonce) = encryptRecoveryCodeWithDek(dek, recoveryCode)

        val envelopesMap = mutableMapOf(
            EnvelopeType.MASTER_PASSWORD to masterEnvelope,
            EnvelopeType.RECOVERY_KEY to recoveryEnvelope
        )
        previous?.envelopes?.get(EnvelopeType.BIOMETRIC_KEYSTORE)?.let { bioEnv ->
            envelopesMap[EnvelopeType.BIOMETRIC_KEYSTORE] = bioEnv
        }

        val newState = PersistedSecurityState(
            isAppLockEnabled = true,
            autoLockTimeout = autoLockTimeout,
            envelopes = envelopesMap,
            isPasswordManagerLockEnabled = previous?.isPasswordManagerLockEnabled ?: true,
            useMasterPasswordForVault = previous?.useMasterPasswordForVault ?: false,
            hasDismissedInitialLockSuggestion = true,
            encryptedRecoveryCodePayload = encRecPayload,
            encryptedRecoveryCodeNonce = encRecNonce,
            recoveryCodesDownloadedOnce = false
        )
        envelopeStore.writeStateAtomically(newState)
        keystoreWrapper.deleteDeviceKey()
        lastVaultVerifiedAtMillis = null
        refreshUnlockedState(newState)
        return EnableAppLockResult(recoveryCode = recoveryCode)
    }

    @Synchronized
    fun getDecryptedRecoveryCode(): String? {
        val dek = activeDek ?: return null
        val persisted = envelopeStore.readState() ?: return null
        val payload = persisted.encryptedRecoveryCodePayload ?: return null
        val nonce = persisted.encryptedRecoveryCodeNonce ?: return null
        return runCatching {
            decryptRecoveryCodeWithDek(dek, payload, nonce)
        }.getOrNull()
    }

    @Synchronized
    fun markRecoveryCodeFileDownloadedOnce() {
        val persisted = envelopeStore.readState() ?: return
        val updated = persisted.copy(recoveryCodesDownloadedOnce = true)
        envelopeStore.writeStateAtomically(updated)
        refreshUnlockedState(updated)
    }

    @Synchronized
    fun exportRecoveryCodeForBackup(): Triple<String, String, Boolean>? {
        val persisted = envelopeStore.readState() ?: return null
        val payload = persisted.encryptedRecoveryCodePayload ?: return null
        val nonce = persisted.encryptedRecoveryCodeNonce ?: return null
        val dek = activeDek ?: return null
        val plainCode = runCatching { decryptRecoveryCodeWithDek(dek, payload, nonce) }.getOrNull() ?: return null
        return Triple(
            plainCode,
            Base64.getEncoder().encodeToString(nonce),
            persisted.recoveryCodesDownloadedOnce
        )
    }

    @Synchronized
    fun restoreRecoveryCodeFromBackup(plainRecoveryCode: String, downloadedOnce: Boolean) {
        val dek = activeDek ?: return
        val persisted = envelopeStore.readState() ?: return
        val chars = plainRecoveryCode.toCharArray()
        try {
            val (encPayload, encNonce) = encryptRecoveryCodeWithDek(dek, chars)
            val updated = persisted.copy(
                encryptedRecoveryCodePayload = encPayload,
                encryptedRecoveryCodeNonce = encNonce,
                recoveryCodesDownloadedOnce = downloadedOnce
            )
            envelopeStore.writeStateAtomically(updated)
            refreshUnlockedState(updated)
        } finally {
            chars.clearChars()
        }
    }

    @Synchronized
    fun updateAutoLockTimeout(timeout: AutoLockTimeout) {
        val persisted = envelopeStore.readState() ?: return
        val updated = persisted.copy(autoLockTimeout = timeout)
        envelopeStore.writeStateAtomically(updated)
        val current = _lockState.value
        if (current is LockState.Unlocked) {
            _lockState.value = current.copy(autoLockTimeout = timeout)
        } else if (current is LockState.Locked) {
            _lockState.value = current.copy(autoLockTimeout = timeout)
        }
    }

    @Synchronized
    fun onAppBackgrounded() {
        val persisted = envelopeStore.readState() ?: return
        if (!persisted.isAppLockEnabled) return
        if (persisted.autoLockTimeout == AutoLockTimeout.IMMEDIATE) {
            lockNow()
        } else {
            backgroundEnteredAtMillis = clockMillis()
        }
    }

    @Synchronized
    fun onAppForegrounded() {
        val persisted = envelopeStore.readState() ?: return
        if (!persisted.isAppLockEnabled) return
        val enteredAt = backgroundEnteredAtMillis ?: return
        backgroundEnteredAtMillis = null
        val elapsed = clockMillis() - enteredAt
        if (elapsed >= persisted.autoLockTimeout.timeoutMillis) {
            lockNow()
        }
    }

    @Synchronized
    fun unlockWithMasterPassword(masterPassword: CharArray) {
        val persisted = requireLockEnabledState()
        val masterEnvelope = persisted.envelopes[EnvelopeType.MASTER_PASSWORD]
            ?: throw IllegalStateException("Missing MASTER_PASSWORD envelope.")
        val salt = masterEnvelope.salt
            ?: throw IllegalStateException("MASTER_PASSWORD envelope missing salt.")
        val params = masterEnvelope.kdfParameters
            ?: throw IllegalStateException("MASTER_PASSWORD envelope missing KDF parameters.")

        val dek = argon2Deriver.deriveKey(masterPassword, salt, params).use { kek ->
            envelopeCipher.unwrapDekWithRawKey(masterEnvelope, kek)
        }
        backgroundEnteredAtMillis = null
        lastVaultVerifiedAtMillis = null
        setUnlockedDek(
            dek = dek,
            persisted = persisted
        )
    }

    @Synchronized
    fun recoverAndResetMasterPassword(
        recoveryCode: CharArray,
        newMasterPassword: CharArray,
        kdfParameters: KdfParameters = KdfParameters()
    ): EnableAppLockResult {
        require(newMasterPassword.size >= MIN_MASTER_PASSWORD_CHARS) {
            "Master password must be at least $MIN_MASTER_PASSWORD_CHARS characters."
        }
        val persisted = requireLockEnabledState()
        val recoveryEnvelope = persisted.envelopes[EnvelopeType.RECOVERY_KEY]
            ?: throw IllegalStateException("Missing RECOVERY_KEY envelope.")

        val unwrappedDek = recoveryKeyManager.deriveKekFromRecoveryCode(recoveryCode).use { recKek ->
            envelopeCipher.unwrapDekWithRawKey(recoveryEnvelope, recKek)
        }
        setUnlockedDek(
            dek = unwrappedDek,
            persisted = persisted
        )
        keystoreWrapper.deleteBiometricKey()
        return enableAppLock(newMasterPassword, persisted.autoLockTimeout, kdfParameters)
    }

    /**
     * Allows resetting a forgotten Master Password using Fingerprint authentication
     * when Fingerprint was previously enabled in Settings.
     */
    @Synchronized
    fun recoverAndResetMasterPasswordWithBiometric(
        authenticatedDecryptCipher: Cipher,
        newMasterPassword: CharArray,
        kdfParameters: KdfParameters = KdfParameters()
    ): EnableAppLockResult {
        require(newMasterPassword.size >= MIN_MASTER_PASSWORD_CHARS) {
            "Master password must be at least $MIN_MASTER_PASSWORD_CHARS characters."
        }
        val persisted = requireLockEnabledState()
        val bioEnvelope = persisted.envelopes[EnvelopeType.BIOMETRIC_KEYSTORE]
            ?: throw IllegalStateException("Fingerprint recovery is not enabled.")
        val unwrappedDek = keystoreWrapper.unwrapWithBiometricCipher(authenticatedDecryptCipher, bioEnvelope)
        setUnlockedDek(
            dek = unwrappedDek,
            persisted = persisted
        )
        return enableAppLock(newMasterPassword, persisted.autoLockTimeout, kdfParameters)
    }

    @Synchronized
    fun changeMasterPassword(
        currentMasterPassword: CharArray,
        newMasterPassword: CharArray,
        kdfParameters: KdfParameters = KdfParameters()
    ) {
        require(newMasterPassword.size >= MIN_MASTER_PASSWORD_CHARS) {
            "Master password must be at least $MIN_MASTER_PASSWORD_CHARS characters."
        }
        val persisted = requireLockEnabledState()
        val currentMasterEnvelope = persisted.envelopes[EnvelopeType.MASTER_PASSWORD]
            ?: throw IllegalStateException("Missing MASTER_PASSWORD envelope.")
        val currentSalt = currentMasterEnvelope.salt
            ?: throw IllegalStateException("Missing salt.")
        val currentParams = currentMasterEnvelope.kdfParameters
            ?: throw IllegalStateException("Missing KDF parameters.")

        val verifiedDek = argon2Deriver.deriveKey(currentMasterPassword, currentSalt, currentParams).use { oldKek ->
            envelopeCipher.unwrapDekWithRawKey(currentMasterEnvelope, oldKek)
        }

        verifiedDek.use { dek ->
            val newSalt = argon2Deriver.generateSalt()
            val newMasterEnvelope = argon2Deriver.deriveKey(newMasterPassword, newSalt, kdfParameters).use { newKek ->
                val env = envelopeCipher.wrapDekWithRawKey(
                    dek = dek,
                    kek = newKek,
                    type = EnvelopeType.MASTER_PASSWORD,
                    kdfParameters = kdfParameters,
                    salt = newSalt,
                    createdAtEpochMillis = clockMillis()
                )
                envelopeCipher.unwrapDekWithRawKey(env, newKek).close()
                env
            }
            val updatedEnvelopes = persisted.envelopes.toMutableMap().apply {
                put(EnvelopeType.MASTER_PASSWORD, newMasterEnvelope)
            }
            val updatedState = persisted.copy(envelopes = updatedEnvelopes)
            envelopeStore.writeStateAtomically(updatedState)
            refreshUnlockedState(updatedState)
        }
    }

    @Synchronized
    fun disableAppLock(currentMasterPassword: CharArray) {
        val persisted = requireLockEnabledState()
        val masterEnvelope = persisted.envelopes[EnvelopeType.MASTER_PASSWORD]
            ?: throw IllegalStateException("Missing MASTER_PASSWORD envelope.")
        val salt = masterEnvelope.salt ?: throw IllegalStateException("Missing salt.")
        val params = masterEnvelope.kdfParameters ?: throw IllegalStateException("Missing KDF parameters.")

        val verifiedDek = argon2Deriver.deriveKey(currentMasterPassword, salt, params).use { kek ->
            envelopeCipher.unwrapDekWithRawKey(masterEnvelope, kek)
        }
        val deviceEnvelope = keystoreWrapper.wrapWithDeviceKey(verifiedDek, clockMillis())
        keystoreWrapper.unwrapWithDeviceKey(deviceEnvelope).close()

        val updatedState = PersistedSecurityState(
            isAppLockEnabled = false,
            autoLockTimeout = persisted.autoLockTimeout,
            envelopes = mapOf(EnvelopeType.DEVICE_KEYSTORE to deviceEnvelope),
            isPasswordManagerLockEnabled = persisted.isPasswordManagerLockEnabled,
            useMasterPasswordForVault = false,
            hasDismissedInitialLockSuggestion = true,
            encryptedRecoveryCodePayload = null,
            encryptedRecoveryCodeNonce = null,
            recoveryCodesDownloadedOnce = false
        )
        envelopeStore.writeStateAtomically(updatedState)
        keystoreWrapper.deleteBiometricKey()
        setUnlockedDek(
            dek = verifiedDek,
            persisted = updatedState
        )
    }

    @Synchronized
    fun prepareBiometricEnrollmentCipher(): Cipher {
        require(activeDek != null) { "Workspace must be unlocked to enroll biometrics." }
        return keystoreWrapper.createBiometricEncryptCipher()
    }

    @Synchronized
    fun completeBiometricEnrollment(authenticatedEncryptCipher: Cipher) {
        val dek = activeDek ?: throw IllegalStateException("Workspace must be unlocked.")
        val persisted = requireLockEnabledState()
        val bioEnvelope = keystoreWrapper.wrapWithBiometricCipher(
            authenticatedCipher = authenticatedEncryptCipher,
            dek = dek,
            createdAtEpochMillis = clockMillis()
        )
        val updatedEnvelopes = persisted.envelopes.toMutableMap().apply {
            put(EnvelopeType.BIOMETRIC_KEYSTORE, bioEnvelope)
        }
        val updatedState = persisted.copy(envelopes = updatedEnvelopes)
        envelopeStore.writeStateAtomically(updatedState)
        refreshUnlockedState(updatedState)
    }

    @Synchronized
    fun prepareBiometricUnlockCipher(): Cipher {
        val persisted = requireLockEnabledState()
        val bioEnvelope = persisted.envelopes[EnvelopeType.BIOMETRIC_KEYSTORE]
            ?: throw IllegalStateException("Biometric unlock is not enabled.")
        return try {
            keystoreWrapper.createBiometricDecryptCipher(bioEnvelope.nonce)
        } catch (e: KeyPermanentlyInvalidatedException) {
            handleInvalidatedBiometricKey(persisted)
            throw e
        }
    }

    @Synchronized
    fun unlockWithBiometricCipher(authenticatedDecryptCipher: Cipher) {
        val persisted = requireLockEnabledState()
        val bioEnvelope = persisted.envelopes[EnvelopeType.BIOMETRIC_KEYSTORE]
            ?: throw IllegalStateException("Biometric unlock is not enabled.")
        val dek = keystoreWrapper.unwrapWithBiometricCipher(authenticatedDecryptCipher, bioEnvelope)
        backgroundEnteredAtMillis = null
        lastVaultVerifiedAtMillis = null
        setUnlockedDek(
            dek = dek,
            persisted = persisted
        )
    }

    @Synchronized
    fun disableBiometricUnlock() {
        val persisted = requireLockEnabledState()
        keystoreWrapper.deleteBiometricKey()
        val updatedEnvelopes = persisted.envelopes.toMutableMap().apply {
            remove(EnvelopeType.BIOMETRIC_KEYSTORE)
        }
        val updatedState = persisted.copy(envelopes = updatedEnvelopes)
        envelopeStore.writeStateAtomically(updatedState)
        if (activeDek != null) {
            refreshUnlockedState(updatedState)
        } else {
            _lockState.value = LockState.Locked(
                isBiometricEnabled = false,
                hasRecoveryEnvelope = updatedEnvelopes.containsKey(EnvelopeType.RECOVERY_KEY),
                autoLockTimeout = persisted.autoLockTimeout
            )
        }
    }

    @Synchronized
    fun lockNow() {
        val persisted = envelopeStore.readState() ?: return
        if (!persisted.isAppLockEnabled) return
        backgroundEnteredAtMillis = null
        lastVaultVerifiedAtMillis = null
        lockCallbacks.forEach { it.invoke() }
        clearActiveDek()
        _lockState.value = LockState.Locked(
            isBiometricEnabled = persisted.envelopes.containsKey(EnvelopeType.BIOMETRIC_KEYSTORE),
            hasRecoveryEnvelope = persisted.envelopes.containsKey(EnvelopeType.RECOVERY_KEY),
            autoLockTimeout = persisted.autoLockTimeout
        )
    }

    /**
     * Destructive reset when all unlock credentials (master password and recovery key) are lost.
     */
    @Synchronized
    fun performDestructiveReset(onBeforeReinit: () -> Unit) {
        lockCallbacks.forEach { it.invoke() }
        clearActiveDek()
        lastVaultVerifiedAtMillis = null
        keystoreWrapper.deleteDeviceKey()
        keystoreWrapper.deleteBiometricKey()
        envelopeStore.clearAll()
        onBeforeReinit()
        initializeOrAutoUnlock()
    }

    private fun encryptRecoveryCodeWithDek(
        dek: SensitiveBytes,
        recoveryCode: CharArray
    ): Pair<ByteArray, ByteArray> {
        val rawDek = dek.copyBytes()
        val codeBytes = String(recoveryCode).toByteArray(StandardCharsets.UTF_8)
        try {
            val nonce = ByteArray(KeyEnvelope.GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(rawDek, "AES"),
                GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(RECOVERY_CODE_STORAGE_AAD)
            val ciphertext = cipher.doFinal(codeBytes)
            return Pair(ciphertext, nonce)
        } finally {
            rawDek.clearBytes()
            codeBytes.clearBytes()
        }
    }

    private fun decryptRecoveryCodeWithDek(
        dek: SensitiveBytes,
        ciphertext: ByteArray,
        nonce: ByteArray
    ): String {
        val rawDek = dek.copyBytes()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(rawDek, "AES"),
                GCMParameterSpec(KeyEnvelope.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(RECOVERY_CODE_STORAGE_AAD)
            val plain = cipher.doFinal(ciphertext)
            return String(plain, StandardCharsets.UTF_8)
        } finally {
            rawDek.clearBytes()
        }
    }

    private fun handleInvalidatedBiometricKey(persisted: PersistedSecurityState) {
        keystoreWrapper.deleteBiometricKey()
        val updated = persisted.envelopes.toMutableMap().apply {
            remove(EnvelopeType.BIOMETRIC_KEYSTORE)
        }
        val updatedState = persisted.copy(envelopes = updated)
        envelopeStore.writeStateAtomically(updatedState)
        if (activeDek == null) {
            _lockState.value = LockState.Locked(
                isBiometricEnabled = false,
                hasRecoveryEnvelope = updated.containsKey(EnvelopeType.RECOVERY_KEY),
                autoLockTimeout = persisted.autoLockTimeout
            )
        } else {
            refreshUnlockedState(updatedState)
        }
    }

    private fun requireLockEnabledState(): PersistedSecurityState {
        val persisted = envelopeStore.readState()
            ?: throw IllegalStateException("Security state is not initialized.")
        require(persisted.isAppLockEnabled) { "App lock is not enabled." }
        return persisted
    }

    private fun setUnlockedDek(
        dek: SensitiveBytes,
        persisted: PersistedSecurityState
    ) {
        clearActiveDek()
        activeDek = dek
        refreshUnlockedState(persisted)
    }

    private fun refreshUnlockedState(persisted: PersistedSecurityState) {
        _lockState.value = LockState.Unlocked(
            isAppLockEnabled = persisted.isAppLockEnabled,
            isBiometricEnabled = persisted.envelopes.containsKey(EnvelopeType.BIOMETRIC_KEYSTORE),
            autoLockTimeout = persisted.autoLockTimeout,
            isPasswordManagerLockEnabled = persisted.isPasswordManagerLockEnabled,
            useMasterPasswordForVault = persisted.useMasterPasswordForVault,
            hasDismissedInitialLockSuggestion = persisted.hasDismissedInitialLockSuggestion,
            hasStoredRecoveryCode = persisted.encryptedRecoveryCodePayload != null &&
                persisted.encryptedRecoveryCodeNonce != null,
            recoveryCodesDownloadedOnce = persisted.recoveryCodesDownloadedOnce
        )
    }

    private fun clearActiveDek() {
        activeDek?.clear()
        activeDek = null
    }

    companion object {
        const val MIN_MASTER_PASSWORD_CHARS = 8
        const val VAULT_VERIFICATION_WINDOW_MILLIS = 5 * 60 * 1000L // 5 minutes
        private const val FIELD_CIPHER_PREFIX = "ENC1:"
        private val FIELD_CIPHER_AAD = "dao-secret-field-v1".toByteArray(StandardCharsets.UTF_8)
        private val RECOVERY_CODE_STORAGE_AAD = "dao-recovery-code-store-v1".toByteArray(StandardCharsets.UTF_8)
    }
}
