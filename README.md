# EduInvoice

An offline Android app for private tutors. Keep a register of students and groups, log lessons, see
who owes what, record payments and send PDF invoices. All data stays on the phone.

**Version:** 2.0.0-alpha02 · **Branch:** `Rebuild_v2` · **Android:** 8.0 (API 26) or newer

## Install a test build

1. Open the latest green **CI** run for `Rebuild_v2` in the repository's *Actions* tab.
2. Download the `app-debug` artifact and unzip it.
3. Copy `app-debug.apk` to the phone, open it, and allow installs from that app when asked.

Debug builds install over each other and keep your data. Uninstalling the app deletes its data, so
export a backup first (see below).

## Using the app

| To… | Go to |
|-----|-------|
| Put your name, address, tax ID and IBAN on invoices | Home → ⚙ Settings → *Your details* → **Save** |
| Add a student and their rate (per hour or per lesson) | Students → **+** |
| Log a lesson | **Log lesson** on Home or on a student. The fee is calculated from the rate and can be changed. |
| Log a lesson for a whole group, with attendance | Groups → **Log lesson** on the group |
| Mark lessons paid | Tick the box on a lesson. Or use a student's **Record payment** to settle everything they owe. |
| Move several lessons to another day | Lessons → long-press the lessons → **Reschedule** |
| Bill a student | Student → **Invoice**, or Invoices → **+**. Choose the lessons, then **Create**, then **Share PDF**. |
| Cancel an invoice | Invoices → ✕ → **Void**. The number is never reused, and the lessons can be invoiced again. |
| See who owes what | Home → *Who owes* |

**Back up regularly:** Settings → *Backup* → **Export** saves a JSON file. Keep it off the phone,
for example in cloud storage or email. **Import** replaces *all* data on the phone with the backup.

How fees, absences, locking and numbering work is described in
[`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md#2-business-rules).

## Build from source

You need JDK 17 or newer and the Android SDK. Set `ANDROID_HOME`, or put `sdk.dir` in
`local.properties`.

```bash
./gradlew :core:test                    # business rules, no Android SDK needed
./gradlew :app:testDebugUnitTest        # ViewModel and navigation tests
./gradlew :app:lintDebug                # Android lint
./gradlew :app:assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest  # needs a running emulator or device
```

## Project map

```
core/   Pure Kotlin business rules: models, billing, reports, invoicing, use cases, backup format
app/    Android app: SQLite storage, PDF rendering, Compose screens, ViewModels, navigation
docs/   PRODUCT_SPEC.md: business rules, features, decisions, dependency register
.github/workflows/ci.yml   Unit tests, lint and builds on one runner. Emulator tests on a second runner.
```

| Document | Read it when you… |
|----------|-------------------|
| [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) | need to know how the app should behave, or why it was built this way |
| [`AGENTS.md`](AGENTS.md) | are changing code: rules, recipes and the checks to run |
| [`CHANGELOG.md`](CHANGELOG.md) | want to know what changed in each version |

## License

MIT. See [LICENSE](LICENSE).
