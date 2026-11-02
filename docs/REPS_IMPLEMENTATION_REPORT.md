# REPS — Repair & Improvement Report

**Date:** 2026-11-02 · **Base:** `main` @ `d6ca824` · **Baseline:** [REPS_AUDIT.md](REPS_AUDIT.md) · **Not committed yet.** Every change is in the working tree for review.

## 1. Verification — what was actually run

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` (clean `--rerun-tasks`) | **151 tests, 0 failures, 0 errors, 6 skipped.** The 6 skipped are the live-backend `RepsAiIntegrationTest`, now opt-in (`REPS_AI_INTEGRATION=1`). |
| `:app:assembleDebug` | Passes; APK built. |
| `tools/validate_user_db_migration.py` | Room v1→v2 migration produces the exact v2 schema; seeded v1 rows survive. A deliberately wrong default is caught (negative check run). |
| `:app:assembleRelease` | **Not run.** It needs `org.jetbrains.kotlin:compose-group-mapping:2.3.10`, which isn't in the offline Gradle cache, and the build machine had no network. The new R8/Gson keep rules are therefore unverified. |
| On-device / emulator testing | **Not done.** No device or emulator was available. Nothing below has been seen running on a phone; §6 is the device test plan still to execute. |
| Lint | Not run (offline). |

**Build note:** offline builds need a local JDK 21. The project requests one through the foojay toolchain resolver. With Android Studio installed:
`JAVA_HOME="<Android Studio>/jbr" ./gradlew -Porg.gradle.java.installations.paths="<Android Studio>/jbr" -Porg.gradle.java.installations.auto-download=false …`

## 2. Critical bug: assistant Send button stays disabled

**Root cause (confirmed in code):**
1. `NutritionAssistantViewModel.startReply()` reset `responding = false` only on its last line.
2. After a successful reply, the code did `if (!draft.readyForAnalysis) return@launch`.
3. The real RAG repository always returns an empty `MealDraft()`, whose `readyForAnalysis` defaults to `false`.

So **every successful answer exited early and Send stayed disabled for good**. Failed requests skipped that return, which is why only success broke it.

**Fix** (`feature/nutrition/assistant/`):
- Explicit `ReplyPhase { IDLE, SENDING, FAILED }` replaces the bare Boolean. Only `SENDING` blocks Send.
- Each turn releases the phase in `finally`, so success, an error result, an exception, a timeout and cancellation all hand the composer back.
- Turn ownership uses a counter, not a `Job` comparison. `viewModelScope` dispatches immediately, so a reply that completes without suspending would finish before `launch` returns. New Chat, opening a saved chat or deleting the open chat abandons the turn, and a late reply can't touch the new state.
- `send()` returns whether the text was accepted. The composer clears only accepted text. Blank or whitespace-only input and taps during a reply are rejected; rapid taps submit exactly once.
- On a failed request, the question goes back into the composer for a one-tap retry, unless the user has started typing something else. History is saved only for answered turns, and each answer stays paired with its question.
- The repository rethrows `CancellationException`. It used to swallow it, which posted an error bubble into the next chat.

**RAG backend findings** (checked against the `RepsAI` FastAPI source next to the repo):
- `POST /api/v1/chat` is a single JSON response with **no streaming**. The completion signal is therefore the request finishing.
- There's no auth, and messages must be 1–4000 characters.

Client changes:
- **Security:** removed a trust-all TLS manager that accepted any certificate.
- Added an overall call timeout (45 s), distinct `Timeout` / `RateLimited` / `ModelUnavailable` / `MessageTooLong` / `NotConfigured` errors with translated messages, and a client-side 4000-character check.
- A response missing `message` or `conversation_id` is now a handled failure, not a crash. Error mapping walks the whole cause chain; the timeout test exposed that coroutine stack-trace recovery wraps the exception one extra level.

**Endpoint configuration:** `BuildConfig.REPS_AI_BASE_URL` comes from the Gradle property `reps.ai.baseUrl`.
- Debug builds default to `http://10.0.2.2:8000` (emulator → host).
- Release builds default to **blank**, and the assistant then says it isn't available in this version.
- **Blocked:** no production RepsAI deployment exists, so no production URL was invented. To ship the assistant, deploy RepsAI over HTTPS and build with `-Preps.ai.baseUrl=https://…`. Never put API keys in the app.

