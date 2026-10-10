# AGENTS.md: working on EduInvoice

The playbook for coding agents and contributors. It covers the rules, where things live, how to make
common changes, and what to run before you push. For product behaviour, see
[`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md).

**Version:** 2.0.0-alpha02

## 1. Non-negotiable rules

1. **Kotlin 2.x, Jetpack Compose + Material 3. No XML layouts.** XML is allowed only for the manifest,
   `res/values*/`, `res/xml/` (FileProvider paths, backup rules) and launcher icons.
2. **State:** each screen has one ViewModel exposing `StateFlow<…UiState>`. Use `remember` only for
   transient UI state such as an open dialog or a text field.
3. **Gradle Kotlin DSL** with every version in `gradle/libs.versions.toml`.
4. **No new dependency without the owner's approval.** That includes test-only ones. Prefer framework
   APIs. Record every approval in the spec's dependency register (§7).
5. **No Hilt.** Wire objects by constructor injection in `AppContainer`. Koin would need approval.
6. **Money is `Money` (Long cents).** Never use `Double` for amounts.
7. **Business rules live in `:core`** and are tested there. ViewModels orchestrate; they don't decide.

## 2. Where things live

| Path | Contents |
|------|----------|
| `core/src/main/.../model` | `Money`, `Rate`, `Student`, `StudentGroup`, `Lesson`, `Invoice`, `AppSettings` |
| `core/src/main/.../usecase` | Rules that span repositories: `SaveLesson`, `CreateInvoice`, `VoidInvoice`, `RescheduleLessons`, … |
| `core/src/main/.../repository` | Repository interfaces |
| `core/src/main/.../report`, `invoice`, `backup` | Summaries, invoice numbering and document, backup codec |
| `core/src/testFixtures` | `InMemoryRepositories`: a fake implementing every repository, used by all JVM tests |
| `app/src/main/.../data` | `EduDatabase` (schema and migrations), SQL repositories, settings, backup files, PDF |
| `app/src/main/.../ui/<feature>` | `XxxScreen.kt` + `XxxViewModel.kt` per screen |
| `app/src/main/.../ui/nav/Navigation.kt` | `Route` list and the back stack |
| `app/src/main/.../ui/common` | Shared components, icons, `appViewModel {}`, `launchCatching` |
| `app/src/test` | JVM tests for ViewModels and navigation |
| `app/src/androidTest` | Emulator tests: SQL repositories, migrations, end-to-end flow |

## 3. Recipes

**Add a field to an entity:**
1. Add it to the model in `:core`.
2. Update the in-memory fake and the backup codec. Bump `BackupCodec.VERSION` and keep decoding older
   files.
3. Bump `EduDatabase.VERSION` and add the `ALTER TABLE` to `onUpgrade`.
4. Update `Mappers.kt`.
5. Add a migration test from a frozen copy of the previous schema in
   `app/src/androidTest/.../data/SqlRepositoriesTest.kt`.

**Add a business rule:**
1. Write a use case in `core/usecase`.
2. Throw `DomainException` with a user-readable message when the rule is broken.
3. Add tests in `core/src/test`.
4. Call it from the ViewModel through `launchCatching`.

**Add a screen:**
1. Add a `Route` and its `encode`/`decode` entries, and extend `NavigatorTest`.
2. Write `XxxViewModel(…repositories)` and `XxxScreen(…, viewModel = appViewModel { XxxViewModel(it.…) })`.
3. Dispatch the route in `ui/EduInvoiceApp.kt`.

**Write to the database:**
- Go through `db.write(Table…)` and list every table you touch, so open screens refresh.
- Keep multi-row changes in one `db.write` block so they are atomic.

**Propose a dependency:** don't add it. Explain what it replaces, its APK size impact and why the
framework isn't enough, then wait for approval.

## 4. Before you push

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- `:core:test` runs with no Android SDK. It is the minimum check in restricted sandboxes.
- CI (`.github/workflows/ci.yml`) runs the line above plus `assembleRelease` with R8. A second job
  boots an API 35 emulator and runs `connectedDebugAndroidTest`. Lint findings are printed into the
  CI log.
- Every behaviour change comes with a test: `:core` for rules, `app/src/test` for ViewModels,
  `app/src/androidTest` for SQL and UI flows.

**Known sandbox limits:** some cloud sandboxes block `dl.google.com`, which also blocks Google Maven.
There, only `:core` builds locally, and CI is the source of truth for `:app`.

## 5. Versions and releases

- Every PR adds an entry to `CHANGELOG.md`.
- A release bumps `versionCode` and `versionName` in `app/build.gradle.kts`. Keep the version shown
  in `README.md`, this file and `CHANGELOG.md` the same.
- Release signing is **not configured yet**: `assembleRelease` produces an unsigned APK. Adding a
  keystore needs a decision on where the key lives. Never commit keystores or passwords.

## 6. Pull requests

- Title: `[feat|fix|refactor|docs|build|test](scope): summary`
- Fill in `.github/pull_request_template.md`.
- Target `Rebuild_v2`.
- CI must be green before review.
