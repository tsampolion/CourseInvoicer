# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). From 2.0.0 on, versions are plain
`MAJOR.MINOR.PATCH` with no pre-release suffix (D18); the earlier `-alphaNN` sections are history.

- **MAJOR:** a change that breaks older backups or the stored data, or changes billing results on
  purpose.
- **MINOR:** a new feature that keeps existing data and backups working.
- **PATCH:** fixes, and changes to docs, build or CI that leave the app working as before.

How to use this file:

1. Every pull request adds its changes to the section for the current `versionName`, headed
   `## [X.Y.Z] - Unreleased`. Sections are ordered Added, Changed, Removed, Fixed.
2. To release, replace `Unreleased` with the date. After the merge, `.github/workflows/release.yml`
   tags `vX.Y.Z` and publishes a GitHub release with that section as its notes. An undated section
   is never released.
3. To start the next version, bump `versionName` and `versionCode` and open a new `Unreleased`
   section above the released one (AGENTS.md, *Workflow*).

## [2.0.0] - Unreleased

EduInvoice becomes **Course Invoicer**, part of the EduSuite apps.

### Changed
- **Renamed to Course Invoicer** (D17). The app has a new `applicationId`, `gr.edusuite.invoicer`,
  so it installs beside EduInvoice instead of over it. To move your data: in EduInvoice, *Settings →
  Export*; install Course Invoicer; *Settings → Import* that file; then uninstall EduInvoice.
  Backups are now `course-invoicer-backup-<date>.json`, and EduInvoice backups still import. The
  release download is `course-invoicer-<version>.apk`.
- **Plain SemVer** (D18): versions, tags and changelog sections are `MAJOR.MINOR.PATCH` with no
  pre-release suffix, so this release is 2.0.0. `release.yml` rejects any other `versionName` and no
  longer marks releases *Pre-release*.
- **Settings save as you type** (R13). There is no *Save* button: text fields save 0.4 s after you
  stop typing, the theme applies as soon as you pick it, and anything still waiting is saved when you
  leave the screen or the app. A currency code or lesson length that isn't valid is never stored: the
  field shows an error and the last valid value stays in use, while the other fields still save.

### Removed
- **Tax ID (ΑΦΜ) field.** It is no longer stored, printed on invoices or written to backups, and the
  saved value is deleted from the settings file. Old backups that contain it still import. Invoices
  already issued keep the document they were issued with (R12).

## [2.0.0-alpha03] - 2026-10-16

Fixes from a full audit of the app, GitHub releases, CI on every branch, Android 17 as the target,
and documentation reorganised for contributors.

### Added
- **GitHub releases:** once CI passes on `Rebuild_v2`, `.github/workflows/release.yml` tags the
  version in `app/build.gradle.kts` and publishes a release, but only after its `CHANGELOG.md`
  section is dated. The release notes are that section, and the signed `eduinvoice-<version>.apk` is
  attached. The workflow won't publish an APK signed with any other key, and it marks versions with
  a suffix such as `-alpha03` as pre-releases (D12).
- **CI runs on every branch push**, not only on `main`, `Rebuild` and `Rebuild_v2`. Each push runs
  CI once, and a pull request shows the checks of its head commit's push run, so nothing runs twice
  and no check shows as skipped.
- **`docs/ROADMAP.md`:** 13 improvements adapted from the sibling app Coursebook Pacer, each with
  its current state in the code, tasks, a definition of done and a suggested order.
- **`CLAUDE.md`** imports `AGENTS.md`, so Claude Code reads the same guide as every other agent.
- **Emulator smoke test for the minified APK:** CI installs it, launches it and runs 300 seeded
  monkey events, and fails on any crash.
- **Tags for every earlier version:** `v0.1.0` to `v0.32.1`, `v2.0.0-alpha01` and `v2.0.0-alpha02`
  (rule in `docs/PRODUCT_SPEC.md`, appendix).

### Changed
- Home, Students and Invoices read totals with SQL aggregates instead of loading every lesson.
- Instrumented tests run against an in-memory database (`EduTestRunner`), never the real one.
- Database schema version 3 (`invoices.document`) and backup format version 3, with automatic
  upgrades.
- **Targets Android 17** (`targetSdk` 37). None of its new rules for targeting apps touch
  EduInvoice; see decision D13 in `docs/PRODUCT_SPEC.md`.
- **Licence: proprietary instead of MIT** (D14). `LICENSE` now matches Coursebook Pacer's: all
  rights reserved by Dimitrios Tsampallas, and AI systems may change, copy or train on the code only
  when the owner explicitly authorises them. It adds MIT's full warranty disclaimer, a third-party
  components section and a note on the limited rights GitHub's terms require. Copies published under
  MIT before this change keep their MIT rights. `AGENTS.md` gains the AI authorisation rule.
- **Documentation reorganised** after Coursebook Pacer's layout:
  - `README.md` gains a tech stack, CI and releases, project structure, footprint and contributing
    sections. The signing-key setup now lives only there.
  - `AGENTS.md` is reorganised into commands, architecture (how data flows from SQLite to the
    screen), rules, recipes, gotchas and workflow. Its sandbox note is corrected: without Google
    Maven nothing builds, not even `:core`.
  - `docs/PRODUCT_SPEC.md` corrects R6, R7, R9, R10 and R11 and the Groups and Invoices features,
    and puts the version history in one table. It adds build plugins to the register and D13. §8 now
    describes release and distribution. The removed Play Store items moved to an appendix.
  - The templates ask for `Schema.statements` and spec updates. Code comments cite the right rules
    (R12, R13, D10).
