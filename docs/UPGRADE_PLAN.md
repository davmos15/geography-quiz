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
| 0 | Recon, baseline and agent setup | Not started | `upgrade/p0-baseline` | |
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

> Phase 0, task 0.1. No prior sessions.

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

---

## 4. Codebase facts

Filled in by Phase 0 and kept current. Agents read this instead of re-exploring.
Until Phase 0 runs, these are expectations from the README only:

- Kotlin, Jetpack Compose, Material 3, MVVM / clean architecture, Hilt, Room (prepopulated from bundled JSON, schema v10), DataStore, Coroutines/Flow, Navigation Compose, AdMob, Play Games Services, JUnit + MockK. minSdk 26, targetSdk 35.
- Modes: Countries, Capitals, Flags. 197 countries. ~2000 aliases. 40+ categories.
- Data: mledoze/countries (ODbL 1.0), UN M49 regions. Flag image source undocumented.
- Expected paths: `data/local/db/`, `di/DatabaseModule`, `domain/usecase/ValidateAnswer`, `ui/quiz/QuizViewModel`, `data/service/AdManager`, `ui/challenges/`, `PlayGamesAchievementService`, `generate_store_assets.py`.

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
| 2.1 | Split static content DB (countries, aliases, categories) from user DB (history, stats, saved quiz, achievements queue). Ship static DB prebuilt via `createFromAsset()`. Static updates replace the asset, never need migrations. | No first-launch JSON parse on main thread; existing users keep history after upgrade | A |
| 2.2 | Room migration tests for every existing user-DB version via `MigrationTestHelper` | All versions migrate to latest without data loss | after 2.1 |
| 2.3 | Answer validation: table-driven tests for every alias and normalisation edge case (diacritics, apostrophes, "St."/"Saint", "&"/"and", hyphens). Optional fuzzy matching (Damerau–Levenshtein ≤1 for names over 6 characters), off in Hard. Near-miss result type for UX feedback. | 100% alias coverage; fuzzy never accepts a different valid country | A |
| 2.4 | Ads: frequency cap (max 1 interstitial per 3 quizzes, never after a quiz under 60 s, never before results render) | Unit-tested policy class | B |
| 2.5 | Quiz state survives process death and rotation (`SavedStateHandle`) | Test with "Don't keep activities" passes | B |
| 2.6 | Timer uses monotonic time (`elapsedRealtime`) with stored start plus accumulated pauses | Unit tests for pause/resume/background | B |
| 2.7 | Deep-link challenges: defensive parsing, HMAC-signed payload, verified App Links | Malformed links show an error, never crash; tampered scores rejected | C |
| 2.8 | Accessibility semantics: flag `contentDescription` is "Flag" during a question and the country name in review; live-region announcements for progress; merged semantics on list rows | TalkBack walkthrough of a full quiz recorded in session log | C |
| 2.9 | Release config: R8 minify + resource shrinking, keep rules for Room/Hilt/serialisation, mapping file | Release build runs a full quiz without crashes | C |
| 2.10 | Achievement unlocks queued locally and synced to Play Games when online | Airplane-mode unlock syncs later | B |
| 2.11 | Achievements defined in one source; README and Play listing count generated or checked from it | Count matches everywhere | C |
| 2.12 | Testing infrastructure: Compose UI tests for the quiz loop, Roborazzi screenshot tests for key screens (light, dark, 200% font), added to CI | `verifyRoborazziDebug` in CI | after others |

Phase 0 may have edited this table. Follow the edited version.

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
| D2 | Licence for app code: All rights reserved, or an open-source licence? | Ask Dav (default: All rights reserved) |
| D3 | Map asset format and size budget | Phase 4 decides |
| D4 | Daily challenge seed: device local date | Decided |

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
