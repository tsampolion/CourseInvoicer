# Prompt to give ChatGPT

Copy everything inside the fence into a ChatGPT session that the owner starts, ideally one with
repository access (Codex or a connected GitHub repo). The brief it points to is
[`CHATGPT_BRIEF.md`](CHATGPT_BRIEF.md).

```text
You are working on my Android app Course Invoicer, in the GitHub repository
tsampolion/EduInvoiceApp (default branch Rebuild_v2). I am the owner, Dimitrios Tsampallas, and I'm
authorising you to do the work described below, and only that work.

Before you write any code, read these files from Rebuild_v2 in this order:
1. docs/handoff/CHATGPT_BRIEF.md: your complete brief. It is long on purpose; follow it exactly.
2. AGENTS.md: the repository's rules for contributors and agents.
3. docs/PRODUCT_SPEC.md and docs/ROADMAP.md (items 1, 5 and 8).

Your job is three stacked pull requests, in this order:
- PR 1, roadmap item 5: golden tests for the billing engine, a rate cap, and a two-decimal-currency rule.
- PR 2, roadmap item 1: stateInCatching, guarded form edits, and MarkPaid / ArchiveStudent / ArchiveGroup use cases.
- PR 3, roadmap item 8: keep a damaged database as .bad, refuse downgrades, and save form drafts in SavedStateHandle.

Claude Code is working on other parts of the app at the same time: the theme, components, the Home
layout and Undo. Don't edit the files the brief lists as Claude's. Keep both sides when docs conflict.

Hard rules:
- no new dependencies
- business rules live in :core with tests
- Money is Long cents
- user-visible text says "statement", code says "invoice"
- never drop user data
- don't date the CHANGELOG, bump the version or tag anything
- never skip or delete a failing test
- never push to Rebuild_v2 or force-push
- open each PR as a draft and drive its CI (two jobs) to green
- don't merge unless I tell you to

If your environment can't reach dl.google.com, Gradle can't build the app. Use the scratch :core
build in §4 of the brief, and say in each PR what you could and couldn't run.

Start by confirming you've read the brief. List the three branches and PR titles you'll create, and
any point in the brief that contradicts the code you see. Then begin PR 1.
```
