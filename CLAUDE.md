# CLAUDE.md: PingPaisa (PingUI) project instructions

Permanent instructions for working in this repository. Current status and the step list live in [PROGRESS.md](PROGRESS.md); read it first and **keep it up to date** at the end of every phase (and after any notable change).

## What this project is
PingUI is a native Android app (Kotlin, Jetpack Compose, package `com.pingpaisa.app`) that started as a design-only prototype. The goal is to make it fully functional by porting the features of the sibling project `../Smart_Expense_Tracker` into PingUI's existing screens. Everything is on-device: there is no backend, no network and no cloud account.

Work happens on the branch `integrate-tracker`. `main` holds the original prototype and must not be changed directly.

## Non-negotiable rules
1. **Keep the design.** Do not change PingUI's look, layout or structure. New screens must be built from the existing clay components (`ClayComponents.kt`, `ClayTextField`, `NativeText`, `PrimaryAction`, …) and its colours. If a feature needs a new control, add the smallest thing that fits and say so.
2. **Do not remove existing functionality**, and do not duplicate code. Reuse the repository, DAOs, ported tracker logic and shared helpers (`Money`, `categoryStyle`, `amountText`, `HomeSummary.parseDate`).
3. **`../Smart_Expense_Tracker` is a read-only reference.** Never edit it.
4. **Git:**
   - Commit **only with the user's configured Git identity**; never add `Co-Authored-By` or other attribution trailers, and never pass `--author`. Check with `git log --format='%an <%ae> %(trailers)' -1`.
   - **Commit and push only when the user asks.** Before committing, list what will be included and scan the changed files for secrets. Never push to or change `main`. Never force-push or rewrite pushed history unless asked.
   - Do not open pull requests unless asked. The repository is owned by Jatin-Kumar-Yadav and is public; the user (JanviY25) is a collaborator.
5. **Ask before acting on the user's data or device.** Never delete, reset or overwrite data on the connected phone (database, app data) without being asked. Inserting test rows is allowed only on request, and they must be clearly marked (for example reference `SAMPLE-n`).
6. **Work step by step.** Follow the numbered plan in PROGRESS.md, one step at a time, and wait for the user to confirm before the next step. Say plainly what was and was not verified; never claim something works if it was only compiled.

## Architecture conventions
- **Data flow:** Room DAOs (`suspend` + `Flow`) → `ExpenseRepository` → `AppViewModel` (StateFlows) → `NativeScreen` in `MainActivity.kt` → screen composables. Screens do not touch the database.
- **One shared database instance** via `AppDatabase.getDatabase`, also used by the notification listener. Categorizing (saved merchant choice first, then the dataset) lives only in `ExpenseRepository.categorize`.
- **Pure `*Ui` builders.** Each real screen gets a plain-Kotlin builder (no Android types except resource ids) that turns data into display text (`HomeUi`, `TransactionsUi`, `DetailUi`, `RecurringUi`, `HealthUi`, `LoanUi`). Put logic there, not in composables, so it can be unit-tested.
- **Sample path:** every screen composable keeps the design's original sample content as its default, shown when `vm == null` (Android Studio previews and the `verification/` screenshot tests). Do not remove it.
- **Navigation and lock:** screens are the `Screen` enum; `StartupGate.kt` decides what may be shown (no account → setup screens only; locked → login). New screens that must not be reachable before sign-in belong in the gate's sets.
- **Money and dates:** format rupees only with `Money.inr` / `Money.compact` / `Money.compactSigned` (Indian lakh/crore grouping, "−" minus sign). Transaction dates are stored as `dd/MM/yy` text and must be read with the strict `HomeSummary.parseDate`.
- **Income vs expense:** `transactions.type` is `expense` or `income`. Feed spending calculations expenses only. The monthly salary is in `user_profile`; extra income is `income` transactions.
- **Security:** the password is never stored or logged, only the salted PBKDF2 hash (`PasswordHasher`); hash and verify off the UI thread; wipe `CharArray`s after use.

## Database rules
- Every schema change **requires a Room `Migration`** and a version bump; the destructive fallback was removed on purpose (only downgrades are destructive). Update `exportSchema` output in `app/schemas/` and commit it.
- **Test a migration on a copy of the phone's database first** (pull `databases/pingpaisa_db*` with `adb exec-out run-as com.pingpaisa.app cat …`, run the SQL on the copy, compare with the exported schema), then install. Delete temporary copies afterwards: they contain the user's data.

## Build, test and run (Windows, Git Bash)
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk      # phone: Samsung SM-M356B over USB
```
- Toolchain is pinned: AGP 8.9.2, Kotlin 2.1.20, KSP 2.1.20-2.0.1, Room 2.7.1, compileSdk/targetSdk 35, minSdk 26, JDK 17. Do not accept the AGP upgrade prompt or bump these casually.
- Use `timeout` on `adb install`; it hangs if the phone is locked or disconnected. Use `${VAR:?}` in any `rm` that uses a variable.
- Run the whole unit-test suite before saying a step is done. Add tests for new logic in `app/src/test/…`.
- The notification listener only works on a real device with notification access on.

## Decisions already made
- On-device account with password (optional fingerprint later: step 27 is deferred); Paisa AI will be rule-based and offline; Scan will use CameraX + ML Kit on-device with manual price entry as a fallback.
- Health score weights: savings 35%, EMI 30%, consistency 20%, reliability 15%. Only EMI and "Other" recurring payments count as outflow; SIPs do not.
- Bank quotes are stored with the tracker's `QuoteStore` (SharedPreferences).

## Files to be careful with
`MainActivity.kt`, `MoneyScreens.kt`, `HomeDashboard.kt`, `IntelligenceScreens.kt`: heavily changed on this branch and likely to conflict if the other collaborator edits the UI on `main`. Before merging, bring `origin/main` into `integrate-tracker` and resolve conflicts there.
