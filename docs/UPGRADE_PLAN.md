# GeoQuiz upgrade plan

Single source of truth for the GeoQuiz upgrade. Lives at `docs/UPGRADE_PLAN.md` in `davmos15/geography-quiz`.
Every Claude Code session reads this first and updates it before finishing.

---

## 0. Kickoff prompt (paste this into each new Claude Code session)

```
You are the lead engineer on the GeoQuiz Android app (this repo).

1. Read docs/UPGRADE_PLAN.md in full, then CLAUDE.md.
2. Find the first phase in "Phase status" that is not Done. That phase is your scope for this
   session. Do not start any later phase.
   - If the previous phase is "Done – PR open" (not merged), stop and tell me to merge it first.
3. Read the phase section. Check "Codebase facts" and "Decisions log" before planning.
4. Make a short plan: split the phase into tasks, mark which can run in parallel (disjoint files)
   and which are sequential. Show me the plan, then proceed without waiting unless a task is
   marked "Ask Dav".
5. Delegate each task to a sub-agent with a self-contained brief (see "Sub-agent brief template").
   Run parallel tasks concurrently only when their file sets don't overlap. You integrate,
   resolve conflicts and review every diff.
6. After each task: build, run tests, commit on the phase branch with a clear message.
7. Phase gate: run the full verification gate. Everything must pass.
8. Update docs/UPGRADE_PLAN.md: tick tasks, fill "Codebase facts" with anything learnt, add
   decisions, write the session log entry and the "Next session starts at" block. Commit it.
9. Push the branch, open a PR titled "Phase N: <name>", set phase status to "Done – PR open".
10. Finish with a summary: what changed, how to test it on a device, any owner actions for Dav.

Rules: Australian spelling in all UI copy and docs. Never copy names, art, layouts or data from
other games/apps. Only use data and assets with a licence listed in "Approved sources".
If you are running low on context, stop at a clean task boundary, update the plan with exactly
where you stopped, commit, and tell me to start a new session.
```

---

## 1. Phase status

| Phase | Name | Status | Branch | PR |
|---|---|---|---|---|
| 0 | Recon, baseline and agent setup | Done (merged) | `upgrade/p0-baseline` | #3 |
| 1 | Licensing and IP compliance | Done (merged) | `upgrade/p1-licensing` | #4 |
| 2 | Code and architecture | Done (merged) | `upgrade/p2-architecture` | #5 |
| 3 | Game engine and UX foundation | In progress | `upgrade/p3-ux-engine` | |
| 4 | Map engine and geodata pipeline | Not started | `upgrade/p4-maps` | |
| 5 | New modes A: Silhouettes, Tap the map | Not started | `upgrade/p5-modes-a` | |
| 6 | New modes B: Border hop, US states and Canadian provinces | Not started | `upgrade/p6-modes-b` | |
| 7 | New modes C: Currencies, Rivers/mountains/lakes, Flag speed round | Not started | `upgrade/p7-modes-c` | |
| 8 | Engagement: daily challenge, streaks, share card, hints, game-feel | Not started | `upgrade/p8-engagement` | |
| 9 | Release readiness | Not started | `upgrade/p9-release` | |

Statuses: Not started, In progress, Done – PR open, Done (merged).
Each phase branches from `main` after the previous PR is merged. Dav tests the debug build on a device before merging.

### Next session starts at

> Phase 3 continues on branch `upgrade/p3-ux-engine` (pushed, no PR yet). Done: 3.9, 3.1, 3.2, 3.3 (+3.3a), 3.5 (a–c), 3.4 (a–c). Next session: 3.6 (toolchain bump first: Gradle, AGP, Kotlin 2.2+, KSP, Roborazzi; then 200% font on every screen), 3.7 ergonomics, 3.8 dark mode, then the full gate (`clean assembleDebug testDebugUnitTest lintDebug`, re-record and check every golden once, `assembleRelease`), the manual test checklist and the PR "Phase 3: Game engine and UX foundation". Read the session 4 and session 5 logs first (D19 build rule and carry-overs).

---

## 2. Goal and scope

Make GeoQuiz more fun, easier to pick up and fully licence-compliant, and add seven new modes.

**In scope**
- New modes (numbers match the earlier review): 1 Country silhouettes, 3 Tap the map (World or a single continent), 5 Border hop, 8 Currencies, 10 US states and Canadian provinces, 12 Rivers, mountains and lakes, 15 Flag speed round.
- Code and architecture: every item in section 6.
- UX: navigation, answer feedback, difficulty tiers, results, text size, ergonomics, dark mode.
- Engagement: daily challenge, streaks, original share card, game-feel, hints economy.
- Licensing and IP: every required fix in section 5.

**Out of scope for now**: Higher or lower, Australian states, Warmer or colder, Pin it, Greetings, Continents, Time zones, Landmarks, online multiplayer, accounts, IAP, onboarding redesign, Learn (spaced repetition) mode. Keep the architecture open to adding them later.

**Product principles**
- Prototype first: get each feature working end to end behind a feature flag, then polish.
- The existing letter/word-pattern categories are the app's signature. Keep them working in every phase.
- Offline first. No new network dependency for gameplay.
- Correct answers: blue + tick icon. Wrong answers: orange + cross icon. Never rely on colour alone.

---

## 3. How the work is run

### Sessions
- One phase per Claude Code session. Start each with `/clear` (or a new session) and the kickoff prompt.
- If a phase is too big for one session, the lead stops at a task boundary, records the exact stopping point in "Next session starts at", and the next session continues the same phase on the same branch.
- Phases 5–7 are deliberately split so each fits one session.

### Sub-agents
Phase 0 creates these in `.claude/agents/`:

| Agent | Job | Tools |
|---|---|---|
| `android-implementer` | Implements one task in Kotlin/Compose to the brief. Writes or updates unit tests for its code. | Read, Edit, Write, Bash, Grep, Glob |
| `test-engineer` | Writes unit, Compose UI, Room migration and screenshot tests. Never changes production code except test hooks agreed in the brief. | Read, Edit, Write, Bash, Grep, Glob |
| `geodata-engineer` | Python build scripts that turn Natural Earth / mledoze data into bundled assets. Records source, licence and version for every file. | Read, Edit, Write, Bash, Grep, Glob |
| `licence-auditor` | Reviews assets, data, dependencies and copy against section 5. Read-only, reports findings. | Read, Grep, Glob, Bash |
| `code-reviewer` | Reviews a diff for bugs, architecture fit, accessibility and test gaps before commit. Read-only. | Read, Grep, Glob, Bash |

Rules for the lead:
- Parallelise only tasks with disjoint file sets. Shared files (nav graph, DI modules, `strings.xml`, DB schema, `libs.versions.toml`) are edited by one agent at a time, or by the lead after the others finish.
- Every task diff goes through `code-reviewer` before commit. Every phase that adds assets or dependencies also goes through `licence-auditor`.
- Agents report: files changed, tests added, anything not done, any assumption made.

### Sub-agent brief template

```
Task: <id and name from the plan>
Goal: <one sentence>
Context: <relevant Codebase facts and decisions, pasted, not referenced>
Files you may change: <explicit list or folders>
Files you must not change: <list>
Acceptance criteria: <copied from the plan>
Tests required: <list>
Done means: builds, tests pass, report back with files changed and open questions.
```

### Verification gate (end of every phase)
```
./gradlew clean assembleDebug testDebugUnitTest lintDebug
./gradlew verifyRoborazziDebug   # once screenshot tests exist (Phase 2)
```
Plus a manual test checklist written into the session log for Dav to run on a device.

### Owner actions (things only Dav can do)
Collected here by any session that finds one. Never block on them; flag and move on.

- [ ] Play Console: create a dedicated support email and replace the current one.
- [ ] Play Console: decide whether the public developer address can be a business or PO box address.
- [ ] Play Console: update Data safety form (deletion route added in Phase 1).
- [ ] Play Console: set target audience to mixed (includes children under 13) per D1, and complete the Families policy declarations.
- [ ] Decide on a distinctive app name or subtitle before paid marketing (many apps are called GeoQuiz).
- [ ] Privacy policy: Phase 1 rewrote it (child-directed ads, UMP, Billing, Reset all data). Replace the GitHub issues contact with the support email (`<!-- TODO(owner) -->` in `docs/privacy-policy.html`). It goes live on geoquiz-app.netlify.app when the Phase 1 PR merges.
- [ ] Confirm `flag_colors.json` and `flag_elements.json` were authored for GeoQuiz, not copied from another quiz or site; then drop "owner to confirm" in `data/SOURCES.md`.
- [ ] Record which mledoze/countries release or commit `countries.json` came from (SHA-256 is in `data/SOURCES.md`), or pin it on the next refresh.
- [ ] AdMob console: publish a GDPR/EEA consent message (Privacy & messaging) so UMP can show a form. Then check on a device with UMP debug geography = EEA whether the Settings "Privacy options" row appears with the under-age tag on.
- [ ] Play Console: confirm Play Games Services sign-in is acceptable for a mixed-audience app under the Families policy.
- [ ] Play Games bulk import: check whether it expects `AchievementsIconMappings.csv` or `AchievementsIconsMappings.csv` (byte-identical copies in `store_assets/achievements/`); keep one.
- [ ] Phase 9: regenerate store images with the Lato scripts and re-upload to Play.
- [x] Merge the Phase 1 PR after checking the debug build on a device (manual checklist in the session 2 log).
- [ ] Back up `challengeHmacKey` (added to the local `signing.properties` in Phase 2) with the keystore in Drive and Bitwarden. Every release must use the same key; losing it means older challenge links open without scores.
- [ ] App Links: add the Play App Signing key SHA-256 (Play Console → App integrity) to the `com.geoquiz.app` entry in `docs/.well-known/assetlinks.json`; after merge check `https://geoquiz-app.netlify.app/.well-known/assetlinks.json` is served as JSON with no redirect. The upload key and debug key are already listed.
- [ ] Debug builds are now `com.geoquiz.app.debug` ("GeoQuiz Debug"). Play Games sign-in and Billing won't work in them unless that package is linked in Play Console (Play Games: add a linked Android app with the debug SHA-1); not required for testing.
- [ ] Play Console achievements: the Flag Master description in Play Games reads "Complete Flags of the World quiz with 80%+" while the app says "Complete the Flags of the World quiz with 80%+"; update it in Phase 9 with the store refresh. Achievement titles still use US spelling ("World Traveler", "Color Expert"); decide in Phase 9 whether to rename.

