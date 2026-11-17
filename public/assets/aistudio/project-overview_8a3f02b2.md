# Digital Account Organizer

## Overview

Digital Account Organizer is a personal Android application for recording and organizing information about multiple digital accounts across user-created services. It provides a local place to remember which accounts a person has, what each account is used for, which service it belongs to, and how the records are categorized. Users create every service manually and may attach a custom logo or icon. The application stores its data on the device, supports manual export and import, offers light and dark themes, and includes optional application-level access protection. It is not a password manager, account client, authentication tool, or service-permission manager. It must not connect to external account providers or access account data from Android or Google.

## Goals

1. Let a user create, view, edit, archive, restore, and delete multiple account records without connecting to the services represented by those records.
2. Let users define their own services and give each service a custom visual identity without relying on a bundled provider catalogue.
3. Let users organize records through both service membership and user-managed categories, with search and filtering for practical retrieval.
4. Keep application data local and usable without an internet connection; do not silently synchronize or upload private organizer data.
5. Provide dependable manual export and import so a user can move or restore their organizer data, including custom service imagery.
6. Protect local data at rest and offer optional app-specific access protection without storing service passwords.
7. Provide a clean Android interface with both light and dark themes and consistent behavior across common phone sizes.

## Goals and non-goals in measurable terms

- A user can create at least two records under one custom service; no one-service/one-account constraint is allowed.
- A user can create, rename, edit, archive, and manage a custom service and its logo/icon.
- A user can create categories, assign accounts to categories, and find accounts through service/category filters and text search.
- All core account and organization flows work in airplane mode after the app is installed.
- A manual backup can be exported, validated, and imported on another compatible installation without requiring the source device's Android Keystore key.
- The final merged Android manifest contains no `INTERNET` permission, and no analytics/advertising/remote SDK transmits organizer data.
- The optional lock can be enabled and disabled through a secure, documented flow; an enabled lock protects access to the encrypted local database.

## Core User Flow

1. The user installs and opens the application. The app presents a short explanation that it is an offline organizer and does not connect to the services whose accounts are recorded.
2. The user enters the organizer. The user may optionally enable application protection. If enabled, the user creates an app-specific master password and is guided through recovery setup before relying on the lock.
3. The user creates a service manually by entering its name and optional details, then chooses or imports a logo/icon from a user-selected local image.
4. The user adds one or more account records under that service. Each record may include an account label, email, username, purpose, category, recovery contact references, website, notes, and status. No service password is requested or stored.
5. The user browses the dashboard, all accounts, a service's accounts, or a category's accounts. They can search, filter, open details, edit records, archive records, or delete them.
6. The user exports all or selected organizer data. Structured backups include services, categories, account records, relevant preferences, and custom service image assets. The recommended backup format is encrypted and portable between devices.
7. On a new installation or another device, the user imports a compatible backup, reviews the preview and conflicts, selects how to resolve duplicates, and confirms the import.
8. The app persists the imported records locally. It does not contact external providers or automatically upload the restored data.

## Features

### Account Management

- Create, view, edit, archive, restore, and delete account records.
- Support multiple account records for the same service.
- Allow optional account information fields; only essential fields should be mandatory.
- Keep service passwords, API keys, access tokens, one-time passwords, and authentication secrets out of the data model and UI.
- Support account status and last-updated metadata.
- Provide account detail screens and clear destructive-action confirmation.

### Custom Service Management

- Do not ship a predefined catalogue of external services as the primary service creation mechanism.
- Require the user to create services manually.
- Store a service name and optional descriptive details and website.
- Allow a user-selected custom logo or icon for each service.
- Support creating, viewing, editing, archiving, and deleting services.
- A service may be linked to many account records.
- Preserve service identity and branding through backup and restore.

### Categories and Organization

- Let users create, rename, and delete custom categories.
- Let users organize accounts by service and category.
- Provide a service directory and category directory as separate ways to browse.
- Provide account search and filters for service, category, status, and archive state.
- The first-release data model may use one optional category per account as a simple baseline; multi-category tagging is a later extension unless explicitly approved.

### Dashboard and Discovery

- Show useful account and service counts.
- Show category summaries and recently added or updated accounts.
- Provide quick actions to add an account or create a service.
- Provide clear empty states and shortcuts into accounts, services, categories, and backup/restore.
- Dashboard statistics are derived from local records; no usage telemetry is required.

