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
| 3 | Game engine and UX foundation | Done (merged) | `upgrade/p3-ux-engine` | #6 |
| 4 | Map engine and geodata pipeline | Done (merged) | `upgrade/p4-maps` | #7 |
| 5 | New modes A: Silhouettes, Tap the map | Done (merged) | `upgrade/p5-modes-a` | #8 |
| 6 | New modes B: Border hop, US states and Canadian provinces | Done (merged) | `upgrade/p6-modes-b` | #9 |
| 7 | New modes C: Currencies, Rivers/mountains/lakes, Flag speed round | Done (merged) | `upgrade/p7-modes-c` | #10 |
| 8 | Engagement: daily challenge, streaks, share card, hints, game-feel | Done (merged) | `upgrade/p8-engagement` | #11 |
| 9 | Release readiness | Done (merged) | `upgrade/p9-release` | #12 |
| 10 | Device-test feedback (UI and quiz changes) | Done (merged) | `upgrade/p10-feedback` | pushed to `main` |
| 11 | Store listing update (text, screenshots, release AAB) | Done – not committed | `upgrade/p11-store-listing` | – |

Statuses: Not started, In progress, Done – not committed, Done – PR open, Done (merged).
Each phase branches from `main` after the previous PR is merged. Dav tests the debug build on a device before merging.

### Next session starts at