---

## 4. Codebase facts

Filled in by Phase 0 (session 1, 2026-10-07) and kept current. Agents read this instead of re-exploring.
`J/` = `app/src/main/java/com/geoquiz/app/`.

**Stack and build**
- Single `:app` module. Kotlin 2.1, AGP 8.9, Compose BOM 2024.12, Material 3, Hilt 2.54, Room 2.6.1, DataStore 1.1.1, Navigation Compose 2.8.5, kotlinx.serialization, AdMob (play-services-ads 23.6.0), Play Games v2, Play Billing 8.3. minSdk 26, compileSdk/targetSdk 36, versionCode 17, versionName 2.7.2.
- Release: R8 minify + resource shrinking already on (`app/build.gradle.kts`); `app/proguard-rules.pro` keeps Room, kotlinx.serialization, Hilt, Play Games, Ads. Signing from `signing.properties` (gitignored).
- Local build needs `local.properties` with `sdk.dir=C\:/Users/.../Android/Sdk` (forward slashes on Windows).
- CI: `.github/workflows/ci.yml` (added Phase 0) runs `assembleDebug testDebugUnitTest lintDebug`. Lint was clean at baseline.
- `buildConfig = true` (added Phase 0 for `BuildConfig.DEBUG`).

**Packages**
- `J/data/local/db`: `AppDatabase` (v10, `geoquiz.db`, `exportSchema = true`), 8 entities, 7 DAOs.
- `J/data/local/preferences`: `SettingsRepository` (DataStore "settings": `show_timer`, `show_flags`, `show_country_hint`, `hard_mode`, `player_name`, `ads_removed`), `AchievementRepository` (DataStore "achievements": unlocks and progress counters; also decides unlocks), `FeatureFlagRepository` (DataStore "feature_flags", Phase 0).
- `J/data/repository`: `CountryRepositoryImpl` (seeding, alias lookup), `SavedQuizRepository`, `QuizHistoryRepository`, `ChallengeRepository`.
- `J/data/service`: `AdManager`, `BillingRepository`, `PlayGamesAchievementService`. `J/data/PlayGamesAchievementIds.kt`, `PlayGamesLeaderboardIds.kt`.
- `J/di`: `AppModule` (`@ApplicationScope` scope), `DatabaseModule` (builder + migrations), `RepositoryModule`, `FeatureFlagModule`.
- `J/domain/model`: `QuizCategory`, `CategoryGroup`, `FlagCategoryGroup`, `QuizMode`, `QuizState`/`AnswerResult`, `Quiz`, `QuizResult`, `Achievement`, `ChallengeDeepLink`, `Country`, `FeatureFlag`.
- `J/domain/usecase`: `GetCountriesFor{Quiz,CapitalQuiz,FlagQuiz}UseCase`, `Validate{,Capital}AnswerUseCase`, `NormalizeInputUseCase`, `CalculateScoreUseCase`.
- `J/ui`: home (Countries), capitals, flags, category, quiz (+components), results, settings, stats, achievements, challenges, ads (`BannerAd`), share (`ShareUtils`), navigation, theme, debug (Phase 0).

**Modes and navigation**
- `QuizMode` enum: COUNTRIES, CAPITALS, FLAGS. Every mode is "type all the names in the set" (no multiple choice). Flags mode = type the countries whose flags match a colour/element filter; it uses the country validator.
- String routes in `J/ui/navigation/Screen.kt`, graph in `J/ui/navigation/AppNavigation.kt`. Bottom bar has 3 tabs (`countries_home`, `capitals_home`, `flags_home`). Other routes: settings, debug_menu (debug only), stats, achievements, challenges, answer_review, `category/{quizMode}/{groupId}`, `quiz/{quizMode}/{categoryType}/{categoryValue}?challengeId=`, `results/...` (11 path args), `challenge_accept/{challengeId}`.
- No `navDeepLink`: `MainActivity.handleDeepLink` writes a mutableState and a `LaunchedEffect` navigates.

**Quiz flow** (since Phase 2)
- Home → `CategoryListScreen` → `QuizViewModel` (`J/ui/quiz/QuizViewModel.kt`). `loadQuiz` picks the use case by mode/category; `onSubmitAnswer` validates (`allowFuzzy = !hardMode`): Correct / AlreadyAnswered / Incorrect / NearMiss. Hard mode ends at 3 Incorrect (NearMiss never counts).
- Progress survives rotation and process death: `J/ui/quiz/QuizSavedState.kt` keeps answered codes, wrong guesses, input, timer millis and the result id in the `SavedStateHandle`. Restore order: SavedStateHandle > Room resume save > fresh quiz; a restored quiz comes back paused. Rotation does not pause (ON_STOP is skipped while `isChangingConfigurations`).
- Timer: `J/domain/time/QuizTimer.kt` on `MonotonicClock` (`SystemClock.elapsedRealtime`, `J/di/ClockModule.kt`): accumulated millis + running-since; the display ticker wakes at each whole second; saving and scoring read the clock.
- On real backgrounding the quiz pauses and saves to Room `saved_quizzes` (single row, only if ≥1 answer) for the "Resume quiz" card on Countries home.
- Score (`CalculateScoreUseCase`): `(correct/total) * correct`, ×1.2 if perfect.
- Completion: `J/domain/usecase/CompleteQuizUseCase.kt` scores the quiz, saves a `CompletedQuiz` to the `last_result` DataStore with an atomic `saveIfAbsent` (only the latest is kept), clears the Room save, then records achievements, Play Games unlocks, history and leaderboards, exactly once per result id. Navigation goes to `results/{resultId}`; Results and `answer_review/{resultId}` load the record by id and show "no longer available" if it is gone. `QuizResultHolder` no longer exists.

**Game modes and difficulty** (since Phase 3)
- `J/domain/mode/GameMode.kt`: `GameModeSpec` (id, `ModeLabels` string/plural res ids, `ModeIcon` key, `AnswerType`, `supportedDifficulties`, `HintType`s, optional `FeatureFlag`, `sortOrder`), `QuestionGenerator.items(category)`, `AnswerValidator`, `ScoringRule`, `ChoiceQuestionGenerator` (Easy). `GameModeRegistry` (`@Singleton`, from a Hilt `@IntoSet` in `J/di/GameModeModule.kt`): `all`, `classic`, `find`, `require`, `findOrDefault`, `available(isEnabled)`. Classic modes in `J/domain/mode/classic/`. Adding a mode = GameMode class + generator + one `@Binds @IntoSet` line. `QuizMode` keeps the persisted ids and exposes `spec`. Icons map to `ImageVector` in `J/ui/mode/ModeIcons.kt` (not used by the UI yet). `ChallengeLinkParser` and `QuizCategory.isOfferedIn` still take `QuizMode` (new modes will need registry ids).
- `J/domain/model/Difficulty.kt`: EASY/NORMAL/HARD (ids easy/normal/hard) with rules (`allowsTypos`, `strikeLimit` Hard 3, `timerAlwaysShown`, `countsForAchievements` Easy false), `forChallenge` (never Easy). Settings DataStore key `difficulty` replaces `hard_mode` (read as fallback, removed on write). Quiz route `quiz/{mode}/{type}/{value}?challengeId=&difficulty=`; resolution: route → SavedStateHandle → matching resume save → default. Selector `J/ui/components/DifficultySelector.kt` on the category list and in Settings; `DifficultyLabel` chip in the quiz header; `MasteryStarsRow`.
- Easy (D14): `J/domain/mode/MultipleChoice.kt` (`ChoiceQuestion`, `ChoicePrompt` InSet/CapitalOf/FlagOf, three generators, injectable `QuizRandom`). Each item asked once; wrong pick = miss + incorrect guess; ~900 ms feedback from the ViewModel. `J/ui/quiz/components/MultipleChoicePanel.kt`. Easy progress lives in `QuizSavedState`; the Room resume save keeps answered codes only (missed items are re-asked).
- Mastery stars (D17): `J/domain/usecase/MasteryStars.kt`, `QuizHistoryDao.observeMasteryRowsForMode`, shown on category rows.
- Feedback (3.3): `QuizViewModel.feedbackEvents` → `J/ui/quiz/feedback/` (`HapticFeedbackPlayer` CONFIRM/REJECT, no VIBRATE permission; pulse/shake `FeedbackMotion`), `J/ui/components/ReducedMotion.kt` (`rememberReducedMotion()`, reuse in Phase 8), recent-answer chips (`RecentAnswers.kt`, last 3, Normal/Hard), shared `FeedbackLine.kt`, `inlineIconSize()` scales inline icons with font. Settings key `vibration` (default on) gates haptics; the system touch-feedback setting also applies.
- Theme (3.9): `J/ui/theme/GeoColors.kt` via `MaterialTheme.geoColors` (correct/wrong/nearMiss + containers, star/onStar, map land/border/water/found/highlight/wrong/start/end/tapZone), `Shape.kt`, full type scale. `GeoColorsContrastTest` enforces contrast. Category tile, tab accent and achievement tier colours in `Color.kt` are legacy.
- Challenge acceptance is `J/domain/challenge/AcceptIncomingChallengeUseCase.kt` (was `ui/challenges/IncomingChallengeHandler`); `ChallengeLinkSigner` is provided by `J/di/ChallengeModule.kt` and injected everywhere (3.5a): `ChallengeLinkSigner.default` is gone, `ChallengeDeepLink.toUri/toShareUrl/parse` take the signer, `domain` reads no `BuildConfig`. Results and category list VMs build, record and sign share links (`createChallengeShareUrl`).