### Backup and Restore

- Manually export all organizer data or a user-selected subset.
- Use a versioned JSON-based structured backup as the recommended primary format.
- Include custom service image assets in structured backups.
- Offer CSV as an optional tabular export for spreadsheet use; warn that CSV is not a complete restorable backup and does not carry image assets or relationships reliably.
- Validate, preview, and import backups.
- Detect ID conflicts and likely duplicates; never silently overwrite records.
- Allow explicit conflict choices such as skip, keep both, or replace after preview.
- Use the Android Storage Access Framework for user-directed file selection and destinations.

### Application Protection and Privacy

- Make application-level lock optional and user-configurable.
- Use the app-specific master password as the primary unlock method in the recommended design.
- Offer biometric unlock as an optional convenience backed by Android Keystore and `BiometricPrompt` where strong biometrics are available.
- Offer a recovery method and explain that forgotten credentials cannot be reset by a server.
- Encrypt the local database even when the optional UI lock is disabled.
- Exclude private app files, database files, preferences containing security state, and keys from Android cloud/device backup.
- Do not request internet access or integrate analytics, ads, crash-upload SDKs, or external authentication.

### Appearance and Settings

- Provide light and dark themes.
- Offer follow-system theme as a recommended convenience option.
- Include appearance, app-lock, backup/data-management, privacy, and about settings.
- Use a modern, minimal, professional Android design with clear hierarchy and consistent components.

## Scope

### In Scope

- Native Android application for a single local organizer workspace.
- Local account, service, category, preference, and service-image records.
- User-created services only; no predefined provider directory dependency.
- Custom service logo/icon selection and private local storage.
- Multiple accounts per service.
- Account CRUD, service CRUD, category CRUD, archive/restore, search, and filtering.
- Dashboard summaries and recent records.
- Light and dark themes.
- Optional app-specific lock with master password, optional strong-biometric convenience unlock, recovery procedure, and automatic-lock behavior.
- Encrypted database at rest.
- Manual encrypted structured backup and restore.
- Optional CSV export with appropriate warnings.
- Local-only operation and privacy-preserving Android configuration.
- Accessibility, validation, error states, migration tests, and security verification.

### Out of Scope

- Password manager or storage of passwords for external services.
- Sign-in to, synchronization with, or integration APIs for Google, Microsoft, GitHub, social networks, gaming services, or other providers.
- Reading accounts or credentials from Android AccountManager, Google Play Services, device settings, browsers, or third-party apps.
- Changing external account passwords, recovery methods, permissions, subscriptions, or security settings.
- Cloud accounts, user registration, multi-device sync, hosted databases, or remote server components.
- Automatic cloud backup or automatic transmission of data to any provider.
- Account recovery operations against external services.
- Password sharing, team workspaces, collaboration, or multi-user organization workspaces.
- Password vault features, TOTP generation, passkey storage, API secret storage, or credential autofill.
- Mandatory biometric access; biometric unlock is optional and device-dependent.
- A bundled predefined catalogue as the service source of truth.

## Success Criteria

1. The app starts and supports core record management without an internet connection.
2. A user can create a custom service, attach a custom logo/icon, and create multiple accounts associated with it.
3. A user can create categories and browse/filter accounts by service and category.
4. Account creation and editing never ask for or persist a service password or authentication secret.
5. The dashboard, account list, service list, category list, and details screens remain understandable in light and dark modes.
6. Account/service/category operations preserve referential integrity and survive application restarts.
7. The app's private database is encrypted at rest, and optional app protection gates access to the database key.
8. The final merged manifest does not request internet access; automated inspection confirms no SDK or code path sends organizer data externally.
9. Android cloud backup and device-transfer rules exclude sensitive organizer storage; only user-initiated export creates a portable backup.
10. A full encrypted backup restores services, accounts, categories, preferences that are intended to be portable, and service image assets on a different compatible device.
11. Import previews conflicts and never silently overwrites existing data.
12. Failure cases (wrong master password, invalid backup, missing image, interrupted import, database migration failure) are handled without silent data loss.
13. Tests cover relationships, migrations, backup compatibility, encryption/unlock flows, and the principal user journeys.
