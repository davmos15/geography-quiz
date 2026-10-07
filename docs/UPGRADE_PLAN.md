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
| 0 | Recon, baseline and agent setup | Done – PR open | `upgrade/p0-baseline` | PHASE0_PR |
| 1 | Licensing and IP compliance | Not started | `upgrade/p1-licensing` | |
| 2 | Code and architecture | Not started | `upgrade/p2-architecture` | |
| 3 | Game engine and UX foundation | Not started | `upgrade/p3-ux-engine` | |
| 4 | Map engine and geodata pipeline | Not started | `upgrade/p4-maps` | |
| 5 | New modes A: Silhouettes, Tap the map | Not started | `upgrade/p5-modes-a` | |
| 6 | New modes B: Border hop, US states and Canadian provinces | Not started | `upgrade/p6-modes-b` | |
| 7 | New modes C: Currencies, Rivers/mountains/lakes, Flag speed round | Not started | `upgrade/p7-modes-c` | |
| 8 | Engagement: daily challenge, streaks, share card, hints, game-feel | Not started | `upgrade/p8-engagement` | |
| 9 | Release readiness | Not started | `upgrade/p9-release` | |

Statuses: Not started, In progress, Done – PR open, Done (merged).
Each phase branches from `main` after the previous PR is merged. Dav tests the debug build on a device before merging.

### Next session starts at

> Phase 1, task 1.1, once the Phase 0 PR is merged. Before 1.1, ask Dav about D7 (keep emoji flags or switch to flag-icons SVGs) and D1 (audience), since 1.1 and 1.5 depend on them.

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
- [ ] Play Console: confirm target audience (13+ vs mixed with children) – see decision D1.
- [ ] Decide on a distinctive app name or subtitle before paid marketing (many apps are called GeoQuiz).
- [ ] Privacy policy: add a contact email (currently "open an issue on GitHub" with no link), mention Play Billing, and fix "suitable for all ages" vs personalised ads once D1 is decided (Phase 1 drafts the text).
- [ ] App Links (task 2.7): provide the SHA-256 fingerprints of the new upload key and the Play app-signing key so `assetlinks.json` can be hosted on geoquiz-app.netlify.app.
- [ ] Merge the Phase 0 PR after checking the debug build on a device.

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

**Quiz flow**
- Home → `CategoryListScreen` → `QuizViewModel` (`J/ui/quiz/QuizViewModel.kt`). It uses `SavedStateHandle` for nav args only (lines 55-64); quiz state is not in it.
- `loadQuiz` (lines 101-142) picks the use case by mode/category. `onSubmitAnswer` (lines 202-250) validates: Correct / AlreadyAnswered / Incorrect. Hard mode ends at 3 incorrect (line 235).
- Timer (lines 144-157): coroutine `delay(1000)` loop incrementing `_timerSeconds`. Not monotonic-clock based, so it drifts.
- On `ON_STOP` (`QuizScreen.kt:80`) the quiz pauses and saves to Room `saved_quizzes` (single row id=1, only if ≥1 answer). Restored if type/value/mode match. A "Resume Quiz" card appears on Countries home only (`HomeScreen.kt:136-174`).
- Score (`CalculateScoreUseCase`): `(correct/total) * correct`, ×1.2 if perfect.
- `getResult` (lines 264-327) fills the global singleton `J/ui/results/QuizResultHolder` (lost on process death), runs achievements, Play Games unlocks, history and leaderboards, then clears the saved quiz. `QuizScreen.kt:107-127` shows an interstitial, then navigates to Results with all data as path args.

**Room**
- Static content tables (seeded from assets): `countries` (cca3 PK), `aliases`, `capital_aliases`, `flag_colors`, `flag_elements`. The four child tables have an FK to countries with CASCADE.
- User tables: `saved_quizzes`, `challenges`, `quiz_history`.
- Migrations in `J/di/DatabaseModule.kt:54-221`: 1→2 … 9→10 plus 1→3, 1→4, 2→4. No destructive fallback. Migrations 3→4, 4→5, 5→6, 8→9 and 9→10 `DELETE` the static tables to force a re-seed.
- Exported schemas: `app/schemas/.../AppDatabase/` 1, 4–10. **2 and 3 are missing.**
- Seeding: no `createFromAsset`, no callback. `CountryRepositoryImpl.ensureSeeded()` (lines 151-157, Mutex, runs when count == 0) parses `countries.json`, `flag_colors.json`, `flag_elements.json` on `Dispatchers.IO`. Inserts are not in a transaction. Filter: `unMember || cca3 in {VAT, PSE, TWN, UNK}` → 197 of 250. Every use case calls `ensureSeeded()`.

**Categories**
- `J/domain/model/QuizCategory.kt`: sealed class with 26 types (22 name-based, 4 flag-based: single colour, colour combo, colour count, element). `typeKey`/`valueKey` for routes; `fromRoute()` falls back to AllCountries.
- `CategoryGroup` (10 groups) and `FlagCategoryGroup` (5 groups). Concrete options are built from data in `J/ui/category/CategoryListViewModel.kt` (`buildQuizOptions` line 144, `buildFlagQuizOptions` line 366); empty options are hidden.
- Filtering happens in memory in the three `GetCountriesFor*` use cases.

**Aliases**
- Room tables `aliases` and `capital_aliases` (alias + `normalizedAlias`, indexed), built at seed time in `CountryRepositoryImpl.kt:205-263`. Sources: common name, official name, `altSpellings`, hard-coded `ABBREVIATIONS` (35 countries) and `CAPITAL_ALIASES` (18 countries). Codes of 3 or fewer upper-case letters are dropped unless whitelisted.
- Matching: `NormalizeInputUseCase` (NFD, strip diacritics, lowercase, `-`→space, drop apostrophes, collapse whitespace), then exact SQL match (`CountryDao.kt:24-32`). No fuzzy matching.