**Play hub and navigation** (since 3.4)
- Bottom nav (`J/ui/navigation/AppBottomBar.kt`): Play, Stats, Achievements, Settings; shown only on those four routes; selection from the route hierarchy; icon-only with names as descriptions at font scale ≥ 1.5. Start destination `play` (`Screen.Play`); the old `countries_home`/`capitals_home`/`flags_home` routes and their screens/VMs are deleted. Stats, Achievements and Settings have no back arrow as tabs (`onNavigateBack` nullable); Stats' Achievements link switches tab.
- `returnToPlay(mode)` in `AppNavigation.kt` hands a mode to Play through Play's back-stack `savedStateHandle` (`Screen.Play.RESULT_SHOW_MODE`), used by Quiz/Results Home, Answer review Home and challenge Decline.
- `J/ui/play/`: `PlayScreen`, `PlayViewModel` (selected classic mode in its SavedStateHandle; classic modes and the New modes grid from `GameModeRegistry.available(flags)` + `ModeIcons`; New modes hidden when empty; `onOpenMode` is a no-op until Phase 5 adds entry routes), `PlayCategoryGroups` (group tiles for all three modes, formerly copied in three home VMs). Cards top to bottom: Today's challenge (only with `FeatureFlag.DAILY_CHALLENGE`, D24, placeholder), Continue (resume save in its own mode), Recommended next (D25, `J/domain/usecase/RecommendNextCategoryUseCase.kt`), Pinned (D23), then the mode switch ("Quiz type" segmented buttons), All countries/All capitals quick start, group tiles, New modes.
- Category options per group: `J/ui/category/CategoryOptionsBuilder.kt` (extracted from `CategoryListViewModel`; `LegacyCategoryOptions.kt` in tests is a verbatim copy of the old code used as the regression oracle). Category rows combine best score (`QuizHistoryRepository.bestScoresForMode`, live), mastery stars and pins live; `QuizHistoryRepository.categoryKeysByRecencyForMode` feeds Recommended.
- Pins: `J/data/local/preferences/PinnedCategoriesRepository.kt`, settings DataStore key `pinned_categories` (JSON array of `mode|type|value`, pin order), so Reset all data clears them; `J/domain/model/PinnedCategory.kt` validates (classic `QuizMode` only, parses, offered in the mode, never Practice). Pin toggle on each category row.

**Results and practice** (since 3.5)
- `CompletedQuiz.newAchievementIds` (default empty) is written by `CompleteQuizUseCase` after unlocks (a second `save`), so Results' "New achievement(s)" card (`NewAchievementsCard`, polite live region) survives process death. `QuizViewModel` no longer holds achievements.
- "Practise the ones you missed": `QuizCategory.Practice(codes)` (type `practice`, value = cca3 codes joined by `+`, quiz order). `CompletedQuiz.missedCodes()` = all items minus correct (Easy wrong picks and give-up leftovers included). Results button carries the difficulty route arg; hidden on a perfect result. D21: `QuizCategory.isRecorded` is false for Practice: `CompleteQuizUseCase` saves the result and returns before clearing the resume save or recording anything; `QuizViewModel` writes no Room resume save; `fromRouteOrNull` rejects it (no challenge links); Results hides Share/Challenge and shows a "not counted" note. Countries Easy uses the flag prompt for a practice set.

**Room** (since Phase 2, task 2.1)
- Two databases. `StaticDatabase` (`J/data/local/db/StaticDatabase.kt`, file `static.db`, `VERSION` 2): `countries` (cca3 PK, no emoji `flag` column any more), `aliases`, `capital_aliases`, `flag_colors`, `flag_elements`; read-only DAOs. Shipped prebuilt as `app/src/main/assets/databases/static.db` and opened with `createFromAsset()` + `fallbackToDestructiveMigration()`: to change content, edit `data/source/*.json`, bump `StaticDatabase.VERSION`, build once (exports `app/schemas/.../StaticDatabase/<n>.json`), run `python tools/data/build_static_db.py`. Never a migration.
- `AppDatabase` (`geoquiz.db`, v12 since 3.2: `difficulty TEXT NOT NULL DEFAULT 'normal'` on `quiz_history` and `saved_quizzes`; leaderboard totals exclude Easy): `saved_quizzes`, `challenges`, `quiz_history` only. Migrations in `J/data/local/db/AppDatabaseMigrations.kt` (`ALL`); 10→11 drops the static tables; the old re-seed `DELETE`s are gone. No destructive fallback. `RoomTransactionRunner` wraps `AppDatabase` only.
- Exported schemas: AppDatabase 1, 4–12 (2 and 3 never existed: commit `1c9326e` went from v1 to v4, so no released build used them); StaticDatabase 1–2.
- No seeding and no `ensureSeeded()`. Source JSON (`countries.json`, `flag_colors.json`, `flag_elements.json`, `alias_overrides.json` with the abbreviations, capital aliases and the four non-UN extras) lives in `data/source/`, not in the APK. Filter: `unMember || cca3 in {VAT, PSE, TWN, UNK}` → 197 of 250.
- `build_static_db.py` builds tables from the exported Room schema (`createSql`, `room_master_table` identity hash, `user_version`); `--check` (in CI) compares logical content. It shares its rules with `tools/data/export_aliases.py` and fails on any alias that maps to two countries.
- Tests (Robolectric 4.17, SDK 36; JVM `--add-opens` flags in `app/build.gradle.kts`): `StaticDatabaseTest`, `StaticDatabaseAssetTest` (raw asset version and hash; an older installed copy is replaced), `AppDatabaseMigrationTest` (every version 1, 4–10 → 11 keeps player data). `RoomSchemaFixture` builds an old-version DB from the schema JSON because `MigrationTestHelper` can't read schemas in JVM tests under AGP 8.9.

**Categories**
- `J/domain/model/QuizCategory.kt`: sealed class with 26 types (22 name-based, 4 flag-based: single colour, colour combo, colour count, element). `typeKey`/`valueKey` for routes; `fromRoute()` falls back to AllCountries.
- `CategoryGroup` (10 groups) and `FlagCategoryGroup` (5 groups). Concrete options are built from data in `J/ui/category/CategoryListViewModel.kt` (`buildQuizOptions` line 144, `buildFlagQuizOptions` line 366); empty options are hidden.
- Filtering happens in memory in the three `GetCountriesFor*` use cases.

**Aliases**
- Room tables `aliases` (704) and `capital_aliases` (204) in `static.db`, built by `tools/data/export_aliases.py` rules. Sources: common name, official name, `altSpellings`, `data/source/alias_overrides.json` (abbreviations for 35 countries, capital aliases for 18). Codes of 3 or fewer upper-case letters are dropped unless whitelisted. Published as `data/aliases.json` (ODbL).
- Normaliser (`NormalizeInputUseCase`, ported exactly in Python): NFD, strip diacritics, lowercase, `-`→space, drop apostrophes and `.`, `,`→space, `&`→" and ", collapse whitespace, whole-word `st`→`saint`, drop a leading "the". `StaticDatabaseTest` fails if stored aliases drift from the Kotlin normaliser.
- Matching (since 2.3): exact alias of any country wins. Otherwise `FuzzyAnswerMatcher` (OSA Damerau–Levenshtein ≤1, input and alias both over 6 characters) over a cached alias list. Normal: a unique match is judged like an exact answer (`Correct(viaTypo = true)`, AlreadyAnswered, or Incorrect if not in the quiz). Hard: `NearMiss` for an unanswered quiz country, AlreadyAnswered for an answered one, Incorrect if not in the quiz. Two or more countries within one edit (e.g. Iceland/Ireland, Kingston/Kingstown) → `NearMiss` in both modes if any is in the quiz, else Incorrect. Feedback text in `AnswerInput` is a polite live region. `NearMiss` is never a strike or an incorrect guess. Shared rules in `J/domain/usecase/AnswerResolution.kt`.

**Flags**
- Since Phase 1 (D7): flag-icons 7.5.0 (MIT) SVGs in `app/src/main/assets/flags/<cca3 lower>.svg` (197 + LICENSE, ~1.3 MB raw, ~420 KB in APK), fetched by `tools/flags/fetch_flags.py` (pinned tarball SHA-256). Rendered by `J/ui/components/FlagImage.kt` (Coil 3.1.0 + coil-svg, 4:3, hairline `outlineVariant` border) in `CountryList` and `AnswerReviewScreen`, only when `show_flags` is on. Description is "Flag" for unanswered rows, "Flag of X" once answered. The emoji `flag` column still exists in Room but is not displayed (Phase 2 can drop it).
- `FlagAssetsPresentTest` hard-codes the 197 filter (also in `fetch_flags.py` and `tools/data/export_aliases.py`); keep them in sync if the country list changes.

**Ads, billing, Play Games**
- Since Phase 1 (D8): `J/data/service/ConsentManager.kt` runs UMP 3.2.0 (`gatherConsent` from `MainActivity.onCreate`, under-age tag) and initialises the Mobile Ads SDK once, after `AdTagging.requestConfiguration()` (TFCD + TFUA + rating G). SDK calls sit behind `ConsentGateway` / `InterstitialAdLoader` (bound in `J/di/AdsModule.kt`) so they are unit-tested. `BannerAd` (Hilt `@EntryPoint`) and `AdManager.preloadInterstitial` wait for `canRequestAds`. The manifest removes `AD_ID` and the three `ACCESS_ADSERVICES_*` permissions (`tools:node="remove"`). UMP 4.0.0 exists; we stayed on 3.x.
- UMP testing: no debug geography is wired up. To see the form, add `ConsentDebugSettings` (EEA + test device hash from logcat) in `GoogleConsentGateway.requestConsent` behind `BuildConfig.DEBUG`, or add a debug-menu action.
- Interstitials (since 2.4): preloaded in `QuizViewModel.init`; `J/data/service/InterstitialPolicy.kt` (in memory) allows one per 3 completed quizzes (give-ups count), never after a quiz under 60 s of play, reset only when an ad actually shows. `ResultsViewModel` decides once per Results entry and the screen shows it after its first frame. Skipped if `ads_removed`. Banners on the three home screens.
- Billing: one INAPP product `remove_ads`; restore on resume and from Settings.
- Achievements: **38** in the `Achievement` enum (`J/domain/model/Achievement.kt`, the single source; `generate_achievements_zip.py` parses it and `--check` runs in CI), mapped to IDs in `PlayGamesAchievementIds.kt` (`PlayGamesAchievementIdsTest`). Local DataStore is the source of truth; `J/data/service/PlayGamesSyncManager.kt` re-syncs all unlocks and leaderboard totals on every sign-in, when a validated network returns and when the unlock set changes (debounced 2 s, needs an attached activity).

