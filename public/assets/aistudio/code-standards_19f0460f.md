# Code Standards

## General

- Keep modules small, cohesive, and single-purpose.
- Fix root causes; do not add workarounds that hide broken state or security failures.
- Separate UI, domain behavior, persistence, file handling, and cryptography.
- Prefer immutable UI state and unidirectional data flow.
- Use Kotlin coroutines and structured concurrency; never create unmanaged threads or global coroutine scopes for feature work.
- Do not add a library for a trivial task. Before adding dependencies, verify maintenance, security history, license, Android compatibility, binary size, and whether it introduces permissions or network behavior.
- Keep the app offline. Inspect dependency manifests and the merged manifest for permissions introduced transitively.
- Avoid premature abstraction. Introduce interfaces at real boundaries such as repositories, key management, backup I/O, and platform file selection.
- Make failure visible and recoverable. Do not swallow exceptions or silently discard records.
- Do not use real user account information in sample data, tests, screenshots, or logs.

## Kotlin

- Use Kotlin as the primary language.
- Prefer `val`; use `var` only when mutation is required.
- Use data classes for immutable domain/UI data where appropriate.
- Use sealed interfaces/classes for finite result and state models.
- Use nullable types deliberately; avoid `!!` and avoid using empty strings to encode missing values.
- Give public APIs explicit types and meaningful names.
- Use `enum` or sealed types for stable finite statuses; avoid storing arbitrary status strings when a domain type is appropriate.
- Avoid `Any`, unchecked casts, reflection, and broad `catch (Throwable)` blocks.
- Use `Result`-like explicit domain outcomes for expected failures; reserve exceptions for exceptional conditions.
- Keep dispatchers injectable for testability. Do not hard-code `Dispatchers.IO` throughout domain logic.
- Use `Clock` or an injectable time source for timestamps and deterministic tests.
- Document non-obvious security assumptions and lifecycle constraints in KDoc.

## Android and Jetpack Compose

- Use Jetpack Compose and Material 3 as the recommended native UI baseline.
- Keep composables focused on rendering state and forwarding user actions. Do not put SQL, encryption, image decoding, archive parsing, or file I/O in composables.
- Collect `Flow` with lifecycle-aware APIs.
- Use ViewModels to expose immutable screen state and handle UI events; do not let ViewModels become repositories or god objects.
- Use `remember` only for UI-local state. Persisted settings and records belong in repositories.
- Use stable keys for lazy lists.
- Model loading, empty, success, and error states explicitly.
- Preserve state across configuration changes without placing secret key material or plaintext passwords in `SavedStateHandle`, `Bundle`, or saved instance state.
- Use Android back navigation and system bars consistently. Handle process recreation and background/foreground transitions deliberately, especially for app-lock behavior.
- Request runtime permissions only when required by a confirmed feature. Prefer Android Photo Picker and Storage Access Framework document pickers to broad storage permissions.
- Never request contacts, phone, accounts, or internet permissions for the core organizer without an explicit product requirement.
- Set secure window behavior for lock/authentication and sensitive organizer screens as defined in `security-and-privacy.md`.

## Styling

- Use semantic Material 3 color roles and project design tokens; do not scatter hard-coded color literals through feature composables.
- Follow `ui-context.md` for themes, typography, spacing, shapes, component behavior, and responsive layout.
- Support light and dark themes. Check text and icon contrast in both.
- Prefer Material components when they meet the requirement; wrap them in project components when shared product behavior or styling is needed.
- Use descriptive content labels for meaningful icons and images. Decorative imagery must not create redundant screen-reader announcements.
- Do not encode account status or category identity through color alone; include text or an accessible label.

## Database and Domain Layer

- Use Room DAOs behind repositories; feature UI must not call DAOs directly.
- Use SQLCipher-backed database access according to the selected compatible integration. Load the native SQLCipher library before opening the database.
- Keep entity definitions and domain models separate where mapping protects domain rules from schema details.
- Use stable UUID identifiers for services, accounts, and categories.
- Use foreign keys and explicit deletion behavior. Do not cascade-delete accounts merely because a service is removed unless the user explicitly confirms an operation that describes the consequence.
- Implement schema migrations. Never use destructive migration fallback for real user data.
- Use database transactions for multi-record import, service/category deletion, and relationship updates.
- Store service image bytes as app-private files and reference them by controlled internal identifiers or relative paths.
- Normalize searchable values for comparison without destroying the user's original display value.
- Keep account status and archive state separate when that distinction is needed by product behavior.

## Security and Cryptography

