# EduInvoice: Product specification

This is the reference for **what the app does and why**. When behaviour changes, update this file in
the same pull request. Engineering rules (how to build it) live in [`AGENTS.md`](../AGENTS.md).

## 1. Purpose

EduInvoice is a single-user, offline Android app for a private tutor:

1. Keep a register of students and groups.
2. Log lessons.
3. See who owes what.
4. Record payments.
5. Issue invoices as PDFs.
6. Back the data up.

Nothing leaves the device unless the tutor shares an invoice or exports a backup.

## 2. Business rules

These rules are enforced in `:core` and covered by tests. The rule numbers are referenced from code
comments and PRs.

| # | Rule | Enforced in |
|---|------|-------------|
| R1 | **Billing:** `HOURLY` fee = rate × minutes ÷ 60. `PER_LESSON` fee = rate. Money is integer cents, rounded half-up. | `Rate.feeFor` |
| R2 | **Fee snapshot:** a lesson stores its fee when it is logged. Changing a rate never rewrites past lessons. | `Lesson.fee`, `SaveLesson` |
| R3 | **Group lessons:** one row per member, billed at the *group* rate. Absent members get an `absent` row that is never charged, and so is never paid either. | `LogGroupLesson`, `SaveLesson` |
| R4 | **Invoices:** one student per invoice. Only lessons that are not invoiced and not absent can be added. A lesson is on at most one invoice. The database guards this in the same transaction. | `CreateInvoice`, `SqlInvoiceRepository` |
| R5 | **Numbering:** invoice numbers run `YYYY-NNNN` per calendar year. They are **never reused**. | `InvoiceNumbering` |
| R6 | **Voiding:** an invoice is voided, never deleted. Voiding releases its lessons so they can be invoiced again. The void invoice and its number stay visible. A void invoice cannot be shared. | `VoidInvoice` |
| R7 | **Locking:** while a lesson is on an invoice, its student, date, time, duration, fee and attendance are read-only, and it cannot be deleted. Notes and the paid flag stay editable. | `SaveLesson`, `DeleteLesson` |
| R8 | **Rescheduling:** several lessons can move to a new date and start time in one step, but only if none of them is paid or invoiced. Duration and fee are kept. | `RescheduleLessons` |
| R9 | **Outstanding** = sum of fees of lessons that are unpaid and not absent. | `Reports` |
| R10 | **Archiving:** archived students and groups leave lists and pickers but keep their history. A student can be deleted only if they have no lessons and no invoices (void ones included). A group can be deleted only if none of its lessons is invoiced; its other lessons are kept without the group. | `DeleteStudent`, `DeleteGroup` |
| R11 | **Backups** are versioned JSON. Restoring replaces all data and settings in one step. A file that is malformed or breaks a rule above (unknown currency, impossible lesson length, a lesson on a void or missing invoice…) is rejected and changes nothing. Older backup versions keep importing. | `BackupCodec`, `SqlBackupRepository` |
| R12 | **Issued documents:** an invoice's content (seller, bill-to, lines, totals, notes, formatting) is frozen when it is created. Later changes to settings, the student, group names or lesson notes never change it. Only the *PAID* mark is live. Invoices created before schema 3 are frozen the first time they are shared. An invoice is paid once it has lessons and all are paid. | `CreateInvoice`, `GetInvoiceDocument`, `InvoicePayment` |
| R13 | **Settings:** the currency is an ISO 4217 code and the default lesson length is 1–720 minutes, both when saving and when restoring. | `SettingsRules`, `SaveSettings` |

## 3. Data model

| Entity | Key fields | Notes |
|--------|-----------|-------|
| Student | name, class, guardian, phone, email, rate (cents + type), notes, archived | |
| StudentGroup | name, class, rate, member ids, archived | Members are stored in `group_members` |
| Lesson | student, group?, date, start time, minutes, fee, absent, notes, paidOn?, invoice? | `paidOn` is the payment record |
| Invoice | number, student, issue date, total, notes, voidedOn?, document? | Never deleted (R6). `document` is the content as issued (R12) |
| AppSettings | business details, currency, default lesson length, theme, invoice footer | SharedPreferences |