**Tests:**
- `NutritionAssistantViewModelTest` (12) covers:
  - first message, second message after a reply, and 10 turns in a row;
  - failure, timeout, recovery after failure;
  - empty input and rapid taps;
  - cancellation via New Chat, including a late reply being ignored;
  - not-configured, and a reply that completes without suspending;
  - history pairing and order.
- `RepsAiApiServiceHttpTest` (11) runs against a real local HTTP server. It covers success, two sequential calls, 503, 429, 500, a malformed body, a read timeout, an unreachable server, a blank URL, an over-long message, and cancellation propagating.
- Streaming tests don't apply, because the backend doesn't stream.

## 3. Audit items: status

Legend: **Fixed** (implemented and covered by tests or a build) · **Fixed (UI)** (implemented and compiles; needs device check) · **Partial** · **Not done**.

### P0

| Audit item | Status | What changed | Evidence |
|---|---|---|---|
| Corrupted FR/AR/EN strings | **Fixed** | Mechanical reversal of the cp1252 double encoding. 4 Arabic lines had a lost byte (ف) restored. Every repaired key matches the clean copy in `95a8684`. `cd_close` translated. | `StringResourceEncodingTest` fails on any future mojibake, a missing translation, or an Arabic file with no Arabic in it. `.editorconfig` pins UTF-8. |
| Language picker didn't change the UI | **Fixed (UI)** | `core/locale/AppLocale`: `LocaleManager` on 13+, a wrapped base context plus recreate on 8–12, and `locales_config.xml` (shows in system per-app language settings). The catalogue language now follows the UI language, so a French phone no longer shows English exercise names. | Compiles. **Needs a device check** for the RTL flip and recreate. |
| Fabricated nutrition (165/12/14/5 for every food) | **Fixed** | Calories and macros per 100 g are now required, validated (P+C+F ≤ 100 g; kcal consistent with the 4/4/9 rule) and editable on existing items, so old placeholder entries can be corrected. Generic targets are labelled as such. Water is now per account. | `MacroInputTest` |
| Fabricated workout duration | **Fixed** | Sessions store `startedAtMs`/`endedAtMs`; duration is measured. Pre-v2 sessions are flagged "not timed" and left out of duration totals, not shown as real. | `WorkoutSessionViewModelTest.finish_…measuredDuration` |
| Active session lost on Back or process death | **Fixed** | The session is persisted as a draft (new `active_session_drafts` table) on every change. Back asks Leave / Discard / Keep training. Home shows "Workout in progress → Resume". A draft from a different workout is offered, never overwritten. | `aNewViewModel_resumesTheSavedDraftExactly`, `aDraftFromAnotherWorkout_isOfferedNotOverwritten`, `discard_dropsTheDraft` |
| Streak could never break | **Fixed** | Builder now sets weekdays. With a schedule, the streak counts planned days honoured; without one, it counts consecutive weeks with a session. It's recomputed from history on read, so it's never stale. | `ProgressionRulesTest` (streak cases, including the 7-sessions-over-7-months regression) |

### P1

