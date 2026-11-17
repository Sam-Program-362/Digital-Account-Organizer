# Data Model

## Purpose and Rules

This document describes the proposed local data model. It is independent from the external service accounts themselves: records describe accounts but never authenticate to or synchronize with those services.

- Use stable UUID identifiers for services, accounts, and categories.
- Keep the original display values supplied by the user; use separate normalized comparison values where needed.
- Database content is persisted in the SQLCipher-encrypted local database.
- Never add fields for external service passwords, API keys, OAuth tokens, recovery codes, passkeys, or TOTP seeds.
- Keep cryptographic key bytes out of ordinary domain entities and Room rows.
- Use explicit schema migrations; never wipe existing user data as a migration shortcut.

## Entity: Service

Represents one user-created external service or platform. There is no mandatory predefined provider list.

| Field | Type (conceptual) | Required | Notes |
|---|---|---:|---|
| `id` | UUID | Yes | Stable primary key. |
| `name` | String | Yes | User-entered service name; trim outer whitespace and reject blank. |
| `normalizedName` | String | Yes | Search/duplicate comparison value; not used as display text. |
| `websiteUrl` | String? | No | Optional service homepage/login page URL; validate syntax only, do not fetch it. |
| `description` | String? | No | User-entered service note. |
| `iconAssetId` | UUID? | No | Reference to a private local service image asset. |
| `createdAt` | Instant | Yes | Creation timestamp. |
| `updatedAt` | Instant | Yes | Last modified timestamp. |
| `archivedAt` | Instant? | No | Optional service archive timestamp. |

### Service rules

- A service is created only by an explicit user action.
- Service names should be unique after normalization within the organizer by default; when a duplicate name is detected, explain it and let the user choose whether to rename or keep a visually distinguishable variation. Do not create duplicate services silently.
- A service may have zero, one, or many accounts.
- Archiving a service must not delete associated accounts.
- Deleting a service with associated accounts must be blocked or require an explicit user decision to move/delete those accounts. Never cascade-delete them silently.
- Changing a service name or icon must not alter account identifiers or associations.

## Entity: Account

Represents one record about an account the user has with a service.

| Field | Type (conceptual) | Required | Notes |
|---|---|---:|---|
| `id` | UUID | Yes | Stable primary key. |
| `serviceId` | UUID / FK | Yes | Links to one user-created service. |
| `displayName` | String | Yes | User label such as Personal, Work, or Secondary; must not be assumed to be unique globally. |
| `email` | String? | No | Account email/identifier, preserved as entered. |
| `username` | String? | No | Optional username or account identifier. |
| `categoryId` | UUID? / FK | No | MVP recommendation: one optional category per account. Multi-category tags are a later extension. |
| `purpose` | String? | No | Why the user maintains the account. |
| `recoveryEmailReference` | String? | No | Optional recovery contact reference; it does not trigger recovery operations. |
| `recoveryPhoneReference` | String? | No | Optional recovery phone reference; it does not grant phone access. |
| `websiteUrl` | String? | No | Optional account-specific URL; never auto-open or fetch without user action. |
| `notes` | String? | No | Free-form text. Never treat this as a password field. |
| `status` | AccountStatus | Yes | Proposed values: ACTIVE, INACTIVE, CLOSED. |
| `createdAt` | Instant | Yes | Creation timestamp. |
| `updatedAt` | Instant | Yes | Last modification timestamp. |
| `archivedAt` | Instant? | No | Separate from status; allows hiding without deleting. |

### Account rules

- An account must reference an existing service.
- One service may have multiple accounts.
- The account display name is required; email and username are optional so accounts with non-email identifiers can be recorded.
- Do not force email validation on username or other account identifier fields.
- Do not add password, access-token, API-key, OTP, or passkey fields.
- Account records remain local. Do not synchronize them with services or Android AccountManager.
- Archive is reversible; permanent deletion requires clear confirmation.
- Updating an account updates `updatedAt`.

## Entity: Category

Represents a user-defined organizational grouping.