**Flags**
- Emoji only, from `countries.json` `flag` (Unicode regional indicators drawn by the system font). Rendered as `Text` in `J/ui/quiz/components/CountryList.kt:50,77,123` and `J/ui/results/AnswerReviewScreen.kt:183`, only when `show_flags` is on (default off). No image files, no contentDescription.

**Ads, billing, Play Games**
- `MobileAds.initialize` in `GeographyQuizApplication.onCreate` with no consent step. No UMP dependency, no `RequestConfiguration`, no child-directed or under-age tags, no max ad content rating. Manifest declares `AD_ID`.
- `AdManager`: an interstitial is preloaded in `QuizViewModel.init` and shown on every quiz completion, including give-up. No frequency cap. Skipped if `ads_removed`. Banners on the three home screens.
- Billing: one INAPP product `remove_ads`; restore on resume and from Settings.
- Achievements: **38** in the `Achievement` enum (`J/domain/model/Achievement.kt`), mapped to IDs in `PlayGamesAchievementIds.kt`. The same list is duplicated in `generate_achievements_zip.py:25-71`. The README says "30+". Local DataStore is the source of truth; `MainActivity` (lines 50-68) re-syncs all unlocks and leaderboard totals once Play Games sign-in succeeds.

**Challenges and deep links**
- `J/domain/model/ChallengeDeepLink.kt`: query params `id`, `ct`, `cv`, `name`, `mode`, optional `score`/`total`/`time` (`toIntOrNull`). Accepts `geoquiz://challenge` and `https://geoquiz-app.netlify.app/challenge.html`. No signing; scores are trusted.
- Manifest has a VIEW filter for `geoquiz://challenge` only (`autoVerify="false"`). There is no https filter; the netlify page (`docs/challenge.html`) redirects to the custom scheme. `allowBackup="false"`.

**Accessibility**
- No `semantics`, `clearAndSetSemantics` or `liveRegion` anywhere. Flag emoji have no description. Some icons are described (Back, Pause/Resume, Give Up, Answered/Missed); many decorative icons use `null`.

**Strings**
- Existing UI hard-codes English strings. `strings.xml` held only IDs until Phase 0. New UI uses `strings.xml`.

**Settings**
- 4 switches (timer, flags, country hint, hard mode), Remove Ads / Restore Purchases. No reset data, credits, licences, privacy policy link or consent entry. The quiz screen has its own settings sheet with the same toggles (`QuizScreen.kt:349`).
- Debug builds: tap the Settings title 7 times to open the debug menu (feature-flag overrides).

**Tests**
- `app/src/test`: `CalculateScoreUseCaseTest`, `NormalizeInputUseCaseTest`, `ValidateAnswerUseCaseTest`, `PurchaseActionTest`, `FeatureFlagRepositoryTest`. No `androidTest`, no Room migration, ViewModel or UI tests.

**Licensing recon (0.2)**
- Flag source: emoji (Unicode), not images. So L4 is about documenting the source, not replacing image files. Glyph rendering is the device's system font.
- No credits or licences screen, no AboutLibraries/oss-licenses, no in-app ODbL notice. Attribution exists only in `README.md` (Data Sources lines ~129-137, Licence lines 169-171). Natural Earth not used yet.
- `countries.json` (1.29 MB, 250 entries) matches the mledoze v3-era schema. Version/commit not recorded; it arrived in initial commit `40a5fd3`. `flag_colors.json` and `flag_elements.json` look hand-curated, source undocumented.
- `generate_store_assets.py` and `generate_achievements_zip.py` use Pillow with system Arial / Arial Bold (Microsoft, proprietary) via `ImageFont.truetype("arialbd.ttf")`. No downloads or external images; all artwork is drawn in code. `store_assets/achievements/` has 38 PNGs plus CSVs; `AchievementsIconMappings.csv` and `AchievementsIconsMappings.csv` both exist (likely a stale duplicate). Launcher icon origin undocumented.
- Privacy policy (`docs/privacy-policy.html`): says personalised ads via advertising ID, "suitable for all ages" (conflicts with personalised ads and no child tags), deletion = clear data/uninstall, contact = "open an issue on GitHub" (no email, no link). Billing not mentioned. Last updated 17 Feb 2026.
- No `LICENSE`, `THIRD_PARTY_NOTICES.md` or `SOURCES.md`.
- README is stale: says version 2.7.0, target API 35, "30+ achievements". No "Data and licences" section.
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
| D1 | Target audience: 13+ only, or mixed audience including children? Affects ad tagging and Families policy. | Ask Dav (default until answered: treat as mixed audience) |
| D2 | Licence for app code: All rights reserved (Dav, 2026-10-07) | Decided |
| D3 | Map asset format and size budget | Phase 4 decides |
| D4 | Daily challenge seed: device local date | Decided |
| D5 | Feature flags: enum `FeatureFlag` + DataStore "feature_flags". Overrides are honoured only in debug builds (`BuildConfig.DEBUG`); release always uses each flag's default. Debug menu opens by tapping the Settings title 7 times (debug builds only). Phase 9 flips approved defaults to on. | Decided (Phase 0) |
| D6 | `.gitignore` ignores `.claude/*` except `.claude/agents/`, so project agents are versioned but personal settings are not. | Decided (Phase 0) |
| D7 | Flags are Unicode emoji from `countries.json`, not images. L4 becomes "document the source and decide whether to keep emoji or switch to flag-icons (MIT) SVGs". Emoji glyphs come from each device's font, so they look different across OEMs and cannot be shown as large images for flag-first modes (e.g. 7.3). Ask Dav in Phase 1 whether to switch. | Open |

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
