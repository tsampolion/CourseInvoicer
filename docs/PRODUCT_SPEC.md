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
| R3 | **Group lessons:** one row per member, billed at the *group* rate. Absent members get an `absent` row that is never charged. | `LogGroupLesson` |
| R4 | **Invoices:** one student per invoice. Only lessons that are not invoiced and not absent can be added. A lesson is on at most one invoice. The database guards this in the same transaction. | `CreateInvoice`, `SqlInvoiceRepository` |
| R5 | **Numbering:** invoice numbers run `YYYY-NNNN` per calendar year. They are **never reused**. | `InvoiceNumbering` |
| R6 | **Voiding:** an invoice is voided, never deleted. Voiding releases its lessons so they can be invoiced again. The void invoice and its number stay visible. A void invoice cannot be shared. | `VoidInvoice` |
| R7 | **Locking:** while a lesson is on an invoice, its student, date, time, duration, fee and attendance are read-only, and it cannot be deleted. Notes and the paid flag stay editable. | `SaveLesson`, `DeleteLesson` |
| R8 | **Rescheduling:** several lessons can move to a new date and start time in one step, but only if none of them is paid or invoiced. Duration and fee are kept. | `RescheduleLessons` |
| R9 | **Outstanding** = sum of fees of lessons that are unpaid and not absent. | `Reports` |
| R10 | **Archiving:** archived students and groups leave lists and pickers but keep their history. A student can be deleted only if they have no lessons. | `DeleteStudent` |
| R11 | **Backups** are versioned JSON. Restoring replaces all data in one transaction. A malformed file changes nothing. Older backup versions keep importing. | `BackupCodec`, `SqlBackupRepository` |

## 3. Data model

| Entity | Key fields | Notes |
|--------|-----------|-------|
| Student | name, class, guardian, phone, email, rate (cents + type), notes, archived | |
| StudentGroup | name, class, rate, member ids, archived | Members are stored in `group_members` |
| Lesson | student, group?, date, start time, minutes, fee, absent, notes, paidOn?, invoice? | `paidOn` is the payment record |
| Invoice | number, student, issue date, total, notes, voidedOn? | Never deleted (R6) |
| AppSettings | business details, currency, default lesson length, theme, invoice footer | SharedPreferences |

Version history:
- Database schema (`EduDatabase.VERSION`):
  - 1 = 2.0.0-alpha01
  - 2 = `invoices.voided_on` (2.0.0-alpha02)
- Backup format (`BackupCodec.VERSION`):
  - 1 = alpha01
  - 2 = adds `voidedOn`. v1 files still import.

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

## 8. Origin

This spec started as the rebuild brief written from the request: *"Rebuild the app under new
specifications on a separate branch; Kotlin 2.x, Compose without XML layouts, ViewModel + StateFlow,
Gradle Kotlin DSL, no external dependencies unless approved, minimal APK, no Hilt."* The original
brief is in git history (commit `39cfa78a`). The v0.x app remains on the `Rebuild` and `main` branches.