**Challenges and deep links** (since 2.7)
- `J/domain/challenge/ChallengeLinkParser.kt` + `ChallengeLinkSigner.kt`: strict origin (`geoquiz://challenge`, `https://geoquiz-app.netlify.app/challenge[.html]`), id `[A-Za-z0-9-]{1,64}`, known mode, `QuizCategory.fromRouteOrNull` + `isOfferedIn(mode)`, numbers clamped (total 1..197, score 0..total, time 0..86400), names sanitised to 24 characters. HMAC-SHA256 over a length-prefixed canonical form, params `v=1` and `sig`. Unsigned or tampered links keep the challenge but drop the score. Key: `BuildConfig.CHALLENGE_HMAC_KEY` from `signing.properties` `challengeHmacKey` or env `CHALLENGE_HMAC_KEY`; dev key fallback for debug/CI; `preReleaseBuild` fails without a real key.
- `J/ui/challenges/IncomingChallengeHandler.kt` rejects categories with no countries; invalid links show a Toast. `MainActivity` handles the launch link only when `savedInstanceState == null`, plus `onNewIntent`.
- Manifest: `geoquiz://challenge` filter plus an https filter with `autoVerify="true"` (host geoquiz-app.netlify.app, pathPrefix `/challenge`). `docs/.well-known/assetlinks.json` lists the upload key (com.geoquiz.app) and the debug key (com.geoquiz.app.debug); the Play signing key is an owner action. `allowBackup="false"`.

**Accessibility** (since 2.8)
- `J/ui/components/A11yText.kt` builds spoken descriptions (plurals in strings.xml). The quiz count ("12 of 197 countries named") and answer feedback are polite live regions; the timer has a description but is not a live region. Quiz and review rows are one item each (`clearAndSetSemantics`), never reading the hidden answer. Headings on titles and sections; cards are buttons with action labels.

**Strings**
- Existing UI hard-codes English strings. `strings.xml` held only IDs until Phase 0. New UI uses `strings.xml`.

**Settings**
- 4 switches (timer, flags, country hint, hard mode), Remove Ads / Restore Purchases. The quiz screen has its own settings sheet with the same toggles (`QuizScreen.kt:349`). The Settings column now scrolls.
- Since Phase 1: "Data" section with Reset all data (`J/domain/usecase/ResetAllDataUseCase.kt`: Room user tables in one transaction via `TransactionRunner`, achievements DataStore, settings DataStore except `ads_removed`, flag overrides; DataStores provided with `@SettingsStore`/`@AchievementStore` in `J/di/UserDataModule.kt`). "About" section: Credits and licences (`J/ui/credits/CreditsScreen.kt`, route `credits`), Privacy policy (browser), Privacy options (only when UMP requires it). Open-source licences: `J/ui/credits/OpenSourceLicencesScreen.kt` (route `open_source_licences`), AboutLibraries 11.6.3 reading `R.raw.aboutlibraries` generated by the plugin.
- Debug builds: tap the Settings title 7 times to open the debug menu (feature-flag overrides).

**Tests**
- `app/src/test`: `CalculateScoreUseCaseTest`, `NormalizeInputUseCaseTest`, `ValidateAnswerUseCaseTest`, `PurchaseActionTest`, `FeatureFlagRepositoryTest`; Phase 1 added `FlagAssetPathTest`, `FlagAssetsPresentTest`, `ConsentManagerTest` (with a reusable `FakeConsentGateway`), `AdManagerTest`, `AdTaggingTest`, `ResetAllDataUseCaseTest`, `SettingsViewModelTest` (64 tests total). No `androidTest`, no Room migration or UI tests.
- Local builds are slow on this machine (R8 release about 14–20 min, 8 GB RAM: never run two Gradle builds at once). Sub-agents run `assembleDebug testDebugUnitTest`; the lead runs lint and release once per wave. Running `lintDebug assembleRelease` together once crashed a lint detector; run separately, both pass.
- `core.autocrlf=true` locally: Git warns LF to CRLF on new files. `tools/fonts/.gitattributes` keeps the font files and OFL byte-exact.

**Licensing recon (0.2)**
- Flag source was emoji (Unicode) until Phase 1; now flag-icons SVGs (see Flags).
- Phase 1 added the Credits and Open-source licences screens with the in-app ODbL notice. Natural Earth and UN M49 are not used yet; the Credits screen leaves out a maps section until Phase 4 (code comment in `CreditsScreen.kt`).
- `countries.json` (1.29 MB, 250 entries) matches the mledoze v3-era schema. Version/commit not recorded; it arrived in initial commit `40a5fd3`. `flag_colors.json` and `flag_elements.json` look hand-curated, source undocumented.
- `generate_store_assets.py` and `generate_achievements_zip.py` use Pillow with system Arial / Arial Bold (Microsoft, proprietary) via `ImageFont.truetype("arialbd.ttf")`. No downloads or external images; all artwork is drawn in code. `store_assets/achievements/` has 38 PNGs plus CSVs; `AchievementsIconMappings.csv` and `AchievementsIconsMappings.csv` both exist (likely a stale duplicate). Launcher icon origin undocumented.
- Privacy policy (`docs/privacy-policy.html`): says personalised ads via advertising ID, "suitable for all ages" (conflicts with personalised ads and no child tags), deletion = clear data/uninstall, contact = "open an issue on GitHub" (no email, no link). Billing not mentioned. Last updated 17 Feb 2026.
- Phase 1 added `LICENSE` (All rights reserved), `THIRD_PARTY_NOTICES.md`, `data/SOURCES.md`, `data/aliases.json` (ODbL, `tools/data/export_aliases.py`, `--check` in CI), `data/README.md`, `data/LICENSE-ODbL.txt`. Every new bundled file needs a `data/SOURCES.md` row and, for dependencies, a `THIRD_PARTY_NOTICES.md` row.
- Store asset scripts use Lato (OFL, `tools/fonts/`, pinned google/fonts commit 5d3b761); committed PNGs still have Arial text until regenerated in Phase 9. The launcher icon is an original adaptive vector; `mipmap-*dpi` PNGs are 1x1 placeholders.
- README updated in Phase 1 (2.7.2, API 36, 38 achievements, "Data and licences" section).
- No "-dle" names or other games' names found in code, strings or store text.

---

## 5. Licensing and IP (all required)

### Approved sources

| Source | Licence | Obligation | Use for |
|---|---|---|---|
| Natural Earth | Public domain | None (credit "Made with Natural Earth" anyway) | All map geometry, rivers, lakes, mountains, admin-1 |
| mledoze/countries | ODbL 1.0 (flags excluded) | In-app attribution; derived databases offered under ODbL | Names, borders, currencies, capitals, area |
| flag-icons (lipis) | MIT | Keep copyright and licence notice | Flag SVGs, if current source can't be verified |
| Wikimedia Commons flag SVGs | Mostly public domain, per file | Record each file's status | Alternative flag source |
| Wikidata | CC0 | None | Cross-checking facts |
| UN M49 | Factual classification | Credit | Regions |
| Google Fonts | Mostly OFL | Include licence | Fonts |
| AndroidX, Hilt, Room, Compose, Kotlin | Apache 2.0 | Show licences in app | Libraries |

Not approved without a decision-log entry: OpenStreetMap (ODbL share-alike), GeoNames (CC BY), any photo, any data copied from another quiz game.

### Required changes (Phase 1 unless noted)
- L1 Credits and licences screen in Settings: ODbL notice for mledoze ("Contains information from mledoze/countries, made available under the Open Database License (ODbL) v1.0", with link), flag source credit, Natural Earth credit, UN M49 credit, fonts.
- L2 Open-source licences screen via AboutLibraries or `oss-licenses-plugin`, linked from Credits.
- L3 Publish the alias table as a separate file (`data/aliases.json`) under ODbL in the repo, with a `data/README.md` explaining its derivation. Link it from Credits.
- L4 Identify the flag image source. If it can't be proven, replace all flags with flag-icons (MIT) and record it.
- L5 Add `LICENSE` for the app code (Dav to choose; default "All rights reserved" – see D2) and `THIRD_PARTY_NOTICES.md` listing every source above with licence and version.
- L6 `data/SOURCES.md`: every bundled data file with source URL, version/commit, licence, transformation script. Kept current by Phases 4–7.
- L7 Ads: frequency cap (also A4), Google UMP consent flow, correct child-directed / under-age-of-consent tagging per D1.
- L8 Data deletion: in-app "Reset all data" plus a deletion contact in the privacy policy. Owner action to update Data safety form.
- L9 Trade dress rules for all new work: no "-dle"/"-le" names, no green/yellow/grey square grids, no other game's names in UI or store listing, no copied layouts or artwork. Describe mechanics generically.
- L10 Store assets: check `generate_store_assets.py` for third-party fonts or images; replace anything unlicensed.
- L11 README: align achievement count with the source of truth, add a "Data and licences" section pointing to Credits, SOURCES.md and THIRD_PARTY_NOTICES.md.

---

## 6. Phases and tasks

Task format: ID, what, acceptance criteria (AC). "Parallel group" letters show what can run concurrently.

