## What and why
<!-- What changed, and the problem it solves. Link the issue or spec rule (R1–R13) it touches. -->

## How to test
<!-- Commands you ran and manual steps a reviewer can repeat on a device. -->
```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Checklist
- [ ] Tests added or updated (`:core` for rules, `app/src/test` for ViewModels, `app/src/androidTest` for SQL/UI)
- [ ] CI green, including emulator tests
- [ ] No new dependency, or approval noted in `docs/PRODUCT_SPEC.md` §7
- [ ] Schema change? `EduDatabase.VERSION` bumped, migration added, migration test added
- [ ] Data shape change? `BackupCodec.VERSION` bumped and older backups still import
- [ ] No XML layouts; Compose only
- [ ] `CHANGELOG.md` updated; `docs/PRODUCT_SPEC.md` updated if behaviour changed

## Screenshots
<!-- For UI changes: before / after, light and dark if relevant. Delete if not applicable. -->
