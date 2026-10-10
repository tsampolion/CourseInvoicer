# EduInvoice v2 — Rebuild Brief

This is the optimized version of the original rebuild request. It is written as a self-contained brief
that an engineer or coding agent can execute without further context, and it doubles as the record of
the decisions taken for v2.

> **Original request:** "This is a very old app and the way it was created is now deprecated. Rebuild it
> under new specifications on a separate branch (like `Rebuild_v2`). Kotlin 2.x, Jetpack Compose (no XML
> layouts), simple Compose state / ViewModel + StateFlow, Gradle Kotlin DSL, no external dependencies
> unless approved, minimal APK, avoid Hilt (Koin or constructor injection)."

---

## 1. Context

EduInvoice is a single-user Android app for a private tutor: keep a register of students and groups,
log lessons, see who owes what, record payments and issue PDF invoices.

The v0.x code base (branches `Rebuild` and `main`) has ~31k lines across three modules, Groovy Gradle,
Hilt, Room + SQLCipher, Firebase and many "enterprise" layers (sync, conflict resolution, concurrency
controllers, retry managers). It targets a 2025 toolchain (AGP 8.8, Kotlin 2.1). Much of it no longer
matches current Android guidance, and it has billing bugs (see §5). **Rebuild from a clean slate. Keep
the business behaviour, not the code.**

## 2. Goal

Ship a lean v2 that a tutor can use end-to-end:

> add students → log individual or group lessons → see what is owed → record payments →
> issue and share a PDF invoice → back up and restore the data.

## 3. Hard rules (Lightweight Android Project Rules)

| Rule | How v2 satisfies it |
|------|---------------------|
| Kotlin 2.x | Kotlin 2.4.21 via AGP 9 built-in Kotlin |
| Jetpack Compose, no XML layouts | Compose + Material 3 only. XML is limited to the manifest, `values/` resources, the FileProvider path file and launcher icons |
| Simple Compose state / ViewModel + StateFlow | One ViewModel per screen exposing `StateFlow<UiState>`; `remember` for transient UI state |
| Gradle Kotlin DSL | `*.gradle.kts` + `gradle/libs.versions.toml` |
| No external dependencies unless approved | Only the baseline in §4. Framework APIs replace libraries (table below) |
| Minimal APK | R8 + resource shrinking on release, no native libs, no icon packs, no analytics SDKs |
| Avoid Hilt; Koin or constructor injection | Constructor injection through a hand-written `AppContainer`. Koin is not used (it would need approval) |

**Framework replacements for libraries that would otherwise need approval:**

| Need | v0.x used | v2 uses (zero dependencies) |
|------|-----------|-----------------------------|
| Persistence | Room + SQLCipher | `SQLiteOpenHelper` + `Flow` invalidation |
| Settings | DataStore | `SharedPreferences` behind a `StateFlow` |
| Navigation | Navigation Compose | ~150-line typed back stack with per-entry `ViewModelStore` and saveable state |
| DI | Hilt | `AppContainer` + constructor injection |
| JSON backup | kotlinx.serialization | Small JSON codec in `:core` |
| PDF | PdfDocument (kept) | `android.graphics.pdf.PdfDocument` |
| Icons | material-icons-extended | A handful of hand-built `ImageVector`s |
| Crash reporting | Firebase | None (no network permission at all) |

## 4. Approved dependency baseline

Runtime: Kotlin stdlib, kotlinx-coroutines (transitive), `androidx.core:core-ktx`,
`androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-{viewmodel,runtime}-compose`,
Compose BOM (`ui`, `foundation`, `material3`, `ui-tooling-preview`).
Debug-only: `ui-tooling`. Test-only: JUnit 4, `kotlinx-coroutines-test`.

Anything else must be proposed with its size and benefit, and approved before it is added. Candidates
held back for v2.x: Room (type-safe SQL, migrations), SQLCipher (encryption at rest), Navigation 3,
Robolectric (JVM tests for the SQLite layer).

## 5. Business rules (preserved, and fixed where v0.x was wrong)

1. **Billing**: `HOURLY` → fee = rate × minutes / 60; `PER_LESSON` → fee = rate. Money is stored as
   integer cents, rounded HALF_UP. *(v0.x used `Double`, and its revenue screen ignored `PER_LESSON`.)*
2. **Fee snapshot**: each lesson stores its fee when it is logged. Changing a student's rate never
   rewrites past lessons. *(v0.x recomputed history from the current rate.)*
3. **Group lessons**: one lesson row per member, billed at the group's rate. Absent members get a row
   marked `absent` that is never charged, so attendance history is kept.