- Use established, maintained cryptographic libraries and platform primitives. Do not implement cryptographic algorithms manually.
- Use Argon2id for master-password-based key derivation with per-installation random salt and versioned parameters. Benchmark on the supported Android device range. Do not weaken below the OWASP baseline without a documented security review.
- Generate the database DEK with a cryptographically secure random generator. Do not derive the DEK directly from the master password.
- Use AES-256-GCM for key envelopes and backup payloads with a fresh unique nonce for every encryption under a given key. Authenticate relevant header fields as associated data.
- Use Android Keystore for device-bound key material. Do not claim hardware-backed storage unless the device reports it and the implementation verifies it.
- Use `BiometricPrompt` with a Keystore-backed `CryptoObject` for optional strong-biometric unlock. Keep master-password fallback available.
- Clear mutable byte arrays and temporary buffers on a best-effort basis when no longer needed. Do not claim managed-runtime memory can be perfectly zeroized.
- Never log passwords, keys, encrypted database passphrases, account values, backup payloads, or imported file contents.
- Do not persist passwords in preferences, databases, saved state, clipboard history, or crash metadata.
- Fail closed if database authentication, AEAD verification, or key-envelope parsing fails.

## File and Backup Handling

- Use Storage Access Framework for user-directed open/create operations.
- Treat every imported backup and image as untrusted input.
- Enforce maximum file, archive, image, entry-count, and decompressed-size limits. The exact numeric limits must be documented in implementation configuration and tested.
- Reject archive path traversal, absolute paths, symlinks, duplicate critical entries, unsupported versions, malformed metadata, and unexpected payload structures.
- Validate an image by decoding its actual content, not only trusting file extension or declared MIME type.
- Stage an import in private temporary storage. Verify authentication, schema, references, and assets before writing to the database.
- Commit imports transactionally. Clean temporary plaintext files on success, failure, or cancellation.
- Do not export raw Room/SQLite/SQLCipher database files or Android Keystore keys as a portable backup.

## API Routes

Not applicable. This project has no backend, server API, network routes, or remote application service. Do not introduce API routes or a backend as an implementation convenience.

## Data and Storage

- Put structured records in the encrypted local database.
- Put user-selected service artwork in app-private files and associate it through stable service asset references.
- Put ephemeral import/export staging files in private temporary storage with cleanup.
- Put security envelopes and KDF metadata in the smallest suitable private local store; never persist raw secrets.
- Exclude organizer files from Android automatic cloud backup and device-transfer backup.
- Use user-selected export locations only for explicit manual exports.
- Keep the backup format versioned and independent from the internal Room schema.

## File Organization

Recommended structure; adjust only to match actual Gradle module boundaries and keep the ownership rules intact:

```text
app/
  src/main/
    AndroidManifest.xml
    java/<application-id>/
      app/                 # Application startup and dependency graph
      core/
        common/            # Shared types and small utilities
        database/          # Room, SQLCipher, entities, DAOs, migrations
        designsystem/      # Themes and reusable components
        files/             # SAF, image validation, private asset store
        navigation/        # Navigation destinations and graph
        security/          # KDF, envelopes, Keystore, lock state machine
      feature/
        dashboard/
        accounts/
        services/
        categories/
        backup/
        settings/
      domain/              # Shared domain rules/use cases if warranted
    res/xml/               # Backup exclusion rules
    test/                  # Local unit tests
    androidTest/           # Instrumented database/platform tests
```

- `feature/accounts/` — account list, detail, add/edit, search, filtering, archive, and deletion UI/state.
- `feature/services/` — manual service creation/editing, logo/icon selection, service list/details, and account associations.
- `feature/categories/` — category CRUD and category-based navigation.
- `feature/backup/` — export/import, cryptographic backup envelope, schema validation, preview, duplicate decisions, and transaction orchestration.
- `core/security/` — security primitives and state only; keep UI prompts in the relevant feature/presentation layer.
- `core/database/` — schema and database infrastructure only; do not place screen-specific behavior here.

## Testing

- Unit-test domain validation, duplicate detection, category/service rules, and account field handling.
- Test database relations, transactions, migrations, and deletion restrictions using an encrypted database.
- Test cryptographic key-envelope round trips, wrong-password failures, corrupted ciphertext, KDF parameter validation, and nonce uniqueness behavior.
- Test app-lock enable/disable, password change, biometric enrollment invalidation/fallback, timeout, process death, and recovery flows.
- Test backup export/import on another app installation/device profile; do not rely only on same-device Keystore state.
- Test malformed and hostile backup archives, oversized images, traversal paths, and interrupted imports.
- Test light/dark themes, content descriptions, large fonts, keyboard/screen-reader navigation, empty states, and common screen sizes.
- Verify the merged manifest contains no internet permission and Android backup rules exclude private organizer data.
- Run unit tests, instrumented tests, lint/static checks, and a release build before marking an implementation unit complete.
