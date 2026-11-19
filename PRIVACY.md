# Privacy Policy

**Last updated: 18 November 2026 (`v3.12.1-release`)**

This policy describes the data handling in the Digital Account Organizer Android application (`v3.12.1-release`, `versionCode` `301201`). It is not a jurisdiction-specific legal opinion. The repository does not identify a registered company or a separate privacy contact; the project maintainer can be contacted through the repository's GitHub profile.

## 1. What the application is

Digital Account Organizer is a local-first Android application for keeping organized records about standalone personal/work accounts and accounts associated with user-created services. It does not sign in to the external services represented by those records, retrieve account data from Google or other providers, or administer those external accounts.

## 2. Information stored on the device

Information entered by the user and stored locally on the device may include:

- standalone account display names, email addresses, and usernames;
- multiple connected account phone numbers (stored separately from the single recovery phone reference);
- optional account Date of Birth (`YYYY-MM-DD`, used locally on-device to calculate and display current age);
- optional account and service-account passwords (encrypted at rest with AES-256-GCM);
- optional standalone account backup / recovery codes (encrypted at rest with AES-256-GCM);
- account purpose, protected notes, status (`Active`, `Inactive`, `Closed`), and archive state;
- single-letter avatar profile color personalization (`avatarColorHex`);
- recovery email/account references and recovery phone number references;
- service names, website URLs, descriptions, archive state, and service-account records;
- categories and account-to-service/category relationships;
- custom service icons/images imported by the user or fetched on demand from a service website URL;
- application appearance preferences (theme mode and color palette) and screenshot-restriction preference (`restrict_screenshots_entire_app`); and
- app-lock, key-envelope, biometric, and encrypted recovery-code configuration data.

Password and backup-code entry are completely optional; the app does not require a password or recovery code to catalog an account or service.

## 3. Where information is stored

The application stores its primary records in its private Android application data area. Room (`version = 3`) is configured to use SQLCipher for the local database. Saved account passwords, service-account passwords, and standalone account backup/recovery codes are additionally encrypted at the field level with AES-256-GCM (`ENC1:` payload with authenticated associated data) under the active 256-bit Data Encryption Key (DEK) before database storage. The app uses Android Keystore and/or Argon2id password-derived key envelopes for database-key protection, depending on the selected lock configuration.

**Runtime caveat:** the current database factory falls back to a standard Room database if the SQLCipher native library cannot be loaded (used to support host JVM/Robolectric testing where Android `.so` binaries are absent). See [SECURITY.md](SECURITY.md) for details.

## 4. Network access

The Android manifest declares `INTERNET` and `ACCESS_NETWORK_STATE` for a single explicit user-triggered feature: fetching a service logo from a website URL when creating or editing a service.

When a user requests a logo fetch, the app may:

1. send an HTTPS GET request to the website URL entered by the user;
2. inspect the returned HTML page for icon links; and
3. send additional HTTPS GET requests to candidate icon URLs on that site, subject to redirect and response-size limits.

The remote website may receive the requested URL, the app's user-agent string, the device's public network address, and ordinary connection metadata. The logo-fetch code never sends the user's account records, saved passwords, backup codes, connected phone numbers, date of birth, notes, recovery references, or local database to that website.

The implementation enforces HTTPS only, rejects localhost/loopback and private network ranges, caps redirects, limits response sizes, and stores accepted images locally. If the device is offline, the user can select an image from local storage instead.

No application-owned backend, cloud synchronization, provider sign-in, advertising integration, or analytics/telemetry SDK is present in the application source or active dependencies.

## 5. Backups, cross-device migration, and exports

The application offers three user-initiated export paths:

### Encrypted `.dao-backup`

The portable `.dao-backup` payload is encrypted using Argon2id and AES-256-GCM with an app-generated Export Lock Key (`DAO-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`). It is designed for manual backup and cross-device migration. The archive includes standalone accounts (including passwords, account backup codes, connected phone numbers, date of birth, and avatar color preferences), service accounts, services, categories, custom service icons, and appearance preferences. Incoming archives are staged for preview and conflict resolution before commit.

Keep the Export Lock Key separate from the `.dao-backup` file. Anyone who obtains both the file and its Export Lock Key can restore the backup. If the Export Lock Key is lost, there is no server-side recovery path.

### CSV export

CSV is an unencrypted plaintext format intended for spreadsheet inspection. It intentionally excludes saved passwords, saved account backup/recovery codes, and security metadata, but includes account labels, email addresses, usernames, connected phone numbers, date of birth, service/category names, notes, purposes, recovery-contact references, account status, and timestamps. Anyone who can read the CSV file can read those values.

### Recovery-code export

When App Lock is enabled, the app can export its 24-character Master Password recovery code as a one-time plain-text `.txt` file. This is a secret that can reset a forgotten Master Password. Protect it like a credential and delete unnecessary copies.

### Storage locations and import paths

The app saves exports in its built-in app-private Backup Vault and, where available, to the device's `Downloads` folder (`MediaStore.Downloads` on Android 10+). Files saved to `Downloads` or another shared folder are outside the app-private database boundary and are not automatically removed when the app is uninstalled.

To support restoring `.dao-backup` files transferred from another device, the app provides:
- a Built-In Backup Vault & Downloads/Documents scanner;
- an in-app Device Storage Folder Browser (`Browse Device Storage`);
- a system file chooser (`ACTION_OPEN_DOCUMENT` / `ACTION_GET_CONTENT`); and
- `ACTION_VIEW` / `ACTION_SEND` intent support for opening `.dao-backup` files directly from a file manager or share sheet.

## 6. Screenshot and screen-recording protection (`FLAG_SECURE`)

By default, the application enables Android's `FLAG_SECURE` window protection whenever the organizer is locked or whenever the user is viewing or editing credentials (standalone account details, service entry details, password/backup-code editors, and security/unlock flows). Users can also enable **"Restrict Screenshots Across Entire App"** in **Settings → Security** to block screenshots and screen recording on every screen.

## 7. Android automatic backup and device transfer

The manifest sets `android:allowBackup="false"`, and the XML backup and data-extraction rules (`backup_rules.xml` and `data_extraction_rules.xml`) exclude app-private database, file, preference, external, and root storage domains from automatic cloud backup and device-to-device transfer.

These exclusions do not apply to files that the user explicitly exports to `Downloads` or copies elsewhere.

## 8. Data sharing and third parties

Account records, passwords, and backup codes are never sent to an application-operated server or shared with advertising or analytics providers. The only network feature is the user-triggered HTTPS service-logo fetch described in Section 4. The website contacted by that request is an independent third party governed by its own privacy practices.

## 9. Retention and deletion

Primary application records remain in the app's local encrypted storage until the user deletes them, triggers a destructive workspace reset, clears the app's storage, or uninstalls the app.

Exported files stored in shared folders such as `Downloads` remain after app data is cleared or the app is uninstalled; users must delete those copies separately if no longer needed.

## 10. Permissions

The application manifest declares:

- `android.permission.INTERNET` — used solely by the explicit HTTPS website-logo fetch flow;
- `android.permission.ACCESS_NETWORK_STATE` — used to check network availability before fetching a website logo.

## 11. Children

The application does not operate a server-side account system or target children. The optional Date of Birth field on standalone account records is a user-managed organizational field used solely to display calculated age locally on the device.

## 12. Changes to this policy

This policy should be updated whenever the application adds a new permission, network request, data field, export format, or storage behavior.

## 13. Contact

For privacy questions, contact the project maintainer through the repository's GitHub profile. Do not send passwords, recovery keys, Export Lock Keys, keystore files, or personal account exports in a public issue.
