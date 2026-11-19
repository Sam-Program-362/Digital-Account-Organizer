# Digital Account Organizer

**A local-first Android application for organizing standalone personal/work accounts and service-specific accounts, featuring an encrypted on-device SQLCipher + AES-256-GCM vault for passwords and account backup codes.**

Digital Account Organizer gives you one offline-first workspace to catalog the accounts you own, manage multiple connected phone numbers and recovery references, store encrypted passwords and 2FA/account recovery codes, associate accounts with user-created services, and transfer your vault across devices with encrypted `.dao-backup` archives. It does not sign in to external services or transmit your credentials over the network.

> **Current release version:** `3.12.1-release` (`versionCode` `301201`)  
> **Database schema version:** Room v3 (`MIGRATION_1_2`, `MIGRATION_2_3`)  
> **Minimum Android version:** Android 8.0 (API 26) • **Target / Compile SDK:** 36  
> **Application ID:** `com.aistudio.accountorganizer.vxqpmz`

## Contents

- [What it does](#what-it-does)
- [How the data model works](#how-the-data-model-works)
- [Security model](#security-model)
- [Network and privacy](#network-and-privacy)
- [Backup, cross-device migration, and export](#backup-cross-device-migration-and-export)
- [Technology stack](#technology-stack)
- [Build from source](#build-from-source)
- [Run tests](#run-tests)
- [Project layout](#project-layout)
- [CI and release workflows](#ci-and-release-workflows)
- [Security reporting](#security-reporting)

## What it does

### Standalone Accounts

- **Rich Identity & Personalization:**
  - Create standalone personal or work account records independent of any service (e.g., primary email identities, banking identities, developer handles).
  - **Single-Letter Email Monogram & Profile Color Palette:** Account avatars display a single letter — the first letter of the account's email address (falling back to username or label when no email is set) — paired with a customizable 10-color profile palette (`Emerald`, `Sapphire`, `Violet`, `Amber`, `Crimson`, `Cyan`, `Indigo`, `Teal`, `Coral`, `Slate`, or `Theme Default`).
  - **Date of Birth & Calculated Age:** Store an optional Date of Birth (`YYYY-MM-DD`) via direct input or the integrated Material 3 Date Picker and view the automatically calculated current age on the account detail page.
  - **Multiple Connected Phone Numbers vs. Single Recovery Phone:** Attach multiple connected phone numbers to a standalone account while keeping the single Recovery Phone Number and Recovery Email Reference separate.
- **Encrypted Password & Backup Codes Vault:**
  - Store an optional account password and multi-code Account Backup / Recovery Codes, each encrypted at rest with **AES-256-GCM** before being stored inside the **SQLCipher** database.
  - Built-in local password generator with real-time strength estimation.
  - **Strict Verification on Reveal & Copy:** Both revealing (`Show`) and copying (`Copy to Clipboard`) a hidden password, backup code, or protected note require Password Manager verification (Device Lock/Biometrics or Master Password).
  - **Quick-Copy Backup Codes:** Once unlocked, each saved account backup/recovery code is rendered as an individual interactive chip with one-tap Quick Copy and a Copy All option.
  - **Blank-on-Edit Protection:** Opening the Edit page never pre-fills or exposes an existing saved password or saved backup codes; fields remain blank with a `"Saved • enter new ... to replace"` indicator and an explicit `"Clear"` chip.
- **One-Tap Quick Copy on Account Detail Page:**
  - Tap any non-secret identity or recovery field on the Standalone Account Detail page (Account Name/Label, Account Address/Email, Username, Date of Birth, Purpose, each Connected Phone Number, Recovery Account/Email, or Recovery Phone Number) to copy that specific value directly to the clipboard.

### Services and Service Accounts

- **User-Created Services:** Create services manually with custom names, website URLs, descriptions, and custom icons (imported from device storage or fetched on demand from the service's HTTPS website).
- **Full-Screen Detail & Editor Pages:** View services, service account entries, and standalone accounts in dedicated full-screen pages with smooth horizontal slide transitions instead of cramped pop-up dialogs.
- **Flexible Linking:** Add multiple service-account entries to any service. Link a service entry to an existing standalone account or maintain a service-specific email/username and optional encrypted password.
- **Exclusive Archived Filter:** Toggle the `"Archived"` filter chip on the Services page to view only archived services (hiding active ones), or manage active services by default.

### Categories and Discovery

- Create and manage user-defined organizational categories with safe reassignment on deletion.
- Search standalone accounts by label, email address, username, connected phone numbers, date of birth, category, or purpose.
- Filter by status (`Active`, `Inactive`, `Closed`), category, and archive state, and sort by recently updated, label, email/username, or creation date.
- View dashboard metrics and recent activity (counting standalone accounts accurately without double-counting service entries).

### App Access, Screenshot Security, and Appearance

- Optional App Lock with an 8+ character Master Password, optional Fingerprint/Biometric unlock, and a permanent encrypted 24-character Recovery Code (with one-time `.txt` download and fingerprint master-password reset support).
- Configurable background auto-lock (`Immediate`, `1 minute`, `5 minutes`).
- **Selective & Full-App Screenshot Protection (`FLAG_SECURE`):** Automatically blocks screenshots and screen recording on lock screens and credential viewing/editing pages by default, with an optional **"Restrict Screenshots Across Entire App"** toggle in Settings.
- Light, Dark, and System theme modes paired with 6 curated color palettes (`Emerald Trust`, `Sapphire Vault`, `Amethyst Cyber`, `Amber Gold`, `Crimson Rose`, `Monochrome Slate`).

## How the data model works

The app uses Room v3 (`OrganizerDatabase`) with non-destructive migrations (`MIGRATION_1_2`, `MIGRATION_2_3`):

| Record | Purpose |
| --- | --- |
| `AccountEntity` | A standalone personal/work account storing display name, email, username, multiple connected phone numbers (`phoneNumbersJson`), `dateOfBirth`, AES-256-GCM `encryptedPassword`, AES-256-GCM `encryptedBackupCodes`, `avatarColorHex`, category, purpose, recovery email/phone references, notes, and lifecycle timestamps. |
| `ServiceEntity` | A user-created service with name, website URL, description, icon asset reference, and archive state. |
| `ServiceAccountEntity` | An account entry belonging to a specific `ServiceEntity`. May link to a standalone `AccountEntity` via `linkedAccountId` or use custom credentials with its own optional `encryptedPassword`. |
| `CategoryEntity` | A user-created organizational category. |
| `ServiceAssetEntity` | Metadata and SHA-256 digest for a locally stored service logo/icon. |
| `AppPreferenceEntity` | Persisted theme, color palette, and screenshot-restriction preferences. |

## Security model

The implementation combines multiple layers of local protection:

- **Encrypted database:** Room is configured to use SQLCipher (`SupportOpenHelperFactory`) for the on-device database.
- **Field-level AES-256-GCM encryption:** Saved account passwords, service-account passwords, and standalone account backup/recovery codes are individually encrypted with AES-256-GCM (`ENC1:` prefix + 96-bit random nonce + 128-bit authentication tag + authenticated associated data) under the active 256-bit DEK before being written to SQLCipher.
- **Argon2id key derivation:** Argon2id derives key-encryption keys from the Master Password and from `.dao-backup` Export Lock Keys (19 MiB memory, 2 iterations, parallelism 1).
- **Android Keystore & key envelopes:** The 256-bit DEK is wrapped via Android Keystore when App Lock is off, or via Master Password, Biometric Keystore, and Recovery Key envelopes when App Lock is enabled.
- **Explicit Password Manager verification:** General app unlock does not pre-authorize hidden vault fields. Revealing or copying a hidden password, backup code, or protected note requires verification via Device Lock/Biometrics or Master Password.
- **Zero-leak edit forms:** Editing an account or service entry never loads existing passwords or backup codes into editable text inputs.
- **Context-aware `FLAG_SECURE`:** Blocks screenshots and screen recording on lock and credential screens by default, or across the entire app when enabled in Settings.
- **Platform backup exclusions:** Android Auto Backup and cloud device-transfer rules exclude app-private database and key-envelope files.

See [SECURITY.md](SECURITY.md) for full security details, caveats, and vulnerability reporting instructions.

## Network and privacy

Digital Account Organizer operates offline for all account, password, backup-code, and backup-archive operations. The manifest declares `INTERNET` and `ACCESS_NETWORK_STATE` solely for an explicit, user-triggered HTTPS service-logo fetch when creating or editing a service.

No cloud backend, account-provider OAuth/sign-in, telemetry SDK, or advertising SDK is included. See [PRIVACY.md](PRIVACY.md) for the complete privacy policy and data inventory.

## Backup, cross-device migration, and export

The Backup screen is designed for reliable backup and cross-device migration:

- **Portable Encrypted `.dao-backup`:**
  - Protected with an app-generated Export Lock Key (`DAO-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`) using Argon2id and AES-256-GCM.
  - Device-independent: any Android device running Digital Account Organizer can restore the `.dao-backup` file as long as you enter its Export Lock Key.
  - Preserves standalone accounts (including passwords, backup codes, connected phone numbers, date of birth, and avatar colors), service accounts, services, categories, custom service icons, and preferences.
- **Multi-Path Restore & Storage Browser:**
  - **Built-In Backup Vault & Downloads Scanner:** Automatically lists `.dao-backup` files saved in the app's Backup Vault or discovered in public `Downloads` and `Documents` folders.
  - **In-App Device Storage Folder Browser:** Tap **"Browse Device Storage..."** to navigate device folders (`Downloads`, `Documents`, `Internal Storage`, parent/child directories) directly inside the app without crashing on devices lacking a system DocumentsUI activity, with a **"System File Chooser"** button (`ACTION_OPEN_DOCUMENT` $\rightarrow$ `ACTION_GET_CONTENT`) and external **"Open With / Share"** intent support.
- **Staged Conflict Resolution:** Previews incoming counts and lets you resolve record conflicts (`Skip`, `Replace`, `Keep Both`) before committing inside a single database transaction.
- **Plaintext CSV Export:** Exports non-secret account and service metadata (including connected phone numbers and date of birth) while strictly excluding all passwords, backup codes, and security keys.

## Technology stack

| Layer | Implementation |
| --- | --- |
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose, Material 3, Adaptive Custom Typography (`Inter`, `Plus Jakarta Sans`, `JetBrains Mono`) |
| Persistence | Room 2.8.4 (`version = 3`) |
| Database encryption | SQLCipher for Android (`net.zetetic:sqlcipher-android`) |
| Field & backup encryption | AES-256-GCM |
| Password-based key derivation | Argon2id (Bouncy Castle `bcprov-jdk18on`) |
| Device key protection | Android Keystore |
| Biometric & device auth | AndroidX Biometric (`BIOMETRIC_STRONG`, `DEVICE_CREDENTIAL`) |
| Async work | Kotlin Coroutines and StateFlow |
| Tests | JUnit 4, AndroidX Test, Robolectric |
| Build | Gradle Kotlin DSL (`compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`) |

## Build from source

### Build a debug APK

```bash
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Build a signed release APK

```bash
export KEYSTORE_PATH="$PWD/my-upload-key.jks"
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"

gradle :app:assembleRelease
```

## Run tests

```bash
gradle :app:testDebugUnitTest
```

Runs the local JVM and Robolectric test suite verifying standalone account persistence, connected phone numbers, date of birth and age calculation, single-letter avatar monogram logic, encrypted password and backup-code vault operations, storage folder browsing, and `.dao-backup` export/restore round-trips.

## Project layout

```text
.
├── app/
│   ├── src/main/java/com/example/
│   │   ├── app/                 # Application container and OrganizerViewModel
│   │   ├── core/database/       # Room v3 entities, DAOs, migrations, repository
│   │   ├── core/designsystem/   # Full-screen scaffolds, single-letter avatars, vault cards
│   │   ├── core/files/          # Local assets, BackupVaultStorage, storage browser, logo fetcher
│   │   ├── core/security/       # DatabaseKeyManager, Argon2id, AES-256-GCM envelopes, recovery
│   │   ├── domain/model/        # Domain models, avatar colors, DOB age calculator, backup code parser
│   │   ├── feature/             # Accounts, services, categories, dashboard, backup, security, settings
│   │   └── ui/theme/            # Material 3 color palettes and bundled typography
│   ├── src/test/                # JVM and Robolectric unit test suite
│   └── src/androidTest/         # Instrumentation smoke tests
├── .github/workflows/           # GitHub Actions APK CI and release workflows
├── PRIVACY.md                   # Privacy Policy & data inventory
├── README.md                    # Project documentation
├── SECURITY.md                  # Security architecture & vulnerability reporting policy
└── metadata.json                # Platform metadata
```

## CI and release workflows

| Workflow | Trigger | Behavior |
| --- | --- | --- |
| `android-release.yml` | Push to `main`/`master`, or manual dispatch | Runs unit/Robolectric tests, builds and signs the release APK, verifies its signature and manifest permissions, and uploads a 30-day Actions artifact. |
| `release-apk.yml` | Push a `v*` tag, or manual dispatch | Builds and signs the release APK, verifies its signature, calculates a SHA-256 checksum, and publishes a GitHub Release with the signed APK asset. |

## Security reporting

Do not publish exploitable vulnerability details in a public issue. See [SECURITY.md](SECURITY.md) for private reporting instructions.
