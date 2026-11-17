package com.example.core.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.SecureRandom
import javax.crypto.Cipher
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
        val autoLockTimeout: AutoLockTimeout
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
            envelopeStore.writeStateAtomically(
                PersistedSecurityState(
                    isAppLockEnabled = false,
                    autoLockTimeout = AutoLockTimeout.IMMEDIATE,
                    envelopes = mapOf(EnvelopeType.DEVICE_KEYSTORE to deviceEnvelope)
                )
            )
            setUnlockedDek(
                dek = freshDek,
                isAppLockEnabled = false,
                isBiometricEnabled = false,
                autoLockTimeout = AutoLockTimeout.IMMEDIATE
            )
            return
        }

        if (!persisted.isAppLockEnabled) {
            val deviceEnvelope = persisted.envelopes[EnvelopeType.DEVICE_KEYSTORE]
                ?: throw IllegalStateException("Lock-disabled state missing DEVICE_KEYSTORE envelope.")
            val dek = keystoreWrapper.unwrapWithDeviceKey(deviceEnvelope)
            setUnlockedDek(
                dek = dek,
                isAppLockEnabled = false,
                isBiometricEnabled = false,
                autoLockTimeout = persisted.autoLockTimeout
            )
        } else {
            clearActiveDek()
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

        envelopeStore.writeStateAtomically(
            PersistedSecurityState(
                isAppLockEnabled = true,
                autoLockTimeout = autoLockTimeout,
                envelopes = mapOf(
                    EnvelopeType.MASTER_PASSWORD to masterEnvelope,
                    EnvelopeType.RECOVERY_KEY to recoveryEnvelope
                )
            )
        )
        keystoreWrapper.deleteDeviceKey()
        _lockState.value = LockState.Unlocked(
            isAppLockEnabled = true,
            isBiometricEnabled = false,
            autoLockTimeout = autoLockTimeout
        )
        return EnableAppLockResult(recoveryCode = recoveryCode)
    }

    @Synchronized
    fun updateAutoLockTimeout(timeout: AutoLockTimeout) {
        val persisted = envelopeStore.readState() ?: return
        envelopeStore.writeStateAtomically(persisted.copy(autoLockTimeout = timeout))
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
        setUnlockedDek(
            dek = dek,
            isAppLockEnabled = true,
            isBiometricEnabled = persisted.envelopes.containsKey(EnvelopeType.BIOMETRIC_KEYSTORE),
            autoLockTimeout = persisted.autoLockTimeout
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
            isAppLockEnabled = true,
            isBiometricEnabled = false,
            autoLockTimeout = persisted.autoLockTimeout
        )
        keystoreWrapper.deleteBiometricKey()
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
            envelopeStore.writeStateAtomically(persisted.copy(envelopes = updatedEnvelopes))
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

        envelopeStore.writeStateAtomically(
            PersistedSecurityState(
                isAppLockEnabled = false,
                autoLockTimeout = persisted.autoLockTimeout,
                envelopes = mapOf(EnvelopeType.DEVICE_KEYSTORE to deviceEnvelope)
            )
        )
        keystoreWrapper.deleteBiometricKey()
        setUnlockedDek(
            dek = verifiedDek,
            isAppLockEnabled = false,
            isBiometricEnabled = false,
            autoLockTimeout = persisted.autoLockTimeout
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
        val updated = persisted.envelopes.toMutableMap().apply {
            put(EnvelopeType.BIOMETRIC_KEYSTORE, bioEnvelope)
        }
        envelopeStore.writeStateAtomically(persisted.copy(envelopes = updated))
        _lockState.value = LockState.Unlocked(
            isAppLockEnabled = true,
            isBiometricEnabled = true,
            autoLockTimeout = persisted.autoLockTimeout
        )
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
        setUnlockedDek(
            dek = dek,
            isAppLockEnabled = true,
            isBiometricEnabled = true,
            autoLockTimeout = persisted.autoLockTimeout
        )
    }

    @Synchronized
    fun disableBiometricUnlock() {
        val persisted = requireLockEnabledState()
        keystoreWrapper.deleteBiometricKey()
        val updated = persisted.envelopes.toMutableMap().apply {
            remove(EnvelopeType.BIOMETRIC_KEYSTORE)
        }
        envelopeStore.writeStateAtomically(persisted.copy(envelopes = updated))
        if (activeDek != null) {
            _lockState.value = LockState.Unlocked(
                isAppLockEnabled = true,
                isBiometricEnabled = false,
                autoLockTimeout = persisted.autoLockTimeout
            )
        } else {
            _lockState.value = LockState.Locked(
                isBiometricEnabled = false,
                hasRecoveryEnvelope = updated.containsKey(EnvelopeType.RECOVERY_KEY),
                autoLockTimeout = persisted.autoLockTimeout
            )
        }
    }

    @Synchronized
    fun lockNow() {
        val persisted = envelopeStore.readState() ?: return
        if (!persisted.isAppLockEnabled) return
        backgroundEnteredAtMillis = null
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
        keystoreWrapper.deleteDeviceKey()
        keystoreWrapper.deleteBiometricKey()
        envelopeStore.clearAll()
        onBeforeReinit()
        initializeOrAutoUnlock()
    }

    private fun handleInvalidatedBiometricKey(persisted: PersistedSecurityState) {
        keystoreWrapper.deleteBiometricKey()
        val updated = persisted.envelopes.toMutableMap().apply {
            remove(EnvelopeType.BIOMETRIC_KEYSTORE)
        }
        envelopeStore.writeStateAtomically(persisted.copy(envelopes = updated))
        if (activeDek == null) {
            _lockState.value = LockState.Locked(
                isBiometricEnabled = false,
                hasRecoveryEnvelope = updated.containsKey(EnvelopeType.RECOVERY_KEY),
                autoLockTimeout = persisted.autoLockTimeout
            )
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
        isAppLockEnabled: Boolean,
        isBiometricEnabled: Boolean,
        autoLockTimeout: AutoLockTimeout
    ) {
        clearActiveDek()
        activeDek = dek
        _lockState.value = LockState.Unlocked(
            isAppLockEnabled = isAppLockEnabled,
            isBiometricEnabled = isBiometricEnabled,
            autoLockTimeout = autoLockTimeout
        )
    }

    private fun clearActiveDek() {
        activeDek?.clear()
        activeDek = null
    }

    companion object {
        const val MIN_MASTER_PASSWORD_CHARS = 12
    }
}