| Audit item | Status | What changed | Evidence |
|---|---|---|---|
| False PRs; bodyweight PRs impossible | **Fixed** | One rule (`domain/progress/PersonalRecords`): ranked by weight, then reps; it must beat an earlier best. A first-ever set is a "first best", not a PR, and sends no notification. Bodyweight sets rank by reps. "Beat by" shows +kg or +reps, consistently. | `ProgressionRulesTest`, `firstEverSession_setsNoRecord…` |
| Achievements: wrong rules, not persisted | **Fixed** | 8 achievements, each with an exact rule (`domain/progress/Achievements`). Unlocks are stored once with a timestamp in `achievement_unlocks` and never re-lock. Empty sessions count for nothing. Shown with description, progress and unlock date; new unlocks are listed on the completion dialog. | `ProgressionRulesTest` |
| Double-finish duplicate sessions | **Fixed** | In-flight guard; the Finish button shows a loading state. | `finish_savesOnce…` |
| Weight field couldn't be edited normally | **Fixed (UI)** | Fields keep the typed text (`NumericTextField`, session `SetValueField`). Comma decimals and Arabic-Indic digits are accepted everywhere (`NumberInput`), with plausibility ranges. | `NumberInputTest` |
| Rest timer (fixed 90 s, frame clock, no background alert, restart on recreation, wrong colours, covered the list) | **Fixed** | State lives in the ViewModel as timestamps (`RestState`), so it doesn't drift, keeps counting in the background and survives process death. Starts **only** when a set is ticked. Per-exercise rest (template plus in-session chip). **Red while resting, green "GO"** until dismissed. Pause / resume / ±15 s / skip. Shows exercise, next set and total. Docks as a compact bar that expands to the arc. In the background, a system chronometer notification plus a "Rest over" alarm (exact where permitted, otherwise may be late in Doze). Timeline rail per set: done, current, active rest, upcoming. | `RestStateTest`, `WorkoutSessionViewModelTest` (rest cases) |
| Template edit/delete/duplicate, per-exercise plan | **Fixed (UI)** | Builder edits existing templates and sets sets × reps @ weight, rest, order and schedule. Cards show the real schedule; the invented "Intermediate" label is removed and duration reads "≈". Delete confirms, and history is unaffected. | Compiles |
| Workout history | **Fixed (UI)** | New History screen from Progress: every session with measured or "not timed" duration, sets, volume, PR markers, and expandable set-by-set detail. | Compiles |
| Reminders never armed | **Fixed** | Armed at app start when enabled, using the KEEP policy so existing timing is preserved. The notification permission is requested when a scheduled workout is saved. | Compiles; needs a device check |
| Credits placeholder | **Fixed** | Author taken from the Wikimedia Commons file page: "OpenStax, Tomáš Kebert & umimeto.org", CC BY-SA 4.0. | — |
| Tests for progression and session | **Fixed** | 81 new tests across 9 new test classes. | — |

### P2

| Audit item | Status | Notes |
|---|---|---|
| BMR formula / BMI explanation / TDEE | **Fixed** | Mifflin-St Jeor (tests updated to its reference values). Birth date replaces the stale typed age; the legacy age is kept until replaced. Activity level picker. BMR and TDEE shown separately with "estimate, not a medical diagnosis" and an explainer sheet (formulas, WHO ranges). |
| Hardcoded UI text, weekday letters, numerals | **Fixed** | All literals the audit found are now resources, with real `<plurals>` for Arabic's six categories. Weekday names come from the locale. Daily quotes are a translated array. Digits stay Western everywhere, matching the existing `Locale.US` formatting. |
| Daily motivation & remarks | **Fixed** | `DailyRemark` picks one line from real activity: first workout, new best today, logged today, planned today, welcome back after 4+ days, ahead of last week, or week streak. No guilt framing; silent when there's nothing true to say. `DailyRemarkTest`. |
| Progress mislabels | **Fixed** | Weight range filters by date; muscle distribution counts sets, so bodyweight work shows; strength trends use reps for bodyweight moves; units respected; lifetime totals (workouts, sets, reps, volume) added. |
| Day rollover | **Fixed** | `DateTicker` drives Home, Progress and Nutrition. |
| `usesCleartextTraffic="true"` | **Fixed** | Removed; the network security config already limits cleartext to dev hosts. |
| Release R8 would break Gson (AI responses, drafts) | **Partial** | Keep rules added; **unverified** (release build blocked offline, see §1). |
| Body measurements (waist, arms…) | **Not done** | Needs a schema and design decision; out of scope for this pass. |
| Arabic brand typeface | **Not done (blocked)** | No Arabic font file is available offline. The system Arabic font renders correctly connected script; a bundled face (e.g. IBM Plex Sans Arabic / Noto Kufi Arabic) is a design decision plus a font asset. |
| Online sync | **Not done** | No backend exists. Account settings now state plainly that data is on this device only. The Google-services plugin and the Firestore entries in the backup rules are left untouched for whoever owns that decision. |
| AI assistant on real devices | **Blocked** | Needs a deployed RepsAI URL (§2). |

### P3

