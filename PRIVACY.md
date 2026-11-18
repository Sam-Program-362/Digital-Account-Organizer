# Privacy Policy

**Last updated: 18 November 2026**

This policy describes the data handling visible in the Digital Account Organizer source snapshot supplied with the repository documentation update. It is not a jurisdiction-specific legal opinion. The repository does not identify a registered company or a separate privacy contact; the project maintainer can be contacted through the repository's GitHub profile.

## 1. What the application is

Digital Account Organizer is an Android application for keeping records about personal/work accounts and accounts associated with user-created services. It does not sign in to the external services represented by those records, retrieve account data from Google or other providers, or administer those external accounts.

## 2. Information stored on the device

Information entered by the user may include:

- account display names, email addresses, and usernames;
- optional account passwords;
- account purpose, notes, status, and archive state;
- recovery email and phone references;
- service names, website URLs, descriptions, and service-account records;
- categories and account-to-service/category relationships;
- custom service icons/images;
- application appearance preferences; and
- app-lock, key-envelope, biometric, and recovery configuration data.

The app does not need the password for a service merely to record that the user has an account there. Password entry is optional.

## 3. Where information is stored

The application stores its primary records in its Android application data area. Room is configured to use SQLCipher for the local database. Saved account password values are additionally encrypted with AES-256-GCM before database storage. The app uses Android Keystore and/or password-derived key envelopes for database-key protection, depending on the selected lock configuration.

**Runtime caveat:** the current database factory silently falls back to a regular Room database if the SQLCipher native library cannot be loaded. This means the configured SQLCipher protection is not guaranteed in that failure path. The project should change this behavior to fail closed before claiming that every runtime always encrypts the full database. See [SECURITY.md](SECURITY.md).

## 4. Network access

The Android manifest declares `INTERNET` and `ACCESS_NETWORK_STATE` for an explicit user-triggered feature: fetching a service logo from a website.

When a user requests a logo fetch, the app may:

1. send an HTTPS GET request to the website URL entered by the user;
2. inspect the returned page for icon links; and
3. send additional HTTPS GET requests to candidate icon URLs on that site, subject to the implementation's redirect and response-size limits.

The remote website may receive the requested URL, the app's user-agent string, the device's public network address, and ordinary connection metadata. The logo-fetch code does not intentionally send the user's account records, saved passwords, notes, recovery references, or local database to that website.

The current source uses HTTPS only for this feature, rejects local/internal hosts and private address ranges, caps redirects, limits response sizes, and stores accepted images locally. If the device is offline, the user can choose an image from local storage instead.

No application-owned backend, cloud synchronization, provider sign-in, advertising integration, or analytics/telemetry SDK was identified in the active application source and dependency set reviewed for this policy. This statement describes the supplied source snapshot and should be rechecked for every later release.

## 5. Backups and exports

The application offers several user-initiated export paths:

### Encrypted `.dao-backup`

The backup payload is protected using Argon2id and AES-256-GCM with a dedicated backup passphrase. It is intended for manual transfer or restoration. The app supports full and selective export modes and stages incoming data for preview/conflict resolution before commit.

Keep the passphrase separate from the backup file. Anyone who obtains both may be able to restore the data. If the passphrase is lost, the project does not document a server-side recovery path.

### CSV export

CSV is an unencrypted plaintext format. It intentionally excludes saved passwords and security metadata, but can contain account names, email addresses, usernames, service/category information, notes, purposes, recovery-contact references, account status, and timestamps. Anyone who can read the CSV can read those values.

### Recovery-code export

The app can export a recovery code as a plain-text `.txt` file. This is a secret that can help regain access to the encrypted organizer. Protect it like a credential and delete unnecessary copies.

### Storage locations

The app can save exports in an app-internal backup vault and, where available, in the device's Downloads folder. Files copied to Downloads or another shared location are outside the app-private database boundary. Removing the app does not necessarily remove exported copies from shared storage.

## 6. Android automatic backup and device transfer

The manifest disables Android automatic backup, and the checked-in legacy and Android 12+ extraction rules exclude app-private database, file, preference, external, and root storage domains from cloud backup and device-to-device transfer.

These exclusions do not apply to files that the user explicitly exports to Downloads or copies elsewhere.

## 7. Data sharing and third parties

The supplied application source does not show account records being sent to an application-operated server or shared with advertising/analytics providers. The only intentional network feature identified is the user-triggered HTTPS logo fetch described above. The website contacted by that request is an independent third party and applies its own privacy practices.

Android, the device manufacturer, and biometric/security components operate under their own platform policies. This project does not control their device-level data handling.

## 8. Retention and deletion

Primary application records remain in the app's local storage until the user deletes them, clears the app's storage, or uninstalls the app. The application supports deletion of records and a destructive security reset. Users should export a backup before deleting or reinstalling if they need to preserve their information.

Exported files stored outside the app's private area may remain after app data is cleared or the app is uninstalled. Users must locate and delete those copies separately.

The project does not operate a server-side account system, so there is no server profile or cloud database in this source snapshot for the user to request deletion from.

## 9. Permissions

The application manifest declares:

- `android.permission.INTERNET` — used by the explicit HTTPS website-logo fetch flow;
- `android.permission.ACCESS_NETWORK_STATE` — used to determine whether a network connection is available for that flow.

The source uses Android's Photo Picker and Storage Access Framework for user-selected files rather than requesting broad media/storage permissions in the manifest.

## 10. Children

The supplied source does not contain a child-specific account system or age gate. The project does not make a separate child-directed privacy claim.

## 11. Changes to this policy

This policy should be updated when the application adds a new permission, network request, analytics/telemetry component, cloud service, data field, export type, or storage behavior. The date at the top should reflect the actual revision date.

## 12. Contact

For privacy questions, contact the project maintainer through the repository's GitHub profile. Do not send passwords, recovery keys, keystore files, or live personal account exports in a public issue.
