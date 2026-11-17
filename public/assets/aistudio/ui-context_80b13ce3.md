# UI Context

## Product UI Direction

Digital Account Organizer should feel like a modern, minimal, clean, and professional Android utility. The interface should help users quickly recognize services, understand which accounts belong to them, and find records without feeling like an enterprise database console. Prefer clear information hierarchy, restrained decoration, readable typography, consistent spacing, and predictable navigation.

**Confirmed:** light and dark themes are both required.  
**Recommended convenience option:** follow-system appearance.  
**Not finalized:** exact brand colors, typography family, shape scale, icon package, and final component library choices. Use Material 3 semantic tokens as a baseline; do not invent a bespoke brand system without recording it as a proposal.

## Theme System

- Implement light and dark theme variants.
- Provide a settings option for Light, Dark, and (recommended) System.
- Use Material 3 semantic color roles and dynamic color only if it does not undermine consistent service icon visibility or brand contrast. Dynamic color may be offered as a later preference, not silently forced.
- Define colors as semantic tokens in one theme layer. Feature composables must not scatter hard-coded color values.
- Check all surfaces, text, icons, dividers, chips, status labels, dialogs, and system bars in both themes.
- Never communicate status or category only through color; add text, iconography, or accessible labels.

## Navigation

Recommended top-level destinations:

1. **Home** — account/service/category summary, recent items, and quick actions.
2. **Accounts** — all records, search, filtering, account details, and account creation/editing.
3. **Services** — user-created services, custom branding, service details, and associated accounts.
4. **Categories** — custom categories and category-based account browsing.
5. **Backup & Restore** — full/selective export, import, preview, duplicate resolution, and backup help.
6. **Settings** — theme, app protection, privacy/security information, local data management, and about.

This is a recommended information architecture. Use bottom navigation only if the number of destinations and available screen width remain usable; otherwise use a NavigationSuiteScaffold or an appropriate adaptive navigation pattern. Avoid squeezing six long labels into a cramped bottom bar.

## Screen Responsibilities

### Home

- Show total accounts and services, plus category summary where it provides value.
- Show a concise recently added/updated list.
- Offer quick actions for adding an account and creating a service.
- Make empty state useful: explain that the user must first create a custom service or can start by adding one.
- Do not show fake account data in a real installation.

### Accounts

- Present accounts in a readable list or compact cards, with account label, associated service icon/name, and optional category/status indicators.
- Keep search prominent and filters easy to open.
- Support sorting by name, service, creation date, or update date if implemented.
- Provide clear distinction between active and archived records.
- Do not expose passwords because the app does not store them.

### Account Detail and Form

- Show the account's service identity, account label, email/username, category, purpose, recovery contact references, website, notes, status, and timestamps where populated.
- Use an explicit edit mode or a dedicated edit screen; do not make fields unexpectedly editable.
- Mark optional fields as optional and avoid overwhelming the form.
- Group related fields logically: identity, organization, recovery references, notes.
- Provide validation inline and preserve non-secret input when validation fails.
- Confirm destructive deletion and explain whether the record can be restored from archive (if archive is used).

### Services

- Show the user-created service directory; do not show a default list of external providers.
- A service row/card should display its custom logo/icon, service name, and account count.
- Provide a clear Create Service action.
- Service creation/editing should include name, optional website/description, and image/icon selection.
- Provide image preview, change, and remove actions.
- Service detail should list all associated accounts and provide an Add Account action.
- If a service has accounts, deleting it must explain the consequences and offer safe alternatives such as archiving or moving records. Never cascade-delete accounts silently.

### Categories

- Show user-created categories and account counts.
- Allow create, rename, and delete with clear impact messaging.
- Do not assume a fixed system category catalogue. Suggested examples may be shown as optional starter suggestions only if the user explicitly accepts them; otherwise start with an empty custom category list.
- When a category is deleted, account records must remain. Clear or reassign category references according to a confirmed product flow.

### Backup & Restore

