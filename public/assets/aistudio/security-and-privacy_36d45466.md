# Security and Privacy Context

## 1. Security Objectives

The organizer stores personal information about digital accounts. It must not store passwords for those external accounts, but email addresses, usernames, recovery contact references, service names, and private notes may still be sensitive. Protect confidentiality and integrity of local records, custom service assets, app-lock configuration, and portable backups.

Primary objectives:

- Keep data local and prevent automatic cloud transmission.
- Encrypt structured local data at rest.
- Offer optional app-specific access protection.
- Avoid retaining plaintext passwords or encryption keys.
- Provide a recovery path that does not depend on a server.
- Make user-initiated backups portable across devices without exporting device-bound Keystore keys.
- Ensure failures do not silently fall back to plaintext storage.

## 2. Threat Model and Limits

### In-scope risks

- Another person casually opening the app on the same unlocked device.
- Someone obtaining a copy of the app's private files or database from a backup, debug environment, rooted device, or forensic extraction.
- Accidental inclusion of app data in Android Auto Backup or device-to-device transfer.
- A malformed or malicious backup/image file supplied during import.
- Accidental overwrite, duplicate import, partial restore, or database corruption.
- Sensitive data exposed through logs, screenshots, app-switcher previews, clipboard, or crash data.
- Loss of access to the app after password or biometric changes.

### Out-of-scope / not guaranteed

The app must not claim to protect against a fully compromised/rooted Android OS, a hostile person controlling the device while the app is unlocked, kernel-level malware, malicious accessibility services with broad device privileges, hardware attacks, or a determined attacker with control over the running process. Keystore can reduce key extraction risk; it does not make a compromised device trustworthy.

## 3. Recommended App-Lock Architecture

### User-facing behavior

- App lock is optional.
- When disabled, the app opens without asking for a separate organizer password, while the database remains encrypted at rest using a device-bound Keystore key.
- When enabled, the user creates an app-specific master password that is unrelated to Google or any external account.
- The master password is the primary unlock route.
- Strong biometric unlock may be enabled as an optional convenience where supported. It must not be required for the app to function.
- A separate high-entropy recovery key is recommended and should be offered during lock setup.
- The app automatically locks based on a documented inactivity/background policy. The recommended default is to lock when the app leaves the foreground; a configurable grace period may be offered after the MVP.

### Key hierarchy

1. Generate a random 256-bit database Data Encryption Key (DEK) using a cryptographically secure random generator.
2. Use the DEK as the SQLCipher database key. Do not use the master password directly as the database key.
3. Protect the DEK with one or more authenticated key envelopes:
   - **App lock disabled:** a device-bound Android Keystore AES key wraps the DEK, allowing local auto-unlock. This key is non-exportable and should be hardware-backed when available.
   - **App lock enabled:** derive a 256-bit Key Encryption Key (KEK) from the master password with Argon2id and a unique random salt; use AES-256-GCM to wrap the DEK. Remove the unrestricted auto-unlock envelope after the master-password envelope is created and successfully verified.
   - **Biometric unlock enabled:** create a Keystore key configured for strong biometric authentication per cryptographic operation; use it to wrap the DEK. Use `BiometricPrompt` and `CryptoObject`. Master password remains fallback.
   - **Recovery:** create a high-entropy random recovery key, use it to wrap the DEK with authenticated encryption, show it once, and require the user to confirm they retained it. Do not persist the recovery key in plaintext.
4. Store only ciphertext key envelopes, salts, nonces, algorithm identifiers, and versioned KDF parameters. Never store the raw DEK, master password, KEK, or recovery key.
5. Keep the database inaccessible until one valid envelope has been authenticated and the DEK supplied to SQLCipher.

### Master-password guidance

- Accept long passphrases and Unicode characters. Do not silently trim, truncate, or change the user's password before deriving the key.
- Recommend a strong, unique passphrase. A 12-character minimum is a proposed baseline; support longer inputs and do not impose a short maximum.
- Use Argon2id with a unique random salt. OWASP's current password-storage guidance specifies at least 19 MiB memory, two iterations, and parallelism one. Use a parameter set at least meeting that baseline, then benchmark on lower-supported devices and document the chosen parameters.
- Store KDF algorithm, version, salt, memory, iteration, and parallelism values with the envelope so parameters can be upgraded on successful unlock.
- Do not substitute fast SHA-256 hashing or reversible password storage.
- Keep attempt delays/rate limiting local and non-destructive. Never erase user data automatically after a fixed number of wrong attempts.

