# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [2.0.0-alpha02] - Unreleased

### Added
- **Bulk actions in Lessons:** long-press lessons to select several, then *Mark paid* or *Reschedule*
  them to a new date and start time. Only unpaid, uninvoiced lessons can move; length and fee are kept.
- **Emulator tests in CI:** SQL repositories, the schema 1→2 migration and an end-to-end flow
  (add student → log lesson → invoice → void) run on an API 35 emulator.
- Lint findings are printed into the CI log.

### Changed
- **Invoices are voided instead of deleted.** A void invoice stays listed and keeps its number, so a
  number never refers to two documents. Its lessons are released for re-invoicing.
- Database schema version 2 (`invoices.voided_on`), with an automatic upgrade from version 1.
- Backup format version 2 (`voidedOn` on invoices). Version 1 backups still import.
- TalkBack now announces the *Log lesson* buttons on Home and on a student.
- Locked lessons now say to *void* the invoice (not delete it) before changing them.
- Android cloud backup excludes all app data (`data_extraction_rules.xml`): student data stays on the
  phone unless the tutor exports a backup. Device-to-device transfer still works.
- The launcher icon gained a monochrome layer for Android 13+ themed icons.
- Documentation rewritten for day-to-day use. The rebuild brief became `docs/PRODUCT_SPEC.md`, the
  living spec with business rules R1–R11, decisions and the dependency register.

### Dependencies
- Approved test-only: `androidx.test:runner` 1.7.0, `androidx.test.ext:junit` 1.3.0 and
  `androidx.compose.ui:ui-test-junit4` (Compose BOM). Nothing new ships in the APK.

## [2.0.0-alpha01] - 2026-10-10

Ground-up rebuild on `Rebuild_v2` under the Lightweight Android Project Rules
([#349](https://github.com/tsampolion/EduInvoiceApp/pull/349)).

### Added
- `:core` pure-Kotlin module with models, billing, reports, invoice numbering, use cases and a
  versioned JSON backup codec, all unit-tested without Android.
- Compose + Material 3 screens: Home, Students, Student detail and edit, Groups, Group edit, Group
  lesson with attendance, Lessons, Lesson edit, Invoices, Create invoice, Settings.
- A4 invoice PDFs with the tutor's business details, shared through the system share sheet.
- JSON backup export and import through the Storage Access Framework.
- Lightweight typed navigation with per-screen ViewModel scoping and process-death restore.
- GitHub Actions CI: unit tests, lint, debug and release builds on every pull request.

### Changed
- Toolchain: AGP 9.4 with built-in Kotlin 2.4.21, Gradle 9.6.1 Kotlin DSL, Compose BOM 2026.09,
  compileSdk 37, targetSdk 36.
- Dependency injection: Hilt replaced by constructor injection (`AppContainer`).
- Storage: Room + SQLCipher replaced by framework SQLite; DataStore replaced by SharedPreferences.
- Money is stored as integer cents and lesson fees are snapshotted when logged.

### Fixed (compared to v0.x)
- Per-lesson rates were ignored by the revenue screen.
- Changing a student's rate rewrote the value of past lessons.
- Lessons could be invoiced twice under concurrent edits (now enforced in one transaction).

### Removed
- Multi-user login, SQLCipher encryption, Firebase Crashlytics/Analytics, sync and conflict
  resolution, reschedule batches and the "enterprise" infrastructure layers.

## 0.x

The v0.x history (up to 0.32.1) is preserved in git and on the `Rebuild` and `main` branches.
