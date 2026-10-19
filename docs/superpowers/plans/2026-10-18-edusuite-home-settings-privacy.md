# EduSuite Home, Settings, Overdue and Privacy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Make Home in both EduSuite apps use the whole screen. Tiles glide when the column count changes, and the floating "part of EduSuite apps" line goes.
- Give Course Invoicer an app-wide Settings and a Statement details screen.
- Add red overdue notices.
- Add GDPR support: notices, a parent notice in EN and EL, data minimisation, erasure and per-student export.

**Architecture:**
- Two independent deliverables, one per repo, each shippable alone.
- Part 1 (Course Pacer) and Part 2 Tasks I1–I2 (Invoicer) share one design: a custom masonry `Layout` whose column count comes from the real width, plus a placement-animation modifier.
- Part 2 adds the following to Invoicer, keeping its MVVM + `:core` use-case split:
  - a `StatementDetails` route;
  - an overdue rule in `:core` with a SQL query;
  - a `privacy` package in `:core` (notice text, erase, export).

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, framework APIs only (no new dependencies), JUnit.

**Spec:** the *Design* section below (agreed in brainstorming). After approval it is saved as `docs/superpowers/specs/2026-10-18-edusuite-home-settings-privacy-design.md` in both repos, and this plan as `docs/superpowers/plans/2026-10-18-edusuite-home-settings-privacy.md`.

## Design (agreed decisions)

- **A. Footer.** Remove *part of EduSuite apps* from Home in both apps. It appears only in Settings → About. Pacer already has `settings_suite`; Invoicer gains it.
- **B. Grid.**
  - `columns = floor((width + gap) / (160dp + gap))`, between 2 and the tile count. It drops to 1 at font scale ≥ 2 or width < 360dp.
  - No max-width cap on the grid. Pacer's hero stays capped at 560dp.
  - Tiles glide to new positions (`Motion.settle`) when the count changes, and snap when animations are off.
  - Rotation no longer recreates the Activity (`configChanges`), so the glide plays.
  - Every tile stays composed: no lazy grid, because Invoicer's `EndToEndTest` uses `performScrollTo`.
- **C. Settings (Invoicer).**
  - Statement fields (name, address, show address, phone, email, IBAN, footer) move to a **Statement details** screen. It opens from a row at the top of Statements.
  - Create statement shows a bottom sheet asking for name and IBAN when the name is blank.
  - Settings becomes: Appearance, Lessons (default length), Reminders, Privacy, Backup, About.
- **D. Overdue (Invoicer).**
  - A statement is overdue when it is not void, not paid, and today is after the last day of its issue month.
  - New statements carry the note `Payment due by <last day of issue month>`, frozen in the document. Older ones print as they were.
  - Overdue figures use the suite red (`Invoicer.colors.warn`, mapped to `colorScheme.error`).
  - Home: the Statements tile subtitle reads `N overdue` in red with an `Overdue` warning chip, and a warning banner above the grid says `N statements overdue · View`.
  - Students: rows with an overdue statement show an `Overdue` warning chip, and a banner says `N students have overdue statements`.