### Phase 0 – Recon, baseline and agent setup

| ID | Task | Parallel group |
|---|---|---|
| 0.1 | Map the codebase. Fill section 4 with real paths, the quiz flow, DB schema, how categories and aliases work, how modes are wired into navigation. | A |
| 0.2 | Run the review's grep checklist (flag source, oss licences, UMP/child tags, minify, third-party assets in Python scripts). Record results in section 4. | A |
| 0.3 | Confirm or reject each architecture finding in section 6 Phase 2 with file/line evidence. Edit Phase 2 tasks to match reality. | after A |
| 0.4 | Create `.claude/agents/*.md` for the five agents in section 3. | A |
| 0.5 | Add or update `CLAUDE.md`: build commands, architecture conventions, "always read docs/UPGRADE_PLAN.md", Australian spelling, the L9 trade-dress rules. | after 0.1 |
| 0.6 | Add GitHub Actions workflow: assembleDebug, unit tests, lint on PRs. | A |
| 0.7 | Add a simple feature-flag mechanism (DataStore-backed, plus a hidden debug menu) for new modes and features. | after 0.1 |

AC: plan sections 4 and 6 reflect the real code; CI green on the branch; agents present; no behaviour change.

Status: all tasks 0.1–0.7 done in session 1 (2026-10-07).

### Phase 1 – Licensing and IP compliance

| ID | Task | Group |
|---|---|---|
| 1.1 | L4 flag source investigation and replacement if needed | A |
| 1.2 | L3 alias table export + `data/README.md` | A |
| 1.3 | L5, L6, L11 docs: LICENSE, THIRD_PARTY_NOTICES.md, data/SOURCES.md, README | after 1.1, 1.2 |
| 1.4 | L1 Credits screen + L2 OSS licences screen, reachable from Settings | B |
| 1.5 | L7 UMP consent + ad tagging (frequency cap lands in 2.4) | B |
| 1.6 | L8 Reset all data (clears Room user tables, DataStore, saved quiz; keeps static data) with confirm dialog | B |
| 1.7 | L10 store asset script audit | A |
| 1.8 | `licence-auditor` full pass; fix or log every finding | last |

AC: auditor reports no unresolved required items; Credits screen readable with TalkBack and at 200% font; owner actions listed.

Status: 1.1–1.8 done in session 2 (2026-10-07). The auditor's one Required item (AboutLibraries version placeholder) is fixed. TalkBack and 200% font on the new screens still need Dav's device check.

### Phase 2 – Code and architecture (all items)

| ID | Task | AC | Group |
|---|---|---|---|
| 2.1 | Split static content DB (countries, aliases, capital_aliases, flag_colors, flag_elements) from user DB (saved_quizzes, challenges, quiz_history; achievements stay in DataStore). Ship static DB prebuilt via `createFromAsset()`, generated at build time from the JSON assets. Static updates replace the asset, never need migrations. Remove `ensureSeeded()` and the static-table `DELETE`s from user migrations. | No first-launch JSON parse; existing users keep history, challenges and saved quiz after upgrade | A |
| 2.2 | Room migration tests for every existing user-DB version via `MigrationTestHelper` (androidTest or Robolectric). Schemas 2 and 3 are missing from `app/schemas/`: recreate them from git history if possible, otherwise test 1 and 4–10 and log the gap. | All exported versions migrate to latest without data loss | after 2.1 |
| 2.3 | Answer validation: table-driven tests for every alias and normalisation edge case (diacritics, apostrophes, "St."/"Saint", "&"/"and", hyphens). Today `NormalizeInputUseCase` + exact SQL match only. Add optional fuzzy matching (Damerau–Levenshtein ≤1 for names over 6 characters), off in Hard. Near-miss result type for UX feedback. Cover capitals (`ValidateCapitalAnswerUseCase`) too. | 100% alias coverage; fuzzy never accepts a different valid country | A |
| 2.4 | Ads: frequency cap. Today an interstitial shows after every quiz, including give-up (`QuizScreen.kt:123`). Policy: max 1 interstitial per 3 quizzes, never after a quiz under 60 s, never before results render. | Unit-tested policy class | B |
| 2.5 | Quiz state survives process death and rotation (`SavedStateHandle`). Today only nav args use it; state is saved to Room on `ON_STOP` and results go through the `QuizResultHolder` singleton (lost on process death) and 11 path args. | Test with "Don't keep activities" passes, including on Results and Answer review | B |
| 2.6 | Timer uses monotonic time (`elapsedRealtime`) with stored start plus accumulated pauses. Replaces the `delay(1000)` increment loop (`QuizViewModel.kt:144-157`). | Unit tests for pause/resume/background | B |
| 2.7 | Deep-link challenges: defensive parsing (validate `mode`, `ct`/`cv` against known categories, clamp numbers, length-limit `name`), HMAC-signed payload, verified App Links (add an https intent filter with `autoVerify="true"` for geoquiz-app.netlify.app and host `assetlinks.json` with the new upload key's and Play signing key's SHA-256). | Malformed links show an error, never crash; tampered scores rejected | C |
| 2.8 | Accessibility semantics (none exist today): flag `contentDescription` is "Flag" during a question and the country name in review; live-region announcements for progress; merged semantics on list rows | TalkBack walkthrough of a full quiz recorded in session log | C |
| 2.9 | Release config: R8 minify and resource shrinking are already on. Remaining: confirm keep rules cover Billing, DataStore and enum/route parsing (`QuizCategory.fromRoute`, `QuizMode.valueOf`); keep the mapping file with each release. | Release build runs a full quiz, a challenge link and a purchase flow without crashes | C |
| 2.10 | Achievements: unlocks are already stored locally (DataStore) and re-synced on sign-in at app start (`MainActivity.kt:50-68`). Remaining: also sync when sign-in completes later or connectivity returns, and add tests. | Airplane-mode unlock syncs later without restarting the app | B |
| 2.11 | Achievements defined in one source: the `Achievement` enum (38). `generate_achievements_zip.py` duplicates the list and the README says "30+". Generate the script's list from the enum (or add a check) and fix the README. | Count matches everywhere | C |
| 2.12 | Testing infrastructure (no `androidTest` exists yet): Compose UI tests for the quiz loop, Roborazzi screenshot tests for key screens (light, dark, 200% font), added to CI | `verifyRoborazziDebug` in CI | after others |

Phase 0 edited this table to match the code (task 0.3, 2026-10-07). Follow this version.

Status: 2.1–2.12 done in session 3 (2026-10-07). Manual device checks ("Don't keep activities", TalkBack walkthrough, release build run) are in the session 3 log.

### Phase 3 – Game engine and UX foundation

Builds the shared pieces every new mode uses. Do not build new modes here.

| ID | Task | AC | Group |
|---|---|---|---|
| 3.1 | Mode framework: a `GameMode` registry (id, name, icon, question generator, answer type, supported difficulties, scoring, hint types). Existing three modes refactored onto it. | Existing modes behave identically; adding a mode = one registration + one generator | first |
| 3.2 | Difficulty tiers for all modes: Easy (multiple choice, 4 options, distractors from same region), Normal (type), Hard (type, 3 strikes, timer). Per category mastery stars (1–3). | Each existing mode playable at all three tiers | after 3.1 |
| 3.3 | Answer feedback: haptic tick on correct, distinct haptic on wrong, short animation, near-miss message ("Close – check the spelling") using 2.3, last three correct answers shown above the keyboard in type modes | Respects system haptics and reduced-motion settings | B |
| 3.4 | Navigation / home: "Today's challenge" card (placeholder until Phase 8), Continue, Recommended next, Classic modes, New modes grid (feature-flagged), Browse all categories, favourites/pin, bottom nav (Play, Stats, Achievements, Settings) | Any quiz reachable in 2 taps from home | B |
| 3.5 | Results: score, time, review list, "Practise the ones you missed" one-tap quiz built from misses | Practice quiz contains exactly the missed items | B |
| 3.6 | Text size: test and fix every screen at 200% font scale; no fixed-height text containers; long names wrap | Screenshot tests at 200% pass | C |
| 3.7 | Ergonomics: `imePadding()`, input pinned at the bottom in thumb reach, landscape and tablet two-pane layouts | No content hidden by keyboard; tablet layout uses width | C |
| 3.8 | Dark mode: audit all screens; hairline border on every flag in both themes; map colour tokens defined for both themes (used in Phase 4) | Screenshot tests in dark mode pass | C |
| 3.9 | Design tokens: one theme file for colours (including correct = blue, wrong = orange, map land/water/found/highlight), type scale and shapes, used by all new UI | No hard-coded colours in new code | first |

Order used: 3.9 → 3.1 → 3.2a → 3.2b → 3.3 → 3.3a | 3.5a → 3.5b → 3.5c → 3.4a → 3.4b → 3.4c | 3.6 → 3.7 → 3.8 → gate. Sequential, one agent at a time (shared quiz files, one Gradle build at a time).

Status: 3.9, 3.1, 3.2, 3.3 (+3.3a in-app Vibration switch) done in session 4 (2026-10-08). 3.5 and 3.4 done in session 5 (2026-10-08). 3.6–3.8 and the gate not started.

### Phase 4 – Map engine and geodata pipeline

Shared by modes 1, 3, 5, 10 and 12.

