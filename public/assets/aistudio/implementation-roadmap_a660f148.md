# Implementation Roadmap

## Roadmap Principles

- Build and verify the security/storage foundation before broad account CRUD screens.
- Deliver end-to-end increments and preserve user data between milestones.
- Do not begin external service integrations; they are outside scope.
- Do not implement password storage for external services.
- Keep technical choices marked as recommendations until verified against the selected Android toolchain.
- Every milestone has exit criteria. Do not advance because code merely compiles.

## Milestone 0 — Project Setup and Technical Validation

### Work

- Choose final application ID, app name, minimum SDK, compile/target SDK policy, and supported ABIs.
- Create Android Studio/Gradle Kotlin project and version catalog.
- Confirm Kotlin, AGP, Compose, Room, SQLCipher for Android, AndroidX Biometric, Argon2id implementation, and test-library compatibility.
- Review dependency licenses, maintainers, known advisories, ABI support, and transitive manifest permissions.
- Create package structure and dependency injection strategy.
- Configure backup exclusions for cloud backup and device-to-device transfer, including legacy rules as required by target SDK.
- Confirm merged release manifest does not contain INTERNET permission.

### Exit Criteria

- Clean debug and release builds.
- Dependency report and license review recorded.
- Merged manifest reviewed and no network permission present.
- Backup exclusion resources exist and are covered by a verification plan.
- No feature implementation is claimed complete.

## Milestone 1 — Cryptographic and Database Foundation

### Work

- Implement SQLCipher initialization and encrypted Room integration.
- Generate random 256-bit DEK.
- Implement versioned key-envelope format.
- Implement Argon2id master-password derivation and AES-256-GCM key wrapping.
- Implement device Keystore envelope for lock-disabled mode.
- Implement the lock state machine and database-open/close lifecycle.
- Implement lock enable/disable and master-password change.
- Implement recovery-key generation, display, confirmation, and unlock/replace flows.
- Implement optional strong-biometric key envelope and fallback.
- Test background lock, process death, wrong password, invalidated biometric key, tampered envelope, and storage failure.

### Exit Criteria

- Database content is encrypted at rest.
- The database never opens before an authorized DEK is available.
- No plaintext password/key is persisted or logged.
- Enabling/disabling lock is transactional and cannot strand data without warning.
- Master password can be changed by rewrapping the DEK without rewriting all database records.
- Recovery behavior is documented and tested.

## Milestone 2 — Core Data Model and Persistence

### Work

- Implement Service, Account, Category, ServiceAsset, and non-secret preference persistence.
- Create Room entities, DAOs, repositories, and domain mappings.
- Add stable UUIDs, foreign keys, indexes, uniqueness rules, and timestamps.
- Implement migrations from the initial schema onward.
- Enforce one-to-many Service-to-Account relationship.
- Enforce MVP optional single Category-to-Account relationship unless a later decision changes it.
- Implement archive/delete behavior with safe service/category relationship handling.

### Exit Criteria

- Database relation tests pass.
- Migrations preserve data and remain encrypted.
- Service deletion cannot silently delete accounts.
- Category deletion does not delete accounts.
- Invalid references are rejected.

## Milestone 3 — Custom Services and Service Artwork

### Work

- Build empty service directory and create service flow.
- Add service name, optional website/description, and custom logo/icon selection.
- Use Photo Picker/SAF; avoid broad storage permission.
- Validate, decode, re-encode, and store assets privately.
- Build service detail/edit/archive/delete flows.
- Show account counts and linked accounts.

### Exit Criteria

- No predefined service catalogue is required.
- User can create and edit services manually.
- Service image survives app restart.
- Invalid/oversized image files are safely rejected.
- Service deletion/archiving preserves linked-account integrity.

## Milestone 4 — Account Management

### Work

- Build account list, detail, add/edit forms, archive/restore, and delete.
- Implement service association and optional category association.
- Add identity, purpose, recovery references, website, notes, status, and timestamps.
- Ensure no service password field or secret credential feature exists.
- Add field validation and search normalization.

### Exit Criteria

- Multiple accounts can be attached to the same service.
- CRUD and archive behavior persist correctly.
- No password/authentication-secret fields exist in UI or schema.
- Validation and destructive confirmation tests pass.

## Milestone 5 — Categories, Search, Dashboard

### Work

- Implement category CRUD and safe deletion behavior.
- Implement account search and service/category/status/archive filters.
- Implement dashboard counts, recent records, and quick actions.
- Add empty/loading/error states and useful navigation.

### Exit Criteria

- Accounts can be browsed by both service and category.
- Search/filter combinations behave predictably.
- Dashboard counts match repository data.
- Empty states guide users without fabricating records.

## Milestone 6 — Backup and Restore

### Work

- Finalize versioned `.dao-backup` schema and container.
- Implement dedicated backup passphrase flow.
- Implement Argon2id + AES-256-GCM encrypted payload.
- Include only required services/categories/accounts/assets for each export mode.
- Implement full and selective export.
- Implement safe import staging, schema validation, asset validation, preview, and conflict choices.
- Add CSV export if included in the first release.

### Exit Criteria

- Full backup restores on a second device/install without original Keystore keys.
- Custom service images and relationships are preserved.
- Wrong passphrase/corrupt file is rejected without database mutation.
- Conflicts are shown and user-controlled.
- Interrupted imports do not partially corrupt existing data.
- CSV warning and plaintext limitations are clear if CSV ships.

## Milestone 7 — UI Polish, Accessibility, and Privacy

### Work

- Complete light and dark themes and optional follow-system behavior.
- Refine navigation for compact and expanded widths.
- Complete design tokens, service/account components, dialogs, validation, and empty states.
- Verify TalkBack semantics, large font scaling, contrast, keyboard navigation, and touch targets.
- Hide sensitive recent-app snapshots and apply secure-window behavior where appropriate.
- Verify no account values are emitted into logs or notifications.

### Exit Criteria

- Main screens are consistent in light and dark modes.
- App remains usable at large text sizes and narrow widths.
- Critical actions are accessible.
- Sensitive screens follow documented screenshot/recent-app policy.

## Milestone 8 — Release Readiness

### Work

- Run unit, integration, instrumented, UI, migration, cryptographic, and import/export tests.
- Audit merged manifest, dependency permissions, SDKs, and licenses.
- Test Auto Backup/cloud backup and device-to-device exclusion on supported Android versions.
- Test offline flows with network disabled.
- Run static analysis/lint and inspect release build.
- Prepare privacy explanation, app-lock/recovery warnings, backup instructions, and release notes.

### Exit Criteria

- All release acceptance criteria in `project-overview.md` pass.
- No unresolved critical/high security issue.
- No external network path or telemetry SDK is present.
- User data survives normal upgrade/migration tests.
- Documentation and progress tracker reflect actual shipped behavior.

## Later Enhancements (Not Required for First Release)

- Account activity history/audit timeline.
- More advanced filters and sorting presets.
- Additional dashboard customization.
- Additional export selection and reporting options.
- Optional multi-category tags if a later product decision approves them.
- Further UI customization.

These ideas are backlog candidates. Do not implement them ahead of core reliability, security, and backup correctness.
