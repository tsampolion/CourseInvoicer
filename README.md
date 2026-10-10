# EduInvoice

A lean Android app for private tutors: keep a register of students and groups, log lessons, see who
owes what, record payments and send PDF invoices. Everything stays on the device.

**Version:** 2.0.0-alpha01 · **Branch:** `Rebuild_v2` (ground-up rebuild; v0.x lives on `Rebuild` and `main`)

## Features

- **Home**: monthly earned / paid / unpaid totals, total outstanding, and who owes.
- **Students**: search, archive, contact shortcuts, per-student balance, lessons and invoices.
- **Groups**: members, a group rate, and one-tap logging of a group lesson with attendance.
- **Lessons**: monthly list with filters, quick "paid" toggle, fee calculated from the rate.
- **Invoices**: sequential numbering (`2026-0001`), A4 PDF with your business details, share sheet.
- **Settings**: business details, currency, default lesson length, theme, JSON backup and restore.

Billing rules (hourly vs per-lesson, fee snapshots, absences, invoice locking) are documented in
[`docs/REBUILD_V2.md`](docs/REBUILD_V2.md#5-business-rules-preserved-and-fixed-where-v0x-was-wrong).

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.4 (AGP built-in Kotlin) |
| UI | Jetpack Compose + Material 3, no XML layouts |
| State | ViewModel + `StateFlow` |
| DI | Constructor injection via `AppContainer` (no Hilt or Koin) |
| Storage | Framework SQLite + SharedPreferences |
| PDF | `android.graphics.pdf.PdfDocument` |
| Build | Gradle 9.6 Kotlin DSL, version catalog, AGP 9.4 |
| SDK | min 26 · target 36 · compile 37 |

Runtime dependencies are limited to AndroidX core, activity-compose, lifecycle-compose and the Compose
BOM (see [`gradle/libs.versions.toml`](gradle/libs.versions.toml)).

## Project layout

```
core/   Pure Kotlin: models, billing, reports, invoice numbering, use cases, backup codec (+ tests)
app/    Android: SQLite repositories, PDF, Compose screens, ViewModels, navigation
docs/   REBUILD_V2.md: brief, decisions, parity map with v0.x
```

## Build and test

Requires JDK 17+ and an Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```bash
./gradlew :core:test                 # business rules, no Android needed
./gradlew :app:testDebugUnitTest     # ViewModel and navigation tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/
```

CI (`.github/workflows/ci.yml`) runs all of the above on every pull request.

## License

MIT. See [LICENSE](LICENSE).
