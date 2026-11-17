# Architecture Context

## Stack

The following is the recommended baseline, not a user-confirmed technology selection. Verify current stable versions, compatibility, licensing, and maintenance before implementation. Keep dependency versions centralized and pinned through the build's version catalog.

| Layer | Technology | Role |
|---|---|---|
| Platform | Android | Target operating system; final minimum API level remains a release decision. SQLCipher for Android currently documents API 23+ support. |
| Language | Kotlin | Primary application language. |
| UI | Jetpack Compose + Material 3 | Native declarative UI, theme system, screen composition, and accessibility semantics. |
| Architecture | Feature-oriented Clean Architecture / MVVM | Keep presentation, domain rules, and data access separate without unnecessary abstraction. |
| Database API | AndroidX Room | Typed entities, DAOs, migrations, and repository persistence. |
| Database encryption | SQLCipher for Android | Encrypt the SQLite database used by Room. Confirm current Room integration version. |
| Key management | Android Keystore | Protect device-bound key-encryption keys; use hardware-backed protection where available without assuming every device provides it. |
| Password-based KDF | Argon2id | Derive a wrapping key from the app-specific master password; benchmark parameters on supported devices. |
| Authenticated encryption | AES-256-GCM | Wrap the random database encryption key and encrypt portable backup payloads. Use unique nonces per key. |
| Biometric UI | AndroidX Biometric / BiometricPrompt | Optional strong-biometric convenience unlock using Keystore-backed cryptographic operations. |
| Local image storage | App-private files directory | Store validated, normalized service logo/icon assets. |
| File import/export | Storage Access Framework | Let the user explicitly choose backup input and output locations without broad storage permissions. |
| Async work | Kotlin coroutines / Flow | Structured local operations, database streams, and bounded background work. |
| Testing | JUnit, AndroidX test, Compose UI tests, instrumented database tests | Unit, integration, migration, UI, and security behavior verification. |
| Build | Gradle Kotlin DSL | Reproducible Android build and dependency management. |

**Dependency decision gate:** SQLCipher for Android supports Room 2 and Room 3 in its current project documentation. Select the compatible Room/SQLCipher integration only after confirming the project's chosen Android Gradle Plugin, Kotlin, compile SDK, and minimum Android API. Do not copy an old dependency snippet without checking the current official instructions.

## System Boundaries

Recommended package structure (adapt package root after the application ID is chosen):

- `app/` — Android application entry point, app-level navigation, lifecycle setup, dependency composition, and manifest configuration.
- `core/designsystem/` — Material 3 theme, color/type/shape/spacing tokens, reusable UI components, and icon presentation.
- `core/navigation/` — typed navigation destinations and navigation coordination.
- `core/database/` — Room database declaration, SQLCipher initialization, entities, DAOs, migrations, and database lifecycle.
- `core/security/` — encryption key manager, master-password KDF, key envelopes, app-lock state machine, biometric integration, recovery-key operations, and sensitive-memory handling.
- `core/files/` — Storage Access Framework adapters, image import validation, private asset storage, and safe file handling.
- `core/common/` — shared result types, dispatchers, date/time helpers, and small reusable utilities.
- `feature/dashboard/` — dashboard state and UI; derive counts/recent records from repositories.
- `feature/accounts/` — account list, detail, create/edit, search, filtering, archive, and deletion flows.
- `feature/services/` — manual service creation, editing, custom logo/icon, service details, and service-to-account navigation.
- `feature/categories/` — custom category management and category-based browsing.
- `feature/backup/` — export/import orchestration, backup encryption, schema versioning, preview, and conflict resolution.
- `feature/settings/` — themes, app protection settings, privacy information, and app metadata.
- `domain/` (or feature-local domain packages) — business rules and use cases independent of Android UI and persistence.

Feature modules must not open the database directly. They call use cases or repositories. UI composables must not perform cryptographic operations, filesystem operations, or database work directly.

## Storage Model

