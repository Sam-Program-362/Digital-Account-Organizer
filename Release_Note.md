# Release Notes — Digital Account Organizer `v3.12.1-release`

**Release Version:** `3.12.1-release` (`versionCode` `301201`)  
**Release Date:** 18 November 2026  
**Database Schema Version:** Room v3 (`MIGRATION_2_3` non-destructive upgrade)  
**Target / Compile SDK:** Android 16 (API 36) • **Minimum SDK:** Android 8.0 (API 26)

---

## Highlights in `3.12.1-release`

### 1. Standalone Account Identity & Personalization
- **Multiple Connected Phone Numbers (Separate from Recovery Phone):**
  - Standalone accounts now support adding, viewing, and removing **multiple connected account phone numbers** (`phoneNumbers`), stored and displayed separately from the single **Recovery Phone Number** (`recoveryPhoneReference`).
- **Date of Birth & Automatic Current Age Calculation:**
  - Added a **Date of Birth** field (`YYYY-MM-DD`) to standalone accounts with an integrated Material 3 calendar `DatePickerDialog` and manual input support.
  - Automatically calculates and displays the account owner's **current age in years** on the Standalone Account Detail page and live inside the account editor.
- **Single-Letter Email Avatar Monogram & Custom Profile Color Palette:**
  - Updated standalone account avatars (`AccountAvatarFrame`) to display **only 1 letter** — specifically the **first letter of the email address** (falling back to the username or account label when no email is set).
  - Added a 10-color **Profile Color Palette** selector (`Emerald`, `Sapphire`, `Violet`, `Amber`, `Crimson`, `Cyan`, `Indigo`, `Teal`, `Coral`, `Slate`, plus `Theme Default`) with a live avatar preview in the Add/Edit page.
- **Refined Standalone Account List Typography:**
  - Adjusted the visual hierarchy in `StandaloneAccountCard` by slightly reducing the Account Label size (`13.5.sp`) and increasing the Account Address size (`15.5.sp`, `FontWeight.SemiBold`, monospace accent) so primary account addresses stand out immediately.

### 2. Encrypted Account Backup / Recovery Codes Vault
- **AES-256-GCM + SQLCipher Protection:**
  - Added dedicated storage for **Account Backup / Recovery Codes** (`encryptedBackupCodes`) on standalone accounts, encrypted at rest with AES-256-GCM under the active 256-bit DEK before being stored in SQLCipher.
- **Password-Manager-Verified Viewing & Quick Copy:**
  - Gated backup/recovery codes behind Password Manager verification (`ProtectedBackupCodesVaultCard`).
  - Once verified, each individual recovery code is rendered as an interactive monospace chip with a **one-tap Quick Copy** button, alongside a **Copy All Codes** action.
- **Zero-Leak Blank-on-Edit Protection:**
  - Editing an account with saved backup codes never exposes the existing codes in the form; the input stays blank with a `"Saved • enter new backup codes to replace"` banner and an explicit `"Clear Codes"` chip.

### 3. Security & Verification Fixes
- **Strict Verification Before Both Showing and Copying Passwords:**
  - Fixed an issue where copying a hidden password to the clipboard could bypass the verification prompt if a previous session window was active or right after unlocking the app.
  - General app unlock (`unlockWithMasterPassword`, `unlockWithBiometricCipher`, `enableAppLock`) no longer pre-authorizes hidden Password Manager fields, and tapping either **Show (Eye)** or **Copy to Clipboard** on any hidden vault field strictly requires verification first.
- **Selective & Full-App Screenshot Protection (`FLAG_SECURE`):**
  - By default, screenshots and screen recording are restricted only when the organizer is locked or when viewing/editing credentials (Passwords, Backup Codes, Unlock/Security flows).
  - Added a **"Restrict Screenshots Across Entire App"** toggle in **Settings → Security** for users who prefer app-wide `FLAG_SECURE` enforcement.

### 4. One-Tap Quick Copy on Standalone Account Detail Page
- Every identity and recovery field on the Standalone Account Detail page (**Account Name / Label**, **Account Address / Email**, **Username / Handle**, **Date of Birth**, **Purpose / Role**, each **Connected Phone Number**, **Recovery Account / Email**, and **Recovery Phone Number**) now supports **one-tap copy to clipboard** with immediate inline and banner confirmation.

### 5. Full-Screen Pages & Cross-Device Backup Browser (Since `2.37.1-release`)
- **Full-Screen Detail & Editor Architecture:**
  - Replaced pop-up modal dialogs across Services, Service Account Entries, and Standalone Accounts with full-screen pages (`FullScreenSubpageScaffold`) and smooth horizontal transitions.
- **Exclusive Services `"Archived"` Filter:**
  - Toggling the `"Archived"` chip on the Services page now exclusively displays archived services and hides unarchived services.
- **In-App Device Storage Folder Browser & Portable `.dao-backup` Migration:**
  - Added an in-app folder browser (`DeviceStorageBackupBrowserDialog`), multi-intent system file chooser fallback (`ACTION_OPEN_DOCUMENT` $\rightarrow$ `ACTION_GET_CONTENT`), external `Open With` / `Share` intent support, and `MediaStore` / `Downloads` discovery so `.dao-backup` files transferred from another device can always be imported reliably.
  - Updated `.dao-backup` (`data.json`) and CSV export to include connected phone numbers, date of birth, encrypted backup codes (in `.dao-backup` only), and avatar color personalization.
- **Updated Adaptive Launcher Logo:**
  - Refreshed the adaptive and raster launcher icons (`mipmap-mdpi` through `mipmap-xxxhdpi` and `ic_launcher_foreground.xml` / `ic_launcher_background.xml`) with the new glowing emerald security-shield & digital identity vault emblem on a `#0B1726` background.

---

## Upgrade & Migration Notes
- Upgrading from `2.37.1-release` (Room schema v2) to `3.12.1-release` (Room schema v3) runs `MIGRATION_2_3` automatically, preserving all existing services, categories, standalone accounts, service accounts, and encrypted passwords while adding `phoneNumbersJson`, `dateOfBirth`, `encryptedBackupCodes`, and `avatarColorHex`.
- `.dao-backup` archives created in earlier versions remain fully restorable in `3.12.1-release`.
