# Security Policy

**Last updated: 18 November 2026 (`v3.12.1-release`)**

## Reporting a vulnerability

Do not publish exploitable vulnerability details in a public issue, discussion, pull request, or screenshot.

1. Check whether **Security → Advisories → Report a vulnerability** is available on the repository's GitHub page. Use that private reporting route when enabled.
2. If private vulnerability reporting is not enabled, contact the repository maintainer through the GitHub profile and request a private reporting channel. Do not disclose exploit details publicly while waiting for a private route.
3. Provide the affected app version (e.g., `3.12.1-release`), Android version/device model, a concise impact assessment, and reproducible steps. Remove personal account data, passwords, backup codes, recovery codes, Export Lock Keys, keystore files, and other secrets from the report.

The repository does not publish a response-time guarantee or a dedicated security email address. Do not assume a report has been received until the maintainer acknowledges it.

## Scope

Security reports are relevant to, among other things:

- app-lock and unlock state handling;
- Android Keystore and biometric key handling;
- Argon2id key derivation and AES-256-GCM key envelopes and field-level secret encryption (`encryptedPassword` and `encryptedBackupCodes`);
- SQLCipher database initialization, Room v3 migrations (`MIGRATION_1_2`, `MIGRATION_2_3`), and database lifecycle;
- portable `.dao-backup` encryption, archive parsing, zip-bomb/path-traversal guards, restore staging, and conflict handling;
- Password Manager verification enforcement on both reveal (`Show`) and clipboard copy (`Copy`) flows for passwords, backup codes, and protected notes;
- zero-leak blank-on-edit protection for saved passwords and saved backup codes;
- context-aware and full-app `FLAG_SECURE` screenshot/screen-recording restrictions;
- image import and the user-triggered HTTPS website-logo fetcher (SSRF and redirect protections);
- Android manifest permissions, backup exclusions, and release signing.

## Security design in `3.12.1-release`

The current implementation contains:

- **SQLCipher + Room v3 database encryption:** Room is configured with SQLCipher (`SupportOpenHelperFactory`) using a 256-bit Data Encryption Key (DEK).
- **Field-level AES-256-GCM secret encryption:** Saved standalone account passwords, service-account passwords, and standalone account backup/recovery codes are individually encrypted with AES-256-GCM (`ENC1:` prefix + 96-bit random nonce + 128-bit authentication tag + authenticated associated data `dao-secret-field-v1`) under the active 256-bit DEK before being written to SQLCipher.
- **Argon2id key derivation:** Argon2id (19 MiB memory, 2 iterations, parallelism 1, 256-bit output) derives Key-Encryption Keys (KEKs) from the Master Password and from `.dao-backup` Export Lock Keys.
- **Android Keystore & multi-envelope DEK wrapping:**
  - When App Lock is off, the 256-bit DEK is wrapped by an Android Keystore AES-256-GCM hardware-backed key (`DEVICE_KEYSTORE` envelope).
  - When App Lock is enabled, the `DEVICE_KEYSTORE` key is deleted and the DEK is wrapped in `MASTER_PASSWORD` and `RECOVERY_KEY` envelopes (plus an optional crypto-bound `BIOMETRIC_KEYSTORE` envelope for fingerprint unlock and fingerprint master-password reset).
- **Strict Password Manager verification on Reveal & Copy:**
  - Unlocking the app does not pre-authorize hidden vault fields.
  - Whenever a password, backup-code list, or protected note is hidden on screen, **both** the **Show (Reveal)** button and the **Copy to Clipboard** button require explicit identity verification (Device Lock/Biometrics or Master Password).
- **Blank-on-Edit credential protection:**
  - Opening the Edit page for an account or service entry never loads or displays an existing saved password or existing saved backup codes. The inputs remain blank with a `"Saved • enter new ... to replace"` indicator and an explicit `"Clear"` chip.
- **Selective & Full-App `FLAG_SECURE` window protection:**
  - Automatically applies `WindowManager.LayoutParams.FLAG_SECURE` whenever the workspace is locked or whenever the user is viewing or editing credentials, with an optional **"Restrict Screenshots Across Entire App"** toggle in **Settings → Security**.
- **Explicit Android cloud-backup and device-transfer exclusions:**
  - `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` exclude app-private databases and key envelopes from automatic cloud backup.
- **Portable encrypted `.dao-backup` export and staged restore:**
  - Outer `.dao-backup` container uses an 8-byte magic header (`DAOBKP01`), Argon2id KDF parameters, and AES-256-GCM authenticated encryption with header AAD binding.
  - Inner ZIP parser enforces entry-count limits, path-traversal checks, 50 MB total decompressed size limits, and per-asset SHA-256 checksum verification before staging records.

These are implementation details of the source repository, not a claim that the application has undergone an independent third-party security audit.

## Known implementation caveat: SQLCipher JVM test fallback

`EncryptedDatabaseFactory` calls `System.loadLibrary("sqlcipher")`. If loading fails, the code falls back to a standard `Room.databaseBuilder(...)` without the SQLCipher open-helper factory so that local JVM/Robolectric unit tests (which do not bundle Android ARM/x86 `.so` native binaries) can execute on the host JVM. In production Android APK builds, `net.zetetic:sqlcipher-android` bundles the native libraries for Android ABIs.

## Security boundaries and user responsibilities

The app is designed to protect locally stored account data at rest and require verification before revealing or copying vault secrets. It cannot guarantee protection against:

- a rooted, compromised, or debug-compromised device;
- malware or an attacker with root/process privileges on the Android operating system;
- physical observation while a secret is intentionally unlocked and revealed on screen;
- clipboard inspection by other apps or system services after the user explicitly copies a field or password to the Android clipboard;
- user-exported plaintext CSV files or one-time recovery-code `.txt` files saved to shared storage (`Downloads`);
- loss of both the Master Password and the 24-character Recovery Code (or loss of a `.dao-backup` file's Export Lock Key).

Additional cautions:

- **CSV exports are plaintext:** They exclude passwords, backup codes, and security keys, but contain account labels, email addresses, usernames, connected phone numbers, date of birth, service/category details, notes, and recovery-contact references.
- **Recovery-code `.txt` files are plaintext:** Store the downloaded recovery code securely outside the device and delete unnecessary copies from `Downloads`.
- **Portable `.dao-backup` archives:** Anyone who possesses both a `.dao-backup` file and its Export Lock Key (`DAO-...`) can restore the backup on another device. Keep the Export Lock Key separate from the backup file.

## Supported versions

| Version | Version Code | Database Schema | Status |
| --- | --- | --- | --- |
| `3.12.1-release` | `301201` | Room v3 (`MIGRATION_2_3`) | **Current Supported Release** |
| `2.37.1-release` | `203701` | Room v2 (`MIGRATION_1_2`) | Superseded by `3.12.1-release` |