- **Encrypted relational database:** services, account records, categories, relationships, record statuses, timestamps, and portable application preferences. SQLCipher must encrypt the SQLite database file and its relevant journal/WAL files according to its documented behavior. Database access must be unavailable until the key manager has successfully unlocked the database key.
- **Database key:** generate a cryptographically random 256-bit Data Encryption Key (DEK). Do not derive the database key directly from the master password. Store only authenticated-encrypted key envelopes on disk.
- **Master-password envelope:** when app lock is enabled, derive a 256-bit Key Encryption Key (KEK) with Argon2id using a unique random salt and versioned parameters. Use AES-256-GCM to wrap the DEK. The master password itself is never persisted.
- **Device envelope:** when app lock is disabled, use a device-bound Android Keystore key to wrap the DEK for seamless local opening. Enabling the app lock must remove the unrestricted device envelope after successfully creating and verifying the master-password envelope.
- **Biometric envelope:** if the user enables biometric unlock, use an Android Keystore key configured for strong biometric, authentication-per-use cryptographic operations to wrap the DEK. Use `BiometricPrompt` with a `CryptoObject`. The master password remains the fallback.
- **Recovery envelope:** generate an independent high-entropy recovery key and use it to wrap the DEK. Show it to the user once during setup, require a confirmation step that it was saved, and do not store it in plaintext. The user can use it if the master password is lost. Its exact UX should be carefully tested.
- **Service image assets:** store selected and normalized files under app-private storage; database records hold internal asset IDs or relative paths, not arbitrary external file URIs. Backups package referenced assets with checksums.
- **Temporary files:** staging and cache files must be stored in private temporary locations, cleaned after use, and never contain plaintext secrets longer than required. Avoid external cache and shared storage for intermediate plaintext backup contents.
- **Portable backups:** a user-selected `.dao-backup` file contains a small non-sensitive header and an authenticated encrypted payload. The encrypted payload includes the structured data and service image assets. Its backup passphrase is independent of the device's Keystore keys so restore works on another device.
- **CSV export:** user-selected plaintext tabular export may be offered as a convenience, but it is not a restorable full backup. Display a warning before writing it to a user-selected location.
- **No Android automatic backup:** explicitly exclude app-private databases/files/preferences and device-transfer data using supported backup rules, and set the relevant manifest backup controls. Test both cloud-backup and device-to-device transfer behavior. Do not assume `allowBackup=false` alone behaves identically on every Android/OEM transfer path.

## Auth and Access Model

- There is no remote user account, registration, sign-in, server-side authentication, or multi-user cloud identity.
- The application contains one local organizer workspace per app installation.
- App protection is optional. When disabled, a device-bound Keystore envelope permits opening the encrypted database without asking for an app password. When enabled, the master password is required to unwrap the DEK unless the user chooses a valid biometric unlock route.
- The master password is distinct from all external account credentials. It must never be described as a Google password or service password.
- Use Argon2id rather than a fast hash for password-based key derivation. Use per-installation random salt, store KDF algorithm/version/parameters with the key envelope, and benchmark parameters against supported devices. OWASP currently specifies a minimum Argon2id baseline of 19 MiB memory, 2 iterations, and parallelism 1; benchmark and choose a parameter set at least as strong as the applicable guidance without causing unacceptable device failures.
- Biometric unlock is optional and device-dependent. Require strong biometrics for a cryptographic unlock path; do not silently substitute weak biometrics. If biometric authentication is unavailable or the key is invalidated by biometric enrollment changes, fall back to the master password and allow biometric re-enrollment.
- Recovery uses the separate high-entropy recovery key. There is no server reset. If the user loses all unlock material, data recovery may be impossible; communicate this before enabling protection.
- The app lock controls access to the organizer UI and database key. It does not claim to protect against a compromised/rooted OS, malicious accessibility services, an attacker who can control an already-unlocked device, or forensic attacks beyond the stated threat model.

## Invariants

1. Never add an internet permission, network client, external account API, telemetry, advertising SDK, or upload path that transmits organizer data without an explicit future product-scope change.
2. Never store service passwords, API secrets, access tokens, refresh tokens, passkeys, or one-time authentication codes. App-lock credentials are the only password-like material in scope, and the plaintext master password must never be stored.
3. All persistent database content is encrypted at rest. Database access must not begin before the key manager completes a valid unlock path.
4. The master password must not be used directly as the SQLCipher database key. Use a random DEK and separate, authenticated key envelopes.
5. Do not store plaintext DEK, KEK, recovery key, master password, or backup password in logs, crash reports, analytics, saved state, preferences, or exported metadata.
6. Never export Android Keystore private material or assume it can be migrated to a different device. Portable backups must be independently encrypted with a user-supplied backup passphrase.
7. User-created services are the only service source. Do not introduce a mandatory predefined provider catalogue or automatic service discovery.
8. One service may have many account records. Foreign keys and deletion flows must preserve referential integrity.
9. Import must validate and preview records before mutation. Never silently overwrite existing user data. Apply a selected import as one transaction or provide a recoverable staged commit.
10. Never use automatic Android cloud/device backup for organizer database, icon files, or security envelopes. User-initiated export is the only supported portable backup flow.
11. Imported files and images are untrusted input. Validate schema, size, archive paths, MIME/content signatures, and image decoding before use.
12. Every security-sensitive design change requires corresponding tests and an update to `security-and-privacy.md`, `architecture.md`, `code-standards.md`, and `decision-log.md`.
