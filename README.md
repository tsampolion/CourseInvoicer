# Course Invoicer

[![CI](https://github.com/tsampolion/EduInvoiceApp/actions/workflows/ci.yml/badge.svg?branch=Rebuild_v2)](https://github.com/tsampolion/EduInvoiceApp/actions/workflows/ci.yml?query=branch%3ARebuild_v2)

An offline Android app for private tutors. Keep a register of students and groups, log lessons, see
who owes what, record payments and send PDF lesson statements. All data stays on the phone. Part of
the EduSuite apps, with Course Pacer. Formerly EduInvoice.

**Version:** 2.0.0 · **Branch:** `Rebuild_v2` · **Android:** 8.0 (API 26) or newer

How the app should behave is in [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md). What changed in each
version is in [`CHANGELOG.md`](CHANGELOG.md).

## Install a test build

1. Open the repository's *Releases* page and pick the newest release.
2. Download `course-invoicer-<version>.apk` from its *Assets*. It is the small, optimised build.
3. Copy it to the phone, open it, and allow installs from that app when asked.

Until the first v2 release is published, or for a build between releases, open the latest green
**CI** run for `Rebuild_v2` in the *Actions* tab instead. Download its `app-debug` artifact, a zip
holding `app-debug.apk`: a larger, slower debug build of the same code. Artifacts expire after 14
days. Release and debug builds install over each other.

A new build installs over the previous one and keeps your data, **as long as both were signed with
the owner's key**. If Android says *App not installed*, the builds have different keys: export a
backup, uninstall, install the new build and import the backup. Uninstalling deletes all data, so
always export a backup first.

## Using the app

| To… | Go to |
|-----|-------|
| Put your name, address and IBAN on invoices | Home → ⚙ Settings → *Your details* (saves as you type) |
| Add a student and their rate (per hour or per lesson) | Students → **+** |
| Log a lesson | **Log lesson** on Home or on a student. The fee is calculated from the rate and can be changed. |
| Log a lesson for a whole group, with attendance | Groups → **Log lesson** on the group |
| Mark lessons paid | Tick the box on a lesson. Or use a student's **Record payment** to settle everything they owe. |
| Move several lessons to another day | Lessons → long-press the lessons → **Reschedule** |
| Bill a student | Student → **Invoice**, or Invoices → **+**. Choose the lessons, then **Create**, then **Share PDF**. |
| Send an invoice again | Invoices → tap the invoice. It is the same document as the first time. |
| Cancel an invoice | Invoices → ✕ → **Void**. The number is never reused, and the lessons can be invoiced again. |
| See the month's earnings and who owes what | Home → **Dashboard** |
| Change or turn off the month-end reminder to send statements | Home → ⚙ Settings → *Reminders* |

**Back up regularly:** Settings → *Backup* → **Export** saves a JSON file. Keep it off the phone,
for example in cloud storage or email. **Import** replaces *all* data on the phone with the backup.

How fees, absences, locking and numbering work is described in
[`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md#2-business-rules).

## Tech stack

| | |
| --- | --- |
| Language | Kotlin 2.4.21, JVM target 17 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3, no XML layouts |
| Storage | Framework SQLite (`SQLiteOpenHelper`), SharedPreferences for settings |
| Build | Gradle 9.6.1 (wrapper), Android Gradle Plugin 9.4.0, Kotlin DSL, version catalog in `gradle/libs.versions.toml` |
| SDK | `minSdk 26`, `targetSdk 37`, `compileSdk 37` |
| Runtime libraries | Kotlin coroutines, AndroidX `core-ktx`, `activity-compose`, `lifecycle-*-compose`, Compose. Nothing else. |

Every dependency, including test-only ones, needs the owner's approval. The register is in
[`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) §7.

## Build from source

You need JDK 17 or newer and the Android SDK. Set `ANDROID_HOME`, or put `sdk.dir` in
`local.properties`. Android Studio provides both.

```bash
./gradlew :core:test                      # business rules (pure JVM)
./gradlew :app:testDebugUnitTest          # ViewModel and navigation tests
./gradlew :app:lintDebug                  # Android lint
./gradlew :app:assembleDebug              # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest  # needs a running emulator
```

Run the instrumented tests on an emulator, not on the phone you use: the tests themselves use a
throwaway database, but Gradle uninstalls the app when they finish, which deletes its data.

## CI and releases

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs once on every branch push, and on
  manual dispatch. One job runs the unit tests and lint, builds the debug APK and the minified
  release APK, signs both with the owner's key and checks the key, writes their sizes to the job
  summary and uploads them as the `app-debug` and `app-release` artifacts. A second job runs the
  emulator tests on API 35, then installs the minified APK and smoke-tests it.
- [`.github/workflows/release.yml`](.github/workflows/release.yml) runs after CI passes on
  `Rebuild_v2`. When the `CHANGELOG.md` section for the current `versionName` has a date, it tags
  `vX.Y.Z` and publishes a GitHub release with that section as notes and the minified APK attached.

### Signing key

Set this up once, as the repository owner. CI signs every `app-debug.apk` with your key and checks
its fingerprint. In *Settings → Secrets and variables → Actions* add:

| Kind | Name | Value |
|------|------|-------|
| Secret | `SIGNING_KEYSTORE_B64` | Your PKCS12 keystore (alias `release`), base64-encoded on one line |
| Secret | `SIGNING_PASSWORD` | The keystore password |
| Variable | `SIGNING_CERT_SHA256` | The certificate's SHA-256 fingerprint from `keytool -list -v` |
| Variable (optional) | `SIGNING_KEY_ALIAS` | Only if your alias is not `release` |

Create the key with:

```bash
keytool -genkeypair -keystore invoicer-release.p12 -storetype PKCS12 -alias release \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Course Invoicer"
```

Keep tested backups of the file and the password, and never commit them. Until the secrets exist, CI
warns and signs with a throwaway key, and the release workflow refuses to publish. The job summary
shows which certificate signed each build. Moving to your key needs one last export, uninstall and
import.

## Project structure

```text
.
├── core/                         # pure Kotlin, no Android: every business rule, tested on the JVM
│   └── src/
│       ├── main/…/core/          # model, usecase, repository, report, invoice, backup, format
│       ├── testFixtures/         # InMemoryRepositories: fakes shared by all JVM tests
│       └── test/                 # rule, report, invoice and backup tests
├── app/                          # the Android app
│   └── src/
│       ├── main/…/invoicer/      # AppContainer, MainActivity, data/ (SQLite, PDF, backups), ui/
│       ├── test/                 # ViewModel and navigation tests (JVM)
│       └── androidTest/          # emulator tests: SQL, migrations, end-to-end flow
├── docs/PRODUCT_SPEC.md          # business rules, features, decisions, dependency register
├── docs/ROADMAP.md               # planned work, with tasks and status
├── AGENTS.md                     # guide for contributors and coding agents (CLAUDE.md imports it)
├── gradle/libs.versions.toml     # version catalog
└── .github/workflows/            # ci.yml, release.yml
```

## Footprint

- No DI framework, database library, serialization library or navigation library. Objects are wired
  by hand in `AppContainer`. The app uses framework SQLite, its own JSON backup codec in `:core` and
  a small typed back stack. The spec's decision log (D2–D6) says why.
- `minSdk 26`, so `java.time` works without desugaring. The theme is a framework theme, not
  AppCompat, and icons are drawn in code.
- PDFs are drawn with the framework's `PdfDocument`, so no PDF library ships.
- Releases ship a minified build (D16): 1.9 MB, against 12.5 MB for the debug APK. More ideas to
  shrink it are in [`docs/ROADMAP.md`](docs/ROADMAP.md) item 11.

## Contributing

Read [`AGENTS.md`](AGENTS.md) first: it has the commands, architecture, rules, recipes and gotchas.
In short:

1. Branch off `Rebuild_v2`.
2. Put business rules in `:core`, with tests.
3. Run `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` before you
   open a pull request. CI runs the same line, plus the emulator tests.
4. Add your change to the `Unreleased` section of [`CHANGELOG.md`](CHANGELOG.md).
5. New dependencies need approval first.

## License

Proprietary. Copyright © 2025-2026 Dimitrios Tsampallas. All rights reserved. See
[LICENSE](LICENSE).

No one may use, copy, modify or distribute this app or its code without the owner's written
permission. AI systems (assistants, agents, bots and automated tools) may not change, copy or train
on the code unless the owner has explicitly authorised them for that work. Third-party libraries
keep their own licences. Versions published before this licence stay under the MIT License for
anyone who already received them.