### AES-GCM rules

- Use AES-256-GCM with a fresh 96-bit nonce for each encryption operation under a given key.
- Never reuse a nonce with the same key.
- Authenticate envelope metadata and format/version identifiers as associated data where appropriate.
- Verify authentication before using decrypted bytes.
- On tag failure or wrong password, fail closed and present a generic, non-revealing error.

## 4. Biometric Unlock

- Biometric unlock is optional and depends on device capability.
- Require `BIOMETRIC_STRONG` for the cryptographic unlock path; do not silently downgrade to weak face recognition or device credentials if the spec requires a crypto object.
- Generate a Keystore-backed AES key requiring user authentication per operation and use `BiometricPrompt` with a `CryptoObject` to unwrap the DEK.
- Keep master password as fallback.
- Handle `KeyPermanentlyInvalidatedException` and biometric enrollment changes by disabling that biometric envelope and requesting master-password unlock; allow re-enrollment after a successful password unlock.
- Never store biometric templates. Android/system biometric APIs handle biometric enrollment and matching.
- Do not make biometric unlock mandatory or describe it as equivalent to a remote login.

## 5. Recovery and Credential Changes

### Recovery key

- Generate an independent random recovery secret with sufficient entropy (recommended 256 bits).
- Encode it in a user-enterable or file-saveable format with a typo-detection checksum.
- Display it only during setup/regeneration and instruct the user to keep it separate from the device.
- Require an explicit confirmation step before marking recovery setup complete.
- Do not store the raw recovery key, sync it, email it, or add it to ordinary organizer exports.
- If the recovery key is lost along with the master password and no valid biometric envelope remains, the encrypted database cannot be recovered.

### Change or disable app lock

- Require successful authentication using the current master password, valid biometric envelope, or recovery key before changing lock configuration.
- To change the master password, unwrap the DEK using the old credential, derive a new KEK with a new salt, write and verify the new envelope, and only then retire the old envelope.
- Do not re-encrypt all database rows merely to change the master password; rewrap the DEK.
- To disable the lock, require successful unlock, create a device-bound Keystore envelope, verify it, then remove master-password/biometric/recovery envelopes only according to a clearly explained user confirmation and recovery policy.
- Never provide a bypass that deletes the lock metadata while leaving database inaccessible or silently resets the database.

### Forgotten master password

- There is no server-side reset because there is no server account.
- Allow recovery-key unlock if configured.
- A still-valid biometric envelope may allow access and password replacement, but it must not be represented as guaranteed recovery.
- If all valid unlock materials are lost, explain that data is unrecoverable and offer a destructive reset that deletes the encrypted local organizer only after explicit confirmation.

## 6. Database Encryption and Lifecycle

- Use SQLCipher for Android with a compatible Room integration.
- Generate the DEK independently using a secure random source.
- Keep DB open only while the app is in an authorized unlocked state.
- Close/checkpoint the database and clear references to key material when locking or when the app leaves the foreground according to the lock policy.
- Avoid long-lived global references to database keys.
- Never fall back to ordinary SQLite if SQLCipher fails to load, initialize, or authenticate.
- Verify that WAL, shared-memory, journal, and temporary database files are handled safely by the selected SQLCipher integration.
- Database migrations must preserve encryption and must never create a plaintext export as an intermediate shortcut.
- Use transaction rollback for failed migrations and imports.

## 7. Android Keystore and Device Binding

- Use Android Keystore for non-exportable device-bound wrapping keys.
- Prefer hardware-backed/StrongBox protection when available, but detect and report actual capabilities rather than assuming availability.
- Do not persist a Keystore key alias as if it were portable recovery material.
- Device-bound keys do not move with a manual backup. Portable backups must be independently encrypted with a user-supplied backup passphrase.
- If a Keystore key is invalidated, fail closed and use an alternative valid envelope; do not create a new empty database automatically.

## 8. Backup Encryption and Portability