> **Ship 3.0.0 (Dav, mostly Play Console).** Session 15 finished the store listing on `upgrade/p11-store-listing`: text in `docs/STORE_LISTING.md` (D67–D77 covered), 8 regenerated screenshots in `store_assets/screenshots/`, and a release AAB built from this tree (`app/build/outputs/bundle/release/app-release.aab`, 3.0.0 / 18, upload key). Remaining:
> 1. Merge `upgrade/p11-store-listing` into `main` (or open its PR) and close PRs #7–#12 as merged into `main`.
> 2. Upload the AAB to internal testing, install it over the store version and run the upgrade test (session 13 checklist step 0).
> 3. Paste the listing text, upload the icon, feature graphic and 8 screenshots (in file order), and hand-edit the 4 achievement texts (D66). Then the rest of the owner checklist in `docs/STORE_LISTING.md`.
> 4. Optional code follow-ups: Border hop's button row is clipped by the map on a 360x640 dp phone (visible in `06_border_hop.png`); see the session 15 log.

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
- [ ] Play Games bulk import expects `AchievementsIconsMappings.csv` (Google's doc, checked in Phase 9; the script writes and zips that one). The stale duplicate `AchievementsIconMappings.csv` was deleted in Phase 9 (Dav approved).
- [ ] Upload the store images regenerated in Phase 9 (icon, feature graphic, 8 phone screenshots in `store_assets/screenshots/`) and paste the text from `docs/STORE_LISTING.md`.
- [x] Merge the Phase 1 PR after checking the debug build on a device (manual checklist in the session 2 log).
- [ ] Back up `challengeHmacKey` (added to the local `signing.properties` in Phase 2) with the keystore in Drive and Bitwarden. Every release must use the same key; losing it means older challenge links open without scores.
- [ ] App Links: add the Play App Signing key SHA-256 (Play Console → App integrity) to the `com.geoquiz.app` entry in `docs/.well-known/assetlinks.json`; after merge check `https://geoquiz-app.netlify.app/.well-known/assetlinks.json` is served as JSON with no redirect. The upload key and debug key are already listed.
- [ ] Debug builds are now `com.geoquiz.app.debug` ("GeoQuiz Debug"). Play Games sign-in and Billing won't work in them unless that package is linked in Play Console (Play Games: add a linked Android app with the debug SHA-1); not required for testing.
- [ ] Play Console achievements: edit by hand (no re-import) the World Traveller, Colour Expert and Rainbow text to the app's Australian spelling (D66), and the Flag Master description ("Complete the Flags of the World quiz with 80%+"). Checklist in `docs/STORE_LISTING.md`.

---

## 4. Codebase facts

Filled in by Phase 0 (session 1, 2026-10-07) and kept current. Agents read this instead of re-exploring.
`J/` = `app/src/main/java/com/geoquiz/app/`.

**Stack and build**
- Single `:app` module. Kotlin 2.2.21 (KSP2 2.2.21-2.0.5), AGP 8.13.2, Gradle 8.14.5, Compose BOM 2024.12, Material 3, Hilt 2.57.2, Room 2.7.2, Roborazzi 1.76.0 (since 3.6a; Kotlin 2.3 needs Hilt 2.60 → AGP 9, see D26), DataStore 1.1.1, Navigation Compose 2.8.5, kotlinx.serialization, AdMob (play-services-ads 23.6.0), Play Games v2, Play Billing 8.3. minSdk 26, compileSdk/targetSdk 36, versionCode 18, versionName 3.0.0 (D64; was 17 / 2.7.2).
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
- `J/domain/model/Difficulty.kt`: EASY/NORMAL/HARD (ids easy/normal/hard) with rules (`allowsTypos`, `strikeLimit` Hard 3, `timerAlwaysShown`, `countsForAchievements` Easy false), `forChallenge` (never Easy). Every selector starts at Normal and nothing persists the choice (D72); the old `difficulty`/`hard_mode` DataStore keys are no longer read. Quiz route `quiz/{mode}/{type}/{value}?challengeId=&difficulty=`; resolution: route → SavedStateHandle → matching resume save → default. Selector `J/ui/components/DifficultySelector.kt` on the category list and every setup screen; `DifficultyLabel` chip in the quiz header.
- Easy (D14): `J/domain/mode/MultipleChoice.kt` (`ChoiceQuestion`, `ChoicePrompt` InSet/CapitalOf/FlagOf, three generators, injectable `QuizRandom`). Each item asked once; wrong pick = miss + incorrect guess; ~900 ms feedback from the ViewModel. `J/ui/quiz/components/MultipleChoicePanel.kt`. Easy progress lives in `QuizSavedState`; the Room resume save keeps answered codes only (missed items are re-asked).
- Mastery stars (D17) were removed in phase 10 (D70); category rows show the completed tick and Best score only.
- Feedback (3.3): `QuizViewModel.feedbackEvents` → `J/ui/quiz/feedback/` (`HapticFeedbackPlayer` CONFIRM/REJECT, no VIBRATE permission; pulse/shake `FeedbackMotion`), `J/ui/components/ReducedMotion.kt` (`rememberReducedMotion()`, reuse in Phase 8), recent-answer chips (`RecentAnswers.kt`, last 3, Normal/Hard), shared `FeedbackLine.kt`, `inlineIconSize()` scales inline icons with font. Settings key `vibration` (default on) gates haptics; the system touch-feedback setting also applies.
- Theme (3.9): `J/ui/theme/GeoColors.kt` via `MaterialTheme.geoColors` (correct/wrong/nearMiss + containers, star/onStar, map land/border/water/found/highlight/wrong/start/end/tapZone), `Shape.kt`, full type scale. `GeoColorsContrastTest` enforces contrast. Category tile, tab accent and achievement tier colours in `Color.kt` are legacy.
- Challenge acceptance is `J/domain/challenge/AcceptIncomingChallengeUseCase.kt` (was `ui/challenges/IncomingChallengeHandler`); `ChallengeLinkSigner` is provided by `J/di/ChallengeModule.kt` and injected everywhere (3.5a): `ChallengeLinkSigner.default` is gone, `ChallengeDeepLink.toUri/toShareUrl/parse` take the signer, `domain` reads no `BuildConfig`. Results and category list VMs build, record and sign share links (`createChallengeShareUrl`).

**Play hub and navigation** (since 3.4)
- Bottom nav (`J/ui/navigation/AppBottomBar.kt`): Play, Stats, Achievements, Settings; shown only on those four routes; selection from the route hierarchy; icon-only with names as descriptions at font scale ≥ 1.5. Start destination `play` (`Screen.Play`); the old `countries_home`/`capitals_home`/`flags_home` routes and their screens/VMs are deleted. Stats, Achievements and Settings have no back arrow as tabs (`onNavigateBack` nullable); Stats' Achievements link switches tab.
- `returnToPlay(mode)` in `AppNavigation.kt` hands a mode to Play through Play's back-stack `savedStateHandle` (`Screen.Play.RESULT_SHOW_MODE`), used by Quiz/Results Home, Answer review Home and challenge Decline.
- `J/ui/play/`: `PlayScreen`, `PlayViewModel` (selected classic mode in its SavedStateHandle), `PlayCategoryGroups` (group tiles for all three modes), `CategoryHues.kt` (D68). Cards top to bottom: Today's challenge (D61), Continue (resume save in its own mode), Pinned (D23); then the mode switch ("Quiz type" segmented buttons), the compact All countries/All capitals card, Surprise me (D69), the coloured group tiles, and that page's "More ways to play" mode tiles (`PlayViewModel.extraModesByPage`, flag-gated via `GameModeRegistry.available`, D67). Recommended next and the New modes grid were removed in phase 10.
- Category options per group: `J/ui/category/CategoryOptionsBuilder.kt` (extracted from `CategoryListViewModel`; `LegacyCategoryOptions.kt` in tests is a verbatim copy of the old code used as the regression oracle). Category rows combine best score (`QuizHistoryRepository.bestScoresForMode`, live), and pins live.
- Pins: `J/data/local/preferences/PinnedCategoriesRepository.kt`, settings DataStore key `pinned_categories` (JSON array of `mode|type|value`, pin order), so Reset all data clears them; `J/domain/model/PinnedCategory.kt` validates (classic `QuizMode` only, parses, offered in the mode, never Practice). Pin toggle on each category row.

**Text size, layout and theme** (since 3.6–3.8)
- Large text helpers in `J/ui/components/AdaptiveLayouts.kt`: `AdaptiveButtonRow` (side by side or stacked), `AdaptiveTrailingRow`, `MainWithCappedTrailing`, `UpToTwoColumns` grid (`gridColumnCount`, `maxColumns`), shared `SEGMENT_LABEL_CHROME`. `WrappingTopAppBar.kt` replaces Material's TopAppBar on most screens (min 64 dp, title wraps to 2 lines, heading, actions `onSurfaceVariant`; Settings passes `titleModifier` for the 7-tap debug unlock). `DifficultySelector` falls back to a radio column and `AppBottomBar` goes icon-only when labels don't fit. `BoxWithConstraints` users must never be measured intrinsically.
- Window layout (`J/ui/components/WindowLayout.kt`, no adaptive library): `WidthClass` Compact < 600, Medium < 840, Expanded; `isShortHeight` < 480; `WindowLayout.of(maxWidth, maxHeight)` from each screen's own space; `useTwoPanes` = Expanded, or Medium wider than tall, or landscape phone; `readableWidth()` 840 dp; `TWO_PANE_MAX_WIDTH` 1200. `navigationChrome(route, layout)` in `AppBottomBar.kt`: bottom bar or `AppNavigationRail` (from 600 dp) only on the four tab routes.
- `AppNavigation`: Row(rail + Scaffold(`contentWindowInsets = WindowInsets(0)`)), NavHost `padding(padding).consumeWindowInsets(padding)`, horizontal display cutout handled once on the outer Row. Manifest `adjustResize`; the quiz uses `imePadding()`.
- Quiz (`QuizScreen.kt`, `J/ui/quiz/QuizLayout.kt`): compact portrait = title, status, list, then recent answers + field + Give up/Submit pinned at the bottom (D27); traversal indices read title → status → controls → list. Below 560 dp of space ÷ font scale the title scrolls (typed: first list item; Easy: top of the options panel); below 300 recent answers hide. Two panes: controls start, list end (D28). One hoisted `LazyListState` (`CountryList(state = …)`) survives layout switches; Play hoists its grid state.
- Easy: `J/ui/quiz/components/PanelAboveList.kt` (options scroll, list keeps min(40%, 120 dp × font scale), scroll resets per question).
- Theme (3.8): no hard-coded colours in `ui/` outside `ui/theme`; tier tokens (gold/silver/bronze accent, container, on-container) and `flagBorder` (light `#9E9E9E`, dark `#7A7A7A`) in `GeoColors`; dark scheme sets neutral surface containers, outline and inverse roles; map tokens retuned and contrast-tested in both themes (`GeoColorsContrastTest`). `values-night` window background. Legacy `Category*`/accent/tier colours removed from `Color.kt`. Stateless `AchievementsContent`.

**Results and practice** (since 3.5)
- `CompletedQuiz.newAchievementIds` (default empty) is written by `CompleteQuizUseCase` after unlocks (a second `save`), so Results' "New achievement(s)" card (`NewAchievementsCard`, polite live region) survives process death. `QuizViewModel` no longer holds achievements.
- "Practise the ones you missed": `QuizCategory.Practice(codes)` (type `practice`, value = cca3 codes joined by `+`, quiz order). `CompletedQuiz.missedCodes()` = all items minus correct (Easy wrong picks and give-up leftovers included). Results button carries the difficulty route arg; hidden on a perfect result. D21: `QuizCategory.isRecorded` is false for Practice: `CompleteQuizUseCase` saves the result and returns before clearing the resume save or recording anything; `QuizViewModel` writes no Room resume save; `fromRouteOrNull` rejects it (no challenge links); Results hides Share/Challenge and shows a "not counted" note. Countries Easy uses the flag prompt for a practice set.

**Map engine** (since Phase 4)
- Data: `tools/geodata/build_geodata.py` (pinned Natural Earth 5.1.2, pyshp only in a hash-pinned venv, shared-arc Douglas–Peucker, reproducible, `--check` needs the cached zips) writes 8 layers to `app/src/main/assets/geo/*.bin` (606 KB of the 1.5 MB D3 budget): `admin0_110m`, `admin0_50m` (197 playable keyed by cca3 + non-playable land with `playable = 0`; part flags hole/remote/wrap; label points; `area_km2`), `tap_zones` (46 small countries), `admin1_50m` (US 50 + DC, Canada 13; ISO 3166-2 ids), `rivers_50m`, `lakes_50m`, `regions_50m`, `geo_points_50m` (Wikidata ids from NE `WIKIDATAID`). Byte contract `data/geo/FORMAT.md` (GQGM v1); build log `data/geo/build_log.txt`. D31 (not an ODbL derived database), D32 (disputed areas: NE default view).
- Kotlin: `J/data/geo/GeoLayerReader.kt` (strict; `GeoFormatException` reasons), `AssetMapRepository` (`MapRepository.layer(GeoLayerId)`, lazy, cached, IO; `J/di/MapModule.kt`). `J/domain/map/`: `GeoLayer`/`GeoFeature`/`GeoPart`, `Projection.kt` (`EqualEarthProjection`, `LambertAzimuthalEqualAreaProjection(centreLon, centreLat, maxDistanceDeg = 100)`, `isVisible`, `centreAngleDeg`; plane coordinates are y-down), `ProjectedLayer`, `HitTester.kt` (`FeatureHitIndex` grid + even-odd, smallest box wins; `TapZones`; `resolveTap`; `MapHit(featureId, isPlayable, viaTapZone, isMember)`), `RegionPreset` (World + 6 continents, members from the app's region/subregion; `frame()` skips remote parts; Europe frames Russia to 60°E), `OutlineGeometry` (per-country LAEA, 92% fit with a minimum-area clamp, geometry neighbours), `MapFeatureState`. Use cases `GetRegionMembersUseCase`, `GetCountryOutlineUseCase` (`@DefaultDispatcher`, new qualifier in `J/di/AppModule.kt`).
- UI `J/ui/map/`: `MapCanvas` (pan, pinch, double-tap zoom / reset at max, `doubleTapToZoom = false` for instant taps, `onFeatureTap(MapHit?)`, `showTapZones`, `mapContentDescription` + custom actions Zoom in/out/Show whole map), `MapViewState` (saveable camera, `fitTo`, `updateContent` re-fits on new bounds unless `userMoved`, interrupted animations return normally), `MapScene`/`rememberMapScene` (110m/50m switch at 160 dp per unit; members, pan margin, far-side non-members beyond 80° neither drawn nor hit), `MapPathCache` (process-wide, LRU of 3 projections), `MapHitTesting.kt` (`MapScene.hitTest`, tap zones 44 dp; circles drawn only for the 46 listed or a country with a state), `CountryOutline` (+ LRU path cache), `MapListAlternative` + `MapOrListToggle` + `rememberIsTouchExplorationEnabled()` (TalkBack path), `MapAccessibility.kt`. States: Found blue + tick, Wrong orange + cross + hatch, Start dot, End ring + dashed outline, Highlighted thick outline; non-playable/non-member land `mapLandInactive`.
- Debug: Settings title ×7 → Debug menu → Map preview (presets, level/lakes/rivers toggles, map/list toggle, tap shows the hit and cycles states, decode/build timings).
- Tests: reader/repository/projection/camera/hit (all 197 tappable on a phone at world fit and 50m at 3×)/presets (no clipping on phone and tablet)/outline (197 at consistent size)/accessibility; map timing assertions only with `-Pgeoquiz.perf=true`. Goldens: 15 `map_*` in `MapScreenshotTest` (47 goldens in total).
- Build tooling (D33): Sonnet sub-agents; one Gradle build at a time via a lock helper (`mkdir` lock directory; call Git Bash by its full path, plain `bash` resolves to the WSL stub on this machine); Gradle build + configuration cache on; CI on every push to `upgrade/**`. Never pipe a Gradle run through `head` (it hung a build).
- `admin1_50m` (Phase 6.2): exercised end to end for the first time. Each feature's properties already carry `name`/`name_en`/`postal`/`country` (ISO 3166-2 id, e.g. `US-CA`), so US/CA regions reads them directly (`J/domain/mode/usca/UsCaRegionSet.kt`) rather than needing a new alias data file. Not loaded via `TapMapMapLoader` (that stays admin0-only, D43): its own `J/ui/usca/UsCaRegionsMapState.kt`/`UsCaRegionsFraming.kt` load the layer and fit a frame, reusing `GeoLayer`/`MapScene`/`HitTester` generically.

**New modes** (since Phase 5)
- Plumbing (5.0): `GameMode.offers(category)` decides a mode's categories; new modes register in `J/di/GameModeModule.kt` behind their `FeatureFlag`. Routes `mode_setup/{modeId}` (from Play's New modes grid), `silhouettes/{categoryType}/{categoryValue}?difficulty=`, `tap_map/{variant}/{categoryType}/{categoryValue}?difficulty=`; `J/ui/navigation/ModeRoutes.kt` builds start / play-again / practise routes for any mode id (classic ids go to `Screen.Quiz`); `ModeAvailabilityViewModel` pops a new-mode route whose flag is off. `QuizMode.fromIdOrNull`; `CompletedQuiz.variantId` (optional, JSON-compatible); `CompleteQuizUseCase.Request(modeId, variantId)`: a non-classic mode is recorded in history only (D35: no achievements, Play Games unlocks or leaderboards; `QuizHistoryDao.getTotalCorrectAnswersSync` counts classic modes only) and never clears the classic resume save (D37). Results: `ResultsUiState.Loaded(result, modeSpec, showChallenge)`; `createChallengeShareUrl` returns null for new modes, so Share text has no link (`ShareUtils.buildResultsShareText`). `QuizCategory.MapRegion(presetId)` (`map_region/<RegionPreset.id>`, World or a continent) resolves in `GetCountriesForQuizUseCase` via `GetRegionMembersUseCase`.
- Shared: `J/domain/mode/HintCosts.kt` (placeholder hint costs, D37; any hint drops the perfect bonus) + `QuizState.hintsUsed`; `MultipleChoicePanel(showPrompt = false)` for modes with their own prompt; `DifficultySelector(summaryRes = ...)` per-mode summary (D41).
- Country silhouettes (5.1): `J/domain/mode/silhouettes/` (15-random generator, practice = exact codes; same-region Easy choices), `J/ui/silhouettes/` (setup from `CategoryOptionsBuilder` filtered by `offers`; `SilhouettesGameViewModel` own state machine with 3 lives, Pass, hints, `pendingAdvance` so restore never replays an answered silhouette; `SilhouettesSavedState`; pauses on ON_STOP like classic; Back asks first; layout from `rememberWindowLayout()` + `quizSpace`, two panes on wide screens, header scrolls on short ones, field + Submit pinned). D34, D38, D41.
- Tap the map (5.2): `J/domain/mode/tapmap/` (`TapMapQuestionGenerator`, `TapMapChoiceGenerator`, `VARIANT_FIND`/`VARIANT_NAME_ALL`), `J/ui/tapmap/`: `TapMapSetup*` (region chips, Find/Name all, difficulty; Name all hides Easy), `TapMapGameScreen` dispatches on `TapMapVariantViewModel` to Find (`TapMapGameViewModel`, `TapMapSavedState`, stateless `TapMapActiveContent`: controls pinned under the map, two panes on landscape/wide; input locked during a reveal or pending flash; taps on resolved countries ignored; Hard ends at the strike limit even via Skip or restore; Easy = highlighted country + subregion clue + 4 names, no list/Hint/Skip, D39) or Name all (`TapMapNameAllViewModel`/`Screen`/`SavedState`: classic Countries validation, `imePadding()` with the layout computed before the keyboard, field + buttons pinned, one submit at a time; TalkBack list shows found countries only + remaining count, D40). `TapMapMapLoader.kt` loads layers and frames for both (practice framed to its members with a minimum size; World not framed). D36, D39, D40.
- Tests: `NewModesScreenshotTest` (16 goldens; 63 in total) renders real outlines and maps and pre-warms `CountryOutlinePathCache.Shared` and `MapPathCache.Shared` (both build paths asynchronously; an un-warmed first frame is blank). Never call `advanceUntilIdle()` while a round's timer ticker runs: it never goes idle (it hung a build for an hour); use `advanceTimeBy` + `runCurrent`. Nodes using `clearAndSetSemantics` must be found by content description in UI tests. Stateless screen content in screenshots needs a `Surface` (the real `Scaffold` sets the content colour).
- Border hop (6.1, reworked in phase 10, D73): `J/domain/mode/borderhop/` (`BorderGraph`: BFS adjacency over `app/src/main/assets/border_hop/borders.json`, a bundled byte-identical copy of `data/borders.json`; `randomPuzzle(candidates, hopRange, random)` excludes islands/too-small components; `BorderHopMoves` resolves typed text against every country's aliases and judges NotRecognised / AlreadyOnRoute / NotNeighbour / Hop; `BorderHopChoiceBuilder` builds Easy's 4 options with exactly one valid hop on a shortest route; `BorderHopScoring` packs the route into a plain `QuizState` via a documented field convention), `J/ui/borderhop/` (own screen/ViewModel, D37: a blank world map from admin0 110m/50m with start, target and route feature states, auto-framed to the route; `J/ui/components/CountryAutocompleteField.kt` + `J/domain/usecase/CountryNameSuggester.kt` at Normal; Undo is free; `HintType.SHOW_VALID_HOP` at Normal/Hard). `offers()`: AllCountries/ByRegion/BySubregion only. D42, D44, D73.
- States and territories (6.2; named "US states and Canadian provinces" until phase 10, which added Australia, D74; package and mode id `us_ca_regions` unchanged): `J/domain/mode/usca/` (`UsCaRegionSet`: fixed ISO 3166-2 member lists per `UsCaRegionGroup`, DC optional for US; `UsCaRegionsAnswerValidator` matches a feature's name or postal code, postal exact-only below `FuzzyAnswerMatcher`'s minimum length; `QuizCategory.FixedRegion(label)`, a new category case for a fixed named set that is never offered in a classic mode, same reasoning as `MapRegion` - an earlier version reused `ByRegion`, which other modes' `offers()`/`AchievementRepository`'s region tracking/`AnswerReviewScreen` all pattern-match on and would have silently mismatched). Three variants, each its own ViewModel/screen (`J/ui/usca/`): Tap (`UsCaTapViewModel`, D36-style wrong-tap rules), Name all (`UsCaNameAllViewModel`, Normal/Hard, a "remaining names" list rather than a map, D46), Choice (`UsCaChoiceViewModel`, Easy-only, 4 names). No hints (`hintTypes = emptySet()`, same as classic Countries/Capitals/Flags). `GameModeSpec.supportsAnswerReview` (new field, default `true`) is `false` here: this mode's codes are admin1 ids, not cca3, so `AnswerReviewViewModel`'s `CountryRepository` lookup would come back blank; Results hides "View Answers" when it's false. D43, D46.
- Tests: `NewModesBScreenshotTest` (18 goldens; 83 in total). Border hop's fixture used synthetic country codes until phase 10 (it now uses real cca3 codes and the real admin0 layers, since the reworked screen shows no flags): a real flag's `FlagImage` load is not reliably deterministic under Robolectric when several appear in one capture (confirmed by a flaky Spain flag during image review), whereas a code with no bundled SVG renders identical placeholder pixels on every timing outcome (`error`/`fallback`/loading all resolve to the same `ColorPainter`). `AdaptiveTrailingRow` (not a plain `Row(SpaceBetween)`) is required for any status row with a progress text plus a wrapping trailing group (strikes/timer/tier), or it overlaps at 200% font scale (D29) - `TapMapGameScreen.StatusRow` already uses it; `UsCaStatusRow` and `UsCaNameAllScreen`'s header needed the same fix after screenshot review caught the overlap.
- Currencies (7.1): `J/domain/mode/currencies/` (`CurrencyGameMode`, `CurrencyEntry`/`CurrencyData` from the bundled `assets/currencies/currencies.json`; `CurrencyItems.build(data, allCountriesByCode, candidateCodes)` assigns each currency's bookkeeping "representative" country **scoped to the active category filter**, not globally - a shared currency's representative can differ between categories, which is fine since the real question always goes back to the `CurrencyEntry` itself; `CurrencyItems.buildAll` is the full-game case. D50: a non-shared currency is typed at Normal/Hard, 4-choice at Easy; a shared currency is **always** `ChoicePrompt.CurrencyOf` at every tier, since typing has 2+ valid answers - `CurrencyAnswerValidator` rejects a typed guess for one. `listedInBasketBy` countries (Zimbabwe's basket, D48) are excluded from both the correct-answer and wrong-option pools. `CUC`/`KID`/`TVD` have no typed representative (nobody's real primary currency in this data, a reasoned extension of D48 beyond its literal text, flagged for Dav) but Kiribati/Tuvalu remain askable via the shared `AUD` question. `J/ui/currencies/` (setup + game screen/ViewModel; flags on every choice option; a post-answer fact banner from the currency's `fact` field; `CurrenciesSavedState` persists currency codes keyed by category, not representative country codes, so restore rebuilds the exact same round).
- Rivers, mountains and lakes (7.2): `J/domain/mode/physicalfeatures/` (own `PhysicalFeature`/`PhysicalFeatureFilter` - no `QuizCategory` fits 101 curated features with no region/subregion structure, D51; `PhysicalFeaturesGameMode`'s `generator`/`validator`/`choiceGenerator` are unused placeholders, D43's pattern, since the screen builds a round directly from `PhysicalFeatureRepository` filtered by tab/continent). Typed answers strip generic geography words ("river"/"lake"/"mount"/"mountains"/"range") before comparing, so "Everest"/"Baikal"/"Amazon River" are all accepted; typo tolerance is scoped to the current target only, never any other round feature. The map highlights one feature via a new, optional `MapScene.overlay`/`MapCanvas` layer (`domain/map`/`ui/map` additions, D51: additive only, defaults to `null` for every existing caller, zero behaviour change for Tap the map/US-CA/Silhouettes) composited over a real `admin0_50m` basemap, sidestepping `regions_50m`'s ~517 overlapping polygons by building a synthetic single-feature layer rather than drawing the whole noisy layer. `MapCanvas.drawMap` also now skips its fill pass for `GeometryKind.LINE` (rivers): filling an open polyline as if closed was a latent, generic bug, not mode-specific. A point feature (a peak) draws via the same fixed-size marker circle a tap zone uses. `supportsPractice` (new `GameModeSpec` field, default `true`, mirrors `supportsAnswerReview`) is `false` here: this mode's feature-id codes would otherwise silently mis-parse back into the wrong filter via `PhysicalFeaturesCategory.filterFromLabel`'s safe default, starting an unrelated round instead of practising the real misses - worse than a blank Answer Review (also `false`, D43's reasoning). A touch-exploration text clue (the feature's continent) is the non-visual fallback, since there is no list alternative for a single highlighted feature the way Tap the map/US-CA have for a whole region.
- Flag speed round (7.3): `J/domain/mode/flagspeed/` - the one mode with no fixed-size `QuizState` precedent (a hard 60-second clock, open-ended item count). Every question reuses the existing `ChoicePrompt.FlagOf` shape (flag shown large, name options), with distractors drawn only from the target's own lookalike set (`data/flag_lookalikes.json` via `assets/flag_speed/`). D52 (corrected 2026-10-16 after code review checked the real data): Easy draws only tier 1 (`clear`, easiest) sets, Normal tiers 1-2, Hard tiers 1-3; a set marked `in_app: "near_identical"` is never drawn at any tier, shown only in the post-round review. The round packs into a plain `QuizState`/`QuizResult` the way `BorderHopScoring` does: `quiz.countries` = every target actually answered (repeats allowed, an item still open at time-up/give-up is discarded entirely so `correct == asked - incorrectGuesses` always holds), the longest combo streak is stashed in `currentInput` as a parsed string (`FlagSpeedScoring`). `supportsPractice` defaults `true` here (unlike Physical features): the asked codes are real cca3, so a practice round is meaningful.
- Tests: `NewModesCScreenshotTest` (29 goldens; 112 in total) - the Rivers/mountains/lakes goldens are the first real visual confirmation that the new map-engine overlay/line-fill-skip code actually renders correctly for each of the four geometry kinds (a river, a mountain range, a peak, a lake), since the fix round that wrote it could only verify by code reading.

**Engagement** (Phase 8)
- Daily challenge (8.1, D53/D54): `J/domain/mode/dailychallenge/` (`DailyChallengeDate.today()` device-local ISO date, `DailyChallengeGenerator` seeded from it, `QUESTION_COUNT` 10, `DailyChallengeScoring`), `J/ui/dailychallenge/` (Setup + Game screens/VMs). Recorded in `quiz_history` as mode `daily_challenge`, category `QuizCategory.DailyChallenge(date)` (type `daily`, value = the round's start date). The Game VM refuses a fresh round for an already-recorded date (`DailyChallengeUiState.AlreadyPlayed`, D54's hard guard since 8.2); restoring an in-progress round is never blocked.
- Streaks (8.2, D59): `J/domain/streak/StreakCalculator.kt` (pure; rules in its KDoc) over the distinct daily dates from `QuizHistoryDao.observeDistinctCategoryValues`; `ObserveStreakUseCase` combines them with `localDateTicks()` (today, re-read just after each local midnight) — no new storage, so Reset all data clears streaks. `StreakStatus.NONE`, `StreakCalculator.MAX_FREEZES` (2). Setup derives "already played today" from `StreakStatus.playedToday` (one live source, so Back from Results flips Start off).
- Share card (8.3, D60): `J/ui/share/ShareCardContent.kt` (pure; wording from `ShareCardStrings`, resolved by `shareCardStrings()` from `strings_share.xml`), `ShareCardRenderer` (android.graphics, 1080x1080 PNG, title ≤ 3 lines, short `displayLink` ≤ 2 lines, `placementFor` keeps blocks apart; `writeToCache` → `cacheDir/share/share_card_<time>.png`, older cards deleted; FileProvider `${applicationId}.fileprovider`, `res/xml/file_paths.xml`; ClipData for the chooser preview), `ShareCardDialog` (preview shrinks to fit so the buttons stay visible). Results' Share → `ResultsViewModel.openSharePreview()`: dialog state and the challenge link live in the `SavedStateHandle`, so rotation keeps the dialog and one outgoing challenge is recorded per Results entry. An image failure falls back to the text share. `ShareUtils.buildResultsShareText(..., date)`: classic text unchanged; new modes add the date and the Play Store link. FileProvider's root matching uses `/`, so the URI half of `ShareCardRendererTest` is skipped on a Windows host (runs on CI).
- Home (8.6): `PlayUiState.todayChallenge: TodayChallengeInfo?` (streak + playedToday), shown while the daily challenge mode is available in the registry (today: `FeatureFlag.DAILY_CHALLENGE`); the card opens `mode_setup/daily_challenge`; header `StreakChip` (count + flame, icon dropped above 1.5 font scale; title is one line, ellipsised). Daily challenge is filtered out of `newModes`. `ScreenTestFixtures.playViewModel(streak = …)` registers the real `DailyChallengeGameMode`.
- Hints (8.4, D55): `HintCosts.maxHintsPerRound(difficulty)` (Easy 0 / Normal 2 / Hard 3). Game-feel (8.5, D56–D58): `J/domain/feedback/ComboTracker.kt` (feel-only), `J/ui/quiz/feedback/ConfettiOverlay.kt`, `J/data/service/SoundEffectPlayer.kt` + `res/raw/sound_*.wav` (from `tools/sound/generate_sound_effects.py`, deterministic), Settings key `sound_effects`.

**Release candidate** (Phase 9)
- Version 3.0.0 / 18 (D64). Every `FeatureFlag` has `defaultEnabled = true` (D63); turning a mode off before shipping = set its default to `false`. Flag tests no longer assume default-off: `PlayScreenUiTest.dailyOff()`, `PlayViewModelTest.flagStates()`, `ModeAvailabilityViewModelTest.allFlagsOn()`; the screenshot fixtures follow the defaults.
- `GameModeSpec.supportsPlayAgain` (default true; false for the daily challenge). Unavailable states, never recorded, in Border hop, Currencies, Rivers/mountains/lakes and the three US/CA variants (`ui/usca/UsCaUnavailable.kt`). Border hop setup hides filters with no puzzle at the chosen tier (`BorderGraph.hasPuzzle`, cached per difficulty).
- Daily challenge rounds are always scored out of all 10 questions; `DailyChallengeGeneratorTest` pins three dates' exact output: if it fails, players' questions changed, so it needs a decision, not a golden update. `ObserveStreakUseCase.localDateTicks` re-reads the wall clock at least every 60 s.
- Stats: Reset Statistics keeps `daily_challenge` rows (`QuizHistoryDao.deleteHistoryExceptMode`); By Mode comes from the registry; Highest Score is classic-only.
- `GeographyQuizApplication.onTrimMemory`/`onLowMemory` clear the map path caches.
- Store assets: `python generate_store_assets.py`, `python generate_achievements_zip.py` (Lato), phone screenshots via `./gradlew testReleaseUnitTest --tests "*StoreScreenshotTest" -Pgeoquiz.storeShots=true` (release variant, so the header shows the real app name; skipped otherwise) → `store_assets/screenshots/` (since session 15: 01 play, 02 play_more_ways, 03 quiz_countries, 04 tap_the_map, 05 silhouettes, 06 border_hop, 07 australia, 08 results; Easy was dropped by Dav). Listing text in `docs/STORE_LISTING.md`.
- CI also checks that the bundled `assets/{border_hop,currencies,flag_speed,physical_features}` JSON copies match `data/` (`cmp`).

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
- Screenshots (since the Phase 3 gate): 32 goldens. `KeyScreensScreenshotTest` (Pixel 5; quiz, quiz_easy, results, results_achievements, answer_review, play, category_list, achievements, settings × light/dark/font200) and `WideLayoutsScreenshotTest` (`-xhdpi` qualifiers: quiz_tablet, quiz_landscape, play_tablet, results_tablet, nav_rail); screens built without Hilt in `ui/screenshot/ScreenshotScreens.kt`, capture in `ScreenshotCapture.kt`. Text-measuring UI tests use `@GraphicsMode(NATIVE)` and `LargeTextAssertions.kt` (legacy graphics fakes text widths).
- Local builds are slow on this machine (R8 release about 14–20 min, 8 GB RAM: never run two Gradle builds at once). Sub-agents run `assembleDebug testDebugUnitTest`; the lead runs lint and release once per wave. Running `lintDebug assembleRelease` together once crashed a lint detector; run separately, both pass.
- `core.autocrlf=true` locally: Git warns LF to CRLF on new files. `tools/fonts/.gitattributes` keeps the font files and OFL byte-exact.

**Licensing recon (0.2)**
- Flag source was emoji (Unicode) until Phase 1; now flag-icons SVGs (see Flags).
- Phase 1 added the Credits and Open-source licences screens with the in-app ODbL notice. Since Phase 4 Natural Earth map layers are bundled and the Credits screen has Maps (Natural Earth) and Regions (UN M49) sections.
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

Status: 3.9, 3.1, 3.2, 3.3 (+3.3a in-app Vibration switch) done in session 4 (2026-10-08). 3.5 and 3.4 done in session 5 (2026-10-08). 3.6 (a toolchain, b 200% font), 3.7, 3.8 and the gate done in session 6 (2026-10-09). PR open.

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

Status: D3 decided; 4.1–4.6 and the gate done in session 7 (2026-10-09 to 2026-10-13). PR open. Device checks (60 fps, API 26/27, world view density) in the session 7 log.

### Phase 5 – New modes A

| ID | Mode | Spec | Group |
|---|---|---|---|
| 5.1 | 1. Country silhouettes | Outline from 4.5, type the answer (Normal/Hard) or 4 choices (Easy). 3 lives. Hints: Show region, First letter, Show neighbours (costs per hints economy, placeholder costs until Phase 8). Guessed-so-far chips. Works with existing categories as filters where they make sense (region, letters). | A |
| 5.2 | 3. Tap the map | Region picker: World or one continent. Prompt shows a country; tap it. Found countries fill blue. Timer, Skip, Hint (flash region outline). Wrong tap shows the tapped country name briefly. Name-all variant: type names and they fill in. | A |

AC: both behind feature flags, reachable from New modes grid, results and practice-missed work, screenshot tests added, TalkBack path works via list alternative.

Status: 5.0 (mode plumbing), 5.1, 5.2 (Find and Name all), 5.3 (screenshots) and the gate done in session 8 (2026-10-13 to 2026-10-15). PR open. Device checks in the session 8 log.

### Phase 6 – New modes B

| ID | Mode | Spec | Group |
|---|---|---|---|
| 6.1 | 5. Border hop | Adjacency graph from mledoze land borders, cross-checked against Natural Earth geometry; disagreements logged and resolved in decision log (D42). Puzzle generator picks start/end with shortest path of 2–6 hops (BFS par), never island countries. Each step must border the previous one. Shows route chips and a tappable list of the current country's neighbours (no map — D45). Undo last hop. Score vs par. "Land borders only" rule shown in-app. | A |
| 6.2 | 10. US states and Canadian provinces | Admin-1 layer. Toggle: US states (50, DC optional setting) or Canadian provinces and territories (13). Variants: name-all with timer, tap the map, multiple choice. Aliases for postal abbreviations (via the layer's own `postal` property, D43). Name all has no map (D46). | A |

AC as Phase 5.

Status: 6.1, 6.2 (all three variants) and the gate done in session 9 (2026-10-15). PR open. Device checks in the session 9 log.

### Phase 7 – New modes C

| ID | Mode | Spec | Group |
|---|---|---|---|
| 7.1 | 8. Currencies | From mledoze currencies. Question: currency name, symbol and code; answer the country. Shared currencies (euro, US dollar, CFA francs, East Caribbean dollar, etc.) only used as "which of these uses…" with exactly one valid option. Flags on options. One-line fact after answering (facts stored in data file with source). | A |
| 7.2 | 12. Rivers, mountains and lakes | Tabs: Rivers, Mountains, Lakes. Curated lists in `data/physical_features.json` (~40 rivers, ~30 ranges or peaks, ~30 lakes), each verified against Wikidata with the QID stored. Highlight the feature on a regional map; type or choose. | A |
| 7.3 | 15. Flag speed round | 60 seconds. Lookalike pairs and sets (e.g. Chad/Romania, Indonesia/Monaco, Ireland/Côte d'Ivoire, Australia/New Zealand, Netherlands/Luxembourg, Senegal/Mali, Slovenia/Slovakia, Colombia/Ecuador/Venezuela, Norway/Iceland), curated in a data file. Big tap targets, combo counter, explanation after each answer in review. | A |

AC as Phase 5. `licence-auditor` checks every new data file.

Status: 7.1, 7.2, 7.3, screenshots and the gate done in session 10 (2026-10-16). PR open. Device checks in the session 10 log.

### Phase 8 – Engagement

| ID | Task | AC | Group |
|---|---|---|---|
| 8.1 | Daily challenge: 10 questions across modes, seeded from the device's local calendar date so everyone gets the same set on the same date. Offline. One attempt per day; results saved. | Same date gives the same set on two devices; unit-tested generator | A |
| 8.2 | Streaks: consecutive days with a completed daily challenge. One streak freeze earned per 7 days played, max 2 held, never purchasable. | Edge cases tested: time zone change, missed day with/without freeze | after 8.1 |
| 8.3 | Original share card: text version and image version (Android share sheet). Own layout and glyphs; no square tile grids, no green/yellow/grey scheme, no other game's naming. Includes date, score, time, app link. | `licence-auditor` signs off on L9 | after 8.1 |
| 8.4 | Hints economy: hints cost points, never money. Per-mode hint types from the registry. Costs and caps in one config. Daily challenge allows limited hints. | Scores reflect hint use; config unit-tested | B |
| 8.5 | Game-feel: combo multiplier for fast consecutive correct answers, confetti on perfect score, short sound effects with a toggle (default respects silent mode), haptics from 3.3 | Reduced-motion and sound settings respected; sounds are original or CC0 and logged in SOURCES.md | B |
| 8.6 | Home "Today's challenge" card wired up, streak shown in header | | after 8.1, 8.2 |

Status: 8.1, 8.4, 8.5 done in session 11 (2026-10-16, `7524c43`). 8.2, 8.3 (`406da22`), 8.6 (`9e9c654`), screenshots and the gate done in session 12 (2026-10-16). PR open.

### Phase 9 – Release readiness

| ID | Task | Group |
|---|---|---|
| 9.1 | Remove feature flags for modes Dav has approved; keep the flag system | A |
| 9.2 | Full `licence-auditor` and `code-reviewer` pass over the whole diff since Phase 0 | A |
| 9.3 | Update store listing text (generic mechanic descriptions, no competitor names), screenshots via store asset script, what's new notes | A |
| 9.4 | Performance pass: cold start, map rendering, APK/AAB size against budget | A |
| 9.5 | Final manual test checklist for Dav covering every mode at every tier, light/dark, 200% font, TalkBack, offline | last |

Status: 9.1–9.5 done in session 13 (2026-10-17), prepare only: nothing uploaded, merged or tagged. 9.1 turned every flag on for the RC (D63) instead of removing approved ones, because Dav approves modes after testing. PR open.

---

## 7. Decisions log

| ID | Decision | Status |
|---|---|---|
| D1 | Target audience: mixed audience including children (Dav, 2026-10-07). | Decided |
| D2 | Licence for app code: All rights reserved (Dav, 2026-10-07) | Decided |
| D3 | Map assets: compact binary, one file per layer (lon/lat quantised to int16 per layer bounds, delta-encoded, simplified per level), read by a Kotlin reader; all map layers together at most 1.5 MB raw, enforced by the pipeline (Dav, 2026-10-09). | Decided |
| D4 | Daily challenge seed: device local date | Decided |
| D5 | Feature flags: enum `FeatureFlag` + DataStore "feature_flags". Overrides are honoured only in debug builds (`BuildConfig.DEBUG`); release always uses each flag's default. Debug menu opens by tapping the Settings title 7 times (debug builds only). Phase 9 flips approved defaults to on (D63: all on for the 3.0.0 RC). | Decided (Phase 0) |
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
| D26 | Toolchain (3.6a): latest AGP 8.x (8.13.2) with Gradle 8.14.5 and Kotlin 2.2.21; Kotlin 2.3+ waits for a separate AGP 9 / Gradle 9.1+ task because Hilt 2.59+ requires AGP 9 (lead, Dav approved the plan, 2026-10-09). | Decided |
| D27 | Phone quiz: the answer field, recent answers and Give up/Submit sit pinned at the bottom above the keyboard, below the country list (Dav, 2026-10-09). | Decided |
| D28 | Wide layouts (3.7): no adaptive library; breakpoints Compact < 600 dp, Medium < 840, Expanded; navigation rail from 600 dp; two panes on Expanded, Medium landscape and landscape phones (quiz: controls start, list end – Dav, 2026-10-09; Results: summary | actions; Play: cards | browse); other screens capped at 840 dp. | Decided |
| D29 | Large text (3.6b): buttons that don't fit side by side stack rather than clip, also at 100% on 360 dp phones (Results Share/Challenge, Stats); title scrolls with the content below 560 dp of space ÷ font scale; when it scrolls, TalkBack reads it in screen order (after the pinned status) (lead, 2026-10-09). | Decided |
| D30 | Stacked phases (Dav, 2026-10-09): Dav tests on a device and merges only at the end. Until then each phase branches from the previous phase's branch (not `main`), and its PR targets that branch; "Done – PR open" on the previous phase does not block the next one. Device checklists stay in each session log. At the end, merge the PRs in order (#6 first), retargeting each to `main` after the one before it merges. | Decided |
| D31 | Map layers use only Natural Earth geometry and attributes; mledoze contributes nothing but the list of 197 cca3 ids used to key and check features, which is not a substantial extract, so the `.bin` files are not an ODbL derived database. If mledoze names, regions or borders are ever written into map files, revisit this (licence audit, 2026-10-09). | Decided |
| D32 | Disputed areas follow Natural Earth's default de facto view (Crimea in Russia, Golan in Israel, Western Sahara separate and not playable, Kashmir split along the lines of control); Christmas/Cocos and Ashmore & Cartier stay separate non-playable land (lead, 2026-10-09; Dav to confirm at the end). | Decided (lead) |
| D33 | Faster runs (Dav, 2026-10-13): implementer, test-engineer, geodata-engineer and licence-auditor run on Sonnet (`model: sonnet` in `.claude/agents/`); code-reviewer and the lead stay on Opus, and the lead may pick Opus per task for tricky algorithms. A fresh agent per task (no long-lived resumed agents). Up to 2–3 agents in parallel on disjoint files, but only one Gradle build at a time on this machine (agents take a lock: `mkdir` of a shared lock directory, remove it after the build). CI runs on every push to `upgrade/**`, so GitHub does the full suite, Roborazzi verify and lint; locally per task stays D19, the local gate drops `clean`, and `assembleRelease` runs once per phase, last and alone. Gradle build cache and configuration cache are on (`gradle.properties`). | Decided |
| D34 | Country silhouettes round = 15 random countries from the chosen filter (fewer if the filter is smaller; a practice set is exactly its codes); 3 lives in every tier can end it early (Dav, 2026-10-13). | Decided |
| D35 | New modes (Phase 5 on) are recorded in history and Stats with mastery stars and have Share text without a link, but unlock no achievements, submit no Play Games scores (the Overall total counts classic modes only) and have no challenge links, until a later phase decides (Dav, 2026-10-13). | Decided |
| D36 | Tap the map: a wrong tap names the tapped country briefly and is a wrong guess; Normal: 3 wrong taps on one prompt reveal it and count it missed; Hard: 3 wrong taps in total end the round. Taps on the sea, non-members, already-found or already-missed countries, and taps while an answer is being revealed, are ignored (Dav, 2026-10-13; ignore rules lead). | Decided |
| D37 | New modes get their own screen and ViewModel; no Room resume save (process death via `SavedStateHandle`), and finishing one leaves a classic resume save alone; hints cost placeholder points from `domain/mode/HintCosts.kt` (any hint also drops the perfect bonus) until Phase 8; Easy is 4 choices (silhouette → names; Tap the map → highlighted country → names). Tap the map regions are `QuizCategory.MapRegion(presetId)` (`map_region`), World or one continent (lead, 2026-10-13). | Decided (lead) |
| D38 | Country silhouettes with TalkBack: the outline stays decorative (describing it would give the answer away); the screen-reader path is the hints (region, first letter, and Show neighbours, which also lists the neighbours' names as text for everyone) plus Easy's four names (lead, 2026-10-14). | Decided (lead) |
| D39 | Tap the map at Easy: no map/list toggle and no list (a list would mark the highlighted answer for TalkBack); a text clue "Highlighted country is in <subregion>" sits above the four names for everyone; Hint and Skip are hidden at Easy. Back during a round asks before giving up (lead, 2026-10-14). | Decided (lead) |
| D40 | Tap the map "Name all" (variant `name_all`): type the region's country names and each one fills in on the map (blue + tick); same validation as the classic Countries quiz (typos allowed at Normal, Hard = no typos, 3 strikes, timer always shown); Normal and Hard only, because Easy's multiple choice is Find's job; Give up confirms and reveals the rest as missed (lead, 2026-10-15). | Decided (lead) |
| D41 | Country silhouettes at Easy has no hints (the four names are the help), matching Tap the map's Easy (D39). Setup difficulty summaries are per mode where the tiers differ: Tap the map Find describes taps, and Silhouettes Hard is "exact spelling + timer" because lives apply at every tier (lead, 2026-10-15, from the screenshot review). | Decided (lead) |
| D42 | Border hop border graph (`data/borders.json`): of the 5 mledoze/Natural Earth disagreements found while building the graph, Botswana–Zambia and Spain–Morocco are kept (real land borders below 50 m map resolution), India–Sri Lanka is removed (no land border across the Palk Strait), and France–Brazil and France–Suriname are added (via French Guiana, which plays as part of the app's France feature). The in-app "land border" rule is worded so overseas territories count, keeping all four added/kept edges reachable in puzzles (Dav, 2026-10-15, confirming `upgrade/data-prep`'s `OVERRIDES` table in `tools/data/build_borders.py`). | Decided |
| D43 | US states and Canadian provinces gets its own `GameMode`, `QuestionGenerator`, answer validator and screens/ViewModel (D37's pattern, as Border hop also used), not literal reuse of Tap the map's classes: `TapMapQuestionGenerator`/`ValidateAnswerUseCase`/`RegionPreset.includes` are typed to `Country` throughout, with no generic "nameable region" abstraction to parameterise. It does reuse what's actually generic: `GeoLayer`/`HitTester`/`MapScene` for rendering, and the bundled `admin1_50m.bin` directly (it already carries each feature's name and postal abbreviation, so no new alias data file is needed). Tap the map's screens/variants are the UI and architecture reference, not shared code (lead, revised 2026-10-15 after D43's original "reuse the screens" assumption didn't hold up; Dav confirmed). | Decided |
| D44 | Border hop (6.1) is a fixed start/target path puzzle: each round gives a start and target country chosen by difficulty tier via shortest-path distance in the border graph (2–6 hops); the player taps neighbouring countries to build a path, "Undo last hop" backtracks, and scoring is hops used vs the optimal path length. One hint, `HintType.SHOW_VALID_HOP`, reveals one valid next country at a placeholder `HintCosts` cost, same as other Phase 5/6 hints (Dav, 2026-10-15). | Decided |
| D45 | Border hop's game screen shows route chips and a tappable list of the current country's neighbours, not a map (6.1's original spec said "the map"): `MapScene`/`HitTester` are built for whole-layer rendering with region presets, not a lightweight "one country + its immediate neighbours" view, and a list is simpler and fully accessible without extending the map engine or adding a new screenshot-test surface for a one-off view (Dav, 2026-10-15). | Decided |
| D46 | US/CA regions' Name all variant has no map, just a "remaining names" list that drops each one as it's typed (task 6.2's spec said "fills in on the map", matching Tap the map's Name all): the list already gives the same progress signal a map fill would, is naturally accessible text with no list/map toggle needed, and avoids re-rendering a 50-feature choropleth on every keystroke for no extra information. Same reasoning as D45 (lead, screenshot review, 2026-10-15). | Decided (lead) |
| D8 | Ads under D1: every user is treated as child-directed and under the age of consent (TFCD + TFUA, max ad content rating G, so no personalised ads); no age screen, so no age data is collected; `AD_ID` permission removed. Google UMP is still integrated for regional consent and the Privacy options entry. Simplest Families-compliant setup; expect lower ad revenue. | Decided (Phase 1 lead) |
| D47 | `physical_features.json` is public domain + CC0, not ODbL: it extends D31's reasoning, since the only mledoze input is the 197 cca3 ids used to key features (Dav, 2026-10-13, confirmed in `docs/DATA_PREP_NOTES.md`). | Decided |
| D48 | Currency answer edge cases (7.1): Panama's typed answer is balboa (USD circulates too but is not asked); Zimbabwe's typed answer is USD only (its current currency isn't in this mledoze copy; the other 8 currencies in its basket are recorded but never asked); Palestine has no primary currency, so it appears only in "which of these uses…" questions, never as a typed target; Sierra Leone shows SLL, since mledoze has no SLE entry (adding one needs a manual entry and its own decision, not done in Phase 7) (Dav, 2026-10-13, confirmed in `docs/DATA_PREP_NOTES.md`). | Decided |
| D49 | Physical-feature tiers (7.2): tier 1 = Natural Earth scalerank 1, plus Mont Blanc, the Pyrenees and the Carpathians bumped up by judgement; tier 2 = well known; tier 3 = less prominent. "Lake Eyre" is shown for the Lake Eyre North Wikidata item, since no whole-lake item exists. 41 rivers (one more than the spec's "~40") is fine as is (Dav, 2026-10-13, confirmed in `docs/DATA_PREP_NOTES.md`). | Decided |
| D50 | Currencies (7.1) answer mechanism: a currency with one owning country is typed at Normal/Hard and a standard 4-choice question at Easy, like every other mode. A shared currency (EUR, USD, XOF, XAF, XCD, plus the 8 other shared codes) is never typed at any tier — it is always "Which of these uses the X?" with 4 options and exactly one true member, since a shared currency otherwise has 6–26 valid typed answers, breaking the spec's "exactly one valid option" rule. New `ChoicePrompt.CurrencyOf(currencyLabel)` (lead, 2026-10-16). | Decided (lead) |
| D51 | Rivers, mountains and lakes (7.2) answer mechanism: type the name at Normal/Hard, 4-choice at Easy (new `ChoicePrompt.FeatureOf(featureName)`, clearer than reusing `InSet`'s "Which of these fits…" template for a feature name). The map highlights the one feature, framed to its stored bbox, on the matching regional layer (`rivers_50m`/`lakes_50m`/`regions_50m`/`geo_points_50m`, bundled since Phase 4 but not yet exercised by any screen), reusing the generic `GeoLayer`/`HitTester`/`MapScene` engine rather than a list-only fallback (D43's precedent). Rivers/Mountains/Lakes are a category filter on one mode, not three separate modes (lead, 2026-10-16). | Decided (lead) |
| D52 | Flag speed round (7.3) shape, the one piece with no existing precedent (every other mode is a fixed-size quiz with a `QuizState`; this is a hard 60-second clock with an open-ended item count): one continuous timed round reusing the existing `ChoicePrompt.FlagOf` shape for every question (flag shown large, name options as buttons), with distractors drawn from the same lookalike set as the target rather than generic same-region distractors. Difficulty tier picks which lookalike-set tiers are in the draw pool: **Easy = tier 1 only, Normal = tiers 1–2, Hard = tiers 1–3** (corrected 2026-10-16, code review: the lead's original text had this backwards — tier 1 in `data/flag_lookalikes.json` is the `clear`/easiest-to-tell-apart pairs, e.g. Italy/Mexico, Belgium/Germany; tier 3 is `subtle`, the hardest, e.g. Chad/Romania, Netherlands/Luxembourg — so Easy must draw tier 1, not tier 3). Sets marked `in_app: "near_identical"` (currently only Indonesia/Monaco) are never asked at any tier, per the data file's own note, and surface only as a line in the post-round review. The round packs into a plain `QuizState`/`QuizResult` via a documented field convention, the same way `BorderHopScoring` does (D37 precedent): `quiz.countries` = every item asked (repeats allowed), `answeredCountries` = correct ones, `incorrectGuesses` = wrong count, `timeElapsedSeconds` = 60 (fixed; Give up ends it early with whatever elapsed). New `FlagSpeedScoring`: base score from correct/asked percentage, plus a combo bonus for runs of consecutive correct answers that resets on any wrong answer, same spirit as D44's par-based bonus (lead, 2026-10-16; tier direction corrected same day after code review). | Decided (lead) |
| D53 | Daily challenge (8.1) shape: its own `GameMode`/screen/ViewModel (D37 pattern), 10 questions deterministically seeded from the device's local date (`DailyChallengeDate.today()`, hashed into a `Random` seed, D4). v1 draws only 4 question kinds that reuse existing single-item UI with no per-question map/outline render: typed country name, typed capital, flag-to-country choice, capital choice (fixed distribution 3/3/2/2) — Tap the map, Border hop, US/CA regions, Country silhouettes and Rivers/mountains/lakes are excluded from the mix for now, a scope cut in the same spirit as session 10's single-pane cuts. Packs into a plain `QuizState`/`QuizResult` the same way Border hop/Flag speed round do (D37/D52 precedent). "One attempt per day" needs no new Room table: the round is recorded through the normal `CompleteQuizUseCase`/`QuizHistoryRepository` path under a new `QuizCategory.DailyChallenge(date)` case keyed by the ISO date string, and a new `QuizHistoryDao.hasCompletedCategory` query checks it; today's question set is regenerated from the date each time rather than persisted, since the generator is a pure function of (date, the full country list) — this is only safe because `CountryDao.getAllCountries()` has a stable `ORDER BY` (confirmed in code review). No achievements/leaderboards (new-mode rule, D35); always `Difficulty.NORMAL`, no hints yet (lead, 2026-10-16). | Decided |
| D54 | Daily challenge's "Play again"/double-completion safety is a soft, UI-level gate only (the Setup screen hides Start once completed; nothing stops `CompleteQuizUseCase` recording a second round for the same date if the game screen were ever reached a second time by another path) — acceptable for v1 since the only entry point is Setup, but flagged in code review as worth a real guard once 8.6's Home card offers a more direct entry, since 8.2 (streaks) builds directly on one-completion-per-day being true (lead, 2026-10-16, confirmed by code review; Dav to confirm or ask for a harder guard). | Decided |
| D55 | Hints economy (8.4): `HintCosts.maxHintsPerRound(difficulty)` caps hints per round at Easy 0 / Normal 2 / Hard 3, replacing the D37 placeholder's unlimited hints; per-hint point costs are unchanged. This is a real behaviour change for Border hop specifically: its Easy tier had no hint cap at all before (Silhouettes/Tap the map already showed no hints at Easy per D39/D41), so Border hop Easy goes from unlimited hints to none (lead, 2026-10-16, confirmed in code review; Dav to confirm or reverse for Border hop Easy specifically). | Decided |
| D56 | Game-feel (8.5) combo multiplier is feel-only, never a score effect: a new `ComboTracker` (pure, clock-driven, 5-second fast-answer window, visible from a streak of 2) drives a UI counter in the classic quiz header and nothing else; `CalculateScoreUseCase` is untouched. Chosen to avoid touching the shared, heavily-tested classic scoring path for a cosmetic feature (lead, 2026-10-16). | Decided |
| D57 | Game-feel (8.5) sound effects are originally authored, not sourced externally: three short tones synthesised by a checked-in script (`tools/sound/generate_sound_effects.py`, stdlib only) rather than a downloaded CC0 pack, since no audio source is in section 5's approved-sources table and adding one would need its own licensing decision (same bar as D7/L4's flag-source question). Logged in `data/SOURCES.md` as GeoQuiz-original. Confetti is an original Compose Canvas effect, not a new dependency (lead, 2026-10-16). | Decided |
| D58 | Game-feel (8.5) sound respects two independent gates: the new Settings toggle (`sound_effects`, default on, same shape as `vibration`) and the system ringer mode (`AudioManager.RINGER_MODE_NORMAL` only) — silent/DND always wins even with the setting on, same spirit as vibration already respecting the system touch-feedback setting (lead, 2026-10-16). | Decided |
| D59 | Streaks (8.2): derived, never stored — a pure function of the distinct daily-challenge dates in `quiz_history` and today's local date. The streak counts days played in a run of consecutive local dates; not having played yet today never breaks it. One freeze is earned on every 7th day played in the current run, at most 2 held (one earned at the cap is lost), never purchasable; each missed day uses one automatically, a frozen day keeps the run but doesn't add to it, and a gap with too few freezes breaks the run. Evaluation anchors at max(today, latest played date), so flying west or moving the clock back never breaks a streak; flying east past a date is one missed day. Today is re-read at each local midnight (lead, 2026-10-16; Dav to confirm the freeze rules). | Decided |
| D60 | Share card (8.3): text and an original 1080x1080 image card (app name, title, big score + %, date · time, link; plain stacked text on the theme background, no grids or tiles). The text share carries the full link — the signed challenge link for classic modes, else the Play Store listing (always the release id); the image prints only a short form (a challenge link's host + path, or the store URL without its scheme), since a ~250-character signed link can't be tapped or typed from a picture. The card's heading is the launcher name (`app_name`), so recipients can find the app in the store. Licence auditor signed off on L9 (session 12) (lead, 2026-10-16). | Decided |
| D61 | Home (8.6): the Today's challenge card follows the daily challenge mode's availability in the registry (the same rule as its destination's guard), opens the daily challenge Setup, and replaces the mode's tile in the New modes grid; the streak shows as a header chip only while a streak is running. D24's placeholder is gone (lead, 2026-10-16). | Decided |
| D62 | D55 follow-up: Border hop keeps its hint cap of 0 at Easy, but hides the "Show a valid hop" button at Easy instead of showing it disabled, matching Silhouettes and Tap the map (D39/D41). At Normal/Hard the button still shows disabled once the cap is reached (Dav, 2026-10-17). | Decided |
| D63 | Release candidate flags (9.1): Dav approves modes only after testing, so for the RC every flag in `FeatureFlag` is `defaultEnabled = true` (all 7 new modes and the daily challenge with streaks). The flag system and debug overrides stay, so any flag can be turned back off by setting its default to `false` before shipping (Dav, 2026-10-17). | Decided |
| D64 | Version for the Phase 0–9 release: versionCode 18, versionName 3.0.0 (Dav, 2026-10-17). | Decided |
| D65 | Store phone screenshots come from an on-demand Roborazzi test (`StoreScreenshotTest`, 1080x1920 to stay within Play's 2:1 limit, demo fixtures, not part of `verifyRoborazziDebug`) writing to `store_assets/screenshots/`; the icon, feature graphic and achievement icons come from the Lato Python scripts (Dav, 2026-10-17). | Decided |
| D66 | Achievement display text uses Australian spelling: "World Traveller", "Colour Expert" ("…flag colour quizzes") and Rainbow's "…6 different colours". Enum names, ids (`world_traveler`, `color_expert`, `rainbow`) and Play Games ids are unchanged. Play Console text is edited by hand, never re-imported, because a bulk import would create new achievements (Dav, 2026-10-20). | Decided |
| D67 | Play hub (phase 10, Dav 2026-10-20): Recommended next is removed (supersedes D25 and that part of D22) and so is the New modes grid. Each classic page lists its own modes under "More ways to play", after its category tiles. Countries has Country silhouettes, Rivers/mountains/lakes, Border hop, Currencies, Tap the map, and States and territories. Flags has Flag speed round. Capitals has none. Placement is a fixed map in `PlayViewModel`, still flag-gated. The All countries/All capitals card is a compact one-row card. | Decided |
| D68 | Per-category colours are back (Dav): 10 hues (Blue, Indigo, Teal, Cyan, Green, Amber, Orange, Pink, Purple, Brown) taken from the pre-3.4 scheme, now as `GeoColors.categoryHues` theme tokens with light and dark pairs at ≥ 4.5:1 (`GeoColorsContrastTest`). The group → hue mapping per mode is in `ui/play/CategoryHues.kt`, following the old per-mode mapping except that no page uses Amber (Countries Name length is Orange, Capitals Subregions is Blue), so green, yellow and grey tiles never sit together (L9, licence audit 2026-10-21; `CategoryHuesTest` checks 2- and 3-column adjacency). | Decided |
| D69 | "Surprise me" on the Countries, Capitals and Flags pages starts a uniformly random category of that page's classic mode, never "All", at Normal (Dav). | Decided |
| D70 | Mastery stars are removed: no star rows, no `MasteryStars`, no mastery DAO query and no star colour token (Dav: confusing, add nothing). Supersedes D17. D16, D21 and D35's mentions of stars no longer apply. Best score and the completed tick stay. | Decided |
| D71 | Currencies show the symbol only, never the name or ISO code, before the answer (Dav). ISO codes start with the country's cca2 for 142 of 146 currencies. 19 currencies whose symbol encodes the country are excluded: the Arabic abbreviations, RM, MK, ZK, FC, T$ and UM (`CurrencySymbolRules.EXCLUDED_CODES`). A symbol used by more than one currency is always a 4-choice question with exactly one option that uses it. Only unique, single-owner symbols are typed at Normal/Hard. Each symbol is asked at most once per round, except in practice sets, and a new round asks its currencies in a random order (Dav, 2026-10-22: the pool's alphabetical order meant every round opened on the same currency; a restored round keeps its saved order). Result: 57 typed, 70 choice-only, 73 questions in an All countries round. `CurrencySymbolAuditTest` checks every question against the real data. Extends D50. | Decided |
| D72 | Difficulty (Dav): every selector starts at Normal and the choice is never persisted. The Settings "Default difficulty" option is removed. One-tap starts (pinned, All, Surprise me) use Normal. Continue/resume keeps the saved quiz's tier. Challenges are unchanged. | Decided |
| D73 | Border hop rework (Dav, replaces the D44 interaction and D45): a blank world map with the start marked, the target outlined and the route filled in as it grows; the camera auto-frames it. You type a country that borders the end of your route. A recognised non-neighbour flashes orange ("X doesn't border Y") and counts as a wrong guess. Normal has autocomplete and accepts one typo. Hard has no autocomplete, needs exact aliases and allows 3 strikes. Easy offers 4 options, exactly one of them a neighbour on a shortest route. Score = par/(hops + wrong guesses) × par, with ×1.2 only for a perfect route with no wrong guesses. Undo, the D55/D62 hint rules and the setup feasibility filter are kept. | Decided |
| D74 | "US states and Canadian provinces" is renamed "States and territories" and adds Australia's 8 states and territories (Dav). `admin1_50m.bin` now includes AU. Jervis Bay is dropped. AU postal codes are overridden to NSW/VIC/QLD/WA/SA/TAS/ACT/NT. Australia has its own LAEA projection (134, −28). The persisted category labels for US/CA are unchanged; Australia's is "Australian states and territories". The shared quantisation grid now spans AU, so US/CA vertices are stored at about 0.005° instead of 0.002°, which is still below the 50m simplification tolerance. | Decided |
| D75 | What's new (Dav): a one-off full-screen dialog over the app for players updating to 3.0.0. It lists the enabled new modes plus the changes. It is dismissed with Got it, close or back, and records `whats_new_seen_version` = `WHATS_NEW_VERSION` (18). Fresh installs, detected when the install time equals the update time and there is no quiz history, never see it. Reset all data keeps the key (like D10). Debug menu has "Show What's new again". A later release bumps the constant. | Decided |
| D76 | Country silhouettes leave out 9 countries whose outline isn't recognisable at 50m: land covers under 1% of the box, or the largest part has ≤ 4 vertices. They are KIR, FSM, MHL, PLW, TON, MDV, TUV, VAT and MCO (`SilhouetteLegibility`, checked against a 197-country contact sheet, Dav reported Tuvalu). Rivers, mountains and lakes open framed on the feature but can zoom out to a context area of at least 3× the frame, about 2,500–4,000 km. | Decided |
| D77 | `usa.svg` draws its 50 stars with an SVG `<marker>`, which AndroidSVG ignores, so the flag showed with no stars (Dav). `tools/flags/fetch_flags.py` rewrites them as the same star shapes in one explicit path. No other bundled flag uses `<marker>`. | Decided |

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

### Session 6 – 2026-10-09 – Phase 3 (part 3)
- Done: 3.6a toolchain bump (`8130502`: Gradle 8.14.5, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Hilt 2.57.2, Room 2.7.2, Roborazzi 1.76.0), 3.6b 200% font on every screen (`975276e`), 3.7 ergonomics (`5223904`: adjustResize + pinned input, navigation rail, two-pane quiz/Results/Play, readable width), 3.8 dark mode (`951263b`: tokens only, tier and flag-border tokens, neutral dark scheme, map tokens contrast-tested, dark window background), gate (`4499bd5`: 32 goldens re-recorded once and checked by eye; Easy title and Results large-text fixes found in that review). Session 4 and 5 carry-overs closed: the 3.6 list (Results Share/Challenge, Answer review title, selector "Normal", Easy panel and icons, bottom bar labels, Play cards and category rows), goldens renamed `play_*` with Play cards and Results achievements shots, Easy and category list shots, legacy `Color.kt` colours removed, `AppNavigation` back-stack test (real NavHost, stand-in screens; `navigation-testing` not added).
- Not done / carried over:
  - Toolchain follow-ups (Dav: note only, don't fix now): Kotlin 2.2 KT-73255 warnings on 28 constructor-parameter qualifiers (`@param:` or `-Xannotation-default-target=param-property`); Room 2.7 deprecated no-argument `fallbackToDestructiveMigration()` in `J/di/DatabaseModule.kt` (choose `dropAllTables = true` deliberately, check `StaticDatabaseAssetTest`); AboutLibraries 11.6.3 plugin breaks on Gradle 10; Kotlin 2.3+ needs AGP 9 + Gradle 9.1+ (own task, D26). The Roborazzi opt-in is done (`ScreenshotCapture.kt`).
  - Strings and spelling (Phase 9 unless Dav wants it sooner): `CategoryGroup`/`FlagCategoryGroup`/`QuizCategory.displayName` US spelling ("By Color"); hard-coded English in Results, Answer review, Stats, Achievements ("n / m unlocked", tier label from the enum name) and Challenges; `String.format` without a locale (lint DefaultLocale x4); PluralsCandidate x7 in strings.xml.
  - Lint: `ModifierParameter` on `AnswerInput` and `CountryList`; `ObsoleteSdkInt` for `mipmap-anydpi-v26`. Lint 0 errors, 64 warnings (the rest are version and launcher-icon notices).
  - UI: the Give up button is an orange cross like a wrong answer (not new in Phase 3; consider a neutral icon). In Easy at 200% the feedback line can sit below the fold of the scrolling options panel (reachable by scrolling, announced as a live region). On very short landscape phones at large text with the keyboard open, Give up/Submit can sit behind the keyboard (the keyboard's Done key submits).
  - Tests: no Easy-tier wide-layout screenshot; no golden for the Easy flag prompt (async image load); nothing renders the real `AppNavigation` with Hilt.
  - From session 5, still open: Play group tiles count two-colour combos by overlap (existing behaviour); Phase 5 must wire `onOpenMode` and move `PinnedCategory.validOrNull`, `ChallengeLinkParser` and `QuizCategory.isOfferedIn` to registry ids.
- Decisions: D26 (toolchain), D27 (pinned input, Dav), D28 (wide layouts; pane order Dav), D29 (large-text stacking and title scroll).
- Owner actions added: none.
- Verification: gate `./gradlew clean assembleDebug testDebugUnitTest lintDebug` passed (682 tests, 0 failures; lint 0 errors). After the gate fixes, `recordRoborazziDebug` once more and `verifyRoborazziDebug` passed (686 tests, 0 failures). `assembleRelease` run separately as the last step (first attempt stopped by the machine for low memory; retried alone): passed, R8-minified, signed, 4.7 MB APK. Reviews: code-reviewer on 3.6b and 3.7 (two blocking 3.7 findings fixed: TalkBack order and scroll state across layouts), licence-auditor on the toolchain bump (no required items; test-tool and Gradle wrapper rows added to `THIRD_PARTY_NOTICES.md`), lead review of 3.8 and of every golden.
- Manual test checklist for Phase 3 (debug build "GeoQuiz Debug" on a phone; a tablet or emulator for 13–15):
  1. Regression: play a Countries, a Capitals and a Flags quiz end to end at Normal, including a letter/word-pattern category. Typos ("Austrailia") are still accepted in Normal; Hard says "Close – check the spelling" with no strike.
  2. Difficulty: switch Easy / Normal / Hard on a category list. Easy: 4 options; a wrong pick shows the correct option blue with a tick and yours orange with a cross, then the next question about a second later; every item is asked once. Hard: the timer is always on and can't be hidden; 3 wrong guesses end the quiz. Settings → Default difficulty changes the starting tier.
  3. Mastery stars: a finished quiz ≥ 50% gives 1 star; ≥ 80% at Normal/Hard gives 2; 100% at Hard gives 3. Stars and "Best" update on the category row without leaving the screen.
  4. Feedback: a correct answer gives a short tick vibration and a pulse; a wrong one a different vibration and a shake. Settings → Vibration off (or system touch feedback off) stops vibration; system "Remove animations" stops pulse and shake. The last three correct answers show as chips above the field.
  5. Play hub: the Play, Stats, Achievements and Settings tabs keep their state when switching. The quiz type switch (Countries/Capitals/Flags) shows that mode's groups; group → category is 2 taps. Continue resumes the saved quiz in its own mode and can be dismissed; Recommended next changes after playing; Home from Results lands on Play in that quiz's mode.
  6. Pins: pin a category from its row; it appears under Pinned on Play and starts in one tap; unpinning removes it; Settings → Reset all data clears pins.
  7. Results: a quiz that unlocks an achievement shows the "New achievements" card, also after Developer options → "Don't keep activities" and returning. Finish with misses at Easy and at Hard → "Practise the ones you missed (N)" starts a quiz with exactly those items at the same tier; it is not in Stats, has no Share/Challenge, shows the "not counted" note, and the Resume card is untouched. No practise button after a perfect quiz.
  8. Debug menu (tap the Settings title 7 times) → Daily challenge flag on → the "Today's challenge" placeholder card appears on Play.
  9. Keyboard: in a typed quiz the field and Give up/Submit sit just above the keyboard, with no extra gap above the navigation bar, and the last list rows can be scrolled into view above the field. Rotate mid-quiz: answers, typed text, time and list scroll position are kept.
  10. 200% text (Settings → Display → largest font size, plus largest display size): Play cards and tiles (one column), category list (radio-button difficulty, row actions below the name), quiz (title scrolls with the list; in Easy at the top of the options), Results (full-width stacked buttons), Answer review (title wraps), Stats, Achievements, Settings, Credits and Challenges: nothing clipped, everything reachable; the bottom bar shows icons only and TalkBack still reads the tab names.
  11. Dark mode: every screen above in dark theme; flags have a visible thin border (Japan in light, a dark-edged flag in dark); achievement tier cards are readable; no white flash when launching in dark mode.
  12. TalkBack in a typed quiz: after the title and count, focus reaches the field and Submit before the country rows; the count and feedback are announced; Easy options read as buttons with their result.
  13. Tablet or landscape (≥ 600 dp wide): navigation rail on the start side instead of the bottom bar; the quiz shows controls on the left and the list on the right; Results shows the summary beside the buttons; on a large tablet Play shows the cards beside the tiles (up to 3 columns). A 600 dp-wide tablet in portrait keeps one pane.
  14. Landscape phone: the quiz is in two panes and the field is reachable with the keyboard open; on a punch-hole phone rotated with the camera on the left, the rail and quiz content are not under the camera.
  15. Multi-window / resizing: switch between one and two panes mid-quiz; nothing is lost.
- Next session starts at: Phase 4, task 4.1, after the Phase 3 PR is merged (decide D3, the asset format and size budget, before 4.2).

### Session 7 – 2026-10-09 to 2026-10-13 – Phase 4
- Done: D30 (stacked branches) and D3 (compact binary, 1.5 MB); 4.1 geodata pipeline (`3bea617`), 4.2 reader, projections and `MapCanvas` (`5bc323e`), 4.3 hit testing and tap zones (`4a0794d`), 4.4 region presets (`456a9ec`), D33 speed-ups (`ea8dfff`), 4.5 outlines (`047292a`), 4.6 accessibility, list alternative and Natural Earth / UN M49 credits (`b888ee1`), review fixes (`91f1a6d`), gate screenshots and visual fixes (`6bead8b`).
- Not done / carried over:
  - Device checks only (see checklist): 60 fps pan/zoom (plan AC; JVM timings: decode all layers 16–109 ms, 50m paths 35–84 ms); API 26/27 "path too large for texture" at high zoom; how busy the world view looks on a phone (portrait world is small: Phase 5 Tap the map should start on a continent or zoomed); 50m switch at 160 dp per unit (small European countries).
  - `MapPathCache.clear()` is not wired to `onTrimMemory` (needs `GeographyQuizApplication`).
  - Rings straddling the 100° LAEA cut are kept whole (far-side members such as Russia in some presets draw distorted near the rim); the canvas fade-in starts from a `SideEffect` (possible one-frame flash).
  - Europe frame includes Svalbard and Iceland, so on a portrait phone Europe sits low with Arctic above; consider excluding Svalbard from framing in Phase 5.
  - Outline neighbours come from geometry (bbox overlap), not mledoze `borders` (not in the `Country` model yet); revisit with Border hop (Phase 6).
  - `MapPreviewViewModel.onListSelect` builds `MapHit` with `isMember = true` (debug only).
  - Phase 3 carry-overs unchanged (strings and spelling, lint warnings, Give up icon).
  - Data prep for Phases 6–7 (borders graph, currencies, physical features with Wikidata QIDs, flag lookalikes) can run in a separate chat in a git worktree on `upgrade/data-prep` (prompt given to Dav, 2026-10-13); merge it into the Phase 6/7 branches.
- Decisions: D3, D30 (Dav), D31, D32 (lead; Dav to confirm), D33 (Dav).
- Owner actions added: none new. Confirm D32 (disputed areas) at the end.
- Verification: per task D19 (compile + own tests; 4.2–4.6 map classes up to 150 tests green); the review-fix and visual-fix agents ran full `assembleDebug testDebugUnitTest` green; CI on push ran the full suite, `verifyRoborazziDebug` and lint: passed on `fba6a11` (896 tests; the first run caught a flaky outline screenshot, fixed by building outline paths before capture); 15 map goldens recorded and checked by eye; code-reviewer on 4.2 and on 4.3–4.6 (one blocking finding, outline neighbours, fixed); licence-auditor on 4.1 and on the phase (no required items). `assembleRelease` (local, last and alone): passed after making the release-key check configuration-cache safe (`app/build.gradle.kts`), 26 min, signed R8 APK 5.2 MB (was 4.7 MB; +0.5 MB of map layers).
- Manual test checklist for Phase 4 (debug build; Settings title ×7 → Debug menu → Map preview):
  1. World map draws in light and dark; pan, pinch (zooms about your fingers), double-tap zooms in and at maximum zoom resets; "Fit to Australia" animates (snaps with Remove animations on). Turn on Developer options → Profile HWUI rendering or the frame-rate overlay: panning and pinching the world stays smooth (target 60 fps) at auto, forced 110m and forced 50m.
  2. Detail: zooming into Europe switches to the detailed borders; small countries (Luxembourg, Slovenia) look right at the switch point.
  3. Taps: tap each of a few countries (Australia, Chile, Lesotho, San Marino, Vatican at its circle, Fiji, Tuvalu) and check the name shown; tapping the sea shows nothing; each tap cycles the country through found (blue + tick), wrong (orange + cross + hatch), highlighted, start (dot), end (ring + dashed) and back.
  4. World view on a phone: dashed circles only around microstates and small islands (plus any country with a state); none of the 197 is impossible to tap.
  5. Presets: each continent chip frames its countries with nothing cut off (phone portrait and landscape, and a tablet); non-member land is dimmer; no Antarctica under Asia; Fiji, Kiribati and Russia's far east are in one piece; switching presets fades the new map in.
  6. High zoom on an Android 8 (API 26/27) device or emulator: zoom fully into Russia, Canada and Brazil; land and borders keep drawing (no missing shapes).
  7. Rotate and switch dark mode on the Map preview: no long reload, camera kept.
  8. TalkBack: the map reads "Map of Europe. N of 46 countries found, M wrong."; the Zoom in / Zoom out / Show whole map actions work; with TalkBack on, the preview opens in List view; rows read the country and its state; search finds "cote d'ivoire" without accents; selecting a row behaves like a tap.
  9. 200% text: the list view rows and search field are readable, nothing clipped.
  10. Settings → Credits and licences: "Maps" (Made with Natural Earth, link works) and "Regions" (UN M49, link works).
  11. A release build doesn't show the Map preview (no debug menu).
- Next session starts at: Phase 5 (New modes A: Country silhouettes, Tap the map) on `upgrade/p5-modes-a` from `upgrade/p4-maps` (D30); merge `upgrade/data-prep` first if it exists.

### Session 8 – 2026-10-13 to 2026-10-15 – Phase 5
- Done: 5.0 mode plumbing (`76423e9`), hint costs (`8fdc2c2`), 5.1 Country silhouettes (`dbc5678`), 5.2a Tap the map Find (`bec31e9`), verification-review fixes (`3f4ff9d`), 5.2b Name all (`8f56d64`), 5.3 screenshots + the fixes they showed (`4079bfa`). Every task had an Opus code review and a fix round; 5.1/5.2a also had a verification review. No data or dependency changes, so no licence audit.
- Not done / carried over:
  - Classic quiz: the double-submit race fixed in Name all also exists in `QuizViewModel.onSubmitAnswer` (IME Done + Submit on one wrong answer can give two Hard strikes; a late result can un-complete a finished quiz). Fix the same way (one submit job, never un-complete).
  - Lint (0 errors, 72 warnings): new `ComposableNaming` (`TapMapNameAllScreen.kt` ~451) and `ModifierParameter` (~285), and `PluralsCandidate` in `strings_silhouettes.xml`/`strings_tap_map.xml` (same pattern as the older carry-over).
  - Silhouettes at 200%: the answer field's label wraps to two lines over its border (check the classic `AnswerInput` at 200% too).
  - Stats overall totals include new-mode rounds (D35 says they are recorded in Stats); Dav to confirm or ask for classic-only totals.
  - Tap the map draws no lakes or rivers (country taps only). Silhouettes Easy restores the same target but may reshuffle the four names after process death. Name all's Give up is disabled once the round is complete (Find's is not); confirm the parity.
  - New modes have no Continue card (no Room resume save, D37) and no achievements, leaderboards or challenge links (D35); a later phase decides.
  - Phase 4 carry-overs unchanged (`MapPathCache.clear()` not wired to `onTrimMemory`, LAEA rim rings, Europe frame with Svalbard, geometry-based neighbours); Phase 3 strings/spelling carry-overs unchanged.
  - Process: Sonnet agents hit the account session limit four times and stalled twice; the lead finished two fix rounds directly. A test that called `advanceUntilIdle()` with a live timer ticker hung one build for an hour; the lock helper now clears a lock whose owner process is gone. Claude Code once stopped a background Gradle run for low memory (8 GB machine): stop idle daemons (`./gradlew --stop`) and close dev servers before the gate and the release build.
- Decisions: D34–D36 (Dav), D37–D41 (lead).
- Owner actions added: none new. Confirm D32, D35 (Stats totals) and D37–D41 at the end.
- Verification: per task D19 (compile + own tests; up to 151 mode tests per run). Local gate `assembleDebug testDebugUnitTest lintDebug` (no clean): 1143 tests, 0 failures; lint 0 errors, 72 warnings. 16 new goldens recorded and every image checked by eye (that check found invisible dark text in the harness, a stray hints label at Easy and wrong difficulty summaries; all fixed and re-recorded). CI green on every push (last: `4079bfa`, full suite + `verifyRoborazziDebug` with all 63 goldens + lint). `assembleRelease` (local, last and alone, after `./gradlew --stop`): passed in 20 min, R8 APK 5.4 MB (was 5.2 MB).
- Manual test checklist for Phase 5 (debug build "GeoQuiz Debug"; Settings title ×7 → Debug menu → turn on "Mode: Country silhouettes" and "Mode: Tap the map"):
  1. Play → New modes shows both tiles only while their flags are on; each opens its setup screen; turning a flag off hides it.
  2. Silhouettes setup: difficulty (summary matches each tier) and filters All countries, By region, By subregion, Starting/Ending/Containing letter, Islands. A round has 15 silhouettes (fewer for a small filter).
  3. Silhouettes Normal: type the country; a typo ("Itlay") is accepted; another real country loses a life once and shows an orange chip with a cross; repeating it costs nothing; gibberish says "Not recognised" with no life lost. Pass shows the name and moves on. The keyboard stays open between silhouettes.
  4. Hints: Show region (shows the subregion; hidden on By subregion rounds), First letter, Show neighbours (draws the neighbours faintly and lists their names); each once per silhouette, with its point cost; the final score is lower after hints.
  5. Hard: no typo tolerance, timer always shown. Losing the third life shows the wrong answer and the real name briefly, then Results. Easy: "Which country is this?" with 4 names and no hints; a wrong pick costs a life and shows the right answer blue with a tick.
  6. Results for a silhouettes round: label "Country silhouettes", no Challenge button, Share text has no link; "Practise the ones you missed" replays exactly those countries at the same tier and is not counted; Stats shows the round; no achievement unlocks from it.
  7. Tap the map setup: World or a continent, Find or Name all (Name all hides Easy); the Find difficulty summary talks about tapping.
  8. Find, Normal (try Europe): "Find: X"; tapping it fills it blue with a tick; a wrong country shows "That's Y" briefly; three wrong taps on one country reveal it and mark it missed (orange). Taps on the sea, other continents, found or missed countries do nothing. Skip and Hint (flashes the subregion, once per country) work. Small countries are tappable via their dashed circles.
  9. Find, Hard: three wrong taps in total end the round, also if you press Skip straight after the third. Easy: the country is highlighted, "Highlighted country is in …", four names, no list, Hint or Skip; a pick moves on even if you press Home during its feedback and come back.
  10. Name all (Normal and Hard): type names; they fill in on the map with recent-answer chips; the field, Submit and Give up stay above the keyboard; "Close – check the spelling" at Hard costs no strike; pressing Done and Submit quickly on one wrong answer gives one strike.
  11. Both modes: Home mid-round pauses (timer stops) and returning shows Resume; rotating loses nothing; with Developer options → "Don't keep activities" the round comes back paused and never lets you answer the same item twice; Back mid-round asks before giving up; a paused classic quiz's Continue card survives playing a new-mode round.
  12. Practice for Tap the map zooms to the missed countries (not the whole world).
  13. TalkBack: Tap the map starts in List view; Find rows never mark the target; selecting a row acts as a tap; the next prompt is announced. Name all's list shows only named countries plus "N countries left". Silhouettes: the outline is silent; hints (including neighbour names) and Easy's names are read; feedback is announced once per answer.
  14. 200% text and landscape: Silhouettes (header scrolls, field + Submit pinned), Find (map keeps a usable height; Skip/Hint/Give up stack), Name all (field and buttons visible with the keyboard open), both setup screens; on a tablet, two panes.
  15. Dark mode on every new screen: text readable, map tokens and outline visible.
- Next session starts at: Phase 6 (New modes B) on `upgrade/p6-modes-b` from `upgrade/p5-modes-a`; merge `upgrade/data-prep` first.

### Session 9 – 2026-10-15 – Phase 6
- Done: merged `upgrade/data-prep` (`d4b5b1b`; border graph, currencies, physical features, flag lookalikes, and the licence/credits updates it already landed). 6.1 Border hop (`71ba440`), 6.2 US states and Canadian provinces, all three variants (`e830c8c`), 6.3 screenshots plus the fixes they showed (`28d1282`). Decision-log fixes along the way (`bb36534`, `45f3c1d`). Every task had an Opus code review and a fix round (two rounds for 6.2: the review found a licence-auditor-adjacent doc gap first, then the implementation review itself).
- Not done / carried over:
  - `assembleRelease` (local, last and alone, after `./gradlew --stop`) was killed partway through by the harness for low system memory, the same issue session 8 flagged (8 GB machine). Not a build failure; not retried automatically per the harness's own guidance. CI doesn't run it either (only `assembleDebug testDebugUnitTest lintDebug`), and Dav tests on a device before merging regardless (D30), so this doesn't block the PR; retry it in a later session or before the final release if wanted.
  - Phase 7's own data-prep questions (`docs/DATA_PREP_NOTES.md` items 1, 4, 5: `physical_features.json`'s licence basis, currency answer edge cases, physical-feature tier rules/Lake Eyre naming) still have no real decision-log entries - D42–D46 only covered the border graph and this phase's two modes. Assign real D-numbers when Phase 7 starts.
  - US/CA regions has no Answer Review ("View Answers" is hidden via the new `GameModeSpec.supportsAnswerReview = false`, not given a working lookup path): its codes are admin1 ids, not cca3, so `AnswerReviewViewModel`'s `CountryRepository` lookup would come back blank. A later phase could add a non-`CountryRepository` resolution path if Answer Review for this mode is wanted.
  - No Compose UI/interaction tests beyond ViewModel-level and screenshot tests for either mode (consistent with the task briefs' explicit test lists, not an oversight, but noted since Phase 5 didn't add any either).
  - Session 8 carry-overs unchanged (classic double-submit race, Silhouettes 200% label wrap, Stats overall totals including new-mode rounds, Tap the map no lakes/rivers, Name all Give-up parity); Phase 3/4 carry-overs unchanged.
  - Process: the Border hop implementer hit the weekly Sonnet rate limit before writing any code; resumed once per the brief, completed cleanly from a clean working tree (nothing to recover). A licence-auditor sub-agent's already-delivered findings were acted on before a late, unprompted resume of its own lingering background work hit the per-session rate limit - no impact, nothing from it was needed.
- Decisions: D42 (Dav, border graph overrides), D43 (lead's first version wrong - assumed literal reuse of Tap the map's classes was possible; research before briefing 6.2 found every layer below the UI shell is `Country`-typed with no generic abstraction to parameterise; revised and Dav confirmed the correction), D44 (Dav, Border hop's puzzle shape), D45 (Dav, Border hop has no map), D46 (lead, from screenshot review: US/CA Name all has no map either, same reasoning as D45).
- Owner actions added: confirm D42–D46 at the end (as usual); retry `assembleRelease` locally if wanted, or leave it for CI/the final release gate; device-test both modes (checklist below).
- Verification: local gate `assembleDebug testDebugUnitTest lintDebug` (no `clean`): 1268 tests, 0 failures; lint 0 errors, 79 warnings (all pre-existing categories from Phase 5 - `PluralsCandidate`/`ModifierParameter`/`ComposableNaming` in the two new per-mode string/screen files, `GradleDependency`/`NewerVersionAvailable` noise, nothing new in kind). 18 new goldens recorded (83 in total) and every image checked by eye: found Spain's flag rendering as a blank rectangle in some Border hop captures but not others (real `FlagImage` async-load non-determinism under Robolectric, the exact risk session 3's carry-over flagged; fixed by switching the fixture to synthetic codes with no bundled SVG, so every timing outcome renders identical placeholder pixels, rather than trying to make real image loading deterministic) and the US/CA Tap and Name all status rows overlapping at 200% font scale (D29; fixed with `AdaptiveTrailingRow`, the same component `TapMapGameScreen.StatusRow` already uses). `assembleRelease`: killed by low system memory partway through, not completed this session (see above). CI should be green on push (last commit `28d1282`; will confirm from the PR).
- Manual test checklist for Phase 6 (debug build "GeoQuiz Debug"; Settings title ×7 → Debug menu → turn on "Mode: Border hop" and "Mode: US states and Canadian provinces"):
  1. Play → New modes shows both tiles only while their flags are on.
  2. Border hop setup: difficulty (Easy 2–3 hops, Normal 4–5, Hard 6+) and the same All countries/region/subregion filters as the classic Countries mode; Start begins a round with a start and target country from that filter.
  3. Border hop round: current country and target shown as chips; tapping a listed neighbour hops to it and the chip row grows; "Undo last hop" removes the last hop; Hint (0.50 points) reveals one working next country and doesn't recharge until you hop or undo; reaching the target shows "Reached X in N hops (par P)"; Give up ends the round short of the target.
  4. Border hop: a filter/difficulty combo with no valid pair (e.g. a tiny subregion at Hard) shows "No route of the right length..." rather than silently falling back.
  5. US/CA setup: toggle US states ↔ Canadian provinces and territories; "Include Washington, DC" only shows for US states; How to play (Tap the map/Name all/Multiple choice) and difficulty (Name all and Tap hide Easy's slot since Choice is the Easy-equivalent tier).
  6. US/CA Tap: "Find: X" with the real US/Canada map; tapping the right state/province fills it blue; a wrong tap flashes its name; 3 wrong taps on one prompt reveal it at Normal, 3 wrong total end the round at Hard.
  7. US/CA Name all: type a state/province name or its postal abbreviation (e.g. "CA" for California) - both work, postal needs exact spelling even at Normal; each correct one disappears from the "still to go" list; always-on timer.
  8. US/CA Multiple choice: a region is highlighted on the map, pick its name from 4 options; no map/list toggle (D39's pattern).
  9. Both modes: Home mid-round pauses and Resume returns to the same state (process death via "Don't keep activities" too); Back mid-round asks before giving up; Results has no Challenge button and no "View Answers" for US/CA regions (supportsAnswerReview = false); Border hop's own Results Answer review works (its codes are real countries) but the walked path, not a normal quiz list.
  10. 200% text: both setup screens, Border hop's round screen (buttons stack), US/CA's Tap/Name all status rows wrap to two lines rather than overlapping the difficulty badge.
  11. Dark mode on every new screen, including the US/CA map.
  12. TalkBack: Border hop's neighbour list and hop chips are real list items/text (no map to navigate); US/CA Choice's progress-count live region announces a new question; Tap's map falls back to the existing list alternative.
- Next session starts at: Phase 7 (New modes C: Currencies, Rivers/mountains/lakes, Flag speed round) on `upgrade/p7-modes-c` from `upgrade/p6-modes-b` (D30); the currencies/physical-features/flag-lookalikes data is already merged in from `upgrade/data-prep` (Phase 6 pulled the whole branch) but still needs real decision-log entries for the open items in `docs/DATA_PREP_NOTES.md`.

### Session 10 – 2026-10-16 – Phase 7
- Done: 7.1 Currencies, 7.2 Rivers/mountains/lakes, 7.3 Flag speed round, each built by an
  independent Sonnet sub-agent in parallel on fully disjoint new files (own `domain/mode/<mode>/`,
  `data/<mode>/`, `ui/<mode>/`, `di/<Mode>Module.kt`, `strings_<mode>.xml`, bundled asset copy of
  its `data/*.json` source), then integrated by the lead (routes in `Screen.kt`, nav graph entries
  in `AppNavigation.kt`, dispatch in `ModeRoutes.kt`, registration in `GameModeModule.kt`, plus two
  small shared-interface additions made once up front - `ChoicePrompt.CurrencyOf`/`FeatureOf`,
  `ModeIcon.CURRENCY`/`TERRAIN_FEATURE` - so neither of the two modes needing them had to touch
  the shared files themselves). Three parallel Opus `code-reviewer` passes (one per mode) plus one
  `licence-auditor` pass over the three newly-bundled data files, then a fix round per mode, then
  one `test-engineer` pass added 29 new screenshot goldens. Gate (`assembleDebug
  testDebugUnitTest lintDebug`, no clean, then `assembleRelease` alone) all passed this time (no
  low-memory kill on `assembleRelease`, unlike session 9).
- Not done / carried over:
  - Currencies and Rivers/mountains/lakes are single-pane only (no two-pane/large-font adaptive
    layout the way Silhouettes/Tap the map have) - a documented scope cut, not an oversight,
    consistent with section 2's "prototype first" principle; revisit in a polish pass if these
    ship.
  - Currencies: Answer Review shows the representative country for a shared-currency item (e.g.
    "Ecuador" for a US dollar question) rather than anything currency-specific - accurate (that
    country really was the correct option) but a UX nit, not fixed this session.
  - Currencies: `CUC`/`KID`/`TVD` have no typed representative, a reasoned extension of D48 beyond
    its literal text (see the Codebase facts entry above) - flagged for Dav to confirm or reverse.
  - Rivers/mountains/lakes: Normal has no Pass/strike-limit interaction beyond Hard's existing
    3-strike end, and typed rounds never reveal a missed answer (Answer Review is off for this
    mode); a player who doesn't know one feature must Give Up and lose the rest of the round.
    Flagged in review (H3), not fixed this session - a candidate for a quick follow-up.
  - Phase 3/4/5/6 carry-overs unchanged (classic double-submit race, Silhouettes 200% label wrap,
    Stats overall totals including new-mode rounds, Tap the map no lakes/rivers, Name all Give-up
    parity, `MapPathCache.clear()` not wired to `onTrimMemory`, LAEA rim rings, Europe frame with
    Svalbard, geometry-based neighbours).
  - Process: all three implementer-fix-round agents hit the same account-wide weekly Sonnet rate
    limit simultaneously (mid-task) during the review fix round; the lead finished the remaining
    work directly from each agent's partial diff (one test file needed its call sites updated for
    a new parameter; the rest of each agent's changes were already complete and correct).
    `assembleDebug`/`lintDebug` were each killed once for low system memory (the same 8 GB-machine
    issue sessions 8/9 flagged); both succeeded on retry after `./gradlew --stop`.
- Decisions: D47-D49 (Dav's 2026-10-13 data-prep answers, given real D-numbers), D50-D52 (lead).
  D52 was corrected same-day after code review found the lead's original tier-direction text was
  backwards relative to the real data - verified directly against `data/flag_lookalikes.json`
  before correcting.
- Owner actions added: confirm D47-D52 at the end (as usual), including the two Currencies
  judgement calls beyond D48's literal text and Rivers/mountains/lakes' H3 Pass/strike gap;
  device-test all three modes (checklist below, also in the PR description).
- Verification: per-task compile + own package tests during the parallel build wave (3 agents);
  full `assembleDebug testDebugUnitTest` after integration (1381 tests, 1 pre-existing-fallback
  test needed updating once real routing replaced it, otherwise 0 failures); code review found
  real bugs in all three modes (see Decisions and Codebase facts); fix round brought the suite to
  1418 tests, 0 failures, lint clean; screenshot wave brought it to 1447 tests, 0 failures, lint 0
  errors/89 warnings (all pre-existing categories). `assembleRelease` passed this session (5.6 MB
  R8 APK), unlike session 9's low-memory kill.
- Manual test checklist for Phase 7 (debug build "GeoQuiz Debug"; Settings title x7 -> Debug menu
  -> turn on "Mode: Currencies", "Mode: Rivers, mountains and lakes" and "Mode: Flag speed round"):
  1. Play -> New modes shows all three tiles only while their flags are on.
  2. Currencies: pick a category (All countries, a region, a subregion); a non-shared currency
     (e.g. Albanian lek) is typed at Normal/Hard, 4-choice at Easy; a shared currency (euro, US
     dollar, CFA franc, East Caribbean dollar, etc.) is ALWAYS "Which of these uses the X?" with
     exactly one correct option shown, at every tier, with flags on every option; a one-line fact
     appears after answering when the currency has one. Panama types "balboa" (not US dollar);
     Zimbabwe types "US dollar" (never one of its other mledoze-listed currencies); Palestine
     never appears as a typed target. Try Micronesia (or another small filter) and confirm it
     never silently gives a 0/0 round.
  3. Rivers, mountains and lakes: switch the Rivers/Mountains/Lakes tabs and an optional continent
     filter; each question highlights one real feature on a map with visible country context
     underneath (not blank, not a filled-in blob for a river, not hidden under other land for a
     mountain range); "Everest"/"Kilimanjaro"/"Baikal" (without "Mount"/"Lake") are accepted at
     Normal/Hard; Easy shows 4 names from the same tab. "Practise the ones you missed" does not
     appear on Results for this mode.
  4. Flag speed round: a 60-second countdown starts immediately; tapping the correct country name
     for the shown flag increases the combo counter and a wrong pick resets it to zero; Easy only
     draws the clearest lookalike pairs, Hard includes the subtlest; Indonesia/Monaco never
     appears as a question. Post-round review lists each lookalike set actually seen, plus
     occasionally a "did you know" card for a near-identical set that wasn't asked. "Practise the
     ones you missed" works normally for this mode when something was missed.
  5. Both/all: Home mid-round pauses (and for Flag speed round, the countdown genuinely stops, not
     just visually) and Resume returns to the same state; Back mid-round asks before giving up
     (Flag speed and Currencies) or is the only way back when paused (Rivers/mountains/lakes, same
     as Silhouettes/Tap the map's pattern); "Don't keep activities" survives process death for all
     three, including a Currencies shared-currency question keeping its exact same options.
  6. 200% text: all three setup screens and game/round screens - the status rows must wrap rather
     than overlap (watch Rivers/mountains/lakes' header and Flag speed round's countdown/combo/
     difficulty row especially).
  7. Dark mode on every new screen, including the Rivers/mountains/lakes map (country context and
     the highlighted feature should both stay legible).
  8. TalkBack: Rivers/mountains/lakes announces a continent clue when touch exploration is on
     (there is no map list alternative for a single highlighted feature); Currencies' flag images
     are decorative (not separately announced per option, the name is already read); Flag speed
     round's countdown and combo update as live regions without talking every second.
- Next session starts at: Phase 8 (Engagement: daily challenge, streaks, share card, hints,
  game-feel) on `upgrade/p8-engagement` from `upgrade/p7-modes-c` (D30).

### Session 11 – 2026-10-16 – Phase 8 (Wave 1)
- Done: 8.1 Daily challenge, 8.4 Hints economy, 8.5 Game-feel, built by three independent Sonnet
  sub-agents in parallel on disjoint files, then integrated by the lead (routes in `Screen.kt`, nav
  graph entries in `AppNavigation.kt`, dispatch in `ModeRoutes.kt`, registration in
  `GameModeModule.kt` for 8.1, which the implementer agent correctly left for the lead per its
  brief). Three parallel Opus `code-reviewer` passes (one per task), then a fix round each, all by
  the lead directly (see Process below). Commit `7524c43`.
- Not done / carried over:
  - 8.2 Streaks, 8.3 Share card (Wave 2, both depend on 8.1), 8.6 Home wiring (Wave 3, depends on
    8.1 + 8.2). Not started.
  - `licence-auditor` has not formally reviewed 8.5's three bundled sound files yet (the
    code-reviewer pass checked licensing informally - stdlib-only generation script, small files,
    `data/SOURCES.md` rows - but the project's own process wants a real auditor pass before the
    phase gate, same as every data/asset-adding phase).
  - No screenshot goldens added yet for the daily challenge screens, the combo counter, or the
    confetti overlay; `test-engineer` hasn't run.
  - D54: daily challenge's one-attempt-per-day is a soft UI gate only (see D54); worth a harder
    guard once 8.6 gives Home a more direct entry point, before it ships.
  - D55: Border hop Easy's hint cap going from unlimited to zero is a real, user-visible behaviour
    change on an already-shipped mode; flagged for Dav, not reversed.
  - 8.5's review suggested one more test (automated coverage for the perfect-score celebration's
    "fires exactly once" logic, which was traced by hand and is believed correct but has no
    Robolectric test); not added this session, not blocking.
  - Phase 3–7 carry-overs unchanged (classic double-submit race, Silhouettes 200% label wrap, Stats
    overall totals including new-mode rounds, Tap the map no lakes/rivers, Name all Give-up parity,
    `MapPathCache.clear()` not wired to `onTrimMemory`, LAEA rim rings, Europe frame with Svalbard,
    geometry-based neighbours, Rivers/mountains/lakes' no-Pass-on-Normal gap, Currencies'
    `CUC`/`KID`/`TVD` representative gap).
- Decisions: D53-D58 (lead; all flagged for Dav to confirm or reverse, same pattern as D37/D43/D50).
- Owner actions added: none new. Confirm D53-D58 at the end (as usual), especially D54 (soft
  one-per-day gate) and D55 (Border hop Easy hints).
- Process notes:
  - All three Wave 1 implementer sub-agents hit the account-wide weekly Sonnet rate limit
    simultaneously, mid-task (same failure mode sessions 8/10 flagged), each leaving a complete but
    unverified diff (none had confirmed their own compile status before being cut off). The lead
    verified and finished all three directly: fixed a Robolectric API misuse (`ShadowAudioManager`'s
    `setRingerMode` is protected; call the real `AudioManager` setter instead), a JVM-illegal
    backtick test name (a semicolon inside the name), a missing constructor arg in a shared test
    fixture (`ScreenTestFixtures.kt`) after `QuizViewModel`'s constructor grew a new
    `SoundEffectPlayer` parameter, a hard-coded English string in Settings, and a real UX bug in the
    daily challenge screen (content would jump to the next question while still showing the
    previous one's feedback - fixed with a new `displayIndex` that lags during the feedback pause).
    Also reverted one unrelated, accidental edit to Phase 9's task row that slipped into the working
    tree from one of the agents.
  - This machine (8 GB RAM) hit its low-memory build-kill guard twice this session on top of the
    existing slow-build issue (sessions 8-10): once mid test run, once on the combined
    `assembleDebug testDebugUnitTest lintDebug` gate. `./gradlew --stop` (clearing idle Gradle/Kotlin
    daemons) recovered roughly 1-2 GB each time; splitting the combined gate into three separate,
    sequential Gradle invocations (assemble, then test, then lint) avoided a third kill. Recommend
    always running the gate as three separate commands on this machine, not one combined one.
  - A `compileDebugKotlin` run failed once on a transient Windows file-lock in the Gradle build
    cache ("Could not get file mode for ... .class"), unrelated to any code change; retrying the
    exact same command succeeded.
- Verification: per-task compile + own test classes during the parallel build wave (confirmed by the
  lead after the rate-limit cutoff, not by the agents themselves). Full gate run as three separate
  commands after all fixes and the nav/DI integration: `assembleDebug` (passed), `testDebugUnitTest`
  (1509 tests, 0 failures), `lintDebug` (passed, 0 errors, 91 warnings, same pre-existing categories
  as session 10's 89). No screenshot/Roborazzi run this session (no new goldens needed yet since no
  `test-engineer` pass has run). `assembleRelease` not run this session (not required until the
  phase gate).
- Manual test checklist: not written yet (Wave 1 isn't reachable end-to-end from Play yet - the
  Daily challenge tile will appear in Play's New modes grid once `FeatureFlag.DAILY_CHALLENGE` is on
  in the debug menu, but 8.2/8.6 aren't built, so streaks won't show and the Home card stays the
  placeholder). Will be written once the phase gate is reached.
- Next session starts at: Phase 8, Wave 2 (8.2 Streaks, 8.3 Share card) on `upgrade/p8-engagement`
  (branch exists, Wave 1 committed). Then Wave 3 (8.6), then the phase gate (licence-auditor,
  test-engineer screenshots, full verification gate as three separate commands per the process note
  above, decisions/session log, manual test checklist, PR).

### Session 12 – 2026-10-16/17 – Phase 8 (Wave 2, Wave 3, gate)
- Done: 8.2 Streaks and 8.3 Share card (Wave 2, two Sonnet implementers in parallel on disjoint
  files, one Gradle build at a time via a `mkdir` lock), then 8.6 Home wiring (Wave 3, by the lead).
  Each task had an Opus `code-reviewer` pass and a fix round by the lead. Licence auditor: a full pass
  on 8.5's sound files and an L9 trade-dress sign-off on 8.3. Phase gate: screenshot goldens,
  verification gate, plan update. Commits `406da22` (8.2 + 8.3, plus session 11's uncommitted plan
  update), `9e9c654` (8.6), and the gate commit (goldens, test fixes, docs).
- Review fixes worth knowing:
  - 8.2: Setup now takes "already played today" from the live streak, not a one-shot query. Before
    this, Back from Results showed Start next to a 1-day streak and led to the AlreadyPlayed dead
    end. "Today" re-reads at local midnight. 8 more calculator cases. A corrupt far-future date
    can't add freezes.
  - 8.3: the dialog state and challenge link moved into the `SavedStateHandle`. Before this,
    rotation dismissed the dialog and every open recorded another outgoing challenge. The preview
    shrinks so the buttons stay visible in landscape and at 200%. An image failure falls back to
    text instead of crashing. The card prints a short link (D60). Wording is in `strings_share.xml`.
    TalkBack hears "3 minutes 12 seconds", not "3m". ClipData for the chooser preview. A
    timestamped cache file.
  - 8.6: the header title is one line (ellipsised), and the chip drops its flame above 1.5 font
    scale. The card follows the registry's availability rule.
- Gate findings fixed:
  - One Wave 1 test (`DailyChallengeGameViewModelTest`, "incorrect typed attempt") assumed the
    day's first question is typed. It failed once the date rolled over to 2026-10-17. It now
    answers up to the first typed question, like its neighbours.
  - Wave 1 had left three golden sets stale:
    - `border_hop_mid_round_*`: D55 disables Border hop's hint at Easy.
    - `settings_*`: the new Sound effects switch.
    - `play_*`: the old placeholder card.
    All re-recorded and checked by eye.
  - 2 new lint `UseKtx` warnings in `ShareCardRenderer` fixed (core-ktx `createBitmap` /
    `withTranslation`).
- Not done / carried over:
  - No goldens for the daily challenge question screens: the questions are seeded from the real
    local date, so a golden would change every day. That needs an injectable date (e.g. a `Clock`
    for `DailyChallengeDate`). The Setup, AlreadyPlayed, share dialog, card and Play states are
    covered. No golden for the combo counter or confetti (timed/animated).
  - Results still hard-codes "Share"/"Challenge"/"Share your result so they can compare." in
    English. This predates Phase 8 (licence auditor note); move them to strings in an i18n pass.
  - Border hop at Easy shows a disabled "Show a valid hop" button (D55's cap of 0). Silhouettes and
    Tap the map hide hints at Easy instead (D39/D41). Dav to choose: hide it for consistency, or
    reverse D55 for Border hop.
  - `geoColors.correct` on `secondaryContainer` (the Today's card tick) is not in
    `GeoColorsContrastTest`. The icon is decorative and the words carry the meaning; add the pair
    when the test is next touched.
  - Credits screen: optionally mention "sound effects" under "App icon and artwork" (licence audit,
    Recommended, not required: no third party to attribute).
  - 8.5's "celebration fires exactly once" Robolectric test (from session 11) is still not added.
  - Phase 3–7 carry-overs unchanged (see session 11).
- Decisions: D59 (streak rules), D60 (share card and links), D61 (Home card). All lead decisions
  for Dav to confirm, together with D53–D58.
- Owner actions added: none.
- Process notes:
  - The test-engineer sub-agent hit the account-wide Sonnet session limit before writing anything
    (`git status` clean apart from the lead's docs). The lead wrote `EngagementScreenshotTest`
    directly instead of re-delegating.
  - Robolectric's native graphics don't reliably finish a bitmap render started on
    `Dispatchers.Default`. The share-dialog screenshots timed out at random (2 of 4 per run, even
    with 30 s). Fix: `LocalShareCardRenderContext` (default `Dispatchers.Default`; screenshot tests
    provide `EmptyCoroutineContext`). Two full verifies were stable afterwards.
  - The AndroidX `FileProvider` matches roots with "/" separators, so `getUriForFile` always fails
    on a Windows JVM. The URI half of `ShareCardRendererTest` is skipped on Windows (`Assume`) and
    runs on Linux CI and devices.
  - Ran the gate as separate commands with `./gradlew --stop` between them, as planned; no
    low-memory kills this session.
  - The Bash tool mangled a `\'` inside a heredoc twice (unescaped apostrophes in strings XML,
    caught before any build). Prefer the Edit tool for resource strings with apostrophes.
- Verification:
  - `assembleDebug` passed.
  - `testDebugUnitTest`: 1586 tests, 1 skipped (Windows FileProvider). The one date-dependent
    failure was fixed and its class re-run green.
  - `lintDebug`: 0 errors, 91 warnings (same as session 11).
  - `verifyRoborazziDebug`: 127 goldens (15 new in `EngagementScreenshotTest`, 10 re-recorded),
    all pass, run twice.
  - `assembleRelease`: passed on the second try (5.7 MB signed APK). The first run, in the background,
    was stopped by the low-memory guard; the retry ran alone in the foreground after `--stop`.
- Manual test checklist (debug build; Settings title ×7 → Debug menu → turn on "Daily challenge"):
  1. Play shows the Today's challenge card ("10 questions, the same for everyone today…"). It isn't
     also listed in New modes. There's no streak chip yet.
  2. Tap the card → Setup ("No current streak…", "0 streak freezes saved", "7 days to your next
     streak freeze") → Start → play all 10 → Results.
  3. Back from Results to Setup: Start is gone, "You've completed today's challenge", "Streak: 1
     day". Back to Play: the card shows a blue tick and "Done for today…", and the header shows the
     flame and 1.
  4. TalkBack on the header chip: "1-day streak, 0 streak freezes saved". The card reads as one
     button, "Open today's challenge".
  5. 200% font: the Play header title stays on one line, and the chip shows only the number. The
     Setup streak lines wrap and nothing clips.
  6. Results → Share: the dialog previews the card (app name, title, big score, %, date · time,
     short link). Check light and dark.
  7. Share image → a chooser with an image preview → send to yourself (e.g. a messaging app or
     Drive). The image arrives, and the text with the full link comes along where the target
     supports it.
  8. Share text: a classic Countries result has the sentence and the challenge link. A new-mode
     result (e.g. Silhouettes) has the sentence, then the date, then the Play Store link.
  9. Rotate with the share dialog open: it stays open. Cancel, then Share again: in Challenges,
     only one outgoing challenge appears for that result.
  10. Landscape: the dialog preview shrinks, and Share image / Share text stay visible.
  11. Practice result (Practise the ones you missed): there is still no Share or Challenge.
  12. Streak rollover: change the device date to tomorrow and the card says not done, with "Streak:
      1 day" still alive. Skip a day (+2 days) with no freeze: the streak is 0. Set the date back
      to real: no crash, and the streak is back.
  13. Optional, for freezes: play 7 consecutive days by changing the date (or check the unit
      tests): Setup shows "1 streak freeze saved". Skip a day: the streak survives and the freeze
      is used.
  14. Settings → Reset all data: the streak is gone and the card is back to not done.
  15. Regression: the classic letter/word-pattern categories still play, and Border hop at Easy
      shows its hint button disabled (see carry-over).
- Next session starts at: Phase 9 (Release readiness) on `upgrade/p9-release` from
  `upgrade/p8-engagement` (D30).

### Session 13 – 2026-10-17 – Phase 9 (release readiness, prepare only)
- Scope (Dav): prepare the 3.0.0 release candidate. Do not upload, merge, retarget, tag, push to `main` or touch the live site; Dav device-tests the RC first.
- Done:
  - D53–D61 confirmed by Dav and now Decided; D62–D65 added.
  - 9.1: every flag on; version 3.0.0 / 18.
  - 9.2: full review and licence audit, with the fixes listed below.
  - 9.3: `docs/STORE_LISTING.md`, Lato store images, 8 phone screenshots and the owner checklist.
  - 9.4: measurements below. 9.5: checklist below.
  - D62: Border hop hides its hint at Easy.
  - Carry-overs:
    - Results strings moved to `strings.xml`.
    - `geoColors.correct` on `secondaryContainer` added to `GeoColorsContrastTest`; it passes in both themes.
    - Credits now says "App icon, artwork and sounds".
- **Flags for Dav.** All have `defaultEnabled = true` in `domain/model/FeatureFlag.kt`; tell the lead which to turn back off after testing:
  1. `COUNTRY_SILHOUETTES` – Country silhouettes
  2. `TAP_THE_MAP` – Tap the map (Find and Name all)
  3. `BORDER_HOP` – Border hop
  4. `US_CA_REGIONS` – US states and Canadian provinces
  5. `CURRENCIES` – Currencies
  6. `PHYSICAL_FEATURES` – Rivers, mountains and lakes
  7. `FLAG_SPEED_ROUND` – Flag speed round
  8. `DAILY_CHALLENGE` – Daily challenge, streaks, the Today's challenge card and the streak chip

  Always on, with no flag: difficulty tiers, the Play hub, the hints economy, the combo counter, confetti, sound effects (they have a Settings switch) and the share card.
- 9.2 review covered `git diff main...HEAD`, phases 3–8; phases 0–2 were reviewed when they merged.
  - Reviewers: five Opus code-reviewers split by area (data/DI/config; core UI; map engine + Phase 5 modes; Phase 6–7 modes; Phase 8 + tools), one licence-auditor over everything, then an Opus review of the fix diff itself.
  - Licence audit: no Required items. One Recommended item (a stale D34 → D47 reference in `data/SOURCES.md`) is fixed.

  Fixed, High:
  - A daily challenge give-up was scored as a perfect round. It is now always scored out of 10.
  - Border hop setup offered filters that can never produce a puzzle (Oceania, and most subregions at Hard). They are now hidden per tier via `BorderGraph.hasPuzzle`.
  - The Border hop hint was invisible to TalkBack. It now has a label and a polite live region.

  Fixed, Medium:
  - Play Again after a daily challenge led to a dead end. It is now hidden there (`supportsPlayAgain`).
  - Stats "Reset Statistics" wiped the streak and allowed a replay. Daily rows are now kept.
  - Stats "By Mode" covered only the classic modes.
  - Category labels used the US "color". They now say "colour"; persisted keys are unchanged.
  - The daily challenge Setup screen stayed on yesterday after deep sleep. Fixed with `WhileSubscribed` and a 60 s wall-clock poll.
  - The daily generator's output is now pinned by a golden test.
  - Silhouettes' Show neighbours could be spent before the outline loaded.
  - Hard strike counters were missing (Currencies, Rivers) or a bare "1/3" (US/CA). US/CA now reads "1/3 strikes".
  - The Border hop path row could squeeze the neighbour list at 200% font.

  Fixed, Low:
  - Classic double-submit race: one submit at a time, and a quiz is never un-completed.
  - The Give up dialog and the quiz settings sheet now survive rotation.
  - Empty Currencies, Rivers and US/CA rounds were recorded as 0/0. They now show an Unavailable state.
  - Map engine:
    - map path caches are cleared on trim-memory;
    - a failed path build no longer crashes;
    - the Tap the map list keeps its scroll position and TalkBack focus between finds.
  - Name all saves only the typed input on each keystroke.
  - The SoundPool listener is registered before loading.
  - Confetti no longer recomposes every frame.
  - Strings: the Results hard-coded strings moved to `strings.xml`; currency labels are formatted from resources in the UI.
  - Build and CI:
    - the signing-properties stream is closed;
    - CI checks that the bundled JSON copies match `data/`.
  - Tests:
    - new `ModeAvailabilityViewModel` tests;
    - flag tests no longer assume default-off.
- Not done / carried over (logged, not blocking the RC):
  - **Release smoke test.** CI never builds a release; R1 checked the keep rules by reading and they look complete. Dav's device pass on the minified RC covers this.
  - **Data scripts.** `tools/data/build_borders.py` and `build_currencies.py` don't verify `countries.json`'s recorded SHA-256. The recorded hash `9c5b9005…` is of the Windows CRLF checkout, while the committed blob hashes to `06fe415a…`. A check needs line-ending normalisation first, or a `.gitattributes` `-text` rule. Not changed.
  - **Currency label in domain.** `CurrenciesChoiceGenerator.currencyLabel()` still builds the English "the X (s, c)" for `ChoicePrompt.CurrencyOf`. Moving it needs shared choice-prompt plumbing.
  - **Daily challenge Results label.** Unreached questions count under "Incorrect Guesses" (e.g. 8 after giving up at 2/10). Consider a "Missed" label for this mode.
  - **One attempt per day.** Force-stopping mid-round bypasses it, because the in-progress state lives only in the `SavedStateHandle`. Accepted for v1, in D54's spirit.
  - **Hard-coded English.** Stats screen labels and the `ShareUtils` share sentence, plus strings in Answer review, Settings and Challenges. Leave for an i18n pass.
  - **Layering.** `rememberSoundEffectPlayer` (a `@Composable`) lives in `data/service`, and `AcceptIncomingChallengeUseCase` depends on the concrete `ChallengeRepository`.
  - **Map drawing cost.** The hatched Wrong state costs one `clipPath` per wrong country per frame (`MapCanvas`). Profile on a low-end device with many misses.
  - **Name all empty round.** `TapMapNameAllViewModel` still records a round with no members; the empty-round guard was added only to the other modes.
  - **Tile icons.** Silhouettes and Rivers/mountains/lakes share the mountain icon on Play.
  - **Store screenshots.**
    - `03_quiz_countries.png` ends on a "Not recognised" guess (it reuses a shared fixture).
    - Slot 07 shows US states, because the Border hop fixture uses synthetic country names.
    - Dav may prefer device screenshots for some slots.
  - **Missing test.** The 8.5 "celebration fires exactly once" Robolectric test is still not written.
  - Earlier carry-overs (sessions 8–12) are unchanged unless listed as fixed above.
- Decisions: D53–D61 are now Decided (Dav). D62–D65 added (Dav, 2026-10-17). D66 added (achievement spelling, Dav, 2026-10-20): three achievement texts renamed in `Achievement.kt`, the CSVs and zip regenerated, `--check`, the achievement tests and every screenshot golden pass (no golden shows these three).
- Owner actions added or updated:
  - Upload the regenerated store images and text.
  - The duplicate `AchievementsIconMappings.csv` is deleted (Google's doc names `AchievementsIconsMappings.csv`; Dav approved).
  - Achievement spelling decided as "rename" (D66, 2026-10-20). Done in the app; the Play Console text is edited by hand (`docs/STORE_LISTING.md`).
  - Tell the lead which flags to turn off.
  - App name: the launcher says "Geography Quiz" while the repo and docs say "GeoQuiz" (see `docs/STORE_LISTING.md`).
- Process notes:
  - All five Sonnet fix agents hit the account rate limit mid-task at the same time. The lead checked each partial diff and finished the rest directly: Border hop TalkBack and path row, the US/CA Unavailable states, the Name all input save and the daily generator golden. An Opus implementer did the core-UI fix round.
  - The low-memory guard reaped one background store-screenshot build. It was re-run in the foreground after Dav's OK.
  - Store screenshots run on the release unit-test variant, because the debug variant renames the app "GeoQuiz Debug". That variant lacks `ui-test-manifest`, so the test registers `ComponentActivity` with Robolectric's package manager.
- Verification: 
  - `assembleDebug` passed.
  - `testDebugUnitTest`, final full run on the finished tree: 1648 tests, 0 failures, 9 skipped (the 8 store shots and the Windows FileProvider test).
  - `lintDebug`: 0 errors, 91 warnings, the same categories as session 12.
  - `recordRoborazziDebug`: 15 goldens re-recorded and 4 new `play_all_modes_*` goldens, all checked by eye. `verifyRoborazziDebug`: all 131 goldens pass; the 8 skipped tests are the store shots.
  - Store shots: 8 PNGs at 1080x1920 via `testReleaseUnitTest`.
  - `assembleRelease` passed in 12.5 min, foreground (it moved to the background after 10 min and finished). `app/build/outputs/apk/release/app-release.apk` is 5.71 MB, versionCode 18 / 3.0.0, v2-signed with the replacement upload key (SHA-256 `05:DE:14:…`).
  - `bundleRelease` passed: `app/build/outputs/bundle/release/app-release.aab`, 10.86 MB, signature verified with jarsigner. It includes full native debug symbols; Play strips them before download.
  - Nothing was uploaded.
- 9.4 performance: 
  - APK size: 5.71 MB raw, about 4.99 MB download (apkanalyzer). 2.7.2 was 4.6 MB. Compressed parts:
    - dex: 3.62 MB
    - resources: 0.74 MB
    - map layers: 0.47 MB
    - flags: 0.43 MB
    - `static.db`: 0.08 MB
    - native: 0.06 MB
  - D3 map budget: 591 KB raw of 1.5 MB.
  - Map timings (JVM, `-Pgeoquiz.perf=true`, best of 5):
    - decoding all 8 layers: 17.7 ms (`admin0_50m` alone 5.2 ms);
    - projecting `admin0_50m`: 10.4 ms with Equal Earth, 50.6 ms with LAEA;
    - building paths (Robolectric native): 7 ms for `admin0_110m`, 58 ms for `admin0_50m`.
  - Cold start: there was no device, so it was not measured here. Startup does no main-thread IO: `Application` only initialises Play Games; consent, billing and sync start asynchronously, and every asset repository loads lazily on IO. Library baseline profiles are packaged.
  - To measure on Dav's phone: `adb shell am force-stop com.geoquiz.app`, then `adb shell am start -W -n com.geoquiz.app/.MainActivity`. Read `TotalTime`, take the median of 5 runs, and compare it with 2.7.2.
  - No perf code changes. The only memory change is clearing the map caches on trim-memory (9.2).
  - Follow-up if cold start is slow: an app-specific baseline profile (Macrobenchmark needs a device).
- Manual test checklist:
  Use the **release APK** (`app/build/outputs/apk/release/app-release.apk`), not the debug build, so R8 is tested too. Every mode is on by default (D63); no debug menu is needed.
  0. **Upgrade test (do this first).** The Play Store build is signed with Google's app-signing key and this RC with the upload key, so installing the local RC over the store version fails (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Use one of these:
     - (a) Recommended, closest to real users: upload the AAB to Play Console → Internal testing (or Internal app sharing) after the first device pass, install the store version, then update to the internal build through Play.
     - (b) Local: build 2.7.2 from `main` with the same `signing.properties` (`git worktree add ../gq-272 main`, copy `signing.properties` and `local.properties` in, `./gradlew assembleRelease`), install it, then `adb install -r app-release.apk` (the RC) over it.
     Either way, on 2.7.2 first: finish a Countries quiz (achievement progress), leave a Capitals quiz half done (Resume card), set Show timer off and Hard mode on. After updating, check: History and Stats keep the old quizzes; Achievements keep unlocks and progress; Play's Continue card resumes the Capitals quiz with its answers and time; Show timer is still off and the default difficulty is Hard; Remove Ads stays removed if purchased; no crash on first launch.
  1. **Classic modes × tiers.** For each of Countries, Capitals and Flags: one Easy (4 choices, wrong pick shows the right answer blue + tick and yours orange + cross), one Normal (typo "Austrailia" accepted), one Hard (timer always on, 3 wrong guesses end it, near miss = "Close – check the spelling", no strike). Include a letter/word-pattern category (e.g. "Starts with B", "Contains 'land'") and a flag colour category (labels say "colour"). Mastery stars and "Best" update on the category row. "Practise the ones you missed" replays exactly the misses.
  2. **New modes × tiers.** On Play → New modes, all seven tiles show. For each, play one round at Easy, Normal and Hard (where offered), check Results, then "Practise the ones you missed" where shown:
     - Country silhouettes: hints (region, first letter, neighbours, which stays disabled until the outline has drawn), 3 lives, no hints at Easy.
     - Tap the map: Find (World and one continent) and Name all; TalkBack list keeps its scroll position after each find.
     - Border hop: setup lists only filters that can make a puzzle at the chosen tier (Oceania never; few subregions at Hard); no hint button at Easy (D62); a long path scrolls sideways; Undo; "Reached X in N hops (par P)".
     - US states and Canadian provinces: Tap, Name all (postal codes like "CA"), Multiple choice; Hard shows "1/3 strikes", not a bare number.
     - Currencies: shared currencies are always "Which of these uses…"; Hard shows a strike counter.
     - Rivers, mountains and lakes: each tab; "Everest"/"Baikal" accepted; Pass works; Hard shows a strike counter.
     - Flag speed round: 60 s countdown, combo, lookalike review.
  3. **Daily challenge and streaks.** Play → Today's challenge card → Setup → Start → answer 10 → Results (no Play Again button). Back: Start is gone, "Streak: 1 day", card shows a tick, header shows the flame chip. Give-up test on another day: answer 2, End challenge → Results shows 2/10, no "Perfect" line, no confetti. Rollover: set the date to tomorrow → card says not done and streak 1 still alive; leave the Setup screen open across a real midnight (or lock the phone past midnight) and come back → it shows the new day within about a minute. +2 days with no freeze → streak 0. Set the date back → no crash. Stats → Reset Statistics keeps the streak; Settings → Reset all data clears it.
  4. **Share card.** Results → Share → preview (app name, title, big score + %, date · time, short link) in light and dark → Share image → send to yourself; Share text: classic has the challenge link, new modes have the date and Play Store link. Rotate with the dialog open: it stays. Practice results have no Share.
  5. **Game-feel.** Correct/wrong sounds and haptics; Settings → Sound effects off silences them; phone on silent/vibrate silences them even with the setting on; a perfect score shows confetti once (none with system "Remove animations").
  6. **Light/dark.** Every screen above in both themes; maps, outlines and flag borders visible; no white flash on launch in dark mode.
  7. **200% font** (largest font + display size). Play (header title one line, chip number only), every setup screen, every game screen (status rows wrap, nothing clipped, field and buttons reachable with the keyboard open), Results, Stats, Settings, Credits.
  8. **TalkBack.** One round of a classic quiz, Tap the map (list view), Border hop (hinted row says "…, suggested hop" and is announced), US/CA Name all, Flag speed; the daily challenge card and streak chip; Results and the share dialog.
  9. **Offline.** Airplane mode from a cold start: every mode and the daily challenge play fully; no banners, no crash; Play Games sync catches up when back online.
  10. **Reset all data.** Settings → Reset all data → history, stats, streak, pins, saved quiz, achievements progress and settings are cleared; Remove Ads stays.
  11. **Misc.** Stats "By mode" lists every mode you played. Challenge link from a classic Results opens on another device or via `adb shell am start`. Credits shows "App icon, artwork and sounds". Cold start time (see 9.4 commands) noted for the record.
- Next session starts at: Dav device-tests the RC (checklist above), says which flags to turn back off and answers the owner items in `docs/STORE_LISTING.md`. Then ship: merge PRs #6–#12 in order (D30: retarget each to `main` after the one before it merges), build the final AAB from `main`, and upload to Play (internal testing first).

### Session 14 – 2026-10-20/21 – Phase 10 (device-test feedback)
- Scope (Dav): nine UI and quiz changes from the RC device test, plus three more added mid-session (dodgy silhouettes such as Tuvalu, the US flag, Rivers/mountains/lakes zoom-out). Branch `upgrade/p10-feedback` from `upgrade/p9-release` (D30). Nothing committed.
- Decisions: D67–D77 (Dav answered the four open questions: Tap the map and States and territories go under Countries; currencies show the symbol only and are choice-only when the symbol is ambiguous; the Settings default difficulty is removed; Border hop Easy offers exactly one valid hop).
- Done:
  1. Play hub: Recommended next removed; compact All card; per-category colours as theme tokens (D68, no Amber after the licence audit); "More ways to play" per page as outlined tiles (Countries: silhouettes, rivers/mountains/lakes, Border hop, currencies, Tap the map, States and territories; Flags: Flag speed round); Surprise me (D69). Silhouettes now has its own icon.
  2. States and territories with Australia (D74): admin-1 layer rebuilt with AU, mode renamed, third setup chip, AU projection.
  3. What's new (D75): one-off dialog for upgraders, shown only on Play, kept by Reset all data; debug "Show What's new again" (takes effect on the next launch).
  4. Currencies symbol only (D71), with a data audit test.
  5. Border hop rework (D73): blank world map, typed hops with autocomplete (Normal), exact (Hard), 4 options (Easy); old neighbour-list UI and logic removed.
  6. Difficulty always starts at Normal, never persisted; Settings row removed (D72).
  7. Mastery stars and all their code removed (D70).
  8. Silhouettes exclude 9 unrecognisable countries; Rivers/mountains/lakes can zoom out to a wider context (D76).
  9. US flag stars fixed (D77): confirmed by rendering the old and new SVG through Coil's SvgDecoder (old: empty canton; new: 50 stars).
- Reviews: two Opus code reviews (no High after the in-flight compile break; all Medium and most Low fixed: currency random pick per symbol, "Fr"/"Fr." and basket currencies, What's new not over deep links, big-feature zoom room, rotation keeps zoom, Border hop repeated wrong guesses, short-name typos, IME layout, plurals, hint availability). Licence audit: two Required items fixed (Amber/green/grey adjacency; store listing no longer mentions stars or recommendations); notices updated for `usa.svg`.
- Device test (emulators GeoQuiz_API36 and GeoQuiz_API30). Found and fixed on device: Border hop drew Tap the map's tap-zone circles and fell back to a world view when the keyboard opened, and its route was not framed when the detail scene replaced the coarse one (fit tracker mistook the map's own reset for a player move); What's new "Got it" sat under the navigation bar; mode tiles were green like a category and sat beside the grey Islands tile. Checked on device: Play hub per page, Surprise me (Flags → "6 colours", Normal), What's new once only and not on a fresh install, Settings without default difficulty, Border hop typing/suggestions/hop/wrong guess/give up, Currencies symbol only (؋), Australia map and ACT tap, Silhouettes (188 countries), Rivers framing.
- Not verified on device: pinch zoom-out in Rivers/mountains/lakes (adb has no multi-touch; covered by framing unit tests); 200% font and TalkBack passes; dark theme on device (goldens only).
- Emulator note: the API 36 image on this 8 GB machine hits "isn't responding" in System UI and in the app with the main thread waiting on the render thread, even on Play; not attributable to app code. Use a real phone for performance.
- Open items (not done, for Dav):
  - ~~Border hop Results label~~: Dav is happy with the generic Results (2026-10-22).
  - ~~Currencies order~~: rounds are now shuffled (2026-10-22, D71).
  - States and territories setup shows the Normal summary "Type the answers…" while the Tap variant is selected (pre-existing wording).
  - Border hop one-pane layout with the keyboard open can clip the status line mid-text (cosmetic).
  - Fiji, Solomon Islands and Vanuatu stay in Silhouettes (1.4–1.8% land cover); exclude them too if they look like specks on a phone.
  - Earlier carry-overs from session 13 are unchanged.
- Process notes: rate limits stopped agents five times (resumed each time); a Gradle lock left by a killed build stalled one agent for 30 minutes (the lock helper now reclaims locks from dead processes); Claude Code's low-memory reaper killed background gate runs twice, so the final gate ran in the foreground in pieces after stopping stray Gradle daemons.
- Verification (main checkout, final tree):
  - `assembleDebug`: passed.
  - `testDebugUnitTest`: full suite passed at 1798 tests, 0 failures, 10 skipped (gate 2); after the device fixes, re-run in three foreground batches (screenshots, domain/data/di, ui) all passed, plus a final targeted run of Border hop, What's new and Play tests.
  - `lintDebug`: 0 errors, 90 warnings.
  - `recordRoborazziDebug`: goldens re-recorded and checked by eye (Play, category list, Border hop incl. new Easy, currencies, US/CA incl. new Australia shots, What's new, settings; achievements and results-achievements only changed for D66's "World Traveller").
  - `assembleRelease`/store screenshots: not run this phase.
- 2026-10-22 follow-up (Dav): Border hop is fine as is; currencies now shuffle each new round (D71). Committed and pushed to `main` (fast-forward; includes phases 3–9).
- Next session starts at: the store listing update (see "Next session starts at" at the top). Still worth checking on a phone: pinch zoom in Rivers/mountains/lakes, 200% font and TalkBack on Border hop and What's new, dark theme.

### Session 15 – 2026-10-22 – Store listing update (post-phase 10)
- Scope (Dav: "finish things off" from the plan): the store listing items in the session 14 "Next session starts at". Branch `upgrade/p11-store-listing` from `main` (834fead).
- Done:
  - `docs/STORE_LISTING.md`: "More ways to play" heading, Border hop as a blank map with typed hops (D73), Currencies as "currency symbol" (D71), a Colourful tiles + Surprise me line (D68, D69), "More ways to play" in What's new. Re-checked: no stars, recommendations or default difficulty. Counts: short 79, full 2,493, What's new 468. Graphics table lists the new screenshots; owner checklist adds "close PRs #7–#12".
  - Screenshots (Dav chose the lineup: drop Easy, add Border hop and Australia): 01 play, 02 play_more_ways, 03 quiz_countries (now ends on "Japan – Correct!", fixing the session 13 carry-over), 04 tap_the_map, 05 silhouettes, 06 border_hop, 07 australia, 08 results. `ScreenshotScreens.quizInProgress` gained an optional `answers` parameter (defaults unchanged, goldens unchanged). Old files with changed names deleted.
- Seen in the screenshots, not changed (for Dav):
  - `06_border_hop.png`: at 360x640 dp the one-pane layout clips the Give Up button under the map (the same cosmetic issue as session 14's open item). Replace the shot with a device capture or fix the layout if it bothers you.
  - `05_silhouettes.png` shows "1 of 1" (the fixture has one silhouette); `01_play.png` says "8 countries" (fixture data). Both were already like this.
- Process: the first background screenshot run was reaped by Claude Code's low-memory guard; after `./gradlew --stop` (Dav's OK) each build ran alone with the session kept active.
- Verification:
  - Store shots: `testReleaseUnitTest --tests "*StoreScreenshotTest" -Pgeoquiz.storeShots=true` passed, 8 PNGs at 1080x1920, each checked by eye.
  - `verifyRoborazziDebug`: passed (14 min).
  - `bundleRelease`: passed (19.5 min). `app-release.aab` 10.9 MB, versionCode 18 / 3.0.0, `jar verified`, signed by the upload key (SHA-256 `05:DE:14:…`). Nothing uploaded.
  - Not re-run: `assembleDebug testDebugUnitTest lintDebug` (only a test fixture and docs changed; `verifyRoborazziDebug` compiled and ran the unit-test sources).
- Next session starts at: see the top of this file.
