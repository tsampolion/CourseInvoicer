# Handoff brief for ChatGPT: Undo (roadmap item 2, Course Invoicer 2.1.0)

The owner, Dimitrios Tsampallas, gave roadmap item 2 (**Undo for destructive actions**) to ChatGPT.
This file is the whole brief. Read it to the end before you change anything. Where the brief and
the code disagree, trust the code and say so in your PR.

Paste [`CHATGPT_UNDO_PROMPT.md`](CHATGPT_UNDO_PROMPT.md) into ChatGPT to start.

The general rules in [`CHATGPT_BRIEF.md`](CHATGPT_BRIEF.md) §1, §2, §4, §8 and §9 still apply:
- the repository and its rules, and the building/testing limits without Google Maven
- branches, commits, PRs and CI, and docs in every PR

This brief adds only what is specific to Undo. **Read those sections of `CHATGPT_BRIEF.md` first.**

---

## 1. State of `Rebuild_v2` you start from

Everything from your earlier PRs (#372, #375, #376) and Claude's style guide v3 work (#368–#371) is
merged:
- **Version:** `2.1.0`, `versionCode 204`, with `## [2.1.0] - Unreleased` open. Don't date it or
  bump anything.
- **Use cases in `core/src/main/kotlin/gr/edusuite/invoicer/core/usecase/`** that you build on
  (signatures as of now):
  - `LessonUseCases.kt`:
    - `DeleteLesson(lessons).invoke(lessonId: Long)`: returns nothing; refuses a lesson that is on
      a statement.
    - `RecordPayment(lessons).invoke(studentId, paidOn): PaymentResult(lessonCount, amount)`:
      marks every *outstanding* lesson of the student paid.
    - `RescheduleLessons(lessons).invoke(lessonIds, date, startTime): Int`: unpaid, uninvoiced
      lessons only; calls `lessons.updateLessons(...)`.
    - `MarkPaid(lessons).invoke(lessonIds, paidOn: LocalDate?): Int`: your own use case. It sets or
      clears payment on charged lessons and returns the count changed.
  - `StudentUseCases.kt`: `ArchiveStudent(students).invoke(studentId, archived)`.
  - `GroupUseCases.kt`: `ArchiveGroup(groups).invoke(groupId, archived)`.
- **`LessonRepository`** (`core/.../repository/Repositories.kt`):
  - `getLesson`, `getLessons(ids)`, `saveLesson`, `insertLessons(list): List<Long>`,
    `updateLessons(list)`, `deleteLesson(id)`, `setPaid(ids, paidOn)`
  - SQL implementation in `app/.../data/SqlRepositories.kt`; in-memory fake in
    `core/src/testFixtures/.../testing/InMemoryRepositories.kt`.
- **UI messages:**
  - `MessageEffect(message, snackbarHostState, onShown)` in `app/.../ui/common/Components.kt` shows a
    plain snackbar with no action.
  - About 30 places create their own `SnackbarHostState()` per screen.
  - `ConfirmDialog(...)` is in the same file.
- **Confirm dialogs today** (find them with `grep -rn "ConfirmDialog(" app/src/main`):
  - `LessonEditScreen.kt` (delete lesson)
  - `StudentDetailScreen.kt` (`PendingAction.Payment`, `PendingAction.Archive`, `PendingAction.Delete`)
  - `GroupEditScreen.kt`
  - `InvoicesScreen.kt` (void)
  - `SettingsScreen.kt` (import backup)
  - `ui/common/DraftBack.kt` (discard draft)

## 2. Scope (decided; do not widen)

Decision **D15** in `docs/PRODUCT_SPEC.md`, extended by style guide v3 §"Flow" ("Undo over
confirm"):

| Action | Where it starts | Undo restores |
|--------|-----------------|---------------|
| Delete a lesson | `LessonEditScreen` → `DeleteLesson` | The exact row, **same id**, every field |
| Record a payment | `StudentDetailScreen` → `RecordPayment` | Each affected lesson's previous `paidOn` (was `null`) |
| Mark paid / unpaid, one or many | `LessonsScreen`, `StudentDetailScreen` → `MarkPaid` | Each affected lesson's **own** previous `paidOn` (may differ per lesson) |
| Reschedule | `LessonsScreen` → `RescheduleLessons` | Each lesson's previous `date` and `startTime` |
| Archive / restore a student | `StudentDetailScreen` → `ArchiveStudent` | The previous `archived` flag |
| Archive / restore a group | `GroupsScreen` / `GroupEditScreen` → `ArchiveGroup` | The previous `archived` flag |

**Out of scope, keep their confirm dialogs:**
- voiding a statement (R6, D7: a void is final)
- importing a backup (it replaces everything; the advice is to export first)
- deleting a student or group (it cascades through lessons and members, and the inverse is not
  worth the risk now)
- discarding a form draft (`DraftBack.kt`, item 8)

**Undo window: 5 seconds**, from style guide v3 ("offers *Undo* in a snackbar for 5 s"). The
roadmap's older "about 10 seconds" is superseded. Tell the owner in the PR that you followed the
guide.

## 3. Design

### 3.1 Core: an `Undo` value from each in-scope use case
In `core/.../usecase/Undo.kt` (new):

```kotlin
/** Reverses exactly what one action changed. Running it twice is harmless (idempotent). */
fun interface Undo { suspend operator fun invoke() }

/** What an undoable action did: a message for the snackbar and the way back. */
data class Undoable<T>(val result: T, val undo: Undo)
```

Change the in-scope use cases to return `Undoable<…>`, keeping today's result inside so callers
still have it:
- `DeleteLesson` → `Undoable<Unit>`.
  - It captures the full `Lesson` before deleting. Undo re-inserts it with the same id.
  - Add `LessonRepository.restoreLesson(lesson: Lesson)`, which inserts with an explicit id, and
    implement it in SQL (`INSERT` with the id column inside `db.write(Table.LESSONS)`) and in the
    fake.
  - If a row with that id exists, throw `DomainException("This lesson already exists")`.
  - Don't use `saveLesson`, which treats a non-zero id as an update.
- `RecordPayment` → `Undoable<PaymentResult>`. Undo sets `paidOn = null` for exactly the ids it
  paid.
- `MarkPaid` → `Undoable<Int>`.
  - Capture `id → previous paidOn` for the `changed` lessons.
  - Undo restores each lesson's own previous value. Group by value and call `setPaid` once per
    distinct previous date, or add `LessonRepository.restorePaid(map: Map<Long, LocalDate?>)` that
    does it in one `db.write`. **Prefer the new method**, so the undo is one transaction.
- `RescheduleLessons` → `Undoable<Int>`. Capture each lesson's old `date` and `startTime`; undo
  calls `updateLessons` with them restored (one `db.write`).
- `ArchiveStudent` / `ArchiveGroup` → `Undoable<Unit>`. Undo sets `archived` back to its previous
  value. If the previous value was already the requested one, the undo is a no-op.

**Undo safety.** Undo must not break a rule that changed meanwhile:
- If a lesson is now on a statement, R7 locks its date and time. Undoing a reschedule on it must
  fail with `DomainException("A lesson is now on a statement; it can't be moved back")`.
- Undoing a delete when the student no longer exists fails with a readable message. The foreign
  key fails; catch it in the use case and turn it into a `DomainException`.
- Undo is all-or-nothing. Do the checks first, then write in one transaction.

### 3.2 App: one message channel with an action
- New `app/.../ui/common/Messages.kt`:

  ```kotlin
  data class UserMessage(val text: String, val actionLabel: String? = null, val action: (suspend () -> Unit)? = null)
  class Messages { val flow: SharedFlow<UserMessage>; fun post(message: UserMessage) }
  ```

  It is a `MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`, so a newer
  message replaces an older pending one.
- Create one instance in `AppContainer` (`val messages = Messages()`).
- In `ui/CourseInvoicerApp.kt`, hoist **one** `SnackbarHostState` above the `NavHost`. Collect
  `messages.flow` in a `LaunchedEffect` and show each message:
  - `withTimeoutOrNull(5_000) { host.showSnackbar(text, actionLabel, duration = Indefinite) }`
  - On `ActionPerformed`, run the action in the app scope (`rememberCoroutineScope`, or better an
    application-level `CoroutineScope(SupervisorJob() + Dispatchers.Main)` in `AppContainer`, so it
    survives the screen that posted it).
  - Wrap the action so a `DomainException` posts its message as a new plain `UserMessage`.
- **Where it shows:** the host has to appear above every screen. The simplest way is a `Box` in
  `CourseInvoicerApp` containing the `NavHost` with a `SnackbarHost` aligned to the bottom, padded
  for the navigation bar (and above a FAB where the screen has one: add bottom padding 80.dp; check
  Home and Lessons).
- **Countdown bar:** the style guide says the snackbar shows *Undo* with a thin bar that empties.
  - Render a custom `snackbar = { data -> … }` with an `Snackbar` plus a
    `LinearProgressIndicator` (`progress = { remaining }`) animated from 1 to 0 over 5 s with
    `animateFloatAsState` / `Animatable`.
  - Respect reduced motion: when animations are off (`animationsEnabled()` in `SuiteComponents.kt`),
    show no bar.
- **Screens keep their own snackbar hosts** for plain messages (no need to migrate them all). Only
  undo messages go through `Messages`. If you find it simpler to move every screen to the app-level
  host, that is allowed, but say so in the PR.

### 3.3 ViewModels and screens
- Every in-scope action now:
  - runs immediately, with no confirm dialog
  - then posts `UserMessage(text, "Undo") { undoable.undo() }`
- The ViewModels get `messages: Messages` through their constructor, with a default for tests (a
  fresh `Messages()`), and the screen's `appViewModel { … }` passes `it.messages`.
- **Texts** ("statement" in user text, D20; sentence case, no exclamation marks, style guide copy
  rules):
  - "Lesson deleted"
  - "Payment recorded: 3 lessons, €60.00". Format with `Formats`, as the current payment message
    does; reuse its wording if it differs.
  - "Marked 3 lessons paid" / "Marked 1 lesson unpaid" (use `plural()`)
  - "Moved 2 lessons to 12 Mar 2026, 17:00"
  - "Student archived" / "Student restored"
  - "Group archived" / "Group restored"
- **Delete lesson:** `LessonEditScreen` closes straight away. That is fine, because the message now
  lives at app level.
- **Remove these `ConfirmDialog`s:**
  - delete lesson in `LessonEditScreen`
  - `PendingAction.Payment` and `PendingAction.Archive` in `StudentDetailScreen`
  - the archive one in `GroupEditScreen` (check what that dialog confirms; if it is delete, keep it)

  Remove any reschedule or mark-paid confirmations you find in `LessonsScreen`.
- **Keep:** the void, import, delete-student, delete-group and draft-discard dialogs.

## 4. Tests

- **`:core`** (`core/src/test/...`):
  - A new `UndoTest.kt`, or additions to `LessonUseCasesTest` / `StudentAndGroupUseCasesTest`.
  - For **each** in-scope use case: snapshot the relevant rows, do the action, run the undo, and
    assert the rows equal the snapshot (all fields, ids included).
  - Specific cases:
    - **MarkPaid with mixed previous dates:** lesson A paid 1 March, lesson B unpaid. Mark both
      paid on 10 March, undo, and A is back to 1 March and B to unpaid.
    - **Running the undo twice** changes nothing the second time.
    - **Reschedule undo** fails with the R7 message if a lesson went onto a statement in between.
    - **Delete-lesson undo** restores the same id, and a statement link is impossible: deleting an
      invoiced lesson is refused anyway, so assert that.
  - **InMemoryRepositories** gets `restoreLesson` and `restorePaid`, mirroring SQL, with
    `InMemoryRepositoriesTest` coverage.
- **SQL** (`app/src/androidTest/.../data/SqlRepositoriesTest.kt`): `restoreLesson` with an explicit
  id; a duplicate id fails; `restorePaid` with mixed values in one transaction.
- **ViewModels** (`app/src/test/.../ViewModelsTest.kt`):
  - Deleting a lesson posts a message with "Undo".
  - Invoking its action restores the lesson in the repository.
  - Recording a payment no longer needs a confirm step.
- **End to end** (`app/src/androidTest/.../ui/EndToEndTest.kt`), one flow:
  - add a student, log a lesson, open it, delete it
  - click "Undo" on the snackbar (`onNodeWithText("Undo")`)
  - the lesson is listed again (`Lessons (1)` on the student detail)

  Keep the existing tests passing. Some may click a now-removed confirm button; update them.

## 5. Docs

- `docs/PRODUCT_SPEC.md`:
  - D15: mark it **built in 2.1.0**, and note archive/restore was added per style guide v3 and the
    window is 5 s.
  - §4 feature rows for Lessons, Students and Groups: "… happens at once and offers *Undo* for
    5 s".
  - If a rule text says "asks to confirm", update it.
- `docs/ROADMAP.md` item 2: tick every box, set Done (2.1.0), and move it to *Done*. Fix "about 10
  seconds" to 5.
- `CHANGELOG.md` `## [2.1.0] - Unreleased` → `### Added`: "**Undo** for deleting a lesson,
  recording a payment, marking lessons paid or unpaid, rescheduling, and archiving or restoring a
  student or group: the action happens at once and the snackbar offers *Undo* for 5 seconds.
  Voiding a statement, importing a backup and deleting a student or group still ask first."
- `AGENTS.md` §2 *ViewModels*: add "Undoable actions return `Undoable` from their use case and post
  to `Messages`".

## 6. Branch, PR, done

- **Branch:** `chatgpt/undo` from `origin/Rebuild_v2`. **One PR**, draft, base `Rebuild_v2`.
- **Title:** `[feat](undo): undo instead of confirm for lessons, payments, rescheduling and archiving`
- Follow `CHATGPT_BRIEF.md` §8 for commits, the CI requirement, the AI trailer and "don't merge
  unless told".
- **Done when:**
  - every in-scope action runs at once and shows *Undo* for 5 s
  - the undo restores the exact previous rows (tested in `:core` per action, plus SQL and one end
    to end)
  - the confirm dialogs for those actions are gone
  - CI is green
- **Hand-back:** one PR comment listing:
  1. any choice this brief left open
  2. anything you couldn't do
  3. the final signatures of `Undo`, `Undoable`, `Messages` and the changed use cases
