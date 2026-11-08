# PingPaisa: integration progress

Making the PingUI design prototype fully functional by porting the features of `Smart_Expense_Tracker` into PingUI's existing screens, without changing the design.

**Last updated:** 2026-11-09 · **Status:** Phases 1–3 done; Phase 4 done except the Scan steps (47, 48) and its checkpoint (50), which a teammate is building separately.

| | |
|---|---|
| **Git branch** | `integrate-tracker` (tracks `origin/integrate-tracker`) |
| **Latest commit** | "Phase 4: budgets, goals, cash flow, forecast, money map and Paisa AI on real data" (find its hash with `git log -1`). The commit before it is `204b9d6` (Phase 3). |
| **Pushed?** | Yes, once the Phase 4 commit has been pushed. `main` is untouched and still holds the original UI-only prototype. |
| **Uncommitted** | Nothing after the Phase 4 commit. |
| **Remote** | `github.com/Jatin-Kumar-Yadav/PingUI` (public). Owner: Jatin-Kumar-Yadav; JanviY25 is a collaborator. |
| **Commits on the branch** | `f71dcf8` Phase 1 · `acc521d` Phase 2 · `204b9d6` Phase 3 · Phase 4 (this commit; on top of `bf32268` first commit) |
| **Build / tests** | `assembleDebug` succeeds · **163 / 163** unit tests pass (details in [Build and testing](#build-and-testing-status)) |

---

## 1. Plan and completed steps

The plan has 62 steps in 6 phases. Step numbers below are the plan's numbers.

| Phase | Steps | Status | Commit |
|---|---|---|---|
| **1. Foundation** (no visible change) | 1–19 | Done | `f71dcf8` |
| **2. Account and permission** | 20–30 | Done, except step 27 (deferred) | `acc521d` |
| **3. Core money screens on real data** | 31–41 | Done | `204b9d6` |
| **4. Make the remaining dummy screens real** | 42–50 | **Not started** | |
| **5. Settings** | 51–57 | Not started | |
| **6. Hardening** | 58–62 | Not started | |

### Phase 1: Foundation (steps 1–19) ✅
Branch created; KSP, Room 2.7.1, lifecycle 2.8.7 and coroutines added to Gradle; categorization dataset copied; all entities, DAOs and `AppDatabase` created; `SmsParser`, `Categorizer`, `RecurringDetector` and `PaymentNotificationListener` ported unchanged (package renamed); health-score and loan engine ported; one rupee formatter (`Money`); live `Flow` queries; `ExpenseRepository`; `AppViewModel`; listener registered in the manifest; `MainActivity` starts the categorizer and passes the ViewModel to every screen. Checkpoint 1 passed on the phone.

### Phase 2: Account and permission (steps 20–30) ✅ (27 deferred)
- 20 password hashing · 21 Sign up screen · 22 sign-up checks · 23 account saved to Room (insert-if-absent, can never overwrite an account)
- 24 Notification access screen · 25 detects whether access is on (re-checked every time the screen returns to the foreground)
- 26 Lock screen is now a password login with a growing wait after 5 wrong tries
- **27 optional fingerprint unlock: deferred by the user ("later")**
- 28 start-up gate (decides the first screen, re-locks after 2 minutes in the background) · 29 drawer and menu cleanup · 30 checkpoint 2 passed on the phone

### Phase 3: Core money screens on real data (steps 31–41) ✅
- 31 Home · 32 Transactions · 33 Add an expense · 34 Transaction detail · 35 Detail editing (category, note, Save; database v2 migration)
- 36 Recurring list · 37 add / confirm / delete recurring payments · 38 Financial health · 39 Loan simulator · 40 bank quotes, comparison and verdict
- 41 checkpoint 3: each screen was checked on the phone by the user as it was built

### Changes made along the way that are not in the original plan
- Month **Earlier / Later** switcher on Transactions (without it only the current month is reachable).
- Strict date parsing: impossible dates such as `31/02/26` are rejected instead of quietly becoming 28 February.
- **"Other" recurring payments now count as money going out** in the health score (the tracker counted only EMIs). SIPs stay excluded because they are savings.
- Database migration infrastructure: `exportSchema = true` (schemas in `app/schemas/`), the destructive "wipe on schema change" fallback removed.
- Health-score snapshots are now actually recorded (the tracker had the table but never wrote to it).
- Sample content for previews and screenshot tests is kept for every changed screen (see [Architecture](#3-current-application-architecture)).

---

## 2. Current phase progress

**Phase 4: Make the remaining dummy screens real: 7 of 9 steps done (42–46 and 49); 47 and 48 (Scan) are with a teammate; 50 (checkpoint) waits for Scan.** Next step: **51, Settings** (Phase 5).

| Step | Work | Screen |
|---|---|---|
| 42 ✓ | Per-category monthly limits, real progress rings, money remaining | Budgets & goals |
| 43 ✓ | Add / edit a savings goal and track it | Budgets & goals |
| 44 ✓ | 6 months of income against expenses, net savings, month summary | Cash flow |
| 45 ✓ | Month-end projection from the daily average plus upcoming recurring payments | June forecast |
| 46 ✓ | Real daily-spending calendar heatmap | Your money map |
| 47 (deferred) | CameraX and ML Kit text recognition (on-device) | Scan price |
| 48 (deferred) | Camera permission, read the price, "can I afford it" verdict, typing as fallback | Scan price |
| 49 ✓ | Offline answers about your own spending, budget and affordability, plus the suggestion chips | Paisa AI |
| 50 (deferred) | Checkpoint 4: no dummy data left | |

### Screen status

| Screen | State |
|---|---|
| Onboarding | Static design content (no data needed) |
| Sign up · Notification access · Login (Lock) | **Real** |
| Home · Transactions · Transaction detail · Add an expense | **Real** |
| Recurring payments · Financial health · Loan simulator | **Real** |
| Budgets & goals · Cash flow · Forecast · Money map · Paisa AI | **Real** (Phase 4) |
| Scan price | Design sample data. Being built by a teammate; the "can I afford it" verdict is added after his work is merged (steps 47–48) |
| Settings | Design sample data, including the profile "Aanya Mehta" (Phase 5) |
| Style Guide | Design reference; hidden from the menu for signed-in users |

---

## 3. Current application architecture

Kotlin + Jetpack Compose, single activity, package `com.pingpaisa.app`. No backend or network access: everything is stored and computed on the phone.

```
Room database (pingpaisa_db, v2)
   │  DAOs: suspend functions + Flow queries
   ▼
ExpenseRepository            one place for reads/writes and the categorize rule
   │
   ▼
AppViewModel                 StateFlows for every screen; hashing/login; score; session lock
   │  collectAsStateWithLifecycle
   ▼
NativeScreen(...) in MainActivity.kt        builds a *Ui object per screen
   │
   ▼
Screen composables           draw the design (clay components) from the *Ui object
```

**Pattern used for every real screen**
1. A plain-Kotlin builder (no Android) turns data into display text: `HomeSummary` + `HomeUi`, `TransactionsUi`, `DetailUi`, `RecurringUi`, `HealthUi`, `LoanUi`. These are unit-tested.
2. The screen composable takes that object, with a default of the **design's sample** content.
3. `NativeScreen` passes the real object when the ViewModel exists. With `vm == null` (Android Studio previews and the screenshot tests in `verification/`) the screen shows the original sample, so the design reference is never lost.

**Navigation** (`MainActivity.kt`): the `Screen` enum (19 entries), a custom back stack with slide/fade transitions, a hidden drawer, five fixed tabs (Home, Stats, Scan, AI, You). `StartupGate.kt` decides what may be shown on every screen change:
- no account → only Onboarding, Sign up, Notification access
- account with app lock on and not unlocked this session → always the login screen (even if Android restored "Home")
- unlocked → anything except Onboarding and Sign up

The unlocked flag is memory only. The app re-locks after 2 minutes in the background. Setup and login screens are dropped from the back history; Back on a root screen leaves the app.

**Background capture:** `PaymentNotificationListener` (a `NotificationListenerService`) reads Google Messages notifications, parses a UPI debit SMS, and saves it through `ExpenseRepository.saveParsedPayment`. It runs only after the user turns on notification access.

### Package layout (`app/src/main/java/com/pingpaisa/app/`)
| Path | Contents |
|---|---|
| root | `MainActivity`, `AppViewModel`, `StartupGate`, screens (`HomeDashboard`, `MoneyScreens`, `IntelligenceScreens`, `SettingsScreen`, `GettingStartedScreens`, `AccountScreens`), design system (`ClayComponents`, `Motion`, `PingPaisaTypography`), `*Ui` builders, `CategoryStyle`, `TransactionFormat`, `EntryInput` |
| `data/` | Room entities, DAOs, `AppDatabase`, `ExpenseRepository`, `SmsParser`, `Categorizer`, `RecurringDetector`, `PaymentNotificationListener` |
| `healthscore/` | `ScoreCalculator`, `SpendingHistory`, `InsightEngine`, `VerdictEngine`, `LoanBanks` (loan maths, eligibility, quotes, `QuoteStore`), `Money`, `HealthScoreHistory` + DAO |
| `security/` | `PasswordHasher`, `SignUpValidation` |

---

## 4. Database: entities, DAOs and relationships

`pingpaisa_db`, **version 2**, Room 2.7.1 (KSP). Schema JSON for v2 is in `app/schemas/com.pingpaisa.app.data.AppDatabase/2.json`. Migration `MIGRATION_1_2` adds `transactions.note`. The destructive fallback now applies only to a *downgrade* (installing an older build over a newer database).

### Entities (7 tables)
| Table | Columns | Notes |
|---|---|---|
| `transactions` | `id` (PK, auto), `amount`, `recipient`, `date` (`dd/MM/yy`), `refNumber` (**unique**), `rawText`, `source` (`SMS`/`manual`), `category`, `subcategory`, `type` (`expense`/`income`), `note` (v2) | Manual entries get `refNumber = MANUAL-<millis>` |
| `merchant_overrides` | `recipient` (PK, lowercase), `category` | Category the user chose for a merchant |
| `recurring_items` | `id` (PK, auto), `name`, `amount`, `category`, `dayOfMonth` (1–31), `type` (`EMI`/`SIP`/`Other`) | |
| `health_score_history` | `id` (PK, auto), `score`, `riskLevel`, `timestamp` | One snapshot per day, or when the score changes |
| `user_profile` | `id` (PK, always 1), `name`, `monthlySalary`, `passwordHash`, `passwordSalt`, `appLockEnabled`, `biometricEnabled`, `createdAt` | One account per phone; no plain password stored |
| `budgets` | `category` (PK), `monthlyLimit` | Read by Home's "Budget left", but **no screen can add or edit a budget yet** (step 42) |
| `savings_goals` | `id` (PK, auto), `name`, `targetAmount`, `savedAmount`, `createdAt` | Created but **not used by any screen yet** (step 43) |

### DAOs
| DAO | Methods |
|---|---|
| `TransactionDao` | `observeAll`, `observeById`, `getAll`, `insert` (ignore duplicates), `existsByRef`, `updateCategoryForRecipient` (also resets `subcategory`), `updateNote` |
| `MerchantOverrideDao` | `observeAll`, `setOverride` (replace), `getOverride` |
| `RecurringItemDao` | `observeAll`, `getAll`, `insert`, `delete` |
| `HealthScoreHistoryDao` | `observeAll`, `getAll`, `insert`, `latest` |
| `UserProfileDao` | `observe`, `get`, `exists`, `insertIfAbsent`, `save`, `updateNameAndSalary`, `setAppLock`, `setBiometric`, `delete` |
| `BudgetDao` | `observeAll`, `getAll`, `upsert`, `delete` |
| `SavingsGoalDao` | `observeAll`, `getAll`, `insert`, `update`, `delete` |

### Relationships
There are **no foreign keys**. Tables are linked by value, as in the tracker:
- `transactions.recipient` ↔ `merchant_overrides.recipient`: matched on the lowercase name when categorizing, and `updateCategoryForRecipient` rewrites all of a merchant's rows.
- `transactions.category` ↔ `budgets.category`: exact text match (used for "budget left"; budgets are not yet editable).
- `recurring_items.name` ↔ `transactions.recipient`: case-insensitive; a recurring payment already seen this month is not counted twice, and a detected payment with the same name is not listed twice.
- `user_profile.monthlySalary` feeds the health score and the loan limit. Extra income is stored as `transactions` rows with `type = income`.
- `health_score_history` stands alone.

---

## 5. Business logic implemented

- **SMS capture:** `SmsParser` reads amount, recipient, date and reference from a UPI debit SMS; `PaymentNotificationListener` saves it; duplicates are dropped by reference number.
- **Categorizing:** `Categorizer` matches the merchant against the 485-merchant dataset (exact substring, then fuzzy); a merchant the user recategorized before wins (`ExpenseRepository.categorize`, shared by SMS and manual entry).
- **Account and security:** PBKDF2-HMAC-SHA256, 120,000 rounds, random salt per account, constant-time comparison; sign-up rules (name, salary > 0, password ≥ 6 and confirmed); login wait after every 5 wrong tries; start-up lock and 2-minute re-lock.
- **Home:** this month's spending, change versus last month, income (salary + this month's extra income), savings, budget left (shows "Not set"), category chips, recent activity.
- **Transactions:** month donut (top 3 categories + Other), legend, day-by-day list with net amounts, month browsing.
- **Add an expense / income:** keypad rules, required "Paid to", optional category (automatic by default), income entries.
- **Detail editing:** change category (applied to the merchant's past payments and remembered), custom categories, note.
- **Recurring:** next due date (this month's due day, or next month if passed or already paid this month), cycle ring, due-this-month total, EMI/SIP/Other filters, add / confirm detected / delete.
- **Health score:** savings rate 35%, EMI burden 30%, spending consistency 20%, recurring reliability 15%; risk bands LOW ≥ 70, MEDIUM ≥ 40; monthly outflow = this month's spending + unpaid EMI and Other recurring payments; rule-based insight text; change since last month from daily snapshots.
- **Loan simulator:** reducing-balance EMI; borrowing limit from the 50% EMI rule and monthly surplus; comfortable limit at 35%; tenure 1/3/5 years or custom 1–30; bank quotes (saved in SharedPreferences) compared safest first then cheapest; Safe / Tight / Risky verdict with reasons; the slider limit uses the highest quoted rate.

---

## 6. Remaining tasks

**Phase 4** (steps 42–50): done except steps 47–48 (Scan) and the checkpoint 50. Plan: a teammate builds the camera and price reading on `main`; then `origin/main` is merged into `integrate-tracker` (conflicts expected in `build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`), and the "can I afford it" verdict (reusing the Paisa AI affordability rule in `PaisaAi.kt`) is wired to his scan screen.

Phase 4 behaviour worth knowing:
- Budgets count this month's expenses in the budgeted category only. Savings goals are changed by typing the total saved so far.
- Cash flow counts the salary from the month of the first transaction onward; older months use only their transactions (EMIs that never arrived as SMS count only in the current month).
- The forecast = this month's outflow so far + everyday daily average × days left (payments to a recurring item's name are excluded from the average).
- Paisa AI is keyword rules over the user's own numbers, fully offline. The conversation is kept in memory only (lost when the app restarts).

**Phase 5: Settings** (steps 51–57)
| Step | Work |
|---|---|
| 51 | Edit name and salary, change password |
| 52 | Notification access status row (reuses `isNotificationAccessEnabled`); then hide "Notification access" from the drawer |
| 53 | App lock toggle (`appLockEnabled`) |
| 54 | Categories and merchants management (the remembered merchants list and count) |
| 55 | CSV export through the share sheet |
| 56 | Log out, and reset all data with a confirmation |
| 57 | Checkpoint 5 |

**Phase 6: Hardening** (steps 58–62)
| Step | Work |
|---|---|
| 58 | More SMS formats ("debited", "credited", more banks) and more SMS apps; save credits as income |
| 59 | Unit tests for the SMS parser, categorizer, score and loan maths |
| 60 | Run all tests and fix failures |
| 61 | Update `README.md` (it still describes the app as design-only) |
| 62 | Manual test checklist for the phone |

**Deferred:** step 27, optional fingerprint unlock (`androidx.biometric`; the `biometricEnabled` column already exists).

**Not in the plan (gaps found while building):** deleting a transaction; editing a transaction's amount or recipient; editing a recurring payment (only add and delete exist).

---

## Build and testing status

**Build:** `./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL** (2026-11-07, 1 m 20 s). Debug APK ≈ 13.6 MB. About 6,400 lines of app code and 1,000 lines of test code.

**Tests: 163 run, 0 failed, 0 skipped** (last full run after step 49).

| Suite | Tests |
|---|---|
| `HomeSummaryTest` | 16 |
| `LoanUiTest` | 15 |
| `RecurringUiTest` | 14 |
| `TransactionsUiTest` | 10 |
| `DetailUiTest` | 9 |
| `StartupGateTest` | 9 |
| `EntryInputTest` | 8 |
| `SignUpValidationTest` | 7 |
| `HealthUiTest` | 6 |
| `PasswordHasherTest` | 5 |
| `SpendingHistoryTest` | 3 |
| `PaisaAiTest` | 15 |
| `HeatmapUiTest` | 12 |
| `BudgetsUiTest` | 10 |
| `CashFlowUiTest` | 10 |
| `ForecastUiTest` | 9 |
| `GoalsUiTest` | 5 |

**Verified by hand on a Samsung SM-M356B (Android 16, USB):** app opens without crashing; sign-up saves the account and the password is stored only as a hash; notification access detection; login, lock and re-lock; Home, Stats, Detail, Add an expense, Recurring (add/delete), Financial health, the Loan simulator, Budgets, savings goals, Cash flow and the Forecast (steps 42–45 confirmed by the user). The Money map and Paisa AI (steps 46 and 49) are installed but not yet confirmed. The v1 → v2 migration was first run on a copy of the phone's database (all rows kept, all 7 tables matched Room's v2 schema), then on the phone (version 2, 7 transactions kept, integrity check OK).

**Not covered by any test:**
- Compose UI and the screens' layout (no UI or screenshot tests run)
- Room DAOs and the repository (no instrumented tests)
- `SmsParser`, `Categorizer`, `RecurringDetector`, `ScoreCalculator`, `VerdictEngine`, loan maths (ported code, tests planned for step 59)
- the notification listener against a real bank SMS (see known issues)
- the Android emulator: it has never been run; all device testing is on the Samsung phone

---

## Known errors and unresolved issues

**Needs attention**
1. **Payment capture is unverified end to end.** The listener is registered and notification access is on, but no real bank SMS has been captured yet. It reads only Google Messages notifications, only debits, and only the single format `Sent Rs.<amt> … To <name>\n … On dd/MM/yy … Ref <number>`. Other banks and SMS apps are ignored until step 58.
2. **Settings still shows sample data** (profile "Aanya Mehta", "12 custom categories", "48 remembered merchants"); no setting works yet.
3. **`README.md` and the `verification/` harness are out of date.** README still says "design-only" and "16 screens"; `verification/AllScreensTest.kt` has not been run since the new screens were added and renders only the sample data.
4. **Phone database contains test rows** `SAMPLE-1`, `SAMPLE-2`, `SAMPLE-3` (inserted on request) alongside the user's own entries. They can be removed by reference.
5. **Branch not merged and `main` may move.** Jatin has local changes that are not pushed. When his changes land on `main`, merge `origin/main` into `integrate-tracker` and resolve conflicts, most likely in `MainActivity.kt`, `MoneyScreens.kt`, `HomeDashboard.kt` and `IntelligenceScreens.kt`.

**Minor / by design**
6. The `f71dcf8` and `acc521d` commits carry a "Co-Authored-By: Claude" line; later commits do not. Rewriting them would need a force-push (not done).
7. `RecurringDetector` needs 3 payments although its comment says 2 (kept as in the tracker).
8. A forgotten password cannot be recovered; the only way back in is to clear the app's data, which deletes everything. The wrong-password counter resets when the app is fully closed.
9. The selected transaction for the Detail screen is kept in memory only: after Android restarts the app, Detail shows "This transaction isn't available".
10. Home's month figures do not refresh at midnight until data changes.
11. The Transactions list is a plain column (not lazy); a month with thousands of rows would be slow.
12. In a screenshot of the Financial health gauge, the needle hub overlaps the score digits. This layout comes from the original design and was not changed (not compared against the original).
13. Harmless warnings: Android 16 `OnBackInvokedCallback` is not enabled in the manifest; Git reports LF → CRLF conversions on Windows; Android Studio suggests an AGP upgrade (declined: it would change the pinned Kotlin/KSP/Room versions).
14. The GitHub repository is public, so the branch's code is visible to anyone. No secrets are in it.