Version history:
- Database schema (`EduDatabase.VERSION`):
  - 1 = 2.0.0-alpha01
  - 2 = `invoices.voided_on` (2.0.0-alpha02)
  - 3 = `invoices.document` (2.0.0-alpha03)
- Backup format (`BackupCodec.VERSION`):
  - 1 = alpha01
  - 2 = adds `voidedOn`. v1 files still import.
  - 3 = adds `document`. v1 and v2 files still import.

## 4. Features

| Area | What the tutor can do |
|------|-----------------------|
| Home | See the month's lessons, hours, earned, paid and unpaid amounts. See the all-time outstanding total and who owes. |
| Students | Search, filter active or archived, add or edit, see a balance. Call or email. Record a payment, which settles all open lessons. Start an invoice. Archive or delete. |
| Groups | Create a group with members and a rate. Log a group lesson with attendance. |
| Lessons | Browse by month and filter by all, unpaid or not invoiced. Add, edit or delete a lesson. Tick a lesson as paid. **Long-press to select several**, then *Mark paid* or *Reschedule*. |
| Invoices | Create an invoice from a student's open lessons, optionally marking them paid. Share it as an A4 PDF. Void it. |
| Settings | Set the business details printed on invoices, the currency, the default lesson length, the theme and the invoice footer. Export or import a JSON backup. |

## 5. Out of scope and backlog

| Item | Status | Why |
|------|--------|-----|
| Multi-user login, roles, password reset | Dropped | Single-user device app. Android's lock screen protects the device. |
| Database encryption | Backlog | Needs SQLCipher, an approved dependency, and a key-management design. |
| Cloud sync / multi-device | Backlog | Needs a backend. Use backups to move data for now. |
| Greek (or other) translations | Backlog | UI strings are English and hard-coded. Moving them to `strings.xml` comes first. |
| Fiscal invoices (AADE myDATA) | Out of scope | The PDFs are payment statements, not fiscal documents. |
| Crash reporting / analytics | Dropped | No network permission by design. |
| Google Play Store release | Dropped | Not planned. Builds are installed from GitHub releases or CI (see README). Release-only items were removed in alpha03 (§8). |

## 6. Decision log

| # | Decision | Reason | Since |
|---|----------|--------|-------|
| D1 | Rebuild from scratch rather than migrate v0.x | The v0.x toolchain and enterprise layers were deprecated and heavy. No users had shipped data. | alpha01 |
| D2 | Framework `SQLiteOpenHelper` instead of Room | Rule: no unapproved dependencies. The schema is small. Migrations are hand-written and tested on an emulator. | alpha01 |
| D3 | A small typed back stack instead of Navigation Compose | Same rule. It gives a ViewModel store per screen and survives process death. | alpha01 |
| D4 | Constructor injection via `AppContainer` (no Hilt or Koin) | Project rule. The object graph is about 15 objects. | alpha01 |
| D5 | Money as `Long` cents, fees snapshotted | Correctness. v0.x used `Double` and recalculated history. | alpha01 |
| D6 | JSON backup codec written in `:core` | No serialization dependency, and the backup round trip is unit-tested. | alpha01 |
| D7 | Invoices are voided, not deleted | A parent may already hold the PDF, so a number must never mean two documents. | alpha02 |
| D8 | Instrumented tests on an emulator in CI | The SQLite layer and the whole UI flow can only be verified on Android. Test-only dependencies were approved. | alpha02 |
| D9 | No Play Store release. Release-only items were marked for deletion (alpha02), then removed once the owner confirmed (alpha03) | The app is installed from CI builds, so a release build, R8 rules and Play assets were unused weight. | alpha02 |
| D10 | Freeze each invoice's document when it is issued (R12) | D7's promise that a number means one document also covers its content, not just its number. | alpha03 |
| D11 | CI signs debug builds with the owner's key (secrets `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD`) and checks it against `SIGNING_CERT_SHA256` | CI debug builds are how the app is installed (D9). A throwaway key per build stops updates from installing over the previous build; the fingerprint check catches a wrong or replaced key. | alpha03 |
| D12 | Dated changelog versions are published as GitHub releases with the signed APK attached | CI artifacts expire after 14 days and are hard to find from a phone. A release is a permanent, named download whose notes are the changelog. Dating the section is the explicit release step, so merging alone never publishes. | alpha03 |

