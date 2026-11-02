# REPS — Product & Technical Audit

**Date:** 2026-11-02 · **Scope:** `main` @ `d6ca824` (clean tree) · **Phase:** 1 — inspection only, no application code changed

## How this audit was done

- **Read:** every Kotlin file under `app/src/main/java/com/reps/app` (~16.8k lines), the Room schemas, `AndroidManifest.xml`, all three `strings.xml` files, the build config, the unit tests and the git history of the string resources.
- **Ran:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. The result is in [§H.6](#h6-build--test-evidence).
- **Not done:** I didn't run the app on a device or emulator. Anything about runtime behaviour comes from reading the code and is labelled that way. Nothing in this report is marked as "tested on device".
- **Missing input:** the web prototype that past work treated as the design spec (`app/src/Design refence/REPS-web-prototype/`) is no longer in the repo, and neither is `REPS-web-prototype.zip`. So I couldn't check the UI against it. I also had no moodboard file to compare against.

Evidence labels used below:
- **[code]** read in the source, with a file reference.
- **[git]** taken from repository history.
- **[build]** observed in the Gradle run.
- **[inferred]** follows from the code but hasn't been seen at runtime.

---

## A. Executive Summary

**Overall condition: a well-built prototype with a sound architecture, sitting on a few critical defects.** Structurally, REPS is in better shape than most prototypes. It has a clean domain/data/feature split, Hilt DI, a per-account Room database with real relations and transactions, a real 828-exercise offline catalogue in three languages, and a coherent theme system. The defects are concentrated in three places:

1. **The localisation files are corrupted.** `values-ar/strings.xml` (3,268 corrupted characters), `values-fr/strings.xml` (183) and 9 lines of `values/strings.xml` are double-encoded. Arabic UI text shows up as `Ø£ÙˆÙ„ Ø¹Ø¯Ø©` instead of `أول عدة`. **[code][git]** On top of that, the in-app language picker doesn't change the UI language at all. It only changes the exercise catalogue language. **[code]**
2. **Some displayed numbers are invented.** Every food has hardcoded macros. Workout duration is estimated, not measured. Every workout shows the difficulty "Intermediate". A first-ever set counts as a "personal record". The streak can never break, because no screen lets the user schedule days. **[code]**
3. **An active workout is fragile.** The session lives only in ViewModel memory. Pressing Back discards it with no warning, process death loses it, and the rest timer restarts on recreation and doesn't keep time while the app is backgrounded. **[code][inferred]**

**Maturity:** a late prototype, roughly "alpha". The core loop (build a template → run a session → see progress) works end to end offline. The data it produces isn't yet trustworthy enough to drive achievements or nutrition guidance.

**Strongest features:** the exercise catalogue (offline, trilingual, accent-folded search, muscle SVGs), the Room persistence layer, the progress analytics pipeline (pure functions, cleanly separated), the rest-timer visual component, and the theme/design token system.

**Biggest weaknesses:** corrupted translations, no real language switching, fabricated nutrition and duration data, workout session data loss, no template editing or scheduling, a 4-item achievement system with wrong unlock logic, no workout history screen, reminders that never arm, and no online sync.

**Main recommendation:** don't redesign anything. Fix the P0 data-integrity and localisation defects first (§I, "Immediate fixes"). Then make the session durable and give the timer a wall-clock basis. Only after that, extend achievements and the UI.

---

## B. Feature Inventory

| Feature | Status | Evidence | Problems | Priority |
|---|---|---|---|---|
| Splash / onboarding | Completed | `feature/splash`, `feature/onboarding` [code] | — | — |
| Local sign-up / login | Completed (local only) | `LocalAuthRepository`, PBKDF2 `PasswordHasher` [code] | Accounts live only on the device; Google sign-in stubbed; "Account settings" sheet is placeholder copy referring to Firebase (`profile_account_settings_body`) | P2 |
| Exercise catalogue (828, en/fr/ar) | Completed | `assets/reps_exercises.db`, `CatalogExerciseRepository`, `TextFolding` [code] | Demo images are remote-only (by design), so there are no images offline unless cached | P3 |
| Exercise search & muscle filter | Completed | `WorkoutsViewModel`, `ExerciseDao` [code] | — | — |
| Exercise detail + muscle diagram | Completed | `ExerciseDetailScreen`, `MuscleTargetDiagram` [code] | — | — |
| Body map browse | Completed | `feature/bodymap` [code] | — | — |
| Workout template creation | Partially completed | `WorkoutBuilderViewModel.save()` [code] | Every exercise gets a fixed 3 × 10 @ 20 kg; you can't set sets/reps/weight, schedule days or reorder; difficulty is hardcoded to `INTERMEDIATE`; duration is estimated as `n×9+8` | P1 |
| Template edit / delete / duplicate | Not found | `deleteTemplate` exists in the repository; no UI calls it [code] | Users can't fix or remove a template | P1 |
| Workout scheduling (days of week) | Not found (UI) | `scheduledDays` is never set outside `SampleData` [code] | Breaks Home "today", reminders, streak and weekly completion (see §C) | **P0** |
| Workout session — set logging | Partially completed | `WorkoutSessionScreen/ViewModel` [code] | Weight field likely can't be cleared or edited normally (controlled value reformatted to `"%.1f"` on every keystroke) [inferred]; no reorder, remove set, notes or exercise add/remove mid-session | P1 |
| Workout session — durability | Broken | All state is in `MutableStateFlow`s, with no `SavedStateHandle` and no DB draft [code] | Back button / system back discards the session with no confirm; process death loses it [inferred] | **P0** |
| Workout completion | Partially completed | `finish()` [code] | `durationMin = max(12, estimated × completionRatio)` is fabricated, not measured; a double tap can save twice (no in-flight guard); zero-set sessions are saved and counted as workouts | **P0** (duration), P1 |
| Rest timer | Partially completed | `core/components/RestTimer.kt` [code] | Fixed 90 s; frame-delta clock; restarts on Activity recreation; no notification when it ends in the background; no exercise/set context; the large card covers the set list | P1 |
| Workout history list / session detail | Not found | No route in `Destinations.kt` [code] | Users can't see past sessions | P1 |
| Personal records | Partially completed (logic wrong) | `ProgressMath.personalRecords`, `finish()` [code] | First set ever = "PR" plus a notification; bodyweight exercises can never PR (volume = 0 × reps); PR "beat by" compares weight while detection compares volume; always displays "kg" | P1 |
| Achievements | Partially completed | 4 hardcoded items in `computeAchievements` [code] | Wrong unlock logic (§C); no history or dates; not persisted; no unlock event | P1 |
| Streak | Broken | `StreakCalculator` + empty `scheduledDays` [code] | Never breaks; stored value only recomputed on save | **P0** |
| Progress dashboard | Partially completed | `ProgressScreen/Charts/Math` [code] | Duration stat built on fabricated minutes; "30D/60D/90D" really means last N *entries*; weekday letters are English; hardcoded `"h"`/`"kg"`; weekly target always shows `/0` | P1 |
| Weight logging & chart | Completed | `LocalWeightRepository`, unique (uid, date) [code] | No upper bound or sanity check on input; comma decimals likely rejected in FR/AR keyboards [inferred] | P2 |
| Body measurements (waist, arms, …) | Not found | — | Only weight and height exist | P2 |
| BMI | Completed (basic) | `BmiCalculator` + tests [code] | No history or explanation, no "estimate, not diagnosis" disclaimer | P2 |
| BMR | Partially completed | `BmrCalculator` (Revised Harris-Benedict) [code] | Not Mifflin-St Jeor as requested; `"kcal/day"` hardcoded; age stored as a static int (goes stale), not a birth date | P2 |
| TDEE / nutrition targets | Partially completed | `NutritionTargetsCalculator` [code] | Fixed activity factor 1.45 with no user input; fallback targets (2400 kcal / 180 P …) shown as if personal when the profile is incomplete | P1 |
| Meal logging | Broken (data) | `NutritionScreen.AddMealSheet` lines ~505–516 [code] | **Every food is stored as 165 kcal / 12 P / 14 C / 5 F per 100 g** regardless of what it is | **P0** |
| Water tracking | Partially completed | DataStore per date [code] | Stored per *device*, not per account | P2 |
| Nutrition AI assistant | Online-only / not verifiable | `RepsAiApiService` → `http://10.0.2.2:8000` [code] | Only reachable from an emulator with a local FastAPI server; fails on any real device | P2 |
| Workout reminders | Broken | `WorkoutReminderScheduler` [code] | Toggle shows ON by default, but work is only enqueued when the toggle is flipped; also requires `scheduledDays`, which can't be set | P1 |
| Hydration reminders | Partially completed | `HydrationReminderScheduler` (every 8 h) [code] | Same arming bug; no quiet hours | P1 |
| PR notification | Partially completed | `NotificationHelper.postPrNotification` [code] | Fires for first-ever sets (false PRs) | P1 |
| Notifications inbox screen | Partially completed | `NotificationsViewModel` [code] | Only shows "today's scheduled workout", which can never exist | P2 |
| Daily motivation | Partially completed | `MotivationQuotes` [code] | 7 English-only hardcoded quotes; not tied to activity | P2 |
| Theme (light/dark/system) | Completed | `ThemeMode`, `Theme.kt` [code] | — | — |
| Units (metric/imperial) | Completed (mostly) | `UnitConverter` [code] | Some screens ignore units (PR card "kg", session-complete "kg") | P2 |
| Language switch (EN/FR/AR) | Broken | `ProfileViewModel.updateLanguage` [code] | Only writes DataStore and profile; no `AppCompatDelegate.setApplicationLocales`/`LocaleManager`; UI follows the device locale only | **P0** |
| French / Arabic strings | Broken | Corrupted encoding [code][git] | See §G | **P0** |
| Exercise tiers | Not found | `Difficulty` enum exists; catalogue has no difficulty data [code] | See §B note below | P3 |
| Online sync | Not found | Google-services plugin applied, no Firebase SDK [code] | Fully offline; nothing syncs; backup rules reference a non-existent Firestore cache | P2 |
| Credits | Incomplete | `MUSCLE_MAP_AUTHOR = "[author name pending]"` [code] | Placeholder ships to users; attribution licence requirement | P1 |

**About tiers:** the brief asks for an "exercise tier colour system". No tier concept exists in the code or the data. The catalogue explicitly has **no** difficulty field, and the code rightly hides the label. The only "tier" users see is the hardcoded **"Intermediate"** on every template card (`TodayWorkoutCard`), which is invented. A tier system first needs a defined meaning (difficulty? progression level? PR rank?) and a real data source. Assigning colours before that would break the "don't invent data" rule.

---

## C. Achievement & Progression Analysis

### C.1 What exists today

All progression is **derived on the fly** from `workout_sessions` → `workout_session_exercises` → `workout_session_sets`. Nothing about achievements is stored. The only persisted progression value is `user_profiles.streakCount`.

| Metric | Calculation (as implemented) | File | Verdict |
|---|---|---|---|
| Workouts this month | count of sessions with `date` in the month | `ProgressMath.monthlyStats` | Correct, but zero-set sessions count |
| Duration this month | Σ `durationMin` | same | **Invalid**: `durationMin` is estimated, not measured |
| Volume this month | Σ (weight × reps) over completed sets | same | Correct for weighted work; 0 for bodyweight |
| Avg volume / workout | volume ÷ workouts | `ProgressScreen` | Correct, with the same caveat |
| Month-over-month delta | % vs previous month | `ProgressScreen.deltaLabel` | Correct |
| Weekly completion | sessions this week / count of distinct scheduled weekdays | `weeklyCompletion` | **Always `n/0`**, because scheduled days are never set |
| Weekly frequency (8 weeks) | sessions per Mon–Sun week | `weeklyFrequency` | Correct; label `M/D` not localised |
| Muscle distribution | completed-set volume share per muscle group, top 6 | `muscleDistribution` | Bodyweight work is invisible (volume 0) |
| Strength series | max weight per session for one exercise | `strengthSeries` | Correct for weighted lifts; meaningless for bodyweight |
| Personal records | per exercise, the set with highest weight × reps | `personalRecords` | **Flawed**, see C.2 |
| Streak | consecutive session days going back, skipping unscheduled days | `StreakCalculator` | Logic sound in isolation; **broken in practice**, see C.2 |

**Not tracked at all:** total sets, total reps, lifetime volume, sessions per week target, gym vs calisthenics split, best reps for bodyweight moves, estimated 1RM, session duration (real), rest time, achievement unlock dates.

### C.2 Defects in progression logic

1. **Streak never breaks (P0).** `StreakCalculator` treats a day without a session as a miss only when it's a *scheduled* day. `scheduledDays` is always empty (no UI sets it), so every gap counts as a "rest day". As a result, the streak equals *the number of distinct days you have ever trained*. "On a roll (7-day streak)" unlocks after 7 sessions spread over a year.
2. **Stale streak (P1).** The streak is recomputed only inside `saveSession`. If scheduling existed and a user missed days, the stored streak would stay high until the next workout is saved.
3. **False PRs (P1).** `finish()` treats a missing previous best as 0, so *every first-ever set on an exercise is a PR* and triggers a PR notification. `personalRecords()` likewise lists first sets, so the "Record breaker / First PR" achievement unlocks after the first weighted workout.
4. **Bodyweight PRs impossible (P1).** Volume = `weightKg × reps`. Pull-ups and push-ups logged at 0 kg have volume 0 and can never set a record. For a hybrid gym + calisthenics app, this removes half the product.
5. **Inconsistent PR delta (P2).** Detection uses volume, but the "beat by X kg" text uses the weight difference. A volume PR with less weight shows "beat by −5 kg".
6. **Duration is fabricated (P0).** `max(12, estimatedMinutes × completedSets/totalSets)`. A 3-hour session and a 10-minute session with the same template and completion show the same number. This feeds "Duration" on Progress and the hero card.
7. **Zero-set sessions count (P2).** "Finish anyway" saves a session with no completed sets. It counts toward workouts, frequency, streak and "First workout".
8. **Double-save risk (P1).** The finish button has no in-flight guard. A double tap can insert two sessions with different UUIDs. [inferred]

### C.3 Achievement catalogue (existing)

| Id | Title (EN) | Unlock rule | Problem |
|---|---|---|---|
| FIRST_WORKOUT | First rep | ≥ 1 session | Unlocks on a zero-set session |
| STREAK_7 | On a roll | `streakCount ≥ 7` | Can't fail (C.2-1) |
| TEN_WORKOUTS | Consistent | ≥ 10 sessions | OK, except zero-set sessions |
| FIRST_PR | Record breaker | ≥ 1 PR in list | Unlocks on first weighted set (C.2-3) |

There's no unlock date, no unlock event or celebration, no locked-progress text and no history. Everything is recomputed from scratch, so if a session were ever deleted, an achievement would silently re-lock.

### C.4 Recommended model

- **Persist unlocks:** add an `achievement_unlocks(uid, achievementId, unlockedAtMs, sourceSessionId)` table. Evaluate after each `saveSession` inside the same transaction, and never re-lock.
- **PR rules:** a PR requires a previous completed set to beat. Track three PR types per exercise: **heaviest weight**, **best reps at bodyweight** (when weight = 0), and **best estimated 1RM** (Epley `w × (1 + r/30)`, labelled "estimated"). Store PRs at save time in a `personal_records` table rather than rescanning history.
- **Streak:** once scheduling exists, use the scheduled-day rule. Without a schedule, fall back to a **weekly** streak (≥ 1 session per calendar week) rather than a "days trained" count. Recompute on app open / day change, not only on save.
- **Measured duration:** store `startedAtMs` / `endedAtMs` on the session.
- **Suggested additions** (each has an exact, data-backed condition):
  - Volume milestones: lifetime 10 t / 50 t / 100 t.
  - Session count: 25 / 50 / 100.
  - Weekly consistency: 4 / 8 / 12 consecutive weeks with ≥ N sessions.
  - First calisthenics PR.
  - First 100 kg lift.
  - 1,000 lifetime reps.
  - "Complete" session: all planned sets done.

---

## D. UI/UX Audit (by screen)

| Screen | Problems found | Concrete improvement |
|---|---|---|
| **Home** | "Today's workout" can never appear (no scheduling); difficulty pill always "Intermediate"; English-only quote; `today` captured at ViewModel creation (stale after midnight) | Show next/last template when nothing is scheduled; drop the difficulty pill until real; localise quotes; recompute date on resume |
| **Workouts** | Templates can only be started: no edit, delete, duplicate or preview; "My workouts" has no empty-state CTA explaining scheduling | Long-press / overflow menu → Edit, Duplicate, Delete (with confirm); template detail sheet before start |
| **Builder** | No sets/reps/weight/rest config; no day scheduling; no reorder; no unsaved-changes warning | Per-exercise set editor (reuse session `SetRow`), weekday chip row (reuse `RepsChip`), drag handles |
| **Session** | Back loses everything silently; weight input jumps or reformats while typing [inferred]; check target 32 dp (< 48 dp a11y minimum); timer card (~330 dp) covers sets and Finish button; no elapsed-time clock; no "previous" column (Hevy's most valuable pattern) | Confirm-discard dialog + resumable draft; local text state committed on blur; 48 dp hit area; compact docked timer bar that expands on tap; "Previous: 60 × 8" ghost values from last session |
| **Rest timer** | See §E | See §E |
| **Session complete** | Plain `AlertDialog`; reports volume in "kg" regardless of units; no PR or achievement summary | Full completion sheet: duration, sets, volume, PRs, unlocks |
| **Progress** | Hardcoded `"h"`, `"kg"`, `"+"`, `"30D"`; English weekday letters; weight range chips mislabel entry count as days; no history list | Localise all literals; use `DayOfWeek.getDisplayName(TextStyle.NARROW, locale)`; filter weight by date; add "History" entry point |
| **Nutrition** | Fabricated macros per food; fallback targets look personal; water per device | Require real macros (manual per-100 g input, or a bundled food table); show "Complete your profile to personalise targets" instead of fake numbers |
| **Profile** | BMI/BMR card has no explanation or disclaimer; "Settings" opens placeholder copy; "[author name pending]" credit; language picker doesn't change UI | Disclaimer + info sheet; hide unfinished Settings row; fill attribution; real locale switch |
| **Notifications** | Inbox only ever shows a scheduled workout (impossible) | Hide until real content exists, or show reminder settings |
| **Global** | Empty/loading/error states mostly present (good), but errors on DB writes are swallowed (`?: return` when uid is null) | Surface write failures with a snackbar |

**What to keep:** the floating pill nav, the token-based theme, card language, motion durations, and the `RestTimer` visual. These are consistent and already premium.

**Brand colour note:** the brief specifies Electric Lime `#A3FF12` / `#39FF14` and Matte Black `#0F0F0F`. The code uses `RepsGreen #77EC04` and `RepsNearBlack #0B0B0A` (`core/theme/Color.kt`). Per the standing "preserve the design system" rule I haven't treated this as a defect. **Decision needed:** keep the current palette, or migrate the tokens. Migration is a one-file change because everything reads from tokens.

---

## E. Timer & Timeline Audit

### Existing behaviour [code]

- **Auto-start:** `toggleSetComplete` bumps `restSignal` only when a set flips to *completed*. Correct; it doesn't start on un-completing.
- **Controls:** restart, −15 s, pause/resume, +15 s, skip. A haptic and a pulse fire on completion. The card auto-hides 3 s after completion.
- **Clock:** frame-delta accumulation (`withFrameNanos`). When backgrounded, frames stop. On return, the first frame's delta covers the gap, so it *roughly* catches up. But:
  - no alert fires at the moment rest ends while the app is in the background;
  - `rememberRestTimerState` is plain `remember` keyed on `restSignal`, so **any Activity recreation restarts a full 90 s countdown** (`autoStart = restSignal > 0`). The manifest suppresses most config changes, but process death or the system killing the Activity still triggers it;
  - the duration is a hardcoded `90`, the same for every exercise.
- **Colours:** green arc while resting, grey outline when finished. That is the opposite of the requested red (resting) → green (go).
- **Missing context:** no exercise name, set number or "next up".
- **No timeline:** there is no timeline view. Progress shows only as a `completed/total` stat card and per-row check marks.

### Proposed design (preserves the existing component)

- **Move the timer state into the session ViewModel** as `RestState(startedAtElapsedMs, durationMs, pausedRemainingMs?, exerciseId, setIndex)`. Compute remaining time as `durationMs − (SystemClock.elapsedRealtime() − startedAt)`. That gives no drift, survives recomposition, and can be persisted with the session draft. `RestTimerState` becomes a thin UI adapter.
- **Background end:** schedule a one-shot alarm/notification for the end time when the app goes to background, and cancel it on resume or skip.
- **States:**
  - RED (`RepsError`), "RESTING": countdown with progress.
  - GREEN (`RepsGreen`), "GO — Set 3 of 4": persists until the next set is checked or it's dismissed. Don't auto-hide silently.
- **Layout:** a compact docked bar (exercise · set · 1:12 · pause · +15 · skip) above the safe area that expands to the existing arc card on tap. This stops it covering the set rows.
- **Per-exercise rest:** stored on the template exercise (default 90 s). Adjustments are optionally remembered.
- **Timeline:** a vertical rail beside each exercise card showing completed sets (filled green), the current set (outlined lime), the active rest (red segment with remaining time) and upcoming sets (muted). Collapsed exercises show `3/4 ✓`.

---

## F. BMR / BMI & Body Metrics

| Item | Status | Detail |
|---|---|---|
| Inputs | Partial | Sex, height (cm), age (int) and latest weigh-in, all optional; collected in Profile after sign-up (sign-up stores them as null) |
| BMI | Implemented | `kg / m²`, WHO bands (<18.5 / <25 / <30 / ≥30); unit-tested |
| BMR | Implemented, different formula | Revised Harris-Benedict (`66.5 + 13.75w + 5.003h − 6.755a` / `655.1 + 9.563w + 1.850h − 4.676a`); unit-tested |
| TDEE | Implicit only | `BMR × 1.45` inside `NutritionTargetsCalculator`; never shown or labelled |
| Missing-data handling | Good | Card hidden with "Add your height, age and sex…" until all exist; nothing is invented |
| Disclaimer | Missing | No "estimate, not a medical diagnosis" text |
| History | Missing | BMI is computed only from the latest weight; no trend |

**Recommendations:**
- Switch `BmrCalculator` to **Mifflin-St Jeor** (`10w + 6.25h − 5a + 5` / `−161`), as the brief requests. It's generally considered more accurate for modern populations, and the change touches one file plus its test. Show the formula name in an info sheet.
- Replace `age: Int` with `birthDate` so age stays correct over time. This needs a DB migration from v1 to v2.
- Add an **activity level** picker (sedentary 1.2 … very active 1.9). Show BMR and TDEE as separate, clearly labelled tiles.
- Add a BMI trend that reuses `RepsLineChart` over weigh-ins.
- Add a short disclaimer under the card.

---

## G. Localisation Audit

### G.1 Corrupted string files — P0 [code][git]

The three `strings.xml` files contain **UTF-8 text that was decoded as Windows-1252 and re-saved as UTF-8**:

| File | Corrupted chars | Example (as stored) | Should be |
|---|---|---|---|
| `values-ar/strings.xml` | 3,268 | `Ø£ÙˆÙ„ Ø¹Ø¯Ø©` | `أول عدة` |
| `values-fr/strings.xml` | 183 | `PremiÃ¨re rep` | `Première rep` |
| `values/strings.xml` | 23 (9 lines) | `â€”`, `Ã—` | `—`, `×` |

**History:** the files were clean at `33b3a3e` (2026-08-17). Corruption was introduced by `70a1c00` / `32e195e` ("cleaning project", "nutrition tab improvement", 2026-08-25), fixed in `95a8684` ("anes"), then **re-introduced by merge `9c1861c`**, and carried forward. A clean Arabic file with 314 strings exists at `95a8684`. Strings added since then need re-merging, but nothing needs retranslating from scratch. The corruption can also be reversed mechanically (encode cp1252 → decode UTF-8) and then reviewed.

**Root cause:** an editor or merge tool on Windows opening the files as ANSI. Fix: add a `.gitattributes` rule (`*.xml text working-tree-encoding=UTF-8`) and an `.editorconfig` with `charset = utf-8`, plus a CI check that fails on `Ã|Ø|Ù|â€` sequences in `res/values*/strings.xml`.

### G.2 Language switching doesn't switch the UI — P0 [code]

`ProfileViewModel.updateLanguage` writes the profile and DataStore, and the catalogue re-localises exercise names. Nothing calls `AppCompatDelegate.setApplicationLocales()` or `LocaleManager`, and there is no `locales_config.xml`. **UI strings and layout direction follow only the device's system language.** A French-device user who picks Arabic gets Arabic exercise names inside a French, LTR interface.

Fix: use per-app language preferences via `AppCompatDelegate.setApplicationLocales` (requires the `appcompat` dependency and an `AppCompatActivity`, or `LocaleManager` on API 33+ with a fallback), plus `res/xml/locales_config.xml` and `android:localeConfig`. Data isn't affected; it's keyed by uid.

### G.3 Other findings

- **Coverage:** 312 EN strings; FR/AR each have 307. Missing in both: `cd_close` (falls back to English). Language names and `app_name` are intentionally untranslated.
- **Hardcoded UI text:** `"kcal/day"` (Profile), `"h"`, `"+"`, `"30D/60D/90D"`, `" kg × "` (Progress), weekday letters via `DayOfWeek.name.take(1)` (always `M T W…`), the PR card's `"kg"`, the meal list's `"g"`, all 7 motivation quotes, the weekly label `"M/D"`.
- **Numerals:** weights and timers use `Locale.US` (Latin digits, consistent). `"%.1f".format()` on Progress uses the default locale and will print `1,5` in French and possibly Arabic-Indic digits in Arabic, inconsistent with neighbouring numbers. Pick one policy (recommended: Western digits app-wide, matching most North-African usage) and apply it everywhere.
- **Decimal input:** `toDoubleOrNull()` rejects `,`, which French and Arabic keyboards may produce for decimals. [inferred]
- **RTL:** `android:supportsRtl="true"`, and Compose mirrors `Row`/padding automatically. `Icons.AutoMirrored` is used for trending-up. I haven't verified chevrons, the back button, chart direction or the arc timer in RTL on a device.
- **Arabic typography:** the bundled fonts (Poppins, Anton, Archivo Black) have **no Arabic glyphs**, so Android falls back to the system Arabic font. Shaping and joining will be correct (HarfBuzz handles it) once the strings are fixed, but the visual weight won't match the brand. Recommend bundling an Arabic face with matching weights (e.g. Noto Kufi Arabic / IBM Plex Sans Arabic / Cairo) via a font family fallback chain. Also check that tracked eyebrow styles (`letterSpacing = 1.6.sp`) look acceptable on Arabic.
- **Mixed text:** exercise names with Latin brand words and numbers inside Arabic sentences need `BidiFormatter`/Unicode isolates where strings are concatenated (e.g. `"${muscle} · ${equipment}"`).

---

## H. Technical Audit

### H.1 Architecture — keep it

The app is single-Activity Compose with a Navigation host. The five tabs are pages of a single pager. It follows MVVM with `StateFlow`, uses Hilt, and separates domain/data/feature. Repositories hide Room behind interfaces, and pure calculators sit in `core/util`. There is a Compose stability config. There's **no demonstrated reason to replace any of this.**

### H.2 Persistence

- Two Room DBs: the read-only bundled catalogue and the writable `reps_user.db` (v1, exported schema, no destructive fallback). This is good.
- Tree writes are transactional (delete + insert). Templates use `CASCADE` foreign keys; sessions deliberately aren't tied to templates, so history survives template deletion. This is good.
- Gaps:
  - session `startedAt`/`endedAt` aren't stored;
  - no achievement or PR tables;
  - age is stored instead of a birth date;
  - the first schema change needs a real `Migration(1, 2)`. Plan one migration that bundles all of the above.
- **Silent write drops:** every repository write starts with `session.currentUid ?: return`. `cachedUid` is warmed asynchronously at injection, so a write that ran before DataStore emitted would be silently discarded. That's unlikely in practice because a UI is up by then, but nothing reports the failure. [inferred]

### H.3 Offline / online

| Works offline | Needs network |
|---|---|
| Auth, profile, templates, sessions, progress, weight, meals, water, reminders, catalogue text and SVGs | Exercise demo images (cached after first view, 128 MB LRU); nutrition AI assistant (local dev server only) |

There's no online sync. The Google-services plugin is applied without any Firebase dependency, and the backup rules exclude a `firestore` DB that doesn't exist. Either remove the plugin and stale references, or plan sync deliberately (a server-authoritative design with `updatedAt` + tombstones would also need a migration).

### H.4 Lifecycle & stability

- **Active session:** lost on Back, process death or the task being swiped away. **P0**
- **Rest timer:** restarts on recreation; not lifecycle-aware in the background (§E).
- **Screen orientation:** `screenOrientation="portrait"` plus a broad `configChanges` hides most recreation bugs, but doesn't protect against process death.
- **Day rollover:** `HomeViewModel.today` and `NutritionViewModel.today` are captured once.

### H.5 Security & privacy

- **Good:** PBKDF2-HMAC-SHA256 with per-account salt; per-uid scoping on every DAO delete; cleartext limited to local dev hosts.
- **Fix:** `android:usesCleartextTraffic="true"` in the manifest is redundant with (and broader than) the network-security config. Remove it.
- **Review:** `allowBackup="true"` means `reps_user.db` (including password hashes) goes into Google cloud backup. That's acceptable for restore, but should be a conscious choice.
- **Missing permission handling:** the notification permission is requested only when toggling reminders ON, and the default-ON state never asks.

### H.6 Build & test evidence

At audit time (`d6ca824`), compile and the unit test task passed. The test task was reported up-to-date, so the 70 passing tests it reported came from a run on 2026-08-30. Six of those (`RepsAiIntegrationTest`) needed a live RepsAI server on `127.0.0.1:8000` and fail without one. The build needs a local JDK 21 (Android Studio's JBR); without network access Gradle stalls trying to download one. Post-fix results are in [REPS_IMPLEMENTATION_REPORT.md](REPS_IMPLEMENTATION_REPORT.md).

**Unit test coverage:** 12 test classes cover the calculators (BMI, BMR, units), text folding, taxonomy, media URLs, auth validation, the splash model and the AI client. **There are no tests for `ProgressMath`, `StreakCalculator`, `WorkoutSessionViewModel`, the repositories or Room migrations.** These are exactly where the defects in §C live.

### H.7 Performance

Catalogue queries are pushed into SQLite. Progress loads only the referenced exercises. Coil has memory and disk caching. Pure progress functions run in `remember(sessions)`. They scan the full history on every emission, which is fine for now and worth moving to the ViewModel or SQL once history grows past a few hundred sessions. I saw no obvious leaks; `UserSession` owns an app-lifetime scope by design.

---

## I. Priority Roadmap

### Immediate fixes (P0: data integrity & broken core)
1. **Restore string encoding** in `values`, `values-fr` and `values-ar` (recover from `95a8684` + mechanical fix), and add `.gitattributes`/`.editorconfig` guards. *S*
2. **Make language switching real** (per-app locales + `locales_config`). *S–M*
3. **Stop fabricating nutrition**: per-100 g macro inputs are required (or a real food table), with no hardcoded 165/12/14/5. Label fallback targets honestly. *S*
4. **Measure session duration** (`startedAt`/`endedAt`), within the v2 migration. *S*
5. **Protect the active session**: confirm on Back, persist a draft (Room or `SavedStateHandle`), resume on reopen, guard against double-finish. *M*
6. **Fix the streak**: add weekday scheduling to the builder and switch to a weekly-streak fallback when nothing is scheduled. *M*

### Essential improvements (P1)
7. Correct PR rules (a previous best is required; bodyweight reps PRs; consistent delta). *S*
8. Template edit / delete / duplicate, plus per-exercise sets/reps/weight/rest in the builder. *M*
9. Rest timer on a wall-clock in the ViewModel; background completion notification; red → green states; exercise/set context; compact docked layout. *M*
10. Workout history list + session detail screen. *M*
11. Arm reminders correctly on first launch / sign-in; request the notification permission in context. *S*
12. Persisted achievements with unlock dates and a completion summary sheet. *M*
13. Fill the muscle-map attribution; hide the placeholder Settings row. *XS*
14. Tests for `ProgressMath`, `StreakCalculator` and the session ViewModel; a Room migration test. *M*

### Major new features (P2)
15. Mifflin-St Jeor BMR, birth date, activity level, separate TDEE, BMI history, disclaimer. *M*
16. Body measurements (waist, chest, arms, thighs, body-fat %) with charts. *M*
17. Expanded achievement catalogue (§C.4), gym vs calisthenics split, total sets/reps/lifetime volume. *M*
18. Activity-aware daily remarks, localised (§I.20 below). *S–M*
19. Arabic brand typeface + RTL device pass + numeral policy. *M*
20. Decide on sync: remove the dead Firebase scaffolding, or design sync properly. *L*

### Final polish (P3)
21. Exercise tier system, **only after** the meaning and data source are defined.
22. A "Previous set" ghost column; session-complete celebration motion; brand palette decision (`#77EC04` vs `#A3FF12`).

*Complexity: XS < ½ day · S ≈ 1 day · M ≈ 2–4 days · L ≈ 1–2 weeks (one developer).*

**Daily motivation (brief §9) design note:** build it as a pure `RemarkEngine(sessions, templates, today)` → one localised string resource. Rules use only real data, for example:
- "3 sessions this week — one more than last week."
- "Last trained 4 days ago. One session gets you back on track."
- After a PR: "New best on Bench Press."

Fall back to the localised quote list when there isn't enough history. Never use guilt framing.

---

## J. Acceptance Criteria

| Area | Measurable condition |
|---|---|
| Encoding | `grep -E "Ã\|Ø\|Ù\|â€"` over `res/values*/strings.xml` returns 0 matches; CI enforces it |
| Language | Choosing AR in Profile switches all UI strings to Arabic and layout to RTL without restarting the device or losing data; choosing FR then EN round-trips; selection survives app restart |
| Arabic rendering | Screenshots of Home, Session, Progress and Profile in AR show joined Arabic glyphs, mirrored navigation/chevrons, and numbers in the chosen numeral system |
| Hardcoded strings | Lint `HardcodedText` / a custom check finds no user-visible literals in `feature/**` |
| Nutrition | No food is saved without user-supplied or database-sourced macros; daily totals equal Σ(grams/100 × per-100 g) |
| Duration | Session duration = `endedAt − startedAt` (±1 min) |
| Session durability | Back from an in-progress session shows a confirm; killing the process mid-session and reopening restores every logged set |
| Double finish | Rapid double tap on Finish produces exactly one session row |
| Timer | Lock the phone for 60 s during a 90 s rest; on unlock it shows 0:30 ±1 s; at 0 a notification/haptic fires even if backgrounded; red while resting, green when done |
| Streak | With Mon/Wed/Fri scheduled, missing a Wednesday resets the streak the next day without needing a new session |
| PRs | The first-ever set on an exercise is *not* a PR; beating it is; a 0 kg pull-up set with more reps than before is a reps PR |
| Achievements | Each unlock is stored with a timestamp and never re-locks; the completion sheet lists new unlocks |
| BMR/BMI | Mifflin-St Jeor outputs match reference values in unit tests (e.g. male 80 kg / 180 cm / 30 y → 1780 kcal); a disclaimer is visible |
| Reminders | Fresh install + permission granted → a workout reminder fires on the next scheduled day without touching the toggle |
| Tests | `ProgressMath`, `StreakCalculator`, PR logic and the session ViewModel have unit tests; `testDebugUnitTest` passes |

---

## K. Final Verdict

**What's already good:**
- The architecture and persistence layer.
- The real trilingual offline exercise catalogue.
- The theme and design tokens, and the floating nav.
- The rest-timer visuals.
- Honest handling of missing data in the catalogue and BMI card.
- Pure, separately testable progress maths.

The team has made consistently good engineering decisions.

**What's not ready:**
- Arabic and French are unusable (corrupted text, and a language switch that doesn't switch).
- Nutrition numbers are fake.
- Workout duration, the streak and PRs report things that didn't happen.
- An in-progress workout can be lost with one tap.

**What must be fixed:** everything in "Immediate fixes" (§I 1–6). These aren't polish. Each one either shows users false information or loses their data.

**What should be improved:**
- Template editing and scheduling.
- A wall-clock timer.
- History.
- Correct PRs.
- Persisted achievements.
- Reminders that actually arm.

**What shouldn't change:**
- The module and DI structure.
- The Room schema approach (extend it with one migration).
- The catalogue pipeline and its text-folding parity rule.
- The design token system.
- The floating nav.
- The `RestTimer` visual language (restyle its states; don't rebuild it).

**Next milestone — "Trustworthy core" (≈ 2 weeks, one developer):** ship §I items 1–9 behind a single `UserDataDatabase` v2 migration. Exit criteria are the matching rows in §J. Only after that milestone does it make sense to grow the achievement catalogue, add body measurements and redesign screens. Those features depend on numbers users can trust.
