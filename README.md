# Digital Account Organizer

**A local-first Android application for organizing personal accounts and service-specific accounts, with an encrypted on-device data store and optional password storage.**

Digital Account Organizer gives you one place to record the accounts you use, associate them with services, organize them with categories, and keep the information available offline. It does not sign in to those services or manage the accounts on their behalf.

> **Current source snapshot:** `2.37.1-release` (`versionCode` `203701`)  
> **Minimum Android version:** Android 8.0 (API 26)  
> **Application ID:** `com.aistudio.accountorganizer.vxqpmz`

## Contents

- [What it does](#what-it-does)
- [How the data model works](#how-the-data-model-works)
- [Security model](#security-model)
- [Network and privacy](#network-and-privacy)
- [Backup and export](#backup-and-export)
- [Technology stack](#technology-stack)
- [Build from source](#build-from-source)
- [Run tests](#run-tests)
- [Project layout](#project-layout)
- [CI and release workflows](#ci-and-release-workflows)
- [Project status and known limitations](#project-status-and-known-limitations)
- [Security reporting](#security-reporting)
- [License](#license)

## What it does

### Accounts

- Create standalone personal or work account records.
- Store a display name, email address, username, purpose, recovery-contact references, notes, and status.
- Optionally store an account password. Password values receive field-level AES-256-GCM encryption before being written to the database.
- Generate passwords locally with the built-in password generator.
- Reveal or copy a saved password only through the protected password-view flow.
- Edit, archive, restore, and permanently delete records.

### Services and service accounts

- Create services manually; the application does not require a bundled provider catalogue.
- Add multiple service-account records to a service.
- Link a service-account record to a standalone account, or keep it as a separate service-specific record.
- Add a service website, description, and custom image/icon.
- Select an image from the device or explicitly request a website logo fetch over HTTPS.
- Archive services and manage linked records without treating the app as a client for the external service.

### Categories and discovery

- Create and manage user-defined categories.
- Assign an optional category to standalone and service-account records.
- Search and filter records, browse by service or category, and sort account lists.
- View dashboard counts and recent records.

### App access and appearance

- Optional app lock with a master password.
- Optional biometric unlock where supported and enabled by the user.
- Recovery-key flow for restoring access when the master password is unavailable.
- Configurable background auto-lock: immediate, one minute, or five minutes.
- Light, dark, and system theme modes, plus selectable color palettes.

## How the data model works

The app maintains separate records for:

| Record | Purpose |
| --- | --- |
| `AccountEntity` | A standalone personal/work account. May contain an encrypted password. |
| `ServiceEntity` | A user-created service, including optional website details and an icon reference. |
| `ServiceAccountEntity` | An account associated with one service. It may link to a standalone account or remain independent. |
| `CategoryEntity` | A user-created organizational category. |
| `ServiceAssetEntity` | Metadata for a locally stored service image/icon. |
| `AppPreferenceEntity` | Persisted theme and appearance preferences. |

A service can have multiple service-account records. A service-account record can optionally link to a standalone account. Categories are optional and are not the same thing as services.

## Security model

The implementation includes several layers of local data protection:

- **Encrypted database:** Room is configured to use SQLCipher for the application database.
- **Encrypted password fields:** saved account passwords are encrypted separately with AES-256-GCM before database storage.
- **Key derivation:** Argon2id derives a key-encryption key from the app-lock master password. The current defaults in source are 19 MiB memory, two iterations, and parallelism one.
- **Key wrapping:** the database encryption key is wrapped using Android Keystore when the app lock is disabled, or password/recovery envelopes when the app lock is enabled. Biometric unlock is an optional additional path.
- **Recovery:** a separate high-entropy recovery code can restore the database key. Keep it outside the device and do not share it.
- **Session protection:** the app closes the database when it locks and sets Android's `FLAG_SECURE` while the app is locked or an applicable lock is enabled.
- **Platform backup exclusions:** app-private data is excluded from the configured Android cloud-backup and device-transfer rules.

**Implementation caveat:** `EncryptedDatabaseFactory` currently falls back to a regular Room database if loading the SQLCipher native library fails. That fallback is intended to accommodate JVM/Robolectric testing, but the branch is not limited to a test-only build. A production build should fail closed instead of silently downgrading database encryption. See [Security and Privacy](SECURITY.md) and [the repository audit](docs/REPOSITORY_AUDIT.md). Do not interpret the presence of SQLCipher in the dependency list as proof that every runtime has successfully initialized it.

The application is not a substitute for device security. It cannot guarantee protection on a rooted or compromised device, against malware running with sufficient privileges, or while sensitive information is intentionally exported to an unencrypted file.

## Network and privacy

The app is designed to work offline for account management. The current manifest declares `INTERNET` and `ACCESS_NETWORK_STATE` for one explicit network feature: fetching a logo for a service when the user requests it. That fetch:

- uses HTTPS only;
- rejects local/internal hostnames and private address ranges;
- follows a bounded number of redirects;
- limits the amount of page and image data downloaded; and
- stores the accepted image locally after image validation/normalization.

No account-provider sign-in, account synchronization, application-owned cloud API, advertising SDK, or analytics SDK was found in the active application source/dependency set reviewed for this documentation update. A remote website contacted for logo fetching can still observe the request and the device's network address. The app does not need a Gemini API key for the current runtime implementation; the checked-in `.env.example` contains only a commented placeholder.

See [PRIVACY.md](PRIVACY.md) for the data inventory, exports, network behavior, and retention details.

## Backup and export

The Backup screen supports:

- **Encrypted `.dao-backup`:** a portable backup protected with a separate backup passphrase using Argon2id and AES-256-GCM. Full and selective export modes are implemented.
- **Staged restore:** validates and decrypts the archive, previews incoming records, detects identity/possible-duplicate conflicts, and supports skip, keep-both, and replace decisions before committing.
- **CSV export:** an unencrypted convenience export. Passwords and security metadata are excluded, but the CSV can still contain identifying account information, notes, service details, and recovery-contact references.
- **Backup vault:** exported backup/CSV files can be saved in the app's internal backup vault and, where available, the device Downloads folder.

The recovery code can also be exported as a plain-text file. Treat that file as a secret. Encrypted backup files, CSV files, and recovery-code files copied to Downloads are outside the app's private database boundary and are not protected by the database encryption layer.

## Technology stack

| Layer | Implementation |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Persistence | Room |
| Database encryption | SQLCipher for Android |
| Field encryption | AES-256-GCM |
| Password-based key derivation | Argon2id (Bouncy Castle) |
| Device key protection | Android Keystore |
| Biometric authentication | AndroidX Biometric |
| Async work | Kotlin Coroutines and Flow |
| Tests | JUnit, AndroidX Test, Robolectric |
| Build | Gradle Kotlin DSL, version catalog |

## Build from source

### Requirements

- Android Studio with an Android SDK installation, or a command-line Android SDK.
- JDK 17 for the release workflow (the separate CI workflow currently configures JDK 21).
- Gradle `9.3.1`.
- Android SDK Platform 36 and Android Build Tools 36.0.0, as used by the release workflow.

The supplied source snapshot includes `gradle-wrapper.properties`, but does **not** include the Gradle wrapper scripts or wrapper JAR. Use an installed Gradle 9.3.1 executable, or regenerate the wrapper locally before using `./gradlew`.

### Build a debug APK

```bash
gradle :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Build a signed release APK

The release signing configuration expects a keystore file (default: `my-upload-key.jks`) and these environment variables:

```bash
export KEYSTORE_PATH="$PWD/my-upload-key.jks"
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"

gradle :app:assembleRelease
```

The signing alias in `app/build.gradle.kts` is currently fixed to `upload`. Never commit a production keystore, its password, or a decoded keystore file. The CI workflows read signing material from GitHub Actions secrets; see [CI and release workflows](#ci-and-release-workflows).

## Run tests

```bash
gradle :app:testDebugUnitTest
```

This runs the configured local JVM and Robolectric test tasks. The repository also contains an Android instrumentation test under `app/src/androidTest`; it is a basic application-context smoke test, not a comprehensive device test suite.

The source package was inspected for this documentation update, but a fresh Gradle build and test run was not performed as part of the documentation work.

## Project layout

```text
.
├── app/
│   ├── src/main/java/com/example/
│   │   ├── app/                 # Application container and ViewModel
│   │   ├── core/database/       # Room entities, DAOs, database, repository
│   │   ├── core/files/          # Local assets, backup storage, logo fetching
│   │   ├── core/security/       # Key management, encryption, recovery
│   │   ├── domain/model/        # Domain records and shared models
│   │   ├── feature/             # Accounts, services, categories, backup, settings
│   │   └── ui/theme/            # Theme and typography
│   ├── src/test/                # JVM and Robolectric tests
│   └── src/androidTest/         # Instrumentation tests
├── .github/workflows/           # APK CI and release workflows
├── public/assets/aistudio/      # Project context and AI Studio working assets
├── docs/                        # Repository and release documentation
├── metadata.json
└── gradle/                      # Gradle version catalog and wrapper properties
```

## CI and release workflows

| Workflow | Trigger | Behavior |
| --- | --- | --- |
| `android-release.yml` | Push to `main`/`master`, or manual dispatch | Runs the configured unit/Robolectric tests, builds and signs an APK, verifies its signature and selected permissions, then uploads a 30-day Actions artifact. It does not publish a GitHub Release. |
| `release-apk.yml` | Push a `v*` tag, or manual dispatch | Builds and signs an APK, verifies its signature, calculates a SHA-256 checksum, and creates a GitHub Release with the APK asset. |

Both workflows require signing secrets. The release workflow's current version input controls the release tag/title and APK filename, but it does not update `app/build.gradle.kts` before building. For tags other than the version already declared in Gradle, the release title can therefore disagree with the version embedded in the APK. See [the repository audit](docs/REPOSITORY_AUDIT.md).

## Project status and known limitations

- The source snapshot declares version `2.37.1-release` and version code `203701`.
- The app is Android-only; there is no web client or desktop client in this repository.
- There is no cloud account, automatic synchronization, or provider sign-in.
- There is no `LICENSE` file in the supplied repository snapshot. Public visibility alone does not grant permission to reuse, modify, or redistribute the code. Add a license before describing the project as open source.
- The `public/assets/aistudio/` context package includes older planning documents that describe a no-password organizer and a not-yet-started implementation. Those statements conflict with the current source. Treat the Kotlin implementation and this README as the current implementation reference until the context package is reconciled.
- See `RELEASE_NOTES.md` for the supplied release-archive audit. Imaginary development-vlog material is kept separate in `docs/FICTIONAL_DEVELOPMENT_VLOG.md` and is not a historical release record.

## Security reporting

Please do not publish exploitable vulnerability details in a public issue. Use GitHub's private vulnerability reporting for this repository if it is enabled. If private reporting is unavailable, contact the repository maintainer through the GitHub profile and request a private reporting channel. Include the affected version, Android version/device details, reproduction steps, and impact; do not attach real passwords, recovery keys, or personal account data.

See [SECURITY.md](SECURITY.md) for the reporting policy.

## License

No license file was present in the supplied repository snapshot. Until the project owner adds a license, the repository should not be advertised as open source and downstream users should not assume redistribution or modification rights.