## 7. Dependency register

Runtime dependencies are the only ones that ship in the APK. Anything not listed needs approval
first. Record each approval here.

| Scope | Dependencies |
|-------|--------------|
| Runtime (approved) | Kotlin stdlib, kotlinx-coroutines, `androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose` / `-runtime-compose`, Compose BOM (`ui`, `foundation`, `material3`, `ui-tooling-preview`) |
| Debug only | `androidx.compose.ui:ui-tooling` |
| Unit tests (approved) | JUnit 4, `kotlinx-coroutines-test` |
| Instrumented tests (approved alpha02) | `androidx.test:runner`, `androidx.test.ext:junit`, `androidx.compose.ui:ui-test-junit4` |
| Awaiting approval (not added) | Room, SQLCipher, Navigation 3, Robolectric |

## 8. Removed Play Store items

Under D9 the app is not released on the Google Play Store. These release-only items were marked for
deletion in alpha02 and removed in alpha03, when the owner confirmed. `git show` on the alpha03 commit
brings any of them back.

| # | Item | Where it was |
|---|------|--------------|
| M1 | Ignore rule for `*.aab` (App Bundles are a Play upload format) | `.gitignore` |
| M2 | `release` build type with R8 minify and resource shrinking | `app/build.gradle.kts` |
| M3 | R8 keep rules | `app/proguard-rules.pro` |
| M4 | `assembleRelease` (and `lintVitalRelease`) in CI | `.github/workflows/ci.yml` |
| M5 | Release-signing note and the `assembleRelease` mention | `AGENTS.md` §4 and §5 |
| M6 | 512 px Play listing icon | `app/src/main/ic_launcher-playstore.png` |

The cost of removing M2: the R8 build was 1.9 MB, against 12.5 MB for the debug APK that is installed.
If the APK size ever matters, add R8 back as a signed, sideloadable build type rather than a Play one.

**Kept:**
- The `.gitignore` signing patterns (`*.jks`, `*.keystore`, `*.p12`, `keystore.properties`). They stop a
  key from ever being committed and cost nothing.
- `versionCode` and `versionName`. Android refuses to install an APK with a lower `versionCode` over a
  newer one, so sideloaded debug builds still need them. Settings shows `versionName`.
- `targetSdk`, now 37 (Android 17). Android 17's changes for apps that target it cover local network
  access, background audio, reflection on `static final` fields and `MessageQueue`, `RemoteViews`
  memory, and the orientation and resizability opt-out on large screens. The app uses none of these:
  it has no permissions, no audio, no notifications, no reflection, and an adaptive Compose layout.

**Found on the legacy branches (`main`, `Rebuild`), not changed:** the v0.x app there has:
- release signing configs read from `RELEASE_*` properties
- a `PLAY_INTEGRITY_ENABLED` flag (`Rebuild`)
- the `play-services-auth` dependency (`main`)
- ABI splits for release builds (`Rebuild`)
- the same 512 px Play listing icon as M6
- a Privacy Policy screen, which a Play listing requires
- `app/google-services.json` (Firebase project `eduinvoiceapp`). Its API key is in the git history, so it
  must be deleted in Google Cloud. v2 uses no Firebase, so it needs no replacement.
- a CI release build step (`Rebuild`)

Those branches are frozen, so these items go when the branches are deleted. Before v2 the repository had
no GitHub releases and one tag, `v0.28.0` (v0.x). v2 releases are tagged and published by
`.github/workflows/release.yml` (D12).

## 9. Origin

This spec started as the rebuild brief written from the request: *"Rebuild the app under new
specifications on a separate branch; Kotlin 2.x, Compose without XML layouts, ViewModel + StateFlow,
Gradle Kotlin DSL, no external dependencies unless approved, minimal APK, no Hilt."* The original
brief is in git history (commit `39cfa78a`). The v0.x app remains on the `Rebuild` and `main` branches.
