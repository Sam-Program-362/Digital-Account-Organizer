# Decision Log

This log distinguishes user-confirmed product decisions from recommended technical baselines and unresolved choices. A newer explicit user decision overrides an older proposal. Update all affected documents when a decision changes.

## Confirmed by the Project Owner

| ID | Decision | Consequence |
|---|---|---|
| D-001 | The product is an Android application for organizing digital accounts. | Build a native Android-focused product; do not design a web/cloud product. |
| D-002 | Information is saved on the device, not on external services or the services represented by the account records. | Use local storage; do not integrate with provider APIs. |
| D-003 | The app is not for complete service usage, account permissions, or service administration. | Records are informational only; no external account operations. |
| D-004 | The app supports multiple accounts, not a single record per service. | One service has a one-to-many relationship with account records. |
| D-005 | The app needs export and import. | Manual portable data backup is core scope. |
| D-006 | The chosen product direction is an account organizer without password storage. | No password-vault fields, credential autofill, token store, or TOTP features. |
| D-007 | Light and dark visual directions are both required. | Implement both themes. |
| D-008 | Accounts are organized by both services and categories. | Services and categories are separate first-class browsing/filtering dimensions. |
| D-009 | Services are completely custom and manually entered; no predefined service catalogue. | User action is the source of every service record. |
| D-010 | Custom services support custom logo/icon. | Store service artwork and preserve it in structured backups. |
| D-011 | The application may have optional in-app password/lock protection to prevent other device users from opening it. | Lock is opt-in; the app remains usable without enabling it. |
| D-012 | The project owner expects the assistant to recommend the lock mechanism. | Use the recommended design in `security-and-privacy.md`; do not leave the mechanism wholly unspecified. |
| D-013 | Implement the complete end-to-end app across all milestones in the Android build workspace. | Deliver Milestones 0–8 in the Android codebase. |
| D-014 | Background auto-lock timeout is configurable (`Immediate`, `1m`, `5m`) with `Immediate` as default. | `DatabaseKeyManager` and `ProcessLifecycleOwner` enforce the selected timeout. |
| D-015 | Visual palette uses the Deep Slate & Emerald Trust design system for Light and Dark themes. | Implemented in `Color.kt`, `Type.kt`, and `Theme.kt`. |
| D-016 | One optional category per account (MVP baseline `R-015` applied). | `AccountEntity.categoryId` links to `CategoryEntity.id` with `SET_NULL` on delete. |

## Recommended Technical Baseline (Implemented)

| ID | Recommendation | Status |
|---|---|---|
| R-001 | Kotlin for Android application code. | Implemented. |
| R-002 | Jetpack Compose and Material 3 for UI. | Implemented with Deep Slate & Emerald Trust tokens. |
| R-003 | Room for structured local data. | Implemented (`OrganizerDatabase`). |
| R-004 | SQLCipher for Android (`net.zetetic:sqlcipher-android`) as Room's encrypted database layer. | Implemented (`EncryptedDatabaseFactory`). |
| R-005 | Random 256-bit DEK, separate from user password. | Implemented (`DatabaseKeyManager`). |
| R-006 | Argon2id-derived KEK plus AES-256-GCM master-password envelope. | Implemented (`Argon2idKeyDeriver`, `AesGcmEnvelopeCipher`). |
| R-007 | Android Keystore device envelope when lock is disabled. | Implemented (`AndroidKeystoreKeyWrapper`). |
| R-008 | Optional strong-biometric Keystore envelope using BiometricPrompt/CryptoObject. | Implemented (`MainActivity`, `DatabaseKeyManager`). |
| R-009 | Separate 256-bit Base32 recovery key with SHA-256 checksum. | Implemented (`RecoveryKeyManager`). |
| R-010 | Lock when app leaves foreground as default, with configurable 1m/5m grace options. | Implemented (`AutoLockTimeout`). |
| R-011 | Encrypted versioned `.dao-backup` with a dedicated backup passphrase. | Implemented (`BackupEngine`). |
| R-012 | CSV export as optional plaintext convenience export with explicit warning. | Implemented (`BackupEngine`, `BackupScreen`). |
| R-013 | Storage Access Framework / Photo Picker for file selection. | Implemented (`PickVisualMedia`, `CreateDocument`, `OpenDocument`). |
| R-014 | Exclude all private organizer data from Android Auto Backup/device transfer. | Implemented (`backup_rules.xml`, `data_extraction_rules.xml`, `AndroidManifest.xml`). |
| R-015 | One optional category per account in the MVP. | Implemented. |
| R-016 | Follow-system theme as an additional option alongside Light and Dark. | Implemented (`ThemePreference`). |