- Separate Export and Import actions visually.
- Explain the distinction between encrypted restorable backups and CSV exports.
- Before encrypted export, explain that the user must retain the backup passphrase; it is independent of Android device keys.
- For import, show backup metadata that is safe to disclose, validate the file before mutation, and preview counts and conflicts.
- Explain each conflict action: Skip, Keep Both, or Replace. Do not preselect destructive replacement.
- Show progress, cancellation behavior, success counts, and meaningful errors.
- Warn that CSV is plaintext and does not preserve service artwork or all relationships.

### Settings and App Lock

- Theme selector: Light, Dark, and recommended System option.
- App protection section should clearly state whether the lock is enabled.
- Explain master password, optional biometric unlock, recovery key, automatic locking, and loss-of-credentials consequences before setup.
- Never show a stored password or recovery key again after its one-time setup display; provide deliberate change/regenerate flows that verify the current credential.
- Do not display security configuration values that could help an attacker beyond what is necessary for clear user understanding.

## Components and Interaction Patterns

- Use Material 3 components where appropriate: app bars, navigation, cards, lists, text fields, buttons, dialogs, menus, chips, and snackbars.
- Build reusable product components for service identity, account row/card, category chip, empty state, destructive confirmation, and import conflict item.
- Service logos/icons must fit a consistent container with appropriate clipping and safe padding. Preserve aspect ratio; do not stretch user-provided artwork.
- Fallback service icon must be visually neutral and based on a safe default, not an assumed provider logo.
- Use clear labels for Add, Save, Cancel, Archive, Restore, Delete, Export, and Import actions.
- Use dialogs for high-impact decisions only. Avoid confirmation dialogs for harmless navigation.
- Show transient feedback for successful operations, but do not rely only on transient messages for critical backup/security results.
- Keep destructive actions visually distinct and require confirmation when data may be permanently removed.

## Forms and Validation

- Distinguish required and optional fields.
- Validate service name and account display label locally.
- Trim leading/trailing whitespace for names where safe, but preserve the user's original email/username and notes for display; normalize separate comparison keys instead of rewriting user input.
- Do not impose email syntax on all usernames.
- Keep multi-line notes comfortable to edit.
- Show validation near the relevant field with plain, actionable language.
- Never request a service password field.

## Layout and Responsiveness

- Prioritize phone-sized Android layouts.
- Support narrow phones, larger phones, landscape, split-screen, and font scaling.
- Use adaptive navigation/layout as width allows rather than assuming one fixed device size.
- Use consistent content margins, spacing, and touch targets.
- Allow long service names and emails to wrap or ellipsize without overlapping action icons.
- Make the account list useful at a glance without turning each row into a dense table.
- Ensure forms scroll correctly when the keyboard is visible.

## Typography, Spacing, Shape, and Icons

- Use Material 3 typography roles as the initial typography system; no custom font family is approved.
- Centralize spacing and shape tokens. Exact values remain to be chosen during visual implementation and should be consistent throughout.
- Use the platform's standard iconography or one approved icon package after dependency review. Do not mix unrelated icon styles.
- Give custom service logos a consistent frame; use their source artwork only as imagery, not as the source of UI colors or background contrast.

## Accessibility

- Provide content descriptions for meaningful service logos and controls.
- Ensure controls are reachable by TalkBack and keyboard navigation where supported.
- Maintain readable contrast in both themes.
- Support large font/display scaling without clipping critical actions.
- Do not use color as the only representation of status, category, or selection.
- Announce meaningful import/export results and form errors to accessibility services.
- Respect system animation preferences and avoid essential information conveyed only through animation.

## Privacy-Conscious Presentation

- Hide sensitive organizer previews in the Android recent-apps snapshot when app protection is enabled; use secure-window behavior as specified by `security-and-privacy.md`.
- Do not place account identifiers in notification content. Notifications are not required for the first release.
- Do not copy account data to the clipboard automatically. Any copy action must be explicit and limited to user-visible non-password fields.
- Avoid screen recordings, analytics instrumentation, and debug overlays in release builds.
- Keep sample data confined to previews and tests; never seed real user installations with fake account records.

## UI Decisions Still Open

- Final product logo and brand identity.
- Exact primary/secondary semantic colors and surface palette.
- Exact typography/shape/spacing scale.
- Whether dynamic color is offered.
- Final top-level navigation presentation for compact and expanded windows.
- Final service icon selection/cropping UX.
- Exact content/field ordering in account forms.