| ID | Task | AC | Group |
|---|---|---|---|
| 4.1 | `tools/geodata/` Python pipeline (`geodata-engineer`): download pinned Natural Earth releases (admin-0 50m + 110m, admin-1 states/provinces 50m, rivers/lake centrelines 50m, lakes 50m, geography regions and elevation points), simplify, project and write compact assets (one file per layer). Map Natural Earth country codes to the app's 197 countries; log any mismatch. | Reproducible from one command; SOURCES.md updated; total asset size budget agreed in decision log | A |
| 4.2 | Compose map renderer: draw layers on Canvas with theme tokens, pan and pinch zoom, fit-to-region, per-feature fill state (default, found, highlighted, start/end, wrong) | 60 fps pan/zoom on a mid-range device for the world map | after 4.1 contract agreed |
| 4.3 | Hit testing: tap → country (point in polygon), tap zones (minimum 44 dp) for microstates and small islands, drawn as dashed circles | Every one of the 197 countries is tappable | B |
| 4.4 | Region presets: World and each continent (Africa, Asia, Europe, North America, Oceania, South America) using UN M49, with sensible bounds and projection per region | Each preset frames its countries without clipping | B |
| 4.5 | Outline renderer for single shapes (silhouettes), north up, scaled to fit, optional neighbours drawn faintly | Renders all 197 at consistent visual size | B |
| 4.6 | Accessibility for maps: content descriptions, and a list-based alternative for every map interaction | All map modes playable with TalkBack via the list alternative | after 4.2 |

Decide and log the asset format (e.g. compact binary or GeoJSON-lite) before 4.2 starts.

### Phase 5 – New modes A

| ID | Mode | Spec | Group |
|---|---|---|---|
| 5.1 | 1. Country silhouettes | Outline from 4.5, type the answer (Normal/Hard) or 4 choices (Easy). 3 lives. Hints: Show region, First letter, Show neighbours (costs per hints economy, placeholder costs until Phase 8). Guessed-so-far chips. Works with existing categories as filters where they make sense (region, letters). | A |
| 5.2 | 3. Tap the map | Region picker: World or one continent. Prompt shows a country; tap it. Found countries fill blue. Timer, Skip, Hint (flash region outline). Wrong tap shows the tapped country name briefly. Name-all variant: type names and they fill in. | A |

AC: both behind feature flags, reachable from New modes grid, results and practice-missed work, screenshot tests added, TalkBack path works via list alternative.

### Phase 6 – New modes B

| ID | Mode | Spec | Group |
|---|---|---|---|
| 6.1 | 5. Border hop | Adjacency graph from mledoze land borders, cross-checked against Natural Earth geometry; disagreements logged and resolved in decision log. Puzzle generator picks start/end with shortest path of 2–6 hops (BFS par), never island countries. Each step must border the previous one. Shows route chips and the map. Undo last hop. Score vs par. "Land borders only" rule shown in-app. | A |
| 6.2 | 10. US states and Canadian provinces | Admin-1 layer. Toggle: US states (50, DC optional setting) or Canadian provinces and territories (13). Variants: name-all with timer, tap the map, multiple choice. Aliases for postal abbreviations. | A |

AC as Phase 5.

### Phase 7 – New modes C