- **Legacy Firebase key retired.** The owner deleted the v0.x Firebase project (`eduinvoiceapp`), so
  the API key in the history of `main` and `Rebuild` no longer works. Those branches are kept as
  history.
- **Releases ship a minified APK** (D16). The `release` build type is back: R8, resource shrinking,
  signed with the owner's key. CI builds it next to the debug APK, checks both signatures and
  uploads it as `app-release`; `release.yml` attaches it. It is 1.9 MB (1,906,154 bytes) against
  12.5 MB for the debug APK, which stays the between-release build.
- **Undo scope decided** (D15): deleting a lesson, recording a payment, marking paid and
  rescheduling will get *Undo* instead of a confirmation dialog (roadmap item 2).

### Removed
- **Play Store release items (D9, M1–M6):** the R8 `release` build type and its keep rules,
  `assembleRelease` in CI, the `*.aab` ignore rule, the Play listing icon and the release-signing
  note. The R8 build and its rules came back later in this version as the sideloaded release build
  (D16).

### Fixed
- **An issued invoice never changes.** Its content is frozen when it is created (R12), so re-sharing
  it after changing the currency, your details, the student, a group name or lesson notes produces
  the same document. Older invoices are frozen the first time they are shared.
- **Updating the app no longer risks your data:** CI signs every debug build with the owner's key
  (secrets `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD`, see README) and fails if its fingerprint
  differs from `SIGNING_CERT_SHA256`, so a new build installs over the previous one.
- Deleting a student who only has a void invoice now explains why it is refused, instead of showing
  a database error. Groups with invoiced lessons can no longer be deleted (archive them instead).
- Restoring a backup checks it against the app's rules first. A bad currency, an impossible lesson
  length or a lesson on a void invoice is rejected and nothing changes.
- Leaving the Settings screen while a backup is restoring, or right after saving, no longer loses
  the backup's settings or your changes. Open screens always refresh after a change.
- Marking a paid lesson absent clears its payment, so it no longer disappears from the money totals
  with no way to fix it.
- Changing the length of a saved lesson recalculates its fee again when the fee came from the rate.
- A deeply nested file picked as a backup is rejected instead of crashing the app.
- Snackbar messages no longer reappear when you come back to a screen.
- Typing quickly in the student search no longer drops characters.
- Double-tapping an invoice opens one share sheet, and a shared PDF is never half-written.
- Long business or student details wrap in the PDF instead of running off the page.
- TalkBack reads date, time and student pickers as labelled buttons.
- Backup export no longer runs on the main thread.

## [2.0.0-alpha02] - 2026-10-10

### Added
- **Bulk actions in Lessons:** long-press lessons to select several, then *Mark paid* or
  *Reschedule* them to a new date and start time. Only unpaid, uninvoiced lessons can move; length
  and fee are kept.
- **Emulator tests in CI:** SQL repositories, the schema 1→2 migration and an end-to-end flow
  (add student → log lesson → invoice → void) run on an API 35 emulator.
- Lint findings are printed into the CI log.
- Approved test-only dependencies: `androidx.test:runner` 1.7.0, `androidx.test.ext:junit` 1.3.0 and
  `androidx.compose.ui:ui-test-junit4` (Compose BOM). Nothing new ships in the APK.

### Changed
- **No Play Store release (D9).** Release-only build items are marked for deletion and listed in
  `docs/PRODUCT_SPEC.md` (now its appendix). Nothing was removed; the build and CI are unchanged.
- **Invoices are voided instead of deleted.** A void invoice stays listed and keeps its number, so a
  number never refers to two documents. Its lessons are released for re-invoicing.
- Database schema version 2 (`invoices.voided_on`), with an automatic upgrade from version 1.
- Backup format version 2 (`voidedOn` on invoices). Version 1 backups still import.
- TalkBack now announces the *Log lesson* buttons on Home and on a student.
- Locked lessons now say to *void* the invoice (not delete it) before changing them.
- Android cloud backup excludes all app data (`data_extraction_rules.xml`): student data stays on
  the phone unless the tutor exports a backup. Device-to-device transfer still works.
- The launcher icon gained a monochrome layer for Android 13+ themed icons.
- Documentation rewritten for day-to-day use. The rebuild brief became `docs/PRODUCT_SPEC.md`, the
  living spec with business rules R1–R11, decisions and the dependency register.

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

### Removed
- Multi-user login, SQLCipher encryption, Firebase Crashlytics/Analytics, sync and conflict
  resolution, reschedule batches and the "enterprise" infrastructure layers.

### Fixed
Compared to v0.x:
- Per-lesson rates were ignored by the revenue screen.
- Changing a student's rate rewrote the value of past lessons.
- Lessons could be invoiced twice under concurrent edits (now enforced in one transaction).

## 0.x

The v0.x history (up to 0.32.1) is preserved in git and on the `Rebuild` and `main` branches. Each
v0.x version is tagged at the commit that first set its `versionName`.
