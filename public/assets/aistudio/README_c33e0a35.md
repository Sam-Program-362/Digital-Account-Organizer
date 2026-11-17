# Digital Account Organizer — Context Package

**Package status:** Planning and specification documentation  
**Application implementation status:** Not started  
**Package contents:** Six core context files and six supporting documents

## Purpose

This folder contains the project context for a proposed Android application named **Digital Account Organizer**. It is intended to be supplied to a future coding agent as the persistent source of product requirements, architecture guidance, implementation standards, security requirements, UI context, and development state.

The application is a local-first organizer for information about a user's digital accounts. It does not sign in to external services, read accounts from Android or Google, manage permissions, synchronize accounts, or store service passwords.

## Read in this order

1. `project-overview.md` — product intent, user flows, scope, and acceptance criteria.
2. `decision-log.md` — what the user confirmed, what is a recommendation, and what remains unresolved.
3. `architecture.md` — proposed Android stack, boundaries, storage, relationships, and invariants.
4. `security-and-privacy.md` — detailed data protection, optional app lock, recovery, privacy, and threat boundaries.
5. `data-model.md` — entities, fields, relationships, and validation rules.
6. `backup-format.md` — manual export/import format, encryption, compatibility, and conflict behavior.
7. `ui-context.md` — navigation, visual principles, screen responsibilities, themes, and accessibility.
8. `code-standards.md` — Kotlin, Compose, storage, security, tests, and project organization conventions.
9. `ai-workflow-rules.md` — mandatory operating rules for future coding agents.
10. `implementation-roadmap.md` — staged implementation plan and verification gates.
11. `progress-tracker.md` — current project state and open decisions.

## Document classification

The documents distinguish three classes of information:

- **Confirmed requirement:** explicitly selected or stated by the user.
- **Recommended baseline:** a concrete technical or UX recommendation made to make the project implementable. It should be treated as the current proposed baseline, not misrepresented as a user-authored preference. Revisit it only when an implementation constraint or new explicit decision requires it.
- **Open decision:** not yet determined. Do not invent a value. Keep the implementation behind a clear interface or record the issue in `progress-tracker.md` before proceeding.

## Core files

- `project-overview.md`
- `architecture.md`
- `code-standards.md`
- `ai-workflow-rules.md`
- `ui-context.md`
- `progress-tracker.md`

These six files are the main persistent context set. The additional documents provide more detailed contracts for security, data, backup, decisions, and delivery planning.

## Important product boundaries

- Android application; offline-first and local-only.
- All services are created manually by the user. There is no built-in provider directory.
- Each service may have multiple account records.
- Users may create and manage categories and organize accounts by both service and category.
- Users may select a custom logo or icon for each service.
- No password vault: never add fields or features for service passwords, authentication secrets, access tokens, or one-time codes.
- The application does not connect to Google, Microsoft, GitHub, or other account providers.
- Manual export and import are supported. Android automatic backup and cloud synchronization must not silently copy the private database.
- An optional application lock protects access to the organizer. The recommended baseline is documented in `security-and-privacy.md`.
- Light and dark themes are required. System-following mode is a recommended convenience option.

## Naming

**Digital Account Organizer** is the working product name. The final Android application ID / package identifier has not been selected.

## Source and standards references

The security recommendations were checked against these public references on 17 November 2026:

- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android biometric authentication: https://developer.android.com/identity/sign-in/biometric-auth
- Android Auto Backup and exclusion rules: https://developer.android.com/identity/data/autobackup
- SQLCipher for Android: https://github.com/sqlcipher/sqlcipher-android
- OWASP Password Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- RFC 9106 (Argon2): https://www.rfc-editor.org/rfc/rfc9106

External references are technical guidance, not permission to change confirmed product scope. Verify current library versions, licensing, API compatibility, and security advisories before dependency selection.
