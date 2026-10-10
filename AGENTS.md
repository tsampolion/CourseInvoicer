# AGENTS.md: EduInvoice v2

Guidance for humans and coding agents working on this repository.

**Version:** 2.0.0-alpha01 · **Brief and decisions:** [`docs/REBUILD_V2.md`](docs/REBUILD_V2.md)

## 1. Project rules (Lightweight Android Project Rules)

- **Language:** Kotlin 2.x.
- **UI:** Jetpack Compose + Material 3 only. **No XML layouts.** XML is limited to the manifest,
  `values*/`, `xml/file_paths.xml` and launcher icons.
- **State:** ViewModel exposing `StateFlow<UiState>`; plain Compose state (`remember`) for transient UI.
- **Build:** Gradle Kotlin DSL (`*.gradle.kts`) with `gradle/libs.versions.toml`.
- **Dependencies:** do not add external dependencies unless approved. The approved baseline is the
  catalog as it stands. Prefer framework APIs (SQLite, SharedPreferences, PdfDocument). Keep the APK small.
- **DI:** no Hilt. Use constructor injection wired in `AppContainer` (Koin would need approval).

## 2. Architecture

- `:core` is pure Kotlin/JVM. Every business rule lives here: `model/`, `report/`, `invoice/`,
  `usecase/`, `repository/` (interfaces), `backup/`. In-memory repositories live in `testFixtures`.
- `:app` implements the repositories (`data/`), renders PDFs, and holds Compose screens and
  ViewModels (`ui/<feature>/`). Navigation is the small typed back stack in `ui/nav/Navigation.kt`.
- Money is `Money` (Long cents). Never use `Double` for amounts.
- Lesson fees are snapshotted when a lesson is logged. Invoiced lessons are locked (`SaveLesson`).
- Composables take `modifier: Modifier = Modifier` as their first optional parameter.
- ViewModels are created with `appViewModel { container -> ... }` so each screen entry owns its ViewModel.

## 3. Build, test, lint

```bash
./gradlew :core:test :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug                       # Android lint
./gradlew :app:assembleDebug                   # build the APK
```

`:core:test` needs no Android SDK. Add or adjust tests with every behaviour change: rules in `:core`,
ViewModels in `app/src/test` using `InMemoryRepositories` and `MainDispatcherRule`.

## 4. Database

`EduDatabase` (framework `SQLiteOpenHelper`) is at schema version 1, the v2 baseline. Any schema
change must bump `VERSION` and add a migration in `onUpgrade`. Never drop user data. Repositories
must call `db.write(...)` with every table they touch so observers refresh.

## 5. Changelog and versions

Add an entry to `CHANGELOG.md` for every pull request. When releasing, bump `versionCode` and
`versionName` in `app/build.gradle.kts`, and keep the version in `README.md`, `AGENTS.md` and
`CHANGELOG.md` in sync.

## 6. Pull requests

```
[feat|fix|refactor|docs|build](scope): short summary

- WHAT changed
- WHY it matters
- HOW to test
```

Checklist: tests pass, lint is clean, no new dependencies without approval, no XML layouts,
changelog updated.
