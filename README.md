# EduInvoice

An offline Android app for private tutors. Keep a register of students and groups, log lessons, see
who owes what, record payments and send PDF invoices. All data stays on the phone.

**Version:** 2.0.0-alpha03 · **Branch:** `Rebuild_v2` · **Android:** 8.0 (API 26) or newer

## Install a test build

1. Open the repository's *Releases* page and pick the newest release (alpha and beta builds are marked
   *Pre-release*).
2. Download `eduinvoice-<version>.apk` from its *Assets*.
3. Copy it to the phone, open it, and allow installs from that app when asked.

For a build between releases, open the latest green **CI** run for `Rebuild_v2` in the *Actions* tab
and download its `app-debug` artifact (a zip holding `app-debug.apk`). Artifacts expire after 14 days.

A new build installs over the previous one and keeps your data, **as long as both were signed with
the owner's key**. If Android says *App not installed*, the builds have different keys: export a
backup, uninstall, install the new build and import the backup. Uninstalling deletes all data, so
always export a backup first.

**Signing key setup** (repository owner, once). CI signs every `app-debug.apk` with your key and
checks its fingerprint. In *Settings → Secrets and variables → Actions* add:

| Kind | Name | Value |
|------|------|-------|
| Secret | `SIGNING_KEYSTORE_B64` | Your PKCS12 keystore (alias `release`), base64-encoded on one line |
| Secret | `SIGNING_PASSWORD` | The keystore password |
| Variable | `SIGNING_CERT_SHA256` | The certificate's SHA-256 fingerprint from `keytool -list -v` |
| Variable (optional) | `SIGNING_KEY_ALIAS` | Only if your alias is not `release` |

Create the key with `keytool -genkeypair -keystore eduinvoice-release.p12 -storetype PKCS12 -alias
release -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=EduInvoice"`, keep tested backups of the
file and the password, and never commit them. Until the secrets exist, CI warns and signs with a
throwaway key. The job summary shows which certificate signed each build. Moving to your key needs
one last export, uninstall and import.

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
./gradlew :app:connectedDebugAndroidTest  # needs a running emulator
```

Run the instrumented tests on an emulator, not on the phone you use: the tests themselves use a
throwaway database, but Gradle uninstalls the app when they finish, which deletes its data.

## Project map

```
core/   Pure Kotlin business rules: models, billing, reports, invoicing, use cases, backup format
app/    Android app: SQLite storage, PDF rendering, Compose screens, ViewModels, navigation
docs/   PRODUCT_SPEC.md: business rules, features, decisions, dependency register
.github/workflows/ci.yml        Every push: unit tests, lint and the signed debug APK; emulator tests on a second runner
.github/workflows/release.yml   After CI passes on Rebuild_v2: tags a dated version and publishes its APK
```

| Document | Read it when you… |
|----------|-------------------|
| [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) | need to know how the app should behave, or why it was built this way |
| [`AGENTS.md`](AGENTS.md) | are changing code: rules, recipes and the checks to run |
| [`CHANGELOG.md`](CHANGELOG.md) | want to know what changed in each version |

## License

MIT. See [LICENSE](LICENSE).
