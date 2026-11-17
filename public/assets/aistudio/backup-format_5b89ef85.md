# Backup and Import Format

## 1. Goals

The backup format must preserve user-created services, custom service imagery, accounts, categories, relevant portable preferences, and relationships. It must be independent of the current Room schema so the internal database can evolve without making old backups impossible to import.

The backup must be portable to another compatible device. Do not depend on Android Keystore keys, app installation identifiers, or device-specific secrets.

## 2. Supported Export Types

### Encrypted structured backup (recommended primary format)

- Suggested extension: `.dao-backup`.
- Contains all data needed for a restorable backup, including service assets.
- Encrypted before it leaves private staging storage.
- Uses a user-supplied backup passphrase, independent of Android Keystore and preferably independent of the app-lock master password.
- Portable between devices and app installations that support the format version.
- Must not include the raw SQLCipher database, device Keystore keys, master password, recovery key, or other secret key material.

### CSV export (optional convenience format)

- Plaintext by design; warn users before export.
- Intended for spreadsheet review or simple data portability, not full restoration.
- Does not preserve custom service image binaries, all relationships, security configuration, or all nested data.
- May provide separate tables/files for accounts, services, and categories if needed; document exact columns when implemented.
- Never include security envelopes, keys, master-password configuration, recovery key, or backup passphrase.

## 3. Recommended `.dao-backup` Container

Recommended container is a small ZIP-compatible outer package with exactly two required entries:

```text
backup.dao-backup
  header.json       # Non-sensitive format and cryptographic metadata only
  payload.enc       # Authenticated encrypted compressed payload
```

The encrypted payload, after decryption, is a compressed package such as:

```text
payload/
  manifest.json
  data.json
  assets/
    <asset-id>.<normalized-extension>
```

Do not expose service names, account identifiers, notes, category names, filenames derived from user content, or image metadata in the outer header. Keep such information encrypted inside `payload.enc`.

## 4. Header

`header.json` may contain:

- `format`: fixed identifier such as `digital-account-organizer-backup`.
- `formatVersion`: integer for the outer container format.
- `cipher`: approved authenticated encryption identifier, recommended `AES-256-GCM`.
- `kdf`: identifier, recommended `Argon2id`.
- `kdfVersion`: implementation/parameter format version.
- `kdfParameters`: memory, iterations, parallelism, and output length.
- `salt`: base64-encoded random KDF salt.
- `nonce`: base64-encoded unique GCM nonce.
- `payloadEncoding`: compression/encoding identifier.

Header fields must be strictly validated and included as associated authenticated data (or their canonical byte representation must be bound to the encrypted payload). Reject missing, malformed, unsupported, or out-of-range values before expensive allocation.

## 5. Encryption

- Generate a fresh random salt for each backup.
- Derive a 256-bit key from the backup passphrase using Argon2id.
- Current OWASP guidance specifies an Argon2id minimum of 19 MiB memory, 2 iterations, and parallelism 1; benchmark parameters and use at least the applicable minimum.
- Compress the structured payload before encryption.
- Encrypt the compressed bytes using AES-256-GCM with a fresh 96-bit nonce.
- Keep the GCM authentication tag with the ciphertext according to the selected library format.
- Authenticate the canonical header as associated data so an attacker cannot change KDF/cipher/version parameters without detection.
- Do not reuse a nonce with the same derived key. A fresh salt per backup also derives a distinct key, but nonce uniqueness remains mandatory.
- Never store the backup passphrase in the application database or preferences.
- Clear mutable password/key buffers on a best-effort basis after use.

The backup passphrase must be requested at export time and requested again at import. Do not silently reuse the app-lock master password; the two credentials have different portability and recovery requirements.

## 6. Inner Manifest and Data Schema

`manifest.json` is encrypted inside the payload and should include:

- `dataSchemaVersion`.
- `applicationName` and optional export app version.
- `exportedAt` timestamp.
- Count of services, accounts, categories, and assets.
- List of asset IDs and content checksums.
- Optional export-selection metadata that does not expose sensitive data outside encryption.

`data.json` should contain separate arrays:

- `services`
- `accounts`
- `categories`
- `preferences` (allowlisted non-secret portable preferences only)

Do not include security envelopes, master-password KDF metadata, Android Keystore aliases, biometric keys, recovery key, database DEK, or device-specific access state.

### Data compatibility