- Portable structured backups must be encrypted before they leave app-private staging storage.
- Use a backup passphrase supplied at export time (and requested at import time) or a separately designed export-key workflow. Recommended baseline: a dedicated backup passphrase, independent of the app-lock password and Android Keystore.
- Derive the backup encryption key with Argon2id and a fresh salt; encrypt the compressed payload with AES-256-GCM.
- The file header may contain only non-sensitive format, algorithm, KDF, salt, nonce, and version metadata. Service names, account identifiers, notes, and images belong inside the encrypted payload.
- Never export the raw encrypted database file as the portable backup, raw DEK, Keystore key, master password, or recovery key.
- Explain that losing the backup passphrase makes that backup unrecoverable.
- CSV is plaintext by nature. Show a clear warning and require deliberate user action before export.

## 9. Android Automatic Backup and Network Isolation

- Do not rely on defaults. Android Auto Backup includes many application files by default, and database files are among the documented backup candidates.
- Configure Android backup exclusions for cloud backup and device-to-device transfer using the correct XML formats for the selected target SDK; include legacy rules for Android versions that require them.
- Set manifest backup controls as appropriate, but verify OEM/device-transfer behavior because `allowBackup` is not a complete cross-device guarantee on every Android version/vendor.
- Test that database, images, security envelopes, preferences, and temporary files are excluded from platform backup/transfer.
- Do not request the `INTERNET` permission. Inspect the merged manifest after every dependency change.
- Do not include analytics, advertising, remote crash upload, attribution, social login, cloud storage, or synchronization SDKs.
- Use Storage Access Framework for explicit user-selected file import/export; do not request broad external-storage permission.

## 10. UI, Clipboard, and Process Privacy

- When protection is enabled, hide sensitive content in the recent-apps snapshot and apply `FLAG_SECURE` to sensitive screens where suitable.
- Re-lock when the app leaves the foreground according to the configured policy; do not trust only a navigation event.
- Clear or obscure sensitive screen state on lock. Do not keep decrypted content in saved instance state.
- Do not copy data to the clipboard automatically. Any explicit copy action should be limited to non-password organizer fields and should use platform clipboard behavior consciously.
- Do not show account details in notifications; notifications are not a first-release feature.
- Prevent passwords and keys from appearing in logs, crash reports, analytics, exception messages, screenshots, test snapshots, or debug overlays.

## 11. Import and Image Safety

- Treat backup files and image files as untrusted input.
- Enforce archive entry count, compressed size, decompressed size, image dimensions, and total backup size limits.
- Reject path traversal, absolute paths, symbolic links, duplicate manifest entries, unexpected files, invalid checksums, malformed JSON, and unsupported format versions.
- Validate image bytes by decoding, not only by checking the filename extension or MIME declaration.
- Re-encode accepted images into a supported raster format and store them under generated internal filenames; do not trust imported file paths.
- Stage, verify, preview, and transactionally commit imports. Clean temporary data on cancellation or error.

## 12. Security Verification Checklist

- [ ] Master password never appears in persistent storage or logs.
- [ ] DEK is randomly generated and never derived directly from the master password.
- [ ] Database is unreadable without a valid DEK.
- [ ] Wrong master password and tampered key envelopes fail closed.
- [ ] Argon2id parameters meet the documented baseline and are benchmarked.
- [ ] AES-GCM nonces are unique per key and authentication tags are verified.
- [ ] Biometric unlock uses a Keystore-backed cryptographic operation and master-password fallback.
- [ ] Biometric enrollment invalidation is handled without data deletion.
- [ ] Recovery key can unlock and reconfigure credentials; loss warnings are clear.
- [ ] Lock on background/process lifecycle behaves as documented.
- [ ] Recent-apps preview and screenshots are appropriately protected when lock is enabled.
- [ ] Android Auto Backup and device-transfer tests show no organizer data is copied.
- [ ] Merged manifest has no `INTERNET` permission.
- [ ] Dependency tree contains no analytics/ads/networking SDK used to transmit data.
- [ ] Backup can be restored on a different device using only the backup file and backup passphrase.
- [ ] Malformed backup/image tests do not crash the app or partially corrupt records.
- [ ] No raw database/key/Keystore material is included in manual backups.

## References

- OWASP Password Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- RFC 9106 Argon2: https://www.rfc-editor.org/rfc/rfc9106
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android biometric authentication: https://developer.android.com/identity/sign-in/biometric-auth
- Android Auto Backup: https://developer.android.com/identity/data/autobackup
- SQLCipher for Android: https://github.com/sqlcipher/sqlcipher-android
