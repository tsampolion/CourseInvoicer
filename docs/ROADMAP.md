# Course Invoicer: Roadmap

Work planned after 2.0.0-alpha03. It started as a comparison with [Coursebook
Pacer](https://github.com/tsampolion/course_planner), a sibling app by the same owner. Thirteen of
its patterns were checked against the v2 code, then turned into the tasks below. Each item says
where the code stands today, what to change, and when it counts as done.

Tick a box in the pull request that does the work. When an item is done, move it to *Done* at the
end and note the version it shipped in. Product behaviour still belongs in
[`PRODUCT_SPEC.md`](PRODUCT_SPEC.md): when a task changes a rule (R1–R13) or makes a decision,
update the spec in the same pull request.

## Order of work

| Phase | Items | Why first |
|-------|-------|-----------|
| 1. Tooling | 6, 7, 13 | Cheap, no app code. Every later PR is tested, released and guided by them. |
| 2. Safety nets | 8, 5, 1 | Stop crashes on database errors, pin billing maths, make state handling uniform before features build on it. |
| 3. Features | 3, 2, 4 | Navigation rules first: Undo and the problem list both navigate and need deleted screens handled. |
| 4. Polish | 9, 11 | Printing and APK size. Independent, any time. |
| — | 10, 12 | Done. Re-check only when backup or theming changes. |

Sizes: **S** is under half a day, **M** is one or two days, **L** is more.

## Status

| # | Item | Status | Size |
|---|------|--------|------|
| 1 | One state and one change path per ViewModel | Partial | M |
| 2 | Undo for destructive actions | Not started | L |
| 3 | Navigation rules as pure, tested code | Partial | M |
| 4 | Problems that link to the screen that fixes them | Not started | M |
| 5 | Golden tests for the billing engine | Partial | S |
| 6 | Automatic tag and GitHub release | In review | S |
| 7 | CI on every branch | In review | S |
| 8 | Saving and corrupt data | Partial | M |
| 9 | Print invoices | Partial | S |
| 10 | Backup and device-transfer rules | Done | — |
| 11 | Smaller footprint | Partial | S |
| 12 | App theme drives the system bars | Done | — |
| 13 | Short, rule-focused agent guide | In review | S |

## 1. One state and one change path per ViewModel

**Today.** All 12 screen ViewModels expose one `StateFlow<XxxUiState>`. Writes go through
`launchCatching` (`ui/common/ViewModelSupport.kt`). But:
- the `combine(…).stateIn(…)` flows have no `.catch`, so a database error crashes the app;
- initial loads in the form ViewModels use a bare `viewModelScope.launch` (LessonEdit,
  CreateInvoice, StudentEdit, GroupEdit, GroupLesson);
- `LessonEditViewModel.setPaid` and `setNotes` bypass its `edit()` guard;
- some writes skip the use cases: `lessons.setPaid` is called from `LessonsViewModel` and
  `StudentDetailViewModel`, and archiving is a read-modify-write in `StudentDetailViewModel`;
- R3 (absent lessons are never paid) is guarded only by the UI (`LessonRow.kt`).

**Tasks**
- [ ] Add a `stateInCatching` helper next to `launchCatching`. It catches upstream errors, keeps the
      last good state and sets the screen's `message`. Use it in every list ViewModel.
- [ ] Run every initial form load through `launchCatching`. A missing entity sets a message and a
      `notFound` flag; it does not show a blank form.
- [ ] Give each form ViewModel one private `edit(transform)` that every setter calls, with the lock
      check (R7) inside it.
- [ ] Add `MarkPaid` and `ArchiveStudent` / `ArchiveGroup` use cases in `core/usecase`. `MarkPaid`
      refuses absent lessons (R3). Call them from the ViewModels instead of the repositories.
- [ ] Tests: `core` tests for the new use cases. A `ViewModelsTest` case where the repository flow
      throws and the screen shows a message instead of crashing.

**Done when** no ViewModel calls a repository write directly, no flow error can crash a screen, and
R3 is enforced in `:core`.

## 2. Undo for destructive actions

**Today.** None. Deletes and voids ask for confirmation ("This cannot be undone"). `MessageEffect`
(`ui/common/Components.kt`) shows a snackbar with no action. Each screen owns its snackbar host, and
delete screens close straight away, so their message would be lost. Coursebook Pacer snapshots its
whole state; that doesn't fit SQLite, so each action needs its own inverse.

| Action | Where | What undo needs |
|--------|-------|-----------------|
| Delete lesson | `DeleteLesson` | The deleted row; re-insert it with the same id |
| Record payment | `RecordPayment` → `PaymentResult` | The ids of the lessons it marked paid (not returned today) |
| Mark several paid | `LessonsViewModel` bulk action | The ids marked |
| Reschedule | `RescheduleLessons` | The old date and start time per lesson (returns a count today) |
| Delete group | `DeleteGroup`; foreign keys `SET NULL` / `CASCADE` | The group row, its members and the lesson links |
| Void invoice | `VoidInvoice` → `SqlInvoiceRepository.voidInvoice` | Out of scope: un-voiding conflicts with R6 and D7 |
| Import backup | `SqlBackupRepository.replaceAll` | Out of scope: tell the tutor to export first instead |

**Tasks**
- [x] Decide the scope with the owner: delete lesson, record payment, mark paid (one or in bulk),
      reschedule. Recorded as D15 in the spec.
- [ ] Add an app-level message channel, for example a `Messages` object in `AppContainer` with a
      `SnackbarHostState` in `CourseInvoicerApp.kt`. A message survives the screen that sent it and can
      carry an action.
- [ ] Make each in-scope use case return an `Undo` value: a suspend function that reverses exactly
      what it changed, inside one `db.write`.
- [ ] Show *Undo* on the snackbar for about 10 seconds. A newer action replaces the older undo.
- [ ] Tests: in `:core`, do then undo and check the data equals the original, for each action. One
      emulator test for delete lesson → Undo.

**Done when** every in-scope action shows *Undo*, undo restores the exact previous rows, and the
confirmation dialogs for those actions are removed.

## 3. Navigation rules as pure, tested code

**Today.** `ui/nav/Navigation.kt` has a typed `Route` with string `encode`/`decode`, a `Navigator`
that survives process death, and a ViewModel store per entry. `NavigatorTest` covers the round trip,
tab reset, `replace` and restore. Gaps:
- the stack rules are methods on a stateful ViewModel, not pure functions;
- `navigate` has no guard against a double tap stacking the same screen twice;
- nothing removes screens whose entity was deleted or restored away. Today: StudentDetail says "no
  longer exists"; LessonEdit shows a blank form whose Save fails; GroupEdit's Save updates 0 rows
  and then fails on the members' foreign key.

**Tasks**
- [ ] Move the stack rules into a pure `object BackStack` (`push`, `pop`, `replace`, `selectTab`,
      `prune`) over `List<Route>`. `Navigator` keeps only the ViewModel stores and saved state.
- [ ] `push` ignores a route equal to the top one.
- [ ] `prune(stack, exists: (Route) -> Boolean)` drops entries whose entity is gone. Call it after
      deletes, after a backup import, and on restore after process death.
- [ ] Form screens for a missing entity close with a message instead of showing a blank form.
- [ ] `NavigatorTest` → `BackStackTest`: one test per rule, including prune after delete and after
      import.

**Done when** no screen can be opened for an entity that doesn't exist, and every stack rule has a
JVM test.

## 4. Problems that link to the screen that fixes them

**Today.** Home shows the month's figures, the outstanding total and who owes. Nothing flags setup
gaps. Blank business details are silently left off invoices (`InvoiceDocumentBuilder`).

**Tasks**
- [ ] Add a pure `core/report/Problems.kt`: `fun problems(input): List<Problem>`, where `Problem`
      has a kind, a message and the id it concerns. Start with:
  - [ ] business name or address is blank (before the first invoice);
  - [ ] an active student or group has a zero rate;
  - [ ] a group has no active members;
  - [ ] unpaid lessons older than 60 days. This needs the oldest unpaid date: add `MIN(date)` to the
        `observeBalances` SQL, a field on `StudentBalance`, and the same in `InMemoryRepositories`;
  - [ ] no backup for 30 days. This needs a `lastBackupOn` setting written by `BackupFiles.export`.
- [ ] Map each kind to a `Route` in the app (Settings, StudentEdit, GroupEdit, StudentDetail).
- [ ] Show a *Needs attention* card at the top of Home; tapping a row opens its route.
- [ ] Tests: one `ProblemsTest` case per kind in `:core`; `ViewModelsTest` for the Home state.

**Done when** each problem appears on Home within one screen refresh of being caused, and disappears
once fixed.

## 5. Golden tests for the billing engine

**Today.** `MoneyTest`, `ReportsTest`, `InvoiceNumberingTest`, `InvoiceDocumentTest` and the
use-case tests cover the rules. Gaps:
- half-up rounding (R1) isn't pinned: the only tie tested gives the same answer under half-even;
- no tests at 1 or 720 minutes;
- `Money` arithmetic is unchecked `Long` maths, and rates have no upper limit;
- formatting is only tested in UK English with EUR. Nothing covers Greek, or currencies with no
  minor unit (JPY) or three decimals (KWD). `Formats` always prints two decimals and falls back to
  EUR silently;
- the 9-digit limit on invoice numbers is untested.

**Tasks**
- [ ] Rounding: `Rate(Money(10), HOURLY).feeFor(15)` is 3 cents (2.5 rounds up); add more ties.
- [ ] Bounds: 1 and 720 minutes. Set a maximum rate in `SaveStudent` / `SaveGroup` (for example
      €100,000) so `feeFor` can never overflow, with a `DomainException` message.
- [ ] Formatting: add `el-GR` cases. Decide the policy for currencies without two decimals: either
      restrict the currency list to two-decimal currencies (R13), or format by
      `Currency.defaultFractionDigits`. Record the choice in the spec.
- [ ] Numbering: the year rollover and the 9-digit limit.
- [ ] A golden table test: about 20 rows of (rate, type, minutes) → fee, written once by hand and
      never regenerated.

**Done when** each rule in R1, R5 and R13 has at least one test that would fail if it changed.

## 6. Automatic tag and GitHub release

**In review:** `.github/workflows/release.yml`
([#353](https://github.com/tsampolion/EduInvoiceApp/pull/353)).

**Tasks**
- [x] After CI passes on `Rebuild_v2`, tag `vX.Y.Z` and publish a release with the changelog section
      as notes, but only when that section is dated.
- [x] Attach the signed APK CI built for the commit, and refuse an APK whose key doesn't match
      `SIGNING_CERT_SHA256`.
- [x] Mark versions with a suffix as pre-releases. Manual dispatch for an older commit.
- [ ] Owner: set `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD` and `SIGNING_CERT_SHA256` (README).
- [ ] Owner: date `## [2.0.0-alpha03]` in `CHANGELOG.md` to publish the first v2 release, then check
      it installs over the current build on the phone.

**Done when** the first v2 release exists with a signed APK that installs as an update.

## 7. CI on every branch

**In review** in the same pull request as item 6.

- [x] CI runs on every branch push. Pull requests from this repository reuse their branch's run;
      fork pull requests run their own.
- [x] The APK size is in the job summary (it already was).
- [ ] Optional: fail CI if the debug APK grows by more than 10% against the last release. This needs
      the last release's size, which item 6 now provides.

## 8. Saving and corrupt data

**Today.** Every database write commits at once: `db.write` is a `NonCancellable` transaction, and
settings use `commit()`. Settings save as you type, 0.4 s after the last keystroke; the Settings
screen flushes a waiting save on Back and in `onStop`, and a started save always finishes. Gaps:
- **Corrupt database.** `InvoicerDatabase` uses the framework's default error handler, which *deletes* a
  corrupt database file without asking. Coursebook Pacer keeps the bad file as `.bad` and starts
  clean.
- **Downgrade.** There is no `onDowngrade`. Installing an older build over a newer one throws on
  open, and that error crashes the app (see item 1).
- **Drafts.** Form state lives only in the ViewModel. It survives rotation but not process death,
  and Back discards edits without asking.

**Tasks**
- [ ] Pass a `DatabaseErrorHandler` to `InvoicerDatabase` that copies the file to `course_invoicer.db.bad`
      before the framework resets it, and flags it so Home can say: "The database was damaged.
      Import your last backup."
- [ ] Override `onDowngrade` to refuse with a clear message: "This build is older than your data.
      Install the latest release." Never drop data.
- [ ] Save form drafts in `SavedStateHandle` (LessonEdit, StudentEdit, GroupEdit, CreateInvoice), so
      they survive process death.
- [ ] Ask before Back discards unsaved changes in a form.
- [ ] Tests: on the emulator, a corrupt file gives a `.bad` copy and an empty database, and a
      downgrade is refused. A JVM test that the draft restores from `SavedStateHandle`.

**Done when** no failure mode deletes data silently, and a form survives process death.

## 9. Print invoices

**Today.** `data/InvoicePdfWriter.kt` draws an A4 PDF on a framework `PdfDocument` canvas, writes it
atomically to `cacheDir/invoices/` and shares it through `FileProvider`. There is no print option,
and the PDF layout has no test. Coursebook Pacer prints HTML through a `WebView`; that would mean a
second renderer, so keep the canvas.

**Tasks**
- [ ] Add a `PrintDocumentAdapter` that streams the PDF `InvoicePdfWriter` already wrote, and a
      *Print* action next to *Share PDF*. The system print dialog also offers *Save as PDF*.
- [ ] Emulator test for `InvoicePdfWriter`: a two-page invoice has two pages, and long details wrap.
- [ ] Optional: a print-all action on Invoices for a month's invoices.

**Done when** an invoice can be printed from the app and its PDF has a test.

## 10. Backup and device-transfer rules: done

`data_extraction_rules.xml` keeps all data out of Android cloud backup, so student data stays on the
phone (deliberately unlike Coursebook Pacer). Device-to-device transfer uses the defaults.
`allowBackup` and `fullBackupContent` are `false`.

- [ ] Check once on two phones that device-to-device transfer carries the database and settings.
      Write the result in the spec.

## 11. Smaller footprint

**Today.** The theme is a framework theme and icons are drawn in code (`AppIcons.kt`). Releases ship
a minified build (D16); the debug APK is about 12.5 MB.

**Tasks**
- [ ] Add `androidResources { localeFilters += "en" }`. The UI is English-only, so this drops the
      translations AndroidX ships. Revisit when translations come (spec §5).
- [ ] Measure the APK size before and after, and note both in the changelog.
- [x] A minified build signed with the owner's key, approved as D16. Releases ship it, and CI runs
      an emulator smoke test on it.

## 12. App theme drives the system bars: done

`MainActivity` re-applies `enableEdgeToEdge` with `SystemBarStyle.auto` whenever the app theme
changes, so status-bar icons follow the app's theme even when it differs from the system's. Dynamic
colour applied on Android 12 and later until 2.0.0, which turned it off for the EduSuite accent
(D19).

## 13. Short, rule-focused agent guide

**In review** in the documentation pull request.

- [x] `CLAUDE.md` imports `AGENTS.md`, so every coding agent reads one file.
- [x] `AGENTS.md` has sections for commands, architecture (how data flows), rules, recipes, gotchas
      and workflow.
- [x] The sandbox note is correct: when Google Maven is blocked, nothing builds, not even `:core`,
      because AGP resolves while Gradle configures the build.
- [ ] Optional: make `:core:test` work without Google Maven, for example by moving the AGP plugin
      declaration out of the root build. It is worth doing only if sandboxed agents keep hitting it.

## Found along the way

These came up during the comparison but aren't among the 13 items.

- [x] **Security.** A Firebase API key from v0.x is in the git history of `main` and `Rebuild`
      (`app/google-services.json`). The owner deleted the Firebase project, and its key with it, in
      October 2026. The legacy branches stay as history.
- [ ] A test for R3 in `:core` once `MarkPaid` exists (item 1).
- [ ] **Third-party notices.** The APK bundles Apache-2.0 libraries (Kotlin, AndroidX, Compose).
      Giving the APK to anyone else requires passing on their licence and notices. Add an
      *Open-source licences* entry in Settings, or ship the notices with each release.

## Done

Nothing yet. Move items here with the version they shipped in.