- **E. Privacy (Invoicer, GDPR = Regulation (EU) 2016/679).** The teacher is the controller (Art. 4(7)). The app helps them comply and collects no more than it needs.
  - **Statements are informational only** (D20, R14). They are not tax documents, and the tutor's fiscal receipts are issued outside the app. So no law requires keeping anything this app stores:
    - the only legal basis is the lessons agreement, Art. 6(1)(b);
    - retention is the tutor's own short rule;
    - erasure must really remove the student's identity (no Art. 17(3)(b) exception).
  - **Retention (owner's choice):** kept while lessons continue; after the last lesson, until everything is paid and for at most 12 months, then erased.
  1. **In-app notices.**
     - A hint on the student form: "Only what you need for lessons and billing. It stays on this device. Don't put health or other sensitive details in notes." Optional fields are labelled "(optional)".
     - A line on Statement details: "You are responsible for the personal data you keep here (GDPR). Share a privacy notice with your students."
     - A Settings → Privacy section with the same text, *Share privacy notice*, and a warning: "Backups are not encrypted: keep them somewhere only you can open."
  2. **Notice for parents.** An Art. 13 notice in English and Greek, filled from Statement details and shared as text through the share sheet. The fixed copy is below.
  3. **Minimise and erase.** *Erase student data* (owner's choice: anonymise):
     - The student record becomes "Erased student", with class, guardian, phone, email and notes cleared, and is archived.
     - Lesson notes and absence notes for that student are cleared.
     - Every issued statement's frozen document has its `billTo` lines replaced by `Erased student`, keeping number, dates, lines and amounts. Numbering and past totals stay intact (R6).
     - This is the one allowed change to an issued document (R12 exception, new rule R17).
     - The 5 s Undo restores everything from an in-memory copy.
     - Archived students whose last lesson is over 12 months ago and who owe nothing get a *Due for erasure* chip on Students.
     - *Delete* stays for students with no history.
  4. **Export a student's data.** A JSON file with the student, their lessons and their statements, shared through the existing `FileProvider` (Art. 15, 20).

### Fixed copy: parent notice

**English**:

```
Privacy notice: lessons with {name}

Who is responsible
{name}{, address}{, phone}{, email} is the controller of the personal data below.

What I keep
The student's name and class, the guardian's name, the phone number and email you give me, the dates, length and fees of lessons, payments, and the statements I send you.

Why, and on what legal basis
To arrange lessons and to let you know what they cost, which is necessary for our agreement (GDPR Art. 6(1)(b)). The statements I send are for your information only; they are not tax documents.

Who receives it
No one else. The data stays on my own device and is not sent to any online service or outside the EU.

How long
While lessons continue. After the last lesson, until everything is paid and for no more than 12 months; then it is erased. Statements I have sent then keep only their number, dates and amounts, without your name.

Your rights
You may ask me for access to your data, its rectification or erasure, restriction of processing, to object to processing, and for a copy in a portable format (Art. 15–21), at the contact details above.

Complaints
You may lodge a complaint with the Hellenic Data Protection Authority, 1-3 Kifisias Ave., 115 23 Athens, tel. +30 210 6475600, www.dpa.gr.

Names and lesson details are needed to arrange and bill lessons; phone, email and the guardian's name are optional. No decisions are made automatically and there is no profiling.
```

**Ελληνικά** (with the terms of the official Greek text of the GDPR):

```
Ενημέρωση για την προστασία δεδομένων: μαθήματα με {name}

Υπεύθυνος επεξεργασίας
{name}{, address}{, phone}{, email} είναι ο υπεύθυνος επεξεργασίας των παρακάτω δεδομένων προσωπικού χαρακτήρα.

Ποια δεδομένα τηρώ
Το ονοματεπώνυμο και την τάξη του μαθητή, το όνομα του κηδεμόνα, το τηλέφωνο και το email που μου δίνετε, τις ημερομηνίες, τη διάρκεια και την αμοιβή των μαθημάτων, τις πληρωμές και τις καταστάσεις που σας στέλνω.

Σκοπός και νομική βάση
Η οργάνωση των μαθημάτων και η ενημέρωσή σας για το κόστος τους, που είναι απαραίτητη για την εκτέλεση της συμφωνίας μας (άρθρο 6 παρ. 1 στοιχ. β ΓΚΠΔ). Οι καταστάσεις που σας στέλνω είναι μόνο ενημερωτικές· δεν αποτελούν φορολογικά παραστατικά.

Αποδέκτες
Κανείς άλλος. Τα δεδομένα βρίσκονται μόνο στη δική μου συσκευή και δεν αποστέλλονται σε διαδικτυακή υπηρεσία ούτε εκτός ΕΕ.

Χρονικό διάστημα αποθήκευσης
Όσο συνεχίζονται τα μαθήματα. Μετά το τελευταίο μάθημα, έως ότου εξοφληθούν όλα και όχι περισσότερο από 12 μήνες· στη συνέχεια διαγράφονται. Οι καταστάσεις που σας έχω στείλει διατηρούν τότε μόνο τον αριθμό, τις ημερομηνίες και τα ποσά, χωρίς το όνομά σας.

Τα δικαιώματά σας
Έχετε δικαίωμα πρόσβασης στα δεδομένα σας, διόρθωσης ή διαγραφής τους, περιορισμού της επεξεργασίας, εναντίωσης στην επεξεργασία και φορητότητας των δεδομένων (άρθρα 15–21), στα παραπάνω στοιχεία επικοινωνίας.

Καταγγελία
Έχετε δικαίωμα υποβολής καταγγελίας στην Αρχή Προστασίας Δεδομένων Προσωπικού Χαρακτήρα, Λεωφ. Κηφισίας 1-3, 115 23 Αθήνα, τηλ. 210 6475600, www.dpa.gr.

Το ονοματεπώνυμο και τα στοιχεία των μαθημάτων χρειάζονται για την οργάνωση και τη χρέωση των μαθημάτων· το τηλέφωνο, το email και το όνομα του κηδεμόνα είναι προαιρετικά. Δεν λαμβάνονται αυτοματοποιημένες αποφάσεις και δεν γίνεται κατάρτιση προφίλ.
```

**Sources:**
- GDPR EN/EL official texts: [EUR-Lex EN](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32016R0679), [EUR-Lex EL](https://eur-lex.europa.eu/legal-content/EL/TXT/HTML/?uri=CELEX:32016R0679). The Art. 4(7), 6(1), 13, 15–22 wording was checked against both.
- HDPA address and phone: [dpa.gr](https://www.dpa.gr/en/personal_data/controller_details_contact).
- Invoicer statements are non-fiscal and informational only (D20, R14; confirmed by the owner). Greek tax record-keeping periods therefore do not apply to this app's data, and the notice does not cite them.

## Global Constraints

- No new dependencies (Pacer CLAUDE.md "Approved dependencies only"; Invoicer AGENTS.md rule 5).
- Pacer: visible copy in `res/values/strings.xml` (`NoHardcodedTextTest`); colours, space and motion from tokens only; `applicationId` and `minSdk 26` unchanged.
- Invoicer: copy stays Kotlin literals, as the codebase does today. User-facing text says "statement"; code says "invoice" (D20).
- Issued statements never change (R12). The due-date note is added only by `InvoiceDocumentBuilder` for new statements.
- `docs/EDUSUITE_STYLE_GUIDE.md` must stay byte-identical in both repos.
- **Stacked PRs, one version step each** (see *PR stack*).
  - Every PR bumps `versionCode` by 1 and `versionName` by exactly one SemVer step, under each repo's own rules:
    - MINOR for a new feature;
    - PATCH for fixes, copy and docs that leave the app working as before.
  - Pacer: `## [X.Y.Z] - YYYY-MM-DD` + compare link (released on merge).
  - Invoicer: a new `## [X.Y.Z] - YYYY-MM-DD` section dated on the PR, so each merge releases, plus README/AGENTS version lines.
- Invoicer's base is `Rebuild_v2`; its push is currently refused (GitHub App not installed for `eduinvoiceapp`).

## PR stack

**Revision (2026-10-18, owner):** statements are informational only, not tax documents. Design E, the parent-notice copy, Review Focus 5 and Tasks I6/I7 were updated to match (contract basis only, a 12-month retention rule, anonymising erasure).

**Progress (2026-10-18):**
- **P-1 is done.** [course_planner#26](https://github.com/tsampolion/course_planner/pull/26) is open on `claude/app-ui-ux-improvements-sxtsi9` (2.4.1). The spec and plan copies are on that branch under `docs/superpowers/`. Tests, `assembleRelease` and lint pass locally (Android SDK installed at `/home/user/android-sdk`).
- **Still to do here:** P-2 (`-grid`, 2.5.0).
- **Still to do:** start the Invoicer session. Its prompt points at the plan copy on the P-1 branch, which the new session reads by attaching `tsampolion/course_planner` read-only.
- **Already known:** the two style-guide copies were *already* different (Invoicer has guide v3: Outfit tabular figures). The suite edit is applied to the shared paragraphs only, and PR #26 says so.

**Two sessions.**
- *This* session runs the Pacer stack.
- Right after approval it starts a **new Course Invoicer session** with `create_session`:
  - `source_url` = `https://github.com/tsampolion/eduinvoiceapp`, `source_revision` = `Rebuild_v2`;
  - title "Course Invoicer 2.1.1–2.6.0 · Signing, Home grid, statement details, overdue, privacy";
  - prompt = Part 2 of this plan verbatim, plus the Design section, Global Constraints, Review Focus and PR stack.
- That session runs I-0 … I-7.
- Both sessions write the style guide edit with identical bytes (the text is fixed in Task P2). Each PR body links its twin PR.
- Invoicer pushes need the Claude GitHub App on `eduinvoiceapp`. The new session reports this if its first push is refused.

Each PR's base is the branch of the PR above it. The first PR targets the default branch. They merge top-down; after a merge, GitHub retargets the next PR. Branches extend the designated name `claude/app-ui-ux-improvements-sxtsi9`.

**Course Pacer** (now 2.4.0, base `main`)

| # | Branch suffix | Tasks | Bump | Why |
|---|---|---|---|---|
| P-1 | *(none)* | P2a: footer off Home + style-guide §5/§6 text | **2.4.1** PATCH | Layout fix, no new behaviour |
| P-2 | `-grid` | P1, P2b | **2.5.0** MINOR | Adaptive grid + glide is new behaviour |

**Course Invoicer** (now 2.1.0, base `Rebuild_v2`)

| # | Branch suffix | Tasks | Bump | Why |
|---|---|---|---|---|
| I-0 | `-signing` | I0: release key only on `Rebuild_v2`, separate debug app | **2.1.1** PATCH | Build/CI only; the app works as before |
| I-1 | *(none)* | I2a: footer off Home, footer line in Settings About, same style-guide text as P-1 | **2.1.2** PATCH | Layout fix |
| I-2 | `-grid` | I1, I2b | **2.2.0** MINOR | Adaptive grid + glide |
| I-3 | `-statement-details` | I5 | **2.3.0** MINOR | New screen, Settings regrouped |
| I-4 | `-overdue` | I3, I4 | **2.4.0** MINOR | Overdue rule, due note, red notices |
| I-5 | `-privacy-hints` | I6a: student-form hint, "(optional)" labels, Statement-details notice line | **2.4.1** PATCH | Copy only |
| I-6 | `-privacy-notice` | I6b: Settings Privacy section, EN/EL parent notice + share | **2.5.0** MINOR | New feature |
| I-7 | `-data-rights` | I7, I8 | **2.6.0** MINOR | Erase (anonymise) + erasure reminder + export |

Every PR runs its repo's full check line before it is pushed:
- Pacer: `testDebugUnitTest assembleRelease`
- Invoicer: `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Each PR updates the docs it touches (PRODUCT_SPEC, CLAUDE.md), so the per-repo "version/docs" tasks below become a closing step of every PR rather than one last PR. The `docs/superpowers` spec and plan copies ride in P-1 and I-1.

## Review Focus

1. **Rotation mid-scroll or with a sheet open.** With `configChanges`, open sheets and scroll position must survive rotation, and no tile may be lost. Test: `homeColumns` and `MasonryPlacementTest` cover the arithmetic; rotation is checked by hand on a phone (stated in the PR).
2. **Statement issued on the last day of the month.** It is not overdue that day and is overdue the next day. Test `overdueFlipsOnFirstOfNextMonth` in I3.
3. **Void, fully paid, or all-excused statements are never overdue.** Tests in I3 (`voidIsNeverOverdue`, `excusedOnlyStatementIsPaid`).
4. **Blank optional business fields in the parent notice.** No dangling commas or empty lines. Test `noticeSkipsBlankFields` in I6.
5. **Erasing a student with statements.**
   - Every issued document's `billTo` becomes `Erased student`, while its number, lines and total stay unchanged.
   - The student record and the lesson notes hold no personal data.
   - Undo restores all of it within 5 s.
   - Test `eraseAnonymisesStatements` in I7.

---

# Part 1: Course Pacer (`/home/user/course_planner`)

### Task P1: Adaptive masonry grid and placement glide

**Files:**
- Create: `app/src/main/java/gr/edusuite/pacer/ui/HomeGrid.kt`
- Modify: `app/src/main/java/gr/edusuite/pacer/ui/HomeModel.kt` (add `homeColumns`)
- Test: `app/src/test/java/gr/edusuite/pacer/ui/HomeModelTest.kt` (create if absent; else the existing Home model test file)

**Interfaces:**
- Produces:
  - `fun homeColumns(widthDp: Float, fontScale: Float, tiles: Int, minDp: Float = 160f, gapDp: Float = 8f): Int`. `widthDp` is the grid's inner width (screen minus the 16dp edges), so §6's "under 360dp" is `widthDp < 328f`, the same rule as `DashboardComponents.kt:73`.
  - `fun masonryPlacement(heights: List<Int>, columns: Int, gap: Int): List<Pair<Int, Int>>` returns (column, y) for each tile; each tile goes into the shortest column, ties to the leftmost.
  - `@Composable fun HomeGrid(columns: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit)`
  - `fun Modifier.animatePlacement(): Modifier`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun columnsFollowWidth() {
    assertEquals(2, homeColumns(328f, 1f, 6))   // 360dp phone minus 32dp edges
    assertEquals(2, homeColumns(380f, 1f, 6))
    assertEquals(4, homeColumns(768f, 1f, 6))   // landscape phone
    assertEquals(6, homeColumns(1248f, 1f, 6))  // capped at tile count
}
@Test fun oneColumnAtLargeFontOrNarrow() {
    assertEquals(1, homeColumns(768f, 2f, 6))
    assertEquals(1, homeColumns(300f, 1f, 6))
}
@Test fun masonryFillsShortestColumn() {
    assertEquals(listOf(0 to 0, 1 to 0, 1 to 108, 0 to 168),
        masonryPlacement(listOf(160, 100, 100, 100), columns = 2, gap = 8))
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'gr.edusuite.pacer.ui.HomeModelTest'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** `homeColumns` and `masonryPlacement` in `HomeModel.kt` (plain Kotlin).
  - `HomeGrid` is a `Layout`: column width `(maxWidth - gap*(columns-1)) / columns`; measure each child at that fixed width; place with `masonryPlacement`; height = tallest column.
  - `animatePlacement` follows the pattern `onPlaced { target = it.positionInParent() }` + `Animatable(IntOffset)` + `offset { current - target }`.
    - Its spec is `Motion.settle()`.
    - When `animationsEnabled()` is false it does `snapTo`.
    - The first placement never animates.
- [ ] **Step 4: Run** the same tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(home): adaptive masonry grid with placement glide`.

### Task P2: Home uses the grid; footer leaves Home; rotation keeps the Activity

Split across two PRs:
- **P2a** (PR P-1, done first, before P1): the `home_suite` removal and the style-guide edits.
- **P2b** (PR P-2): everything else (grid, `widthIn` removal, manifest).

**Files:**
- Modify: `app/src/main/java/gr/edusuite/pacer/ui/HomeScreen.kt`:
  - lines 135–176: drop `widthIn(max = 480.dp)` and the `home_suite` Text; cap only `NextLessonCard` with `widthIn(max = 560.dp)`;
  - lines 300–378: `TileGrid` takes `columns: Int` instead of `oneColumn` and emits tiles into `HomeGrid` in the order Classes, Overview, Tests, Homework, Print, Days off, each `Modifier.animatePlacement()`; tall = `columns > 1`.
- Modify: `app/src/main/res/values/strings.xml`: remove `home_suite`.
- Modify: `app/src/main/AndroidManifest.xml`: on `MainActivity` add `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density"`.
- Modify: `docs/EDUSUITE_STYLE_GUIDE.md` §6:
  - *Tile grid*: replace "2-column grid" with "grid of as many 160dp columns as the width fits (at least 2, at most one per tile); when the count changes the tiles glide to their new places (`Motion.settle`)".
  - *Home header*: replace the last paragraph with "*part of EduSuite apps* appears in Settings → About, not on Home."
  - §5 *Lists*: add "Grid tiles glide to new positions when the column count changes."
- Test: existing `NoHardcodedTextTest` and the unit test suite.

- [ ] **Step 1:** Make the edits above.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest lint`. Expected: BUILD SUCCESSFUL. `NoHardcodedTextTest` passes, and lint reports no unused `home_suite`.
- [ ] **Step 3: Commit** `feat(home): fill wide screens, move EduSuite line to About`.

### Task P3: Close each Pacer PR (run once for P-1, once for P-2)

**Files:**
- `app/build.gradle.kts`: P-1 is 2.4.1 / 25; P-2 is 2.5.0 / 26.
- `CHANGELOG.md`: a dated section and compare link.
- P-2 only: `CLAUDE.md` Home sentence ("a tile grid whose columns follow the width").
- P-1 only: `docs/superpowers/{specs,plans}/…`.

- [ ] **Step 1:** Make the edits.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest assembleRelease`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit, then `git push -u origin <branch>`.
- [ ] **Step 4:** Open the PR with base = the previous PR's branch (P-1: `main`). The body names its place in the stack, says the style guide is paired with I-1, and (P-2) that the glide was not seen on a device. Subscribe to it.

---

# Part 2: Course Invoicer (`/home/user/eduinvoiceapp`, from `origin/Rebuild_v2`)

Paths: `app/…` = `app/src/main/kotlin/gr/edusuite/invoicer`, `core/…` = `core/src/main/kotlin/gr/edusuite/invoicer/core`.

### Task I0: Keep the permanent release key on `Rebuild_v2` only (PR I-0)

**Why:**
- Today `ci.yml` (on every branch) decodes the owner's key and puts the password in `GITHUB_ENV` (audit S5).
- `app/build.gradle.kts:21-45` signs the *debug* build with the real key, under the real `applicationId`. Any branch's debug APK therefore installs over the tutor's real app.
- The fix mirrors Course Pacer 1.7.3 (`/home/user/course_planner/docs/SIGNING_BRIEF.md`, `android.yml:14-17`, `app/build.gradle.kts:27-45`). It does not change or regenerate the key.

**Files:**
- `app/build.gradle.kts`:
  - New `signingConfigs.create("release")` from env `INVOICER_RELEASE_KEYSTORE`, `INVOICER_KEYSTORE_PASSWORD`, `INVOICER_KEY_ALIAS` (default `release`).
  - `release` uses it when the keystore is set, else the default debug key.
  - The `debug` signingConfig goes back to the stock debug key.
  - `buildTypes.debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }`.
- `app/src/debug/res/values/strings.xml`: `app_name` = `Course Invoicer (debug)`, if the label is a resource. Otherwise use `manifestPlaceholders`.
- Check that the manifest's FileProvider `android:authorities` uses `${applicationId}`; if it is hard-coded, change it.
- `.github/workflows/ci.yml`:
  - `environment: ${{ github.ref == 'refs/heads/Rebuild_v2' && 'release' || '' }}`.
  - The keystore is decoded in the signing step.
  - The password is passed through step-level `env:` only to the Gradle step that builds `assembleRelease` (no `GITHUB_ENV`).
  - The certificate check requires `SIGNING_CERT_SHA256` on `app-release.apk` on `Rebuild_v2` only.
  - The emulator/smoke job uses package `gr.edusuite.invoicer.debug` wherever it names the debug app.
- `.github/workflows/release.yml`: the publishing job gets `environment: release`.
- `README.md` (*Signing key*, *Install a test build*), `AGENTS.md` (§6 a "release key is permanent" gotcha, as in Pacer's CLAUDE.md), new `docs/SIGNING_BRIEF.md`, and `docs/PRODUCT_SPEC.md` decision **D22** (release key on `Rebuild_v2` only; debug is a separate app).

**Owner steps (written into the PR body; a PR cannot do them):**
1. GitHub → Settings → Environments → new `release`, with deployment branches limited to `Rebuild_v2`.
2. Move the secrets `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD` into it, then delete the repository-level copies. `SIGNING_CERT_SHA256` stays a variable.
3. Until step 2 is done, `Rebuild_v2` CI fails the certificate check. Branches pass with the debug key.

- [ ] **Step 1:** Run `./gradlew :app:assembleDebug :app:assembleRelease` with no signing env. Then confirm with `apksigner verify --print-certs` that both are debug-signed, and with `aapt2 dump packagename` that debug is `gr.edusuite.invoicer.debug`.
- [ ] **Step 2:** Make the edits.
- [ ] **Step 3:** Run step 1 again. Expected:
  - the debug package is `gr.edusuite.invoicer.debug` and the release package is `gr.edusuite.invoicer`;
  - the full check line passes.
- [ ] **Step 4:** Re-read both workflows. Nothing reads `secrets.SIGNING_*` outside the `release` environment, and `GITHUB_ENV` never receives the password.
- [ ] **Step 5: Commit** `build(signing): release key only on Rebuild_v2; debug is its own app`.

### Task I1: Adaptive masonry grid (same contract as P1)

**Files:** Create `app/…/ui/home/HomeGrid.kt` (`homeColumns`, `masonryPlacement`, `HomeGrid`, `animatePlacement`, using `animationsEnabled()` from `ui/common/SuiteComponents.kt:107` and the theme's `Motion.settle`). Test: `app/src/test/kotlin/gr/edusuite/invoicer/ui/home/HomeGridTest.kt`, with the same three tests as P1 (5 tiles, so `homeColumns(1248f, 1f, 5) == 5`).

- [ ] Steps 1–5 as in P1. Run: `./gradlew :app:testDebugUnitTest --tests '*HomeGridTest'`. Commit `feat(home): adaptive masonry grid`.

### Task I2: Home uses the grid; footer leaves Home

Split across two PRs:
- **I2a** (PR I-1):
  - remove the footer Text;
  - add "Part of the EduSuite apps, alongside Course Pacer." under the version line in Settings;
  - copy the style guide from P-1.
- **I2b** (PR I-2): the grid and the manifest change.

**Files:**
- Modify `app/…/ui/home/HomeScreen.kt:187-247`:
  - Drop `widthIn(max = 560.dp)`, the footer Text and the two-column Row.
  - Use `HomeGrid` with tiles in the order Lessons(tall 184dp), Statements(tall), Students(short 120dp), Dashboard(tall), Groups(short), each with `animatePlacement()`.
  - Keep `Spacer(88.dp)` for the FAB.
- Modify the manifest with the same `configChanges` as P2.
- Modify `docs/EDUSUITE_STYLE_GUIDE.md` so it is byte-identical with Pacer's after P2.

- [ ] **Step 1:** Make the edits.
- [ ] **Step 2: Run** `diff docs/EDUSUITE_STYLE_GUIDE.md /home/user/course_planner/docs/EDUSUITE_STYLE_GUIDE.md`. Expected: no output.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: PASS.
- [ ] **Step 4: Commit** `feat(home): fill wide screens, move EduSuite line to Settings`.

### Task I3: Overdue rule and due-date note (core)

**Files:**
- Create `core/…/model/Overdue.kt`.
- Modify `core/…/invoice/InvoiceDocumentBuilder.kt:60-74` (notes) and `core/…/repository/Repositories.kt` (`InvoiceRepository`).
- Modify `core/src/testFixtures/…/InMemoryRepositories.kt` and `app/…/data/SqlRepositories.kt`.
- Tests: `core/src/test/…/model/OverdueTest.kt`, `core/src/test/…/invoice/InvoiceDocumentTest.kt`, `core/src/test/…/testing/InMemoryRepositoriesTest.kt`, `app/src/androidTest/…/data/SqlRepositoriesTest.kt`.

**Interfaces:**
- Produces:
  - `fun dueDate(issueDate: LocalDate): LocalDate` (last day of the issue month)
  - `fun isOverdue(invoice: Invoice, payment: InvoicePayment?, today: LocalDate): Boolean`
  - `data class OverdueSummary(val statements: Int, val students: Set<Long>)`
  - `InvoiceRepository.observeOverdue(today: LocalDate): Flow<OverdueSummary>`
  - The SQL counts non-void invoices with `issue_date < firstOfMonth(today)` that have an unpaid `CHARGED_LESSON`, the same predicate as `observeUnpaidCount` (`SqlRepositories.kt:233`).

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun overdueFlipsOnFirstOfNextMonth() {
    val inv = invoice(issue = LocalDate.of(2026, 10, 31))
    assertFalse(isOverdue(inv, InvoicePayment(2, 1), LocalDate.of(2026, 10, 31)))
    assertTrue(isOverdue(inv, InvoicePayment(2, 1), LocalDate.of(2026, 11, 1)))
}
@Test fun voidIsNeverOverdue() { assertFalse(isOverdue(invoice(issue = oct1, voidedOn = oct2), InvoicePayment(1, 1), dec1)) }
@Test fun excusedOnlyStatementIsPaid() { assertFalse(isOverdue(invoice(issue = oct1), InvoicePayment(1, 0), dec1)) }
@Test fun newDocumentCarriesDueNote() { assertTrue(build(issue = oct12).notes.contains("Payment due by ${formats.date(LocalDate.of(2026, 10, 31))}")) }
@Test fun observeOverdueCountsStudents() // in InMemoryRepositoriesTest: two overdue statements for one student, one paid for another → OverdueSummary(2, setOf(s1))
```

- [ ] **Step 2: Run** `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** Implement as in the Interfaces block. Add the same query to `SqlRepositories` and a matching case in `SqlRepositoriesTest`. Its observe lists `Table.INVOICES, Table.LESSONS`.
- [ ] **Step 4: Run** `./gradlew :core:test :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5:** Update `docs/PRODUCT_SPEC.md` with new rule **R16** (overdue + due note) and commit `feat(core): overdue rule and due-date note`.

### Task I4: Overdue on Home and Students

**Files:**
- Modify `app/…/ui/home/HomeViewModel.kt`: add `overdueStatements: Int` to the state, from `invoices.observeOverdue(today())`.
- Modify `app/…/ui/home/HomeScreen.kt`:
  - The Statements tile subtitle is `"$n overdue"` in `Invoicer.colors.warn` with `StatusChip("Overdue", ChipTone.Warning, onTile = true)`.
  - A banner above the grid (warnSoft, guide §6 *Warning banner*): `"$n statements overdue"` with action `View`, which runs `onOpenStatements`.
- Modify `app/…/ui/students/StudentsViewModel.kt`: `StudentRow` gains `overdue: Boolean`.
- Modify `StudentsScreen.kt:98-116`: `StatusChip("Overdue", ChipTone.Warning)` in `supportingContent`, plus a banner `"$k students have overdue statements"` above the list when k > 0.
- Test: `app/src/test/…/ui/ViewModelsTest.kt`.

- [ ] **Step 1: Write failing tests:** `homeCountsOverdueStatements` (one overdue → `state.overdueStatements == 1`) and `studentsFlagOverdueRows`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ViewModelsTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): red overdue notices on Home and Students`.

### Task I5: Statement details screen; Settings becomes app-wide

**Files:**
- Create `app/…/ui/invoices/StatementDetailsScreen.kt` and `StatementDetailsViewModel.kt`. These take the name/address/show-address/phone/email/IBAN/footer fields, `onName…onFooter`, `onShowAddressOnStatements`, `flush()` and the debounce from `SettingsViewModel.kt:28-85`. Same `SettingsRepository`.
- Modify `SettingsViewModel.kt`/`SettingsScreen.kt`:
  - Remove those fields.
  - Sections become Appearance, Lessons (default length), Reminders, Privacy (I6), Backup, About.
  - About shows `Course Invoicer ${VERSION_NAME}`, "Part of the EduSuite apps, alongside Course Pacer." and "Everything stays on this device."
- Modify `ui/nav/Navigation.kt`: add `Route.StatementDetails` ⇄ `"statement-details"`. Modify `ui/CourseInvoicerApp.kt`: dispatch it.
- Modify `InvoicesScreen.kt`:
  - Add a `ListRow` at the top of the list (and above the empty state): "Statement details", subtitle `name · IBAN set` or `IBAN not set`.
  - When the name is blank it is a warning banner instead: "Add your name before you send statements".
  - New parameter `onStatementDetails: () -> Unit`.
- Modify `CreateInvoiceScreen.kt`/`ViewModel`: when `business.name.isBlank()`, Create opens a bottom sheet with Name and IBAN fields, a primary *Save and create* and a text action *More details* (navigates).
- Tests:
  - `NavigatorTest`: `statementDetailsRoundTrips`.
  - `ViewModelsTest`: move the business-field cases to `StatementDetailsViewModel`; add `createAsksForNameWhenBlank`.
  - `EndToEndTest.settingsSaveAsYouTypeWithoutASaveButton`: open Statements → *Statement details* instead of Settings.

- [ ] **Step 1:** Write or move the failing tests.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5:** Update `PRODUCT_SPEC.md` (screens table, R13 location) and commit `feat(statements): statement details screen; app-wide settings`.

### Task I6: Privacy notices and shareable parent notice

Split across two PRs:
- **I6a** (PR I-5, PATCH): the `StudentEditScreen` hint, the "(optional)" labels and the one-line notice on Statement details. Copy only; it has its own `ViewModelsTest`-free check, `:app:lintDebug`.
- **I6b** (PR I-6): `PrivacyNotice.kt` and its tests, `shareText`, the Settings Privacy section and the *Share privacy notice* button.

**Files:**
- Create `core/…/privacy/PrivacyNotice.kt`.
- Test: `core/src/test/…/privacy/PrivacyNoticeTest.kt`.
- Modify `SettingsScreen.kt` (Privacy section), `StatementDetailsScreen.kt` (one-line notice + *Share privacy notice*), `ui/students/StudentEditScreen.kt` (hint; labels `Class (optional)`, `Guardian (optional)`, `Phone (optional)`, `Email (optional)`, `Notes (optional)`), and `ui/common/Sharing.kt` (`fun shareText(context: Context, title: String, text: String)` via `Intent.ACTION_SEND`, `text/plain`).

**Interfaces:**
- Produces:
  - `enum class NoticeLanguage { EN, EL }`
  - `fun privacyNotice(details: BusinessDetails, language: NoticeLanguage): String`. Its text is the fixed copy in the Design section; blank fields and their separators are skipped.

- [ ] **Step 1: Write failing tests:**
  - `noticeNamesController` (contains the name and "controller" / "υπεύθυνος επεξεργασίας");
  - `noticeSkipsBlankFields` (name only → no ", ," and no "{");
  - `noticeListsAllArt13Items` (EN contains "Art. 6(1)(b)", "Art. 15–21", "Hellenic Data Protection Authority", "12 months", "not tax documents"; EL contains "άρθρο 6 παρ. 1 στοιχ. β", "Αρχή Προστασίας Δεδομένων Προσωπικού Χαρακτήρα", "12 μήνες");
  - `noticeClaimsNoLegalObligation` (EN has no "6(1)(c)" and no "tax authority"; EL has no "στοιχ. γ").
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*PrivacyNoticeTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement, then wire the UI. *Share privacy notice* opens a two-option chooser (English / Ελληνικά) and calls `shareText`. When the name is blank, it first sends the user to Statement details with the snackbar "Add your name first".
- [ ] **Step 4:** Run `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(privacy): GDPR notices and parent notice (EN/EL)`.

### Task I7: Erase student data (anonymise) and the erasure reminder

**Files:**
- Modify `core/…/usecase/StudentUseCases.kt`. Add `class EraseStudentData(students: StudentRepository, lessons: LessonRepository, invoices: InvoiceRepository)` with `suspend operator fun invoke(studentId: Long): Undoable<Unit>`. It:
  - sets the student to `firstName = "Erased student"`, `lastName = ""`, blank class/guardian/phone/email/notes, `archived = true`;
  - clears `notes` and absence notes on the student's lessons (`updateLessons`);
  - for each of the student's invoices, sets the frozen document (`setDocument`) to `billTo = listOf("Erased student")` and clears `notes`;
  - undo restores all three from the copies it took.
- Create `core/…/privacy/Retention.kt` with `fun dueForErasure(lastLesson: LocalDate?, outstanding: Money, today: LocalDate): Boolean`. It is true when there is a last lesson, nothing is owed, and `today` is more than 12 months after `lastLesson`.
- Modify `StudentDetailViewModel.kt`/`StudentDetailScreen.kt`: add a menu action *Erase student data* with a `ConfirmDialog`. Its text: "Removes this student's name and details from the app. Their statements keep only number, dates and amounts. Use this when lessons have ended and everything is paid." The snackbar offers Undo.
- Modify `StudentsViewModel.kt`/`StudentsScreen.kt`: archived rows get `StatusChip("Due for erasure", ChipTone.Neutral)`.
- Docs: `docs/PRODUCT_SPEC.md` gets new rule **R17** (anonymising erasure, the one permitted change to an issued document) and a note under R12 pointing to it.
- Tests: `core/src/test/…/usecase/StudentAndGroupUseCasesTest.kt`, `core/src/test/…/privacy/RetentionTest.kt`.

- [ ] **Step 1: Write failing tests:**
  - `eraseAnonymisesStatements`: after erase, each of the student's invoice documents has `billTo == listOf("Erased student")` with number and total unchanged; the student's name and contact fields hold no personal data; `archived` is true; the lesson notes are blank; undo restores everything.
  - `dueForErasureAfterTwelveMonthsPaid`: last lesson 2025-09-30, nothing owed → false on 2026-09-30 and true on 2026-10-01; with money owed → false.
- [ ] **Step 2: Run** `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(privacy): erase a student's data by anonymising it`.

### Task I8: Export a student's data

**Files:**
- Create `core/…/privacy/StudentDataExport.kt` with `fun studentDataJson(student: Student, lessons: List<Lesson>, invoices: List<Invoice>): String`. It uses `core/…/backup/Json`, with `version = 1`, ISO dates and money as cents.
- Modify `StudentDetailScreen.kt`/`ViewModel`: add a menu action *Export this student's data*. It writes the file to `cache/exports/<first-last>-data.json` and shares it through the existing `FileProvider` (pattern of `Sharing.kt:21-49`, MIME `application/json`).
- Modify `res/xml` FileProvider paths if `exports/` isn't covered.
- Test: `core/src/test/…/privacy/StudentDataExportTest.kt`.

- [ ] **Step 1: Write failing test** `exportHoldsOnlyThatStudent`: the JSON parses with `Json`, contains the student's fields, their 2 lessons and 1 statement, and no other student's id.
- [ ] **Step 2: Run** `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** Implement and wire the UI. Delete files older than 1 day in `cache/exports` before each export (audit S1 pattern).
- [ ] **Step 4:** Run `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(privacy): export one student's data`.

### Task I9: Close each Invoicer PR (once per PR I-1 … I-7)

**Files:**
- `app/build.gradle.kts`: the versionName from the *PR stack* table; versionCode +1 per PR.
- `CHANGELOG.md`: a new dated `## [X.Y.Z] - 2026-MM-DD` section, ordered Added/Changed/Removed/Fixed.
- `README.md` and `AGENTS.md` version lines.
- I-6 only: `docs/ROADMAP.md`, ticking the Privacy Policy screen item if listed.
- I-1 only: the `docs/superpowers/…` copies.

Each PR's base is the previous PR's branch (I-1: `Rebuild_v2`).

- [ ] **Step 1:** Make the edits.
- [ ] **Step 2: Run** `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit.
- [ ] **Step 4:** `git fetch origin Rebuild_v2`, then `git push -u origin claude/app-ui-ux-improvements-sxtsi9`.
- [ ] **Step 5:** If the push is refused, stop and report that the owner must install the Claude GitHub App for `eduinvoiceapp`. Otherwise open a PR against `Rebuild_v2` using `.github/pull_request_template.md`, titled `feat(home,statements,privacy): …`, and subscribe to it.
