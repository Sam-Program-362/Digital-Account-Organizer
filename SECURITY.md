# Security Policy

## Reporting a vulnerability

Do not publish exploitable vulnerability details in a public issue, discussion, pull request, or screenshot.

1. Check whether **Security → Advisories → Report a vulnerability** is available on the repository's GitHub page. Use that private reporting route when enabled.
2. If private vulnerability reporting is not enabled, contact the repository maintainer through the GitHub profile and request a private reporting channel. Do not disclose exploit details publicly while waiting for a private route.
3. Provide the affected app version, Android version/device model, a concise impact assessment, and reproducible steps. Remove personal account data, passwords, recovery codes, keystore files, and other secrets from the report.

The repository does not publish a response-time guarantee or a dedicated security email address. Do not assume a report has been received until the maintainer acknowledges it.

## Scope

Security reports are relevant to, among other things:

- app-lock and unlock state handling;
- Android Keystore and biometric key handling;
- Argon2id key derivation and AES-256-GCM envelopes/field encryption;
- SQLCipher database initialization and database lifecycle;
- backup encryption, archive parsing, restore staging, and conflict handling;
- password reveal/copy flows and sensitive data in memory;
- image import and the user-triggered HTTPS website-logo fetcher;
- Android manifest permissions, backup exclusions, and release signing.

## Security design in the current source

The current implementation contains:

- Room configured with SQLCipher for the application database;
- AES-256-GCM encryption for saved password fields;
- Argon2id-based key derivation for master-password and backup protection;
- Android Keystore-based device key wrapping;
- optional biometric unlock and a separate recovery-code path;
- configurable app background auto-lock;
- Android `FLAG_SECURE` while the organizer is locked or a relevant lock is enabled;
- explicit Android cloud-backup and device-transfer exclusions;
- encrypted `.dao-backup` export and staged restore.

These are implementation details, not a claim that the application has undergone an independent penetration test, formal verification, or a third-party security audit.

## Known implementation caveat: SQLCipher fail-open path

`EncryptedDatabaseFactory` calls `System.loadLibrary("sqlcipher")`. If loading fails, the current code falls back to a normal `Room.databaseBuilder(...)` without the SQLCipher open-helper factory. The comment explains that this accommodates host JVM/Robolectric tests, but the fallback is not guarded by a test-only build condition.

This can silently downgrade database-at-rest encryption in any runtime where the native library cannot be loaded. The production path should fail closed, while tests should use an explicit test database strategy. Until this is corrected and verified on supported Android ABIs, do not make an unconditional claim that all user database content is encrypted at rest on every device.

## Security boundaries and user responsibilities

The app is intended to reduce exposure of locally stored information. It does not guarantee protection against:

- a rooted, compromised, or debug-compromised device;
- malware or an attacker with sufficient access to the Android process or operating system;
- observation of data while the app is unlocked and displaying it;
- weak or reused master/backup passphrases;
- user-exported plaintext files or files copied to shared storage;
- loss of both the master password and recovery key.

Additional cautions:

- CSV exports are plaintext. They exclude passwords and security metadata, but contain identifying account and service information, notes, and recovery-contact references.
- A recovery code exported to a `.txt` file is plaintext. Store it securely and delete unnecessary copies.
- An encrypted backup is only as secure as the passphrase used to protect it. Losing that passphrase can make the backup unrecoverable.
- The app does not automatically synchronize data to another device. Use the encrypted backup workflow and keep a separate copy.

## Supported versions

The supplied source snapshot declares `2.37.1-release`. No formal support window or security-maintenance guarantee is documented for older version labels. Users should prefer a build from the current repository source and review the associated release notes.
