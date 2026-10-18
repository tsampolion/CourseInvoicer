# Prompt to give ChatGPT for Undo

Copy everything in the fence into a ChatGPT session started by the owner, one with access to the
repository.

```text
You are working on my Android app Course Invoicer, in the GitHub repository tsampolion/CourseInvoicer
(formerly EduInvoiceApp), default branch Rebuild_v2. I am the owner, Dimitrios Tsampallas, and I'm
authorising you to implement roadmap item 2, Undo, and only that.

Before you write any code, read these files from Rebuild_v2, in this order:
1. docs/handoff/CHATGPT_UNDO_BRIEF.md: your brief for this task. Follow it exactly.
2. docs/handoff/CHATGPT_BRIEF.md, sections 1, 2, 4, 8 and 9: the general rules, how to build and
   test without Google Maven, and the branch/PR/CI conventions you used before.
3. AGENTS.md, docs/PRODUCT_SPEC.md (decision D15) and docs/ROADMAP.md (item 2).
4. docs/EDUSUITE_STYLE_GUIDE.md: the snackbar, motion and "Undo over confirm" rules.

Deliver one draft PR from branch chatgpt/undo into Rebuild_v2:
- In-scope actions run at once and show Undo for 5 seconds: delete a lesson, record a payment, mark
  paid or unpaid, reschedule, and archive or restore a student or group.
- Undo restores the exact previous rows.
- Their confirm dialogs go.
- Voiding a statement, importing a backup, deleting a student or group, and discarding a draft keep
  their dialogs.

Hard rules:
- no new dependencies
- business rules in :core, with tests
- Money is Long cents
- user-visible text says "statement", code says "invoice"
- never drop user data
- don't date the CHANGELOG, bump the version or tag anything
- never skip or delete a failing test
- never push to Rebuild_v2 or force-push
- drive the PR's CI (two jobs) to green
- don't merge unless I tell you to

Start by confirming you've read the brief. Then list the files you'll change and anything in the
brief that contradicts the code you see. Then begin.
```
