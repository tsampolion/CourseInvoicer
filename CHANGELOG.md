# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [2.0.0-alpha01] - Unreleased

Ground-up rebuild on `Rebuild_v2` under the Lightweight Android Project Rules. See
[`docs/REBUILD_V2.md`](docs/REBUILD_V2.md) for the brief, decisions and the v0.x parity map.

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