| Field | Type (conceptual) | Required | Notes |
|---|---|---:|---|
| `id` | UUID | Yes | Stable primary key. |
| `name` | String | Yes | User-entered category name. |
| `normalizedName` | String | Yes | Comparison/search key. |
| `description` | String? | No | Optional explanation. |
| `createdAt` | Instant | Yes | Creation timestamp. |
| `updatedAt` | Instant | Yes | Last modified timestamp. |

### Category rules

- Categories are created and managed by the user.
- Suggested category examples may be shown as optional suggestions, but do not require a fixed catalogue.
- The MVP baseline associates an account with zero or one category. Confirm multi-category assignment before changing the schema to many-to-many.
- Deleting a category must not delete its accounts; clear the optional category reference or request reassignment.
- Category names should be unique after normalization, with clear duplicate feedback.

## Entity: ServiceAsset

Represents a user-selected custom logo/icon stored in app-private files.

| Field | Type (conceptual) | Required | Notes |
|---|---|---:|---|
| `id` | UUID | Yes | Generated internal asset ID. |
| `relativePath` | String | Yes | App-private relative path; never an arbitrary imported external path. |
| `mimeType` | String | Yes | Detected after decoding/validation. |
| `width` | Integer | Yes | Normalized image width. |
| `height` | Integer | Yes | Normalized image height. |
| `byteSize` | Long | Yes | Stored size. |
| `sha256` | String | Yes | Integrity/deduplication value for the normalized bytes. |
| `createdAt` | Instant | Yes | Asset creation timestamp. |

### Asset rules

- Assets are selected explicitly by the user through Android Photo Picker or Storage Access Framework.
- Validate image bytes and decode before storing.
- Re-encode accepted images into a supported raster format; remove metadata not needed by the application.
- Generate internal filenames; never trust imported paths.
- Keep images in app-private storage and exclude them from Android automatic backup.
- Delete an asset only when no service references it and cleanup can be performed safely.
- Backup payloads include only assets referenced by exported services.

## Entity: AppPreference

Stores non-secret app preferences such as theme selection and optional display settings.

| Field | Type (conceptual) | Required | Notes |
|---|---|---:|---|
| `key` | String | Yes | Stable preference key. |
| `value` | String | Yes | Non-secret preference value. |
| `updatedAt` | Instant | Yes | Last modified time. |

Do not store master passwords, raw DEKs, KEKs, recovery keys, biometric templates, or backup passwords in this entity. Security envelope metadata should be managed by the security subsystem and stored separately from ordinary app preferences.

## Security Metadata (Separate Subsystem)

The key manager may persist the following non-secret metadata in private app storage:

- Envelope identifier and type (device, master-password, biometric, recovery).
- KDF algorithm/version and parameters.
- Random salt and nonce.
- Ciphertext key envelope and authentication tag.
- Key version and envelope creation timestamp.
- Keystore alias reference (not the key material itself).

The metadata must not include plaintext passwords or raw encryption keys. Prefer a small versioned binary or structured metadata store with strict validation. The DB key must be available to SQLCipher only after an authenticated envelope is opened.

## Relationships

```text
Service 1 -------- * Account
Category 1 ------- * Account    (MVP: optional single category per account)
Service 0/1 ------ 1 ServiceAsset (zero or one active custom icon per service)
```

- Deleting/archiving service or category must not unexpectedly delete associated account data.
- Foreign keys should enforce integrity.
- Backup export must include only related service/category/assets required by selected account records, plus any explicitly selected standalone services/categories.

## Indexes and Queries

Recommended indexes:

- `Account.serviceId`
- `Account.categoryId`
- `Account.status`
- `Account.archivedAt`
- `Account.updatedAt`
- `Service.normalizedName`
- `Category.normalizedName`

Search may use indexed normalized values for service name, display name, email, and username. Do not create a remote search index. Avoid storing unnecessary duplicate plaintext search indexes outside the encrypted database.

## Import/Export Identity Rules

- Preserve UUIDs during backup restore when no conflicting local record exists.
- Do not rely only on email address as the unique account key; multiple records may share an email and usernames can be absent.
- If the same UUID exists with different content, mark it as an ID conflict.
- For likely duplicates with different UUIDs, use a review heuristic (for example, same service plus normalized display name plus identifier) but label the result as a potential duplicate, not a guaranteed duplicate.
- Never replace an existing record without explicit user confirmation.