| ID | Mode | Spec | Group |
|---|---|---|---|
| 7.1 | 8. Currencies | From mledoze currencies. Question: currency name, symbol and code; answer the country. Shared currencies (euro, US dollar, CFA francs, East Caribbean dollar, etc.) only used as "which of these uses…" with exactly one valid option. Flags on options. One-line fact after answering (facts stored in data file with source). | A |
| 7.2 | 12. Rivers, mountains and lakes | Tabs: Rivers, Mountains, Lakes. Curated lists in `data/physical_features.json` (~40 rivers, ~30 ranges or peaks, ~30 lakes), each verified against Wikidata with the QID stored. Highlight the feature on a regional map; type or choose. | A |
| 7.3 | 15. Flag speed round | 60 seconds. Lookalike pairs and sets (e.g. Chad/Romania, Indonesia/Monaco, Ireland/Côte d'Ivoire, Australia/New Zealand, Netherlands/Luxembourg, Senegal/Mali, Slovenia/Slovakia, Colombia/Ecuador/Venezuela, Norway/Iceland), curated in a data file. Big tap targets, combo counter, explanation after each answer in review. | A |

AC as Phase 5. `licence-auditor` checks every new data file.

### Phase 8 – Engagement

| ID | Task | AC | Group |
|---|---|---|---|
| 8.1 | Daily challenge: 10 questions across modes, seeded from the device's local calendar date so everyone gets the same set on the same date. Offline. One attempt per day; results saved. | Same date gives the same set on two devices; unit-tested generator | A |
| 8.2 | Streaks: consecutive days with a completed daily challenge. One streak freeze earned per 7 days played, max 2 held, never purchasable. | Edge cases tested: time zone change, missed day with/without freeze | after 8.1 |
| 8.3 | Original share card: text version and image version (Android share sheet). Own layout and glyphs; no square tile grids, no green/yellow/grey scheme, no other game's naming. Includes date, score, time, app link. | `licence-auditor` signs off on L9 | after 8.1 |
| 8.4 | Hints economy: hints cost points, never money. Per-mode hint types from the registry. Costs and caps in one config. Daily challenge allows limited hints. | Scores reflect hint use; config unit-tested | B |
| 8.5 | Game-feel: combo multiplier for fast consecutive correct answers, confetti on perfect score, short sound effects with a toggle (default respects silent mode), haptics from 3.3 | Reduced-motion and sound settings respected; sounds are original or CC0 and logged in SOURCES.md | B |
| 8.6 | Home "Today's challenge" card wired up, streak shown in header | | after 8.1, 8.2 |

### Phase 9 – Release readiness

| ID | Task | Group |
|---|---|---|
| 9.1 | Remove feature flags for modes Dav has approved; keep the flag system | A |
| 9.2 | Full `licence-auditor` and `code-reviewer` pass over the whole diff since Phase 0 | A |
| 9.3 | Update store listing text (generic mechanic descriptions, no competitor names), screenshots via store asset script, what's new notes | A |
| 9.4 | Performance pass: cold start, map rendering, APK/AAB size against budget | A |
| 9.5 | Final manual test checklist for Dav covering every mode at every tier, light/dark, 200% font, TalkBack, offline | last |

---

## 7. Decisions log

| ID | Decision | Status |
|---|---|---|
| D1 | Target audience: mixed audience including children (Dav, 2026-10-07). | Decided |
| D2 | Licence for app code: All rights reserved (Dav, 2026-10-07) | Decided |
| D3 | Map asset format and size budget | Phase 4 decides |
| D4 | Daily challenge seed: device local date | Decided |
| D5 | Feature flags: enum `FeatureFlag` + DataStore "feature_flags". Overrides are honoured only in debug builds (`BuildConfig.DEBUG`); release always uses each flag's default. Debug menu opens by tapping the Settings title 7 times (debug builds only). Phase 9 flips approved defaults to on. | Decided (Phase 0) |
| D6 | `.gitignore` ignores `.claude/*` except `.claude/agents/`, so project agents are versioned but personal settings are not. | Decided (Phase 0) |
| D7 | Flags switch from Unicode emoji to flag-icons (lipis, MIT) SVG images bundled in the APK (Dav, 2026-10-07). Emoji glyphs varied by OEM and could not be shown large. | Decided |
| D9 | Flag images rendered with Coil 3 (coil-compose + coil-svg, Apache 2.0) from `assets/flags/<cca3>.svg`; files named by cca3 so no Room schema change was needed. Open-source licences via AboutLibraries 11.6.3 (Apache 2.0). Store asset font: Lato (OFL 1.1), because Noto Sans and Inter are variable-only in google/fonts. | Decided (Phase 1) |
| D10 | Reset all data keeps `ads_removed` (so a paying user doesn't see ads before Play restores the purchase) and the static content tables; everything else the player created is cleared. | Decided (Phase 1) |
| D11 | Static content ships as a prebuilt `static.db` generated by a committed Python script and checked in CI (`--check`), rather than generated during the Gradle build: no Python needed to build the app, and the asset is reviewable. Static updates bump `StaticDatabase.VERSION`; Room replaces the installed copy. | Decided (Phase 2) |
| D12 | Typo tolerance (2.3): on in Normal, off in Hard; no feature flag, because it changes how existing answers are judged rather than adding a mode. Hard mode answers a near miss on a quiz country with "Close – check the spelling" and no strike. | Decided (Phase 2) |
| D13 | `static.db` is offered as a whole under ODbL 1.0, including the GeoQuiz-authored `flag_colors` and `flag_elements` tables (Dav, 2026-10-07, after the Phase 2 licence audit). Documented in `data/README.md` and `data/SOURCES.md`. | Decided |
| D14 | Easy tier (3.2), Countries mode: "spot the member" – 4 options, one unanswered country from the set and three from the same region that are not in it; categories with no outsiders (All countries) fall back to a flag prompt. Capitals Easy: "capital of X?"; Flags Easy: show the flag, pick the country (Dav, 2026-10-08). | Decided |
| D15 | Hard tier timer (3.2) = always-on count-up timer that can't be hidden, plus 3 strikes; no time limit (Dav, 2026-10-08). | Decided |
| D16 | Easy quizzes are recorded in history, stats and mastery stars, but unlock no achievements and submit no Play Games leaderboard scores (lead, 2026-10-08; Dav can reverse). | Decided (Phase 3) |
| D17 | Mastery stars per (mode, category), best over history: 1 = any finished quiz ≥ 50% correct; 2 = ≥ 80% at Normal or Hard; 3 = 100% at Hard. Rows from before v12 count as Normal. | Decided (Phase 3) |
| D18 | Difficulty tiers are not behind a feature flag: they change how the existing modes are played (Phase 3 AC requires all three tiers), like D12. | Decided (Phase 3) |
| D19 | Phase 3 build rule (Dav, 2026-10-08): per task only `compileDebugUnitTestKotlin` plus that task's own test classes (`testDebugUnitTest --tests ...`); full suite, Roborazzi record/verify, lint and release run once at the phase gate. Goldens are re-recorded once at the end, not per task. | Decided |
| D20 | In-app "Vibration" switch (default on) in Settings and the quiz sheet; haptics need it and the system touch-feedback setting (Dav asked, 2026-10-08). | Decided (Phase 3) |
| D21 | Practice quizzes ("Practise the ones you missed") are not recorded: no history or stats row, no mastery stars, no achievements, no Play Games unlocks or leaderboard scores, no resume save (and an existing one is left alone), no Share/Challenge (Dav, 2026-10-08). | Decided |
| D22 | Navigation (3.4): bottom nav Play, Stats, Achievements, Settings; Play hub with Continue, Recommended next and Pinned cards, a Countries/Capitals/Flags switch above that mode's group tiles (group → category = 2 taps), and a New modes grid from the registry, hidden while empty (Dav, 2026-10-08). | Decided |
| D23 | Favourites: pin individual categories per (mode, category) from the category row; pins show on Play and start in one tap; stored with settings so Reset all data clears them (Dav, 2026-10-08). | Decided |
| D24 | "Today's challenge" placeholder card only behind `FeatureFlag.DAILY_CHALLENGE` (off; debug override only) until Phase 8.6 (Dav, 2026-10-08). | Decided |
| D25 | Recommended next (lead, 2026-10-08): (a) after the most recently played category, the next one in the same group with < 2 stars; (b) else the first category in the mode with 0 stars; (c) else the fewest stars (first in display order); (d) none if all have 3 stars. Never the Continue card's category. All countries/All capitals are not candidates. Dav can change it. | Decided (Phase 3) |
| D8 | Ads under D1: every user is treated as child-directed and under the age of consent (TFCD + TFUA, max ad content rating G, so no personalised ads); no age screen, so no age data is collected; `AD_ID` permission removed. Google UMP is still integrated for regional consent and the Privacy options entry. Simplest Families-compliant setup; expect lower ad revenue. | Decided (Phase 1 lead) |

---

## 8. Session log

Each session appends one entry:

```
### Session <n> – <date> – Phase <n>
- Done: <task ids>
- Not done / carried over: <task ids and why>
- Decisions: <ids>
- Owner actions added: <list>
- Manual test checklist: <steps for Dav>
- Next session starts at: <phase, task, exact state>
```

### Session 1 – 2026-10-07 – Phase 0
- Done: 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7. Branched from `main` after PR #2 (API 36 / Play Billing 8) was merged.
- Not done / carried over: none.
- Decisions: D2 decided (All rights reserved). D5 (feature flags), D6 (`.gitignore` for `.claude/agents/`) added. D7 (emoji flags) opened for Phase 1. D1 still on the mixed-audience default.
- Findings that change later phases: flags are emoji, not images (L4/D7); R8 is already on (2.9 reduced); achievement sync on sign-in already exists (2.10 reduced); 38 achievements, not "30+" (2.11); Room schemas 2 and 3 are missing (2.2); no UMP/child tags and an interstitial after every quiz (1.5, 2.4); store scripts use system Arial (1.7/L10).
- Owner actions added: privacy policy contact/Billing/audience wording; SHA-256 fingerprints for App Links; merge the Phase 0 PR.
- Verification: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug` passed locally (28 unit tests, 7 new; R8 release builds). The `clean` variant could not complete locally (a Windows file lock, then low memory), so CI on the PR is the clean run. Later edits: `translatable="false"` on debug strings and this document. Feature-flag diff reviewed (no blocking issues; debug strings marked untranslatable).
- Manual test checklist (debug build on a device):
  1. Play one Countries, one Capitals and one Flags quiz end to end. Everything behaves exactly as v2.7.2.
  2. Settings: tap the "Settings" title 7 times. A "Debug menu unlocked" toast appears and the Debug menu opens.
  3. Toggle a flag on: its subtitle changes to "Overridden". Leave and reopen the menu: the value is kept. Tap "Reset all flags to defaults": all flags are off and show "Default".
  4. TalkBack on, in the Debug menu: each flag row is read as one switch with its label and state.
  5. (Optional) A release build shows no change on the Settings screen when the title is tapped.
- Next session starts at: Phase 1, task 1.1 (after the Phase 0 PR is merged).

### Session 2 – 2026-10-07 – Phase 1
- Done: 1.1 (flag-icons SVGs), 1.2 (alias table under ODbL), 1.3 (LICENSE, THIRD_PARTY_NOTICES, data/SOURCES.md, README, privacy policy), 1.4 (Credits + Open-source licences), 1.5 (UMP + child-directed tagging; AD_ID and Privacy Sandbox permissions removed), 1.6 (Reset all data), 1.7 (store scripts on Lato), 1.8 (licence audit; Required item fixed, recommendations applied to docs).
- Not done / carried over: none in scope. UMP debug geography is not wired (see Codebase facts, Ads). Frequency cap stays in 2.4.
- Decisions: D1 decided (mixed audience), D7 decided (flag-icons), D8 (treat everyone as child-directed), D9 (Coil, AboutLibraries, Lato), D10 (reset keeps the purchase flag).
- Owner actions added: support email in the privacy policy; confirm flag_colors/flag_elements authorship; record the mledoze version; AdMob EEA consent message and Privacy options check; Play Games in a Families app; achievements mappings CSV name; regenerate store images in Phase 9; Play Console audience, Families and Data safety updates.
- Verification: `assembleDebug testDebugUnitTest` (64 tests, 0 failures), `lintDebug` (0 errors, 76 warnings; the new ones are all "newer version available") and `assembleRelease` (R8) passed locally. The `clean` gate was not run locally (about 20 min per R8 build on this machine); CI on the PR is the clean run. Expect lower ad revenue from D8.
- Manual test checklist (debug build on a device):
  1. Play one Countries, one Capitals and one Flags quiz end to end, including a letter/word-pattern category. Behaviour matches v2.7.2.
  2. Settings: turn on "Show flags". In a quiz every row shows a crisp flag image with a thin border, in light and dark theme. With TalkBack, an unanswered row's flag reads "Flag" and an answered one "Flag of <country>". Answer review shows flags too.
  3. Fresh install, online: banners appear on the home screens once consent completes (G-rated, not personalised). Airplane mode: the app works fully with no banners and no crash.
  4. Settings, About, Credits and licences: country data (ODbL) with working links, aliases link, flags (MIT) with the "View flag licence" dialog, fonts, artwork. Open-source licences lists the libraries. Check with TalkBack and at 200% font size: everything is readable and scrolls, no clipped text.
  5. Settings, About, Privacy policy opens the web page (the new text is live only after merge).
  6. Settings, Data, Reset all data: Cancel does nothing. Reset clears stats, history, challenges, the saved quiz, achievements and settings (timer back on, flags off), shows "All data reset", and Remove Ads stays removed if purchased.
  7. Tapping the Settings title 7 times still opens the debug menu.
  8. (Optional) A release build runs a quiz and opens the Open-source licences screen without crashing.
- Next session starts at: Phase 2, task 2.1 (after the Phase 1 PR is merged).

### Session 3 – 2026-10-07 – Phase 2
- Done: debug build installs alongside Play (`com.geoquiz.app.debug`, "GeoQuiz Debug"); 2.1 (prebuilt `static.db`, AppDatabase v11), 2.2 (migration tests 1, 4–10 → 11; schemas 2/3 never existed), 2.3 (canonical normaliser, typo tolerance, NearMiss), 2.4 (interstitial cap, shown after Results renders), 2.5 (quiz/results/review survive process death; `CompleteQuizUseCase` records once), 2.6 (monotonic `QuizTimer`), 2.7 (validated, HMAC-signed challenge links; App Links filter + `assetlinks.json`), 2.8 (TalkBack semantics), 2.9 (keep rules audited, line numbers kept), 2.10 (`PlayGamesSyncManager`), 2.11 (achievements single source + CI check), 2.12 (Compose UI tests, 15 Roborazzi goldens, CI runs `verifyRoborazziDebug`). Licence audit: two required items fixed (static.db offered under ODbL in `data/README.md`; D13).
- Not done / carried over: none in scope. Follow-ups for Phase 3: (a) colours still green/red in quiz progress, review ticks/crosses and near-miss text (3.9 tokens: correct = blue + tick, wrong = orange + cross); (b) Results "Share"/"Challenge" buttons clip at 100% and 200% font (3.6); (c) hard-coded feedback strings incl. US "Not recognized" (3.3, move to strings.xml); (d) newly unlocked achievements are not shown anywhere (3.5 Results); (e) optional: move `IncomingChallengeHandler` to a domain use case and provide `ChallengeLinkSigner` via Hilt (signer currently reads `BuildConfig` in `domain`); (f) "Paused" live-region announcement may not fire on appear (check on device); (g) Roborazzi 1.60.0 is the newest built for Kotlin 2.1; upgrade with Kotlin. (h) CI advises Gradle 8.11.1 is out of date: plan one toolchain bump (Gradle, AGP, Kotlin 2.2+, then Roborazzi) as its own task, re-recording goldens if rendering changes.
- Decisions: D11 (static DB generated by script, checked in CI), D12 (typo tolerance on in Normal, off in Hard, no flag), D13 (static.db wholly ODbL incl. flag tables). Interstitial counter is in memory (cold start: earliest ad on the 3rd quiz). Unsigned or tampered challenge links keep the challenge, drop the score. Rotation no longer pauses the quiz.
- Owner actions added: back up `challengeHmacKey`; Play signing SHA-256 into `assetlinks.json` and check it is served after merge; debug package not linked to Play Games/Billing; Flag Master description and US-spelt achievement titles in Play Console (Phase 9).
- CI: passes on the PR (about 4 min). Actions updated to checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7 (Node 24); runner pinned to `ubuntu-24.04` so screenshot rendering doesn't change when `ubuntu-latest` moves to Ubuntu 26. The Windows-recorded goldens verify on Linux.
- Verification: `./gradlew clean assembleDebug verifyRoborazziDebug lintDebug` passed locally (311 unit tests incl. 15 screenshot comparisons, 0 failures; lint 0 errors, 88 warnings, the new ones all "newer version available"); all three Python `--check`s pass; `assembleRelease` (R8, real challenge key) passed, 4.6 MB APK with `static.db`. Every task diff was reviewed (code-reviewer agent for 2.1, 2.3, 2.5, 2.7; lead review for the rest); licence-auditor pass done. Goldens were recorded on Windows; if CI on Linux fails only on screenshots, copy the CI `*_actual.png` artifacts into `app/src/test/screenshots/`.
- Manual test checklist (debug build "GeoQuiz Debug", installs next to the Play version):
  1. First launch after upgrading from the Play build data: not applicable (separate package). To test the upgrade path, install the debug build over an older debug build if you have one; otherwise rely on the migration tests.
  2. Play a Countries, a Capitals and a Flags quiz end to end, including a letter/word-pattern category. Typos: "Austrailia" is accepted in Normal (shown as correct); in Hard it says "Close – check the spelling" with no strike. "St Lucia", "the Gambia", "Bosnia & Herzegovina" are accepted.
  3. Rotate during a quiz: it keeps running, nothing lost. Developer options → "Don't keep activities" on: mid-quiz press Home and reopen → same answers, wrong guesses, typed text and time, paused. Finish a quiz → Results; Home and reopen on Results and on Answer review → still there; Stats shows the quiz once.
  4. Ads (test ads, not purchased): no interstitial on quizzes 1–2 after a cold start; on the 3rd quiz of at least 60 s, Results appears first, then the ad; closing it stays on Results. Quizzes under 60 s never show one.
  5. Challenges: share a challenge from Results and open the link on the phone → challenge screen with the score. Edit `score=` in the link → opens without the score. `adb shell am start -a android.intent.action.VIEW -d "geoquiz://challenge?id=x&ct=nonsense&cv=_" com.geoquiz.app.debug` → "This challenge link isn't valid" toast, no crash. After merge and deploy: `adb shell pm get-app-links com.geoquiz.app.debug` shows verified.
  6. Play Games (if linked) or logcat `PlayGamesSync`: airplane mode on, finish a quiz that unlocks an achievement, airplane mode off with the app open → synced within a few seconds.
  7. TalkBack walkthrough: home cards read as buttons ("double-tap to start quiz"); in a quiz the count is announced after each correct answer ("12 of 197 countries named"), the timer does not talk every second, rows read "France, answered" / "Row 12, not yet answered"; Results and Answer review rows read as one item each; check whether "Paused" is announced when pausing.
  8. 200% font: quiz, results, review, home and settings are readable (known: Share/Challenge clip on Results, Phase 3).
  9. Settings → Reset all data still works; Credits shows the ODbL notice covering the built-in database.
  10. Release build (`./gradlew assembleRelease`, needs `challengeHmacKey` in `signing.properties`): runs a quiz, opens a challenge link and the Remove Ads purchase sheet without crashing.
- Next session starts at: Phase 3, tasks 3.9 and 3.1 (after the Phase 2 PR is merged).

### Session 4 – 2026-10-08 – Phase 3 (part 1)
- Done: 3.9 (GeoColors, shapes, type scale; correct blue + tick, wrong orange + cross; follow-up a), 3.1 (GameMode registry; follow-up e mostly), 3.2a (difficulty plumbing, Normal/Hard rules, selector, route arg, AppDatabase v12, mastery stars), 3.2b (Easy multiple choice), 3.3 (haptics, pulse/shake with reduced motion, recent answers, strings incl. "Not recognised" (c), pause announcement (f), inline icons scale with font), 3.3a (Vibration switch).
- Not done / carried over: 3.4, 3.5, 3.6, 3.7, 3.8 and the gate. Carry-overs:
  - 3.5: show newly unlocked achievements on Results (follow-up d: `QuizViewModel.newAchievements` exists but nothing displays it). Finish follow-up (e): pass the injected `ChallengeLinkSigner` at the 3 `toShareUrl()` call sites (`ui/category/CategoryListScreen.kt` ~161, `ui/results/ResultsScreen.kt` ~279 and ~303), then delete `ChallengeLinkSigner.default`, the default parameters in `ChallengeDeepLink` and the `BuildConfig` import in domain; update `ChallengeLinkSignerTest`. "Practise the ones you missed" must carry the difficulty (route arg) and work for Easy misses.
  - 3.4: wire the home "New modes" grid and bottom nav to `GameModeRegistry.available(...)` and `ModeIcons`. Category rows still load "Best: x/y" once while stars update live; tidy when touching home/category.
  - 3.6: toolchain bump first (follow-ups g, h: Gradle, AGP, Kotlin 2.2+, KSP, Roborazzi); then 200% font on every screen: Results Share/Challenge clip (b), Answer review top-bar title clips, difficulty selector "Normal" segment, the Easy panel squeezes the country list, Easy option result icons (24 dp) don't scale.
  - 3.8 / gate: re-record all goldens once (quiz, review, settings and category screens all changed), check each image, add screenshot tests for the Easy panel and the category list with the selector and stars.
  - Answered by Dav (2026-10-08): near miss stays without a haptic; the capitals recent-answer chip format "Paris (France)" (country hint on) is fine.
- Decisions: D14, D15 (Dav), D16–D18 (lead), D19 build rule and D20 vibration switch (Dav).
- Owner actions added: none.
- Verification so far: each task compiled (`compileDebugUnitTestKotlin`; Hilt graph via `hiltJavaCompileDebug` for 3.1/3.2a) and its own test classes passed (latest: QuizLoopUiTest 10, QuizViewModelTest 39, SettingsRepositoryTest 8, SettingsViewModelTest 5, ResetAllDataUseCaseTest 8; earlier: AppDatabaseMigrationTest 54, registry, generators, mastery, choice generators, contrast). The full suite, Roborazzi, lint and release have not run since 3.9 (316 tests passed then). `QuizLoopUiTest` now uses `@Config(qualifiers = "w411dp-h891dp")` (the difficulty chip pushed row 3 off Robolectric's 320x470 default). Goldens are stale for the quiz and settings screens; re-record at the gate.
- Manual test checklist: written at the end of the phase.
- Next session starts at: Phase 3, task 3.5 (Results), then 3.4, on `upgrade/p3-ux-engine`.

### Session 5 – 2026-10-08 – Phase 3 (part 2)
- Done: 3.5a (signer injected at every share site; `ChallengeLinkSigner.default` and the `BuildConfig` import in domain removed: follow-up e finished), 3.5b (newly unlocked achievements on Results, stored with the result: follow-up d), 3.5c ("Practise the ones you missed", any tier incl. Easy misses, difficulty carried, D21), 3.4a (Play hub, 4-tab bottom nav, mode switch and New modes grid from the registry and `ModeIcons`), 3.4b (Today's challenge placeholder behind the flag, Continue, Recommended next), 3.4c (pinned categories, live Best on category rows: the stale "Best: x/y" carry-over). Commits `0398065`, `440ea1d`, `a6264f6`, `bdb3018`, `fc0f836`, `669a1e1`.
- Not done / carried over: 3.6, 3.7, 3.8 and the gate (as planned). Carry-overs:
  - Gate/goldens: re-record once and check by eye: Play (`home_countries_*` now render `PlayScreen`; rename to `play_*`), Settings (no back arrow), Results (practice button, achievements card), category list (pin toggle, 48 dp share button), plus the session 4 list (quiz, review, Easy panel). Add screenshots for the Play cards and the Results achievements card. CI will fail on screenshots until then.
  - 3.6 (200% font): bottom bar between 1.0 and 1.5 may ellipsise "Achievements" on narrow phones (name still read); check the Play mode switch icon-only fallback, Continue/Recommended/Pinned cards, Results new buttons. Plus the session 4 3.6 list.
  - Strings and spelling: `CategoryGroup`/`FlagCategoryGroup` names and `QuizCategory.displayName` are hard-coded with US spelling ("By Color", "2 colors"); Results/Stats still have hard-coded English and `String.format(Locale.US, …)`; Stats "Achievements" button text; achievement titles/descriptions in the enum (Phase 9 decides). Subregion display name differs between cards ("Australia and New Zealand") and the list ("Australasia").
  - Theme: `Color.kt` legacy `Category*`, `CapitalsAccent`, `FlagsAccent` now mostly unused; remove with 3.8.
  - Navigation tests: no test drives `AppNavigation` itself (`returnToPlay`, tab save/restore, deep link to Play); consider a `TestNavHostController` test in 3.7/gate.
  - Play group tiles count two-colour combos by overlap while the category list needs an exact match, so a tile can open an empty-ish list (existing behaviour).
  - Phase 5: wire `onOpenMode` in `AppNavigation`; `PinnedCategory.validOrNull`, `ChallengeLinkParser` and `QuizCategory.isOfferedIn` still take `QuizMode` and need registry ids for new modes.
- Decisions: D21–D24 (Dav), D25 (lead).
- Owner actions added: none.
- Verification: per D19, each task ran `compileDebugUnitTestKotlin` (+ `hiltJavaCompileDebug` where injection changed) and its own test classes, all passing (latest: Play 3 classes 45, CategoryList 3 classes 18, PinnedCategoriesRepositoryTest 11, ResetAllDataUseCaseTest 9, AppBottomBarUiTest 3, RecommendNextCategoryUseCaseTest 20, CategoryOptionsBuilderTest 6, CompleteQuizUseCaseTest 12, QuizViewModelTest 42, ChoiceQuestionGeneratorsTest 12, ChallengeLinkParserTest 23). Full suite, Roborazzi, lint and release not run since 3.9. 3.4a went through the code-reviewer agent (findings fixed; stale goldens deferred to the gate); the other diffs were reviewed by the lead.
- Manual test checklist: written at the end of the phase. Items to include from this session: Play hub in all three modes; tabs keep their state; Home from Results lands on Play in that mode; Continue resumes in its own mode; Recommended changes after playing; pin/unpin from a category row and start from Play; Reset all data clears pins; finish a quiz with misses at Easy and Hard and practise them (only the missed items, same tier, not in Stats, no Share/Challenge, Resume card untouched); a quiz that unlocks an achievement shows the card, also after "Don't keep activities"; debug menu → Daily challenge flag shows the Today card.
- Next session starts at: Phase 3, task 3.6 (toolchain bump first), then 3.7, 3.8, the gate and the PR, on `upgrade/p3-ux-engine`.