4. **Invoices**: one student per invoice. Only lessons that are not invoiced and not absent can go on
   an invoice. Numbers are sequential per year (`2026-0001`). A lesson belongs to at most one invoice.
   Deleting an invoice releases its lessons. Issuing can optionally mark the lessons as paid.
5. **Locking**: an invoiced lesson cannot be deleted, and its student, date, duration, fee and
   attendance cannot change. Notes and the paid flag can still change.
6. **Archiving**: archived students and groups disappear from pickers but keep their history. A
   student can be deleted only when they have no lessons.
7. **Outstanding balance** = sum of fees of lessons that are unpaid and not absent.

## 6. Scope

**In v2.0**

- **Home**: month selector, lessons/hours, earned/paid/unpaid for the month, total outstanding,
  list of students who owe.
- **Students**: search, active/archived filter, balance per student. Detail screen with contact
  details, rate, balance, lessons, invoices and actions (log lesson, record payment, create invoice,
  edit, archive, delete).
- **Groups**: create or edit name, class, rate and members. Log a group lesson with attendance.
- **Lessons**: list per month with filters (all, unpaid, not invoiced). Add, edit or delete, toggle paid.
- **Invoices**: create from a student's open lessons, list, share PDF, delete.
- **Settings**: business details printed on invoices, currency, default lesson length, theme,
  export or import a JSON backup.

**Dropped or deferred** (documented, not forgotten): multi-user login and password reset
(single-user device app), database encryption (needs SQLCipher approval), cloud sync and conflict
resolution, Firebase, bulk reschedule, "enterprise" infrastructure, localisation (English first;
strings are ready to be extracted).

## 7. Architecture

```
:core  (pure Kotlin/JVM, no Android)          :app  (Android)
  model/      Money, Student, Lesson, ...       data/      SQLite repositories, prefs, backup, PDF
  billing/    fee rules                         ui/        Compose screens + ViewModels
  report/     summaries, balances               ui/nav/    back stack + per-entry ViewModelStore
  invoice/    numbering, document builder       AppContainer (constructor injection)
  usecase/    rules that span repositories
  repository/ interfaces
  backup/     snapshot + JSON codec
  testFixtures/ in-memory repositories
```

- `:core` holds every business rule and is unit-tested without an emulator.
- `:app` depends on `:core`. ViewModels stay thin: they map repository flows into UI state and call
  use cases.
- Data flows one way: repositories expose `Flow`, ViewModels expose `StateFlow<UiState>`, and
  screens send events back as lambda calls.

## 8. Toolchain

AGP 9.4.0 · Gradle 9.6.1 · Kotlin 2.4.21 (built-in Kotlin) · Compose BOM 2026.09.00 ·
compileSdk 37 · targetSdk 36 · minSdk 26 · JDK 17+.

## 9. Process

1. Branch `Rebuild_v2` from `Rebuild`. Do the work on a feature branch and open a PR into `Rebuild_v2`.
2. Remove the v0.x sources from the branch. They stay in git history and on `Rebuild` and `main`.
3. Build `:core` first, test-driven. Then the data layer, then the UI.
4. Verify on every push with CI (`.github/workflows/ci.yml`):
   `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
5. Update the docs: `README.md`, `AGENTS.md` (new rules), `CHANGELOG.md` (`2.0.0-alpha01`) and this file.

## 10. Definition of done

- [ ] All §6 v2.0 features work end-to-end on a device or emulator.
- [ ] `:core` unit tests cover billing, reports, invoice numbering, use-case rules and the backup round trip.
- [ ] CI is green: unit tests, lint and debug assemble.
- [ ] No dependency outside §4. No XML layouts. No Hilt or Koin.
- [ ] README, AGENTS and CHANGELOG describe v2 and agree on the version.

## 11. Parity map (v0.x → v2)

| v0.x feature | v2 |
|--------------|----|
| Students CRUD, archive, search | ✅ |
| Classes screen | Class is a student field with suggestions, filterable in search |
| Groups with members and rate | ✅ |
| Group lessons with absences | ✅ (absences kept as non-billable lesson rows) |
| Lesson CRUD, paid flag | ✅ (fee snapshot, locking) |
| Payment batches | "Record payment" marks a student's open lessons paid on a date |
| Reschedule batches | ❌ deferred. Edit lessons individually |
| Invoice creation + PDF + share | ✅ (sequential numbering, business header) |
| Past invoices, edit invoice master | List, share and delete. Edit = delete and re-issue |
| Revenue dashboard, debts, earnings by class | Home: month summary + outstanding by student |
| Settings: theme, currency, PDF theme | Theme, currency, business details, default duration |
| JSON backup / restore | ✅ |
| Users, login, roles, password reset | ❌ dropped (single-user app) |
| SQLCipher, Firebase, sync, enterprise infra | ❌ dropped |
