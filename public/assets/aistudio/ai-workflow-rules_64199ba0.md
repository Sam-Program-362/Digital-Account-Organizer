# AI Workflow Rules

## Approach

Build Digital Account Organizer incrementally using a specification-driven workflow. Treat the six core context files and supporting documents as the source of truth. Implement only behavior described in those documents or explicitly approved by the project owner. Keep the application offline, local-only, and outside the scope of password management. Before coding a feature, identify its user-facing behavior, data changes, security implications, files affected, and verification plan. Do not treat proposed recommendations as user-confirmed requirements when explaining decisions.

## Scoping Rules

- Work on one small, end-to-end feature unit at a time.
- Prefer small, verifiable increments over broad speculative changes.
- Do not combine unrelated database, UI, cryptography, and backup changes in one implementation step.
- Keep product scope limited to the account organizer described in `project-overview.md`.
- Do not add network access, service integrations, cloud sync, account permissions, or password-vault behavior.
- Keep services user-created; do not add a provider catalogue as a hidden dependency.
- Preserve the many-accounts-to-one-service relationship.
- Preserve both service-based and category-based organization.
- Do not make the app lock mandatory; users must be able to choose whether to enable it.
- Do not expose or store external service passwords or authentication secrets.
- Do not claim a feature is complete until it has been tested against its acceptance criteria.

## When to Split Work

Split an implementation step if it combines:

- UI changes and cryptographic/key-management changes.
- Database schema changes and unrelated screen redesign.
- Backup encryption and account CRUD unrelated to import/export.
- Service image import, image decoding, and unrelated category behavior.
- App-lock lifecycle changes and broad navigation rewrites.
- Multiple independent features that can be built and tested separately.
- Any requirement whose behavior is not defined clearly enough to verify.

If a change cannot be verified end to end within a focused test plan, split it into smaller units.

## Handling Missing Requirements

- Do not invent product behavior that is absent from the context files.
- Check `decision-log.md` to distinguish confirmed user requirements from the recommended technical baseline and open decisions.
- If a technical recommendation conflicts with current platform guidance, stop and document the conflict; do not silently substitute a new dependency or architecture.
- If a requirement is ambiguous, add it to `progress-tracker.md` and identify the affected files before implementing.
- If a new decision is required, ask only for the information needed to unblock that decision. Do not repeatedly ask about decisions already established.
- Do not ask the project owner to design cryptographic primitives. Follow `security-and-privacy.md`; obtain a qualified security review when a platform/library limitation requires a change.
- Treat the master-password lock as optional. Do not invent additional lock modes or recovery shortcuts beyond the documented baseline.
- Never silently change a confirmed feature, field, navigation area, export capability, or privacy boundary.

## Protected Files

Do not modify the following without an implementation need and an explicit explanation in the change summary:

- `architecture.md` — architectural changes require the decision log and progress tracker to be updated as well.
- `security-and-privacy.md` — security model changes require threat review and tests.
- `backup-format.md` — backup format changes require versioning and compatibility tests.
- `data-model.md` — entity/schema changes require migrations and import/export compatibility review.
- `ai-workflow-rules.md` — agent behavior rules must not be weakened casually.
- Cryptographic algorithm implementations or vendored third-party library internals — never patch them locally as a shortcut; use documented APIs or replace the dependency through a reviewed decision.
- Generated build outputs and dependency caches — do not commit or hand-edit them.

## Keeping Docs in Sync

Update the relevant context file whenever implementation changes:

- System architecture, module ownership, dependency, or minimum Android API assumptions — update `architecture.md` and `decision-log.md`.
- Database entities, constraints, indexes, or relationships — update `data-model.md`, `architecture.md`, and migrations.
- Lock, encryption, biometric, recovery, backup secrecy, or privacy behavior — update `security-and-privacy.md`, `architecture.md`, and `decision-log.md`.
- Backup container, schema version, or conflict policy — update `backup-format.md` and migration/compatibility tests.
- UI patterns, navigation, design tokens, or theme behavior — update `ui-context.md`.
- Coding conventions or package structure — update `code-standards.md`.
- Milestone status — update `progress-tracker.md` after verification.
- Keep `README.md` reading order and document inventory accurate when files are added, renamed, or removed.

## Before Moving to the Next Unit

1. The current unit works end to end within its defined scope.
2. No invariant in `architecture.md` or security rule in `security-and-privacy.md` was violated.
3. Automated tests cover the new behavior and relevant failure cases.
4. The merged manifest and dependency tree have been checked for unintended permissions or network-capable SDKs when dependencies change.
5. Database changes include forward migrations and migration tests; do not use destructive fallback for real user data.
6. Import/export changes pass compatibility, corruption, malformed input, and interrupted-operation tests.
7. UI changes work in light and dark themes and remain usable with accessibility settings.
8. `progress-tracker.md` reflects only work actually completed and verified.
9. All relevant context documents have been synchronized.
10. Run the project's configured Gradle verification tasks (for example, unit tests, lint, instrumented tests where available, and release assembly). Do not use a web-specific command such as `npm run build` for this Android project.

## Security-Specific Agent Rules

- Never log secrets, account records, decrypted database values, KDF inputs, key bytes, recovery keys, or backup passphrases.
- Never write a plaintext master password or raw database key to disk, preferences, saved state, clipboard, or test snapshots.
- Never bypass the unlock state machine to make a test or screen work.
- Never fall back to a plaintext database if SQLCipher initialization fails.
- Never weaken Argon2id parameters, AES-GCM, SQLCipher settings, Keystore authentication, or backup encryption to improve convenience without a documented security review.
- Never export Android Keystore key material or the raw database as a portable backup.
- Treat imported backup files and image files as hostile input.
- Never upload logs, backups, images, crash data, or analytics to a remote service.
- Ensure temporary plaintext buffers/files are removed as far as platform guarantees permit after use or failure.
- Do not claim protection against rooted devices, OS compromise, or malicious privileged software unless that threat is specifically mitigated and tested.

## Feature Acceptance Discipline

For each feature, define:

- User-visible entry point and expected result.
- Empty, loading, success, validation, cancellation, and error states.
- Persistent data and relationship effects.
- Accessibility expectations.
- Offline behavior.
- Security/privacy implications.
- Automated tests and manual verification steps.
- Context files that need synchronization.

Do not mark the unit complete until all required verification passes or the progress tracker clearly records a remaining blocker.