- Preserve stable UUIDs.
- Preserve service-to-account and category-to-account references.
- Preserve only fields supported by the backup schema version.
- Use ISO 8601 UTC timestamps.
- Treat absent optional fields as absent, not as values to overwrite existing data with.
- Additive schema evolution should use new optional fields where possible.
- Breaking changes require a new `dataSchemaVersion` and migration logic.

## 7. Export Modes

Recommended export modes:

1. **Full organizer export:** all services, accounts, categories, referenced assets, and allowlisted portable preferences.
2. **Selected accounts export:** selected accounts plus referenced services, referenced categories, and referenced image assets.
3. **Selected service export:** selected services and their accounts, categories referenced by those accounts, and corresponding assets.
4. **Selected category export:** selected category and accounts assigned to it, plus referenced services and assets.

When a selection includes a service with no accounts or category with no accounts, include it only when the user explicitly selects it. Show a review summary before file creation.

## 8. Import Pipeline

1. User selects a file through Storage Access Framework.
2. Read only a bounded amount of header data. Validate outer format, version, KDF identifier, salt/nonce lengths, and KDF parameter upper/lower bounds before deriving a key.
3. Request the backup passphrase. Derive the key with Argon2id.
4. Authenticate/decrypt the payload. If authentication fails, show a generic incorrect-password-or-damaged-file error.
5. Enforce maximum encrypted size, decompressed size, number of archive entries, JSON depth/size, and image byte/dimension limits.
6. Reject absolute paths, `..` traversal, symlinks, duplicate critical entries, unexpected entry names, and archive expansion attacks.
7. Validate manifest counts and checksums.
8. Validate the data schema, required identifiers, references, enum values, timestamps, URLs, and field lengths.
9. Decode and re-encode image assets into app-private staging storage using generated internal file names.
10. Compare records with local data and prepare an import preview.
11. Let the user review new records, ID conflicts, and likely duplicates.
12. Apply the user's conflict decisions and commit the import transactionally.
13. Move verified assets into final private storage and update references in a coordinated commit/cleanup process.
14. Clean temporary plaintext files and in-memory key buffers on success, cancellation, or failure.

## 9. Duplicate and Conflict Policy

The import UI should distinguish:

- **New record:** UUID does not exist locally and no likely duplicate is detected.
- **ID conflict:** same UUID exists locally but content differs.
- **Possible duplicate:** different UUIDs but key descriptive values appear to match.
- **Related-record conflict:** a service/category reference is missing or conflicts with a local record.

For each conflict, provide explicit choices where applicable:

- **Skip:** keep the existing local record and ignore the incoming record.
- **Keep both:** retain both records with distinct UUIDs/identity and make any required service/category mapping explicit.
- **Replace:** overwrite the selected local record with incoming values after a clear preview and confirmation.

Never silently replace existing records. Do not use email alone as a unique key. The import operation must preserve referential integrity and be atomic or have a well-defined recoverable staging procedure.

## 10. Error Handling

Handle at least:

- Unsupported format or data version.
- Wrong passphrase or authentication tag failure.
- Truncated or corrupt file.
- Invalid KDF parameters.
- Archive traversal or decompression bomb attempt.
- Missing asset or checksum mismatch.
- Malformed or oversized JSON.
- Duplicate/conflicting IDs.
- Database write failure or insufficient storage.
- User cancellation or process termination during staging.

Do not delete or overwrite existing organizer data until the import preview is accepted and the transaction is ready to commit.

## 11. CSV Export Rules

- Show a warning that CSV is plaintext and may expose email addresses, usernames, recovery contact references, and notes.
- Let the user select which record types/columns to include where practical.
- Do not include password-like fields because such fields must not exist.
- Escape commas, quotes, newlines, and Unicode correctly.
- Document that service images and full relationships are not carried by CSV.
- Use user-selected destination and do not leave persistent plaintext staging copies.

## 12. Compatibility and Testing

- Maintain test fixtures for every released backup schema version.
- Test export/import on the same device and a different device/installation.
- Test wrong passphrase, corrupted ciphertext, tampered header, truncated archive, invalid image, and archive path traversal.
- Test selection modes and relationship preservation.
- Test duplicate resolution, replace, skip, and keep-both behavior.
- Test interrupted import cleanup and ensure existing records remain unchanged after failure.
- Never mark a backup-format change complete without migration/compatibility tests.
