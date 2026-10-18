# Course Invoicer: Product specification

This is the reference for **what the app does and why**. When behaviour changes, update this file
in the same pull request. Engineering rules (how to build it) live in [`AGENTS.md`](../AGENTS.md).

## 1. Purpose

Course Invoicer (formerly EduInvoice, D17) is a single-user, offline Android app for a private
tutor, part of the EduSuite apps with Course Pacer:

1. Keep a register of students and groups.
2. Log lessons.
3. See who owes what.
4. Record payments.
5. Issue lesson statements as PDFs. Statements are informational, not fiscal documents (D20). In code
   and in the database they are still called invoices.
6. Back the data up.

Nothing leaves the device unless the tutor shares a statement or exports a backup.

## 2. Business rules

These rules are enforced in `:core` and, where listed, in the SQL layer of `:app`. Tests cover them.
The rule numbers are referenced from code comments and PRs.

| # | Rule | Enforced in |
|---|------|-------------|
| R1 | **Billing:** `HOURLY` fee = rate × minutes ÷ 60. `PER_LESSON` fee = rate. Money is integer cents, rounded half-up. Rates are at most €100,000 per hour or per lesson (golden table: `RateGoldenTest`). | `Rate.feeFor`, `SaveStudent`, `SaveGroup`, `BackupCodec` |
| R2 | **Fee snapshot:** a lesson stores its fee when it is logged. Changing a rate never rewrites past lessons. | `Lesson.fee`, `SaveLesson` |
| R3 | **Attendance and absences:** a lesson is held or absent. An absence is either *excused* (not charged, so never paid either) or a *late cancellation* (under 24 hours' notice), charged the full fee like a held lesson. An absence can carry a reason. Group lessons have one row per member, billed at the *group* rate; each member is marked Present, Absent or Late cancel in the attendance list. A lesson that was held has no absence charge or reason. | `Lesson.charge`, `LogGroupLesson`, `SaveLesson`, `MarkPaid` (payment guard) |
| R4 | **Statements:** one student per statement. Any lessons of that student that are on no other statement can be added, absences included: an excused absence is a 0.00 line, a late cancellation is charged. A statement whose lessons are all excused totals 0.00 and is allowed. A lesson is on at most one statement. The database guards this in the same transaction. Marking the lessons paid when issuing pays only the charged ones. | `CreateInvoice`, `SqlInvoiceRepository` |
| R5 | **Numbering:** statement numbers run `YYYY-NNNN` per calendar year. They are **never reused**. | `InvoiceNumbering` |
| R6 | **Voiding:** a statement is voided, never deleted. Voiding releases its lessons so they can go on a new statement. The void statement and its number stay visible. A void statement cannot be shared. | `VoidInvoice`, `SqlInvoiceRepository.voidInvoice`, `GetInvoiceDocument` |
| R7 | **Locking:** while a lesson is on a statement, its student, group, date, time, duration, fee, attendance and absence charge are read-only, and it cannot be deleted. Notes, the absence reason and the paid flag stay editable. | `SaveLesson`, `DeleteLesson`, reschedule undo |
| R8 | **Rescheduling:** several lessons can move to a new date and start time in one step, but only if none of them is paid or on a statement. Duration and fee are kept. | `RescheduleLessons` |
| R9 | **Outstanding** = sum of the fees of lessons that are unpaid and *charged*: held or late-cancelled, with a fee above zero. Only charged lessons can be paid. Home, Dashboard and Students sum it in SQL with the same rule (`CHARGED_LESSON`). | `Lesson.isOutstanding`, `Reports`, `SqlLessonRepository.observeBalances`, `MarkPaid`, `setPaid` |
| R10 | **Archiving:** archived students and groups move to the *Archived* filter and keep their history. Archived students leave the student pickers, and an archived group has no *Log lesson* button. A student can be deleted only if they have no lessons and no invoices (void ones included). A group can be deleted only if none of its lessons is invoiced; its other lessons are kept without the group. | `DeleteStudent`, `DeleteGroup`, `ArchiveStudent`, `ArchiveGroup` |
| R11 | **Backups** are versioned JSON. Restoring replaces all data in one transaction, then applies the backup's settings. Once a restore starts, cancelling it does not stop it half-way. A file that is malformed or breaks a rule above (an excessive rate, impossible lesson length, an unknown absence charge, a paid excused absence, a lesson on a void or missing statement…) is rejected and changes nothing. Older backup versions keep importing. | `BackupCodec`, `SqlBackupRepository` |
| R12 | **Issued documents:** a statement's content (title, disclaimer, seller, bill-to, lines and their notes, totals, notes, formatting) is frozen when it is created. Later changes to settings, the student, group names or lesson notes never change it. Only the *PAID* mark is live. Statements created before schema 3 are frozen the first time they are shared; documents issued before 2.0.0 have no title or disclaimer and keep rendering as issued. A statement is paid once it has lessons and every charged one is paid, so one of excused absences only is paid at once. | `CreateInvoice`, `GetInvoiceDocument`, `InvoicePayment` |
| R13 | **Settings:** the default lesson length is 1–720 minutes, both when saving and when restoring. Settings save as they are typed, each field checked on its own: invalid input is never stored, the stored value stays in use and the field shows an inline error, while valid fields still save. | `SettingsRules`, `SaveSettings`, `SettingsViewModel` |
| R14 | **Statement content:** the heading is «ΕΝΗΜΕΡΩΤΙΚΗ ΚΑΤΑΣΤΑΣΗ ΜΑΘΗΜΑΤΩΝ / Lesson statement». The seller block has the tutor's name, phone and email, and the address only when *Show address on statements* is on (off by default). Bill-to is the student and "c/o" the guardian. Each line has date, description, duration and the amount charged (0.00 for an excused absence); an absence line adds the note "Absent — not charged" or "Late cancellation (under 24 h) — charged", followed by the reason if one was given. Then the total, the statement notes, the IBAN payment note and the footer. The non-fiscal disclaimer (D20) appears boxed under the header and in every page footer: «ΤΟ ΠΑΡΟΝ ΔΕΝ ΑΠΟΤΕΛΕΙ ΦΟΡΟΛΟΓΙΚΟ ΣΤΟΙΧΕΙΟ. Δεν είναι τιμολόγιο ούτε απόδειξη παροχής υπηρεσιών κατά τον ν. 4308/2014 (ΕΛΠ) και δεν διαβιβάζεται στην ΑΑΔΕ (myDATA). Για κάθε πληρωμή εκδίδεται χωριστά νόμιμη απόδειξη.» The PDF is named `statement-<number>.pdf`. | `InvoiceDocumentBuilder`, `InvoicePdfWriter` |
| R15 | **Month-end reminder:** opening the app on Home asks *Time to invoice* the month (e.g. *Time to invoice March*) while it has lessons on no statement (absences included). A month's reminder runs from the 29th to its last day; February, which has fewer than 30 days, starts on its second-to-last day (the 27th, or the 28th in a leap year). December, June and the Easter month start on the 21st, each while its switch is on. The Easter month is the month of Orthodox Easter when Easter falls on or after the 21st, else the month before. During days 1–7 of the next month the previous month is still asked about. *Remind me tomorrow* hides it until the next day, *Done for this month* stops it for that month (and earlier ones), and *Create statements* or Back only hide it until the app is next opened. A master switch turns every reminder off. It is a dialog inside the app, not a notification. | `InvoiceReminder`, `HomeViewModel`, `LessonRepository.observeUninvoicedSummary` |

## 3. Data model

| Entity | Key fields | Notes |
|--------|-----------|-------|
| Student | name, class, guardian, phone, email, rate (cents + type), notes, archived | |
| StudentGroup | name, class, rate, member ids, archived | Members are stored in `group_members` |
| Lesson | student, group?, date, start time, minutes, fee, absent, absence charge (`NONE` or `LATE_CANCELLATION`), absence note, notes, paidOn?, invoice? | `paidOn` is the payment record. The absence fields mean something only while `absent` is set (R3) |
| Invoice | number, student, issue date, total, notes, voidedOn?, document? | Shown as a *statement*. Never deleted (R6). `document` is the content as issued (R12, R14) |
| AppSettings | business details (name, address, phone, email, IBAN), default lesson length, theme, statement footer (`invoiceFooter`), show address on statements, reminder switches (on, early December, early June, early Easter month) | SharedPreferences. The Tax ID was removed in 2.0.0 |
| Reminder state | the month marked done, the snooze date | Its own SharedPreferences file `reminder_state`, on this device only: never in a backup (R15) |

Version history:

| Version | Database schema (`InvoicerDatabase.VERSION`) | Backup format (`BackupCodec.VERSION`) | Change |
|---------|-----------------------------------------|---------------------------------------|--------|
| 2.0.0-alpha01 | 1 | 1 | v2 baseline. |
| 2.0.0-alpha02 | 2 | 2 | Adds `invoices.voided_on` (backup field `voidedOn`) for voiding (R6). Schema 1 upgrades automatically. v1 backups still import. |
| 2.0.0-alpha03 | 3 | 3 | Adds `invoices.document` (backup field `document`), the content as issued (R12). Schema 2 upgrades automatically. v1 and v2 backups still import. |
| 2.0.0 | 3 | 3 | Drops the settings field `taxId`: no longer written, and ignored when an older backup has it. |
| 2.0.0 | 4 | 4 | Adds `lessons.absence_charge` and `lessons.absence_note` (backup fields `absenceCharge`, `absenceNote`) for late cancellations (R3), the settings field `showAddressOnStatements`, and optional `title`, `disclaimer` and line `note` in documents (R14). Schema 3 upgrades automatically, with every existing absence excused. v1 to v3 backups still import. It also adds the settings fields `remindersEnabled`, `remindEarlyDecember`, `remindEarlyJune` and `remindEarlyEaster` (R15); a backup without them restores them switched on. |

A damaged database is kept as `course_invoicer.db.bad` before the app starts empty with a warning.
Any journal files are kept beside it. If preservation fails, the original is left in place and the
app shows an error. An older build refuses to open newer data. No schema version changes.

Form drafts use `SavedStateHandle` values in a saved-state registry per navigation entry. After
process death the form restores the draft and checks the lesson's current statement lock (R7).

## 4. Features

| Area | What the tutor can do |
|------|-----------------------|
| Home | The launcher the app opens on: the EduSuite home header (logo, *Welcome to Course Invoicer* over today's date, a Settings button), then a two-column grid of section tiles, Lessons and Statements tall, each with a live figure (Dashboard: the outstanding total; Students: active students; Groups: active groups; Lessons: how many are on no statement, absences included; Statements: how many are unpaid, not counting void ones). Settings opens only from the header button. A section with nothing in it yet shows a faded tile that still opens it. At 200% font size or on a narrow screen the tiles stack in one column. *Log lesson* appears once there is an active student. Every other screen has a Back arrow; Back from a section returns here. After database recovery it shows a warning with Open Settings and Dismiss. At the end of a month it asks once to send that month's statements (R15): *Create statements* opens Statements, or *Remind me tomorrow*, or *Done for this month*. |
| Dashboard | See the month's lessons, hours, earned, paid and unpaid amounts, as tinted figure tiles whose numbers roll when they change. See the all-time outstanding total and who owes. |
| Students | Forms retain drafts after process death and ask before Back discards edits. Search, filter *Active* or *Archived*, add or edit, see a balance. Call or email. Record a payment, which settles all open lessons. Payment, marking paid or unpaid, and archiving or restoring happen at once and offer *Undo* for 5 s. Start a statement. Deleting a student still asks first. |
| Groups | Forms retain drafts after process death and ask before Back discards edits. Filter *Active* or *Archived*. Create or edit a group with members and a rate. Log a group lesson with attendance: each member Present, Absent or Late cancel, with an optional reason. Archiving or restoring happens at once and offers *Undo* for 5 s. Deleting a group still asks first. |
| Lessons | The edit form retains drafts after process death and asks before Back discards edits. Browse by month and filter by all, unpaid or on no statement. Add, edit or delete a lesson. Mark a lesson absent, either not charged or as a late cancellation (charged), with a reason. Tick a lesson as paid. **Long-press to select several**, then *Mark paid* or *Reschedule*. Deleting a lesson, marking paid or unpaid, and rescheduling happen at once and offer *Undo* for 5 s. Undo restores only the fields changed; a lesson now on a statement cannot move back (R7). |
| Statements | The creation form retains drafts after process death and asks before Back discards edits. Create a statement from a student's lessons that are on no statement yet, absences included, optionally marking them paid. Share it as an A4 PDF that says on every page it is not a fiscal document. Tap an issued statement to share it again. See whether each is paid, unpaid or void. Void it. |

| Settings | Set the business details printed on statements (and whether the address is), the default lesson length, the theme and the statement footer. Changes save as you type, with no *Save* button; the theme and switches apply at once. Turn the month-end reminder on or off, and choose whether December, June and the Easter month start it on the 21st. Export or import a JSON backup. |

## 5. Out of scope and backlog

| Item | Status | Why |
|------|--------|-----|
| Multi-user login, roles, password reset | Dropped | Single-user device app. Android's lock screen protects the device. |
| Database encryption | Backlog | Needs SQLCipher, an approved dependency, and a key-management design. |
| Cloud sync / multi-device | Backlog | Needs a backend. Use backups to move data for now. |
| Greek (or other) translations | Backlog | UI strings are English and hard-coded. Moving them to `strings.xml` comes first. |
| Fiscal invoices (AADE myDATA) | Out of scope | The PDFs are lesson statements, not fiscal documents, and say so (D20, R14). |
| Crash reporting / analytics | Dropped | No network permission by design. |
| Google Play Store release | Dropped | Not planned (D9). Builds are installed from GitHub releases or CI (§8). Release-only items were removed in alpha03 (see the appendix). |

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
| D13 | Target the latest Android (`targetSdk` 37, Android 17) | None of Android 17's changes for apps that target it touch the app. They cover local network access, background audio, reflection on `static final` fields and `MessageQueue`, `RemoteViews` memory, and the orientation and resizability opt-out on large screens. The app has no permissions, no audio, no notifications, no reflection, and an adaptive Compose layout. | alpha03 |
| D14 | Proprietary licence, all rights reserved, with AI-use terms; replaces MIT | One licence for the owner's apps, shared with Coursebook Pacer: it keeps every right with the owner and lets AI systems work on the code only when the owner authorises them. It also adds MIT's full warranty disclaimer, leaves third-party libraries under their own licences, and confirms that copies already published under MIT keep their MIT rights. | alpha03 |
| D15 | Undo for deleting a lesson, recording a payment, marking lessons paid or unpaid (one or in bulk), rescheduling, and archiving or restoring a student or group (added per style guide v3). Actions happen at once; an app-level snackbar offers *Undo* for 5 s with a countdown bar, hidden when animations are off. A newer message replaces the older offer. | Mistakes are cheap to reverse. Voiding stays final (R6, D7). Backup import, deleting a student or group, and discarding a draft still ask first. Undo is atomic and checks current rules before writing. | alpha03 (decided); built in 2.1.0 |
| D16 | Releases ship a minified (R8) build signed with the owner's key; D9 still rules out the Play Store | The installed app shrinks from 12.5 MB to 1.9 MB and Compose runs faster than in a debuggable build. Same key and `applicationId` as debug, so either installs over the other. An emulator smoke test in CI launches and exercises the minified APK. | alpha03 |
| D17 | Rename to **Course Invoicer**, part of the EduSuite apps, with a new `applicationId` and package `gr.edusuite.invoicer` | The owner's apps now share one suite name and style. A new `applicationId` installs beside the old EduInvoice app instead of over it, so the tutor moves data once: export a backup from EduInvoice, install Course Invoicer, import, then uninstall the old app. Backups now say `course-invoicer-backup`; files that say `eduinvoice-backup` (versions 1 to 3) still import. The database file is `course_invoicer.db`. Launcher shortcuts to the old app go with it. | 2.0.0 |
| D18 | Plain SemVer `MAJOR.MINOR.PATCH` for `versionName`, tags and changelog sections; no pre-release suffix | The owner's choice. Earlier `-alphaNN` tags and sections stay as history. `release.yml` rejects any other `versionName`, so no release is marked *Pre-release*. 2.0.0 follows 2.0.0-alpha03. | 2.0.0 |
| D19 | EduSuite style: a shared base (paper neutrals, warn and ok colours, Outfit as the only font (tabular figures for numbers; v3, 2.1.0), flat panels) plus one accent per app, with dynamic (wallpaper) colour off. The canonical guide is [`docs/EDUSUITE_STYLE_GUIDE.md`](EDUSUITE_STYLE_GUIDE.md) | The suite apps should look like one family, and the accent tells them apart; wallpaper colours would replace it. One guide, copied into each app, keeps them in step. Course Invoicer's accent is purple `#5B3FA0`. The launcher icon is a vector adaptive icon, so the bitmap mipmaps went. | 2.0.0 |
| D20 | Statements are informational, not fiscal documents (Law 4308/2014, ΕΛΠ; AADE myDATA). Everything user-visible says *statement*; code, routes and the database keep *invoice*. The non-fiscal disclaimer is printed on every page (R14). Absences can go on statements, and late cancellations are charged (R3, R4) | The tutor issues the legal receipt for each payment separately; a document that looks like an invoice but is not one must say so plainly. The owner should have an accountant confirm the disclaimer wording. | 2.0.0 |
| D21 | Euro only | One market (Greece), no store release, and `Money` is euro cents. A second currency needs its own decision. Legacy backup currency codes are ignored without changing amounts; new backups retain `currencyCode: EUR` for 2.0.0 readers. Issued statement text stays frozen (R12). | 2.1.0 |
| D22 | Keep a damaged database; refuse downgrades | Preserve the damaged file before any reset and warn on Home. If copying fails, keep the original and refuse to reset. Opening newer data with an older build stops with a message. User data must never be silently deleted. | 2.1.0 |
| D23 | The owner's key signs only the release build, and only on `Rebuild_v2`: its secrets live in a `release` environment limited to that branch. Debug builds are a separate app, `gr.edusuite.invoicer.debug` (*Course Invoicer (debug)*), with the stock debug key. Replaces the debug half of D11 and D16's "either installs over the other". | Branch builds could read the key, and any branch's debug APK installed over the tutor's real app and its data. The key itself is unchanged, so releases keep installing as updates. `docs/SIGNING_BRIEF.md` has the details. | 2.1.1 |

## 7. Dependency register

Runtime dependencies are the only ones that ship in the APK. Anything not listed needs approval
first. Record each approval here. Versions live in `gradle/libs.versions.toml`.

| Scope | Dependencies |
|-------|--------------|
| Runtime (approved) | Kotlin stdlib, kotlinx-coroutines, `androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose` / `-runtime-compose`, Compose BOM (`ui`, `foundation`, `material3`, `ui-tooling-preview`) |
| Bundled assets (approved) | Outfit typeface (SIL Open Font License), Regular, Medium and SemiBold, in `res/font` (style guide §3). Shipped as Google Fonts' variable file `res/font/outfit_variable.ttf` (from `google/fonts`, `ofl/outfit`), used at those three weights; licence text in `assets/licenses/outfit_ofl.txt` |
| Debug only | `androidx.compose.ui:ui-tooling` |
| Unit tests (approved) | JUnit 4, `kotlinx-coroutines-test` |
| Instrumented tests (approved alpha02) | `androidx.test:runner`, `androidx.test.ext:junit`, `androidx.compose.ui:ui-test-junit4` |
| Build plugins | Android Gradle Plugin 9.4.0 (`com.android.application`), Kotlin Gradle plugin 2.4.21 (root build classpath; `:core` applies `org.jetbrains.kotlin.jvm`), Kotlin Compose compiler plugin 2.4.21 (`org.jetbrains.kotlin.plugin.compose`) |
| Awaiting approval (not added) | Room, SQLCipher, Navigation 3, Robolectric |

## 8. Release and distribution

### Current state

- **No Google Play Store release (D9).**
- **Two APKs.** CI builds the debug APK and a minified `release` APK (D16). On `Rebuild_v2` it
  signs the release APK with the owner's key and checks the fingerprint (D11, D23). The debug APK is
  a separate app, `gr.edusuite.invoicer.debug`, with its own data, so it never replaces the real app
  (D23).
- **GitHub releases.** Dating a version in `CHANGELOG.md` publishes it as a GitHub release with the
  minified APK attached (D12, D16, `.github/workflows/release.yml`). Between releases, the debug APK
  is a CI artifact. The README's *Install a test build* section covers both.
- **Target SDK.** `targetSdk` and `compileSdk` are 37 (Android 17); `minSdk` is 26. D13 gives the
  reason.
- **Version numbers.** `versionName` is plain SemVer with no pre-release suffix (D18).
  `versionCode` and `versionName` stay. Android refuses to install an APK with
  a lower `versionCode` over a newer one, so sideloaded builds still need them. Settings shows
  `versionName`.
- **Signing patterns in `.gitignore`.** `*.jks`, `*.keystore`, `*.p12` and `keystore.properties`
  stop a key from ever being committed and cost nothing.

## 9. Origin

This spec started as the rebuild brief written from the request: *"Rebuild the app under new
specifications on a separate branch; Kotlin 2.x, Compose without XML layouts, ViewModel + StateFlow,
Gradle Kotlin DSL, no external dependencies unless approved, minimal APK, no Hilt."* The original
brief is in git history (commit `39cfa78a`). The v0.x app remains on the `Rebuild` and `main`
branches.

## Appendix: removed Play Store items (alpha03)

Under D9 the app is not released on the Google Play Store. These release-only items were marked for
deletion in alpha02 and removed in alpha03, when the owner confirmed. `git show ee92fb1a` (the
alpha03 removal commit) brings any of them back.

| # | Item | Where it was |
|---|------|--------------|
| M1 | Ignore rule for `*.aab` (App Bundles are a Play upload format) | `.gitignore` |
| M2 | `release` build type with R8 minify and resource shrinking (back since D16, signed with the owner's key) | `app/build.gradle.kts` |
| M3 | R8 keep rules (back since D16) | `app/proguard-rules.pro` |
| M4 | `assembleRelease` (and `lintVitalRelease`) in CI | `.github/workflows/ci.yml` |
| M5 | Release-signing note and the `assembleRelease` mention | `AGENTS.md` §4 and §5 |
| M6 | 512 px Play listing icon | `app/src/main/ic_launcher-playstore.png` |

The cost of removing M2: the R8 build was 1.9 MB, against 12.5 MB for the debug APK that is
installed. If the APK size ever matters, add R8 back as a signed, sideloadable build type rather
than a Play one.

**Kept:** the `.gitignore` signing patterns, `versionCode` and `versionName`, and `targetSdk` (see
§8).

**Found on the legacy branches (`main`, `Rebuild`), not changed.** The v0.x app there has:

- release signing configs read from `RELEASE_*` properties
- a `PLAY_INTEGRITY_ENABLED` flag (`Rebuild`)
- the `play-services-auth` dependency (`main`)
- ABI splits for release builds (`Rebuild`)
- the same 512 px Play listing icon as M6
- a Privacy Policy screen, which a Play listing requires
- `app/google-services.json` (Firebase project `eduinvoiceapp`). The owner deleted that project in
  October 2026, which also deletes its API key, so the key left in the git history no longer works.
  v2 does not use Firebase.
- a CI release build step (`Rebuild`)

The owner keeps those branches, frozen, as the history of v0.x; nothing on them is built or
shipped. Before v2 the
repository had no GitHub releases and one tag, `v0.28.0`. Every earlier version now has an annotated
tag: each v0.x version at the commit that first set its `versionName` (two-part names gain `.0`; the
stray 2025-06-08 `1.0` prototype bump has none), and each v2 alpha at the merge that completed it.
Versions the old changelog lists without a version bump have no tag. From alpha03 on, releases are
tagged and published by `.github/workflows/release.yml` (D12).