| Item | Status |
|---|---|
| Exercise tier colours | **Not done, by design.** The catalogue has no tier or difficulty data, and assigning colours to invented tiers would break the no-fabrication rule. Needs a defined meaning and a data source first. |
| Brand palette (`#77EC04` vs brief's `#A3FF12`) | Unchanged pending a decision; it's a one-file token change. |

## 4. Data & schema

`UserDataDatabase` v1 → **v2**. It's a real migration with no destructive fallback, in `data/user/db/UserDataMigrations.kt`:
- `workout_sessions.startedAtMs/endedAtMs` (default 0 = legacy, "not timed")
- `workout_template_exercises.restSeconds` (default 90)
- `user_profiles.birthDateIso`, `activityLevelName` (nullable)
- new `active_session_drafts`, `achievement_unlocks`

Exported schema `app/schemas/…/2.json` is checked in. **Rule for future changes:** add the SQL to `UserDataMigrations`, then run `python tools/validate_user_db_migration.py`.

## 5. Known limits & follow-ups

- **Templates are uniform per exercise** (same reps and weight for each set). Templates were already uniform; per-set variation lives in sessions. Finishing a session doesn't write its weights back to the template.
- **Background rest alarm** uses an exact alarm only where the OS grants it without the `SCHEDULE_EXACT_ALARM` permission; otherwise it may be late in Doze. The countdown notification itself is always exact.
- **Translations** were written for this pass and should be reviewed by native FR and AR speakers, especially fitness terminology and Arabic plural phrasing.
- The `hiltViewModel` deprecation warnings across the project predate this work.
- `data/fake/` (sample data and fake assistant) is still in the tree, used only by previews; it was not removed.

## 6. Device test plan (still to run)

| # | Flow | Expected |
|---|---|---|
| 1 | Sign up → Profile: height, birth date, sex, activity, weigh-in | BMI, BMR and TDEE appear with the disclaimer; values match the explainer formulas |
| 2 | Weigh-in "72,5" with a French keyboard | Accepted as 72.5 kg |
| 3 | Build a workout with Mon/Wed/Fri, 3×8 @ 60 kg, 120 s rest; save | Permission prompt (Android 13+); card shows "Mon · Wed · Fri" and "≈ N min" |
| 4 | Start it; edit weights; tick a set | Red rest bar 2:00 with "Next: … set 2 of 3"; nothing starts before a set is ticked |
| 5 | Pause, +15, skip; lock the phone mid-rest for 60 s | On unlock remaining = expected ±1 s; a notification counts down while locked and "Rest over" fires at 0 |
| 6 | Rest reaches 0 in the app | Turns green "GO", haptic, stays until dismissed or the next set is ticked |
| 7 | Press Back mid-workout → Leave; force-stop the app; reopen | Home shows "Workout in progress"; Resume restores every set and the running clock |
| 8 | Finish; double-tap Finish | One session saved; dialog shows measured minutes, sets, volume, unlocks |
| 9 | First session on an exercise | No PR notification; Progress shows "First best" |
| 10 | Beat it next session (more kg, or more reps at 0 kg) | PR notification; Progress shows the gain |
| 11 | Skip a scheduled Wednesday | Thursday: day streak reset |
| 12 | Progress → Workout history | Sessions listed, expandable to sets |
| 13 | Log a meal with label values | Totals equal Σ(grams/100 × per-100 g) |
| 14 | Airplane mode: everything except exercise images and the assistant | Works; assistant shows the offline message and Send stays enabled |
| 15 | Assistant against a running RepsAI: send, reply, send again ×5 | Send re-enables after every reply; stop the server → error, then recovery |
| 16 | Profile → Language → Français | All UI in French without restart issues; data intact |
| 17 | Language → العربية | RTL layout, joined Arabic glyphs, back arrow and chevrons mirrored, Western digits, plurals correct (e.g. "سلسلة أسبوعين") |
| 18 | Android 13+: system Settings → Apps → REPS → Language | Same three languages offered; changing there updates the app and exercise names |
| 19 | Restart the app | History, achievements (with dates), drafts and preferences intact |
| 20 | Upgrade from a build with v1 data | Migration runs; old sessions show "not timed", nothing lost |
