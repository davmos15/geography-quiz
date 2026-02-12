# Geography Quiz App (Name TBD)

An Android geography quiz app that challenges players to name countries based on various letter-based and categorical criteria. Built for casual enthusiasts, students, and competitive quiz players alike.

---

## App Overview

Players type country names from memory (free-text input) to complete challenges across dozens of quiz categories. The app emphasizes replayability, retention psychology, and broad accessibility with offline-first gameplay, optional timers, and granular difficulty controls.

**Platform:** Android (Kotlin + Jetpack Compose)
**Monetization:** Freemium (ads + premium/ad-free tier)
**Target Audience:** Casual geography fans, students, competitive quiz players

---

## Features

### Quiz Modes

#### Letter-Based Categories
- **Alphabet (A-Z):** Name a country starting with every letter of the alphabet
- **All Countries:** Name all 193 UN member states (+ optionally 2 observer states: Vatican City & Palestine, for 195 total)
- **Starting Letter:** Name all countries that start with a specific letter
- **Containing Letter:** Name all countries containing a specific letter (e.g., "O")
- **String of Letters:** Name all countries containing a letter sequence (e.g., "and")
- **Repeated Letters:** Countries with any repeated letters
- **Repeated Vowels:** Countries with repeated vowels
- **Repeated Consonants:** Countries with repeated consonants

#### Other Categories
- **By Continent/Region:** Name all countries in Africa, Asia, Europe, etc.
- **By Name Length:** Countries with names of N characters
- **Mixed Mode:** Random questions pulled from all category types

> More categories can be added over time - the data model supports arbitrary filtering.

### Timer & Difficulty
- **Optional timer** on any quiz mode (play relaxed or against the clock)
- **Show/hide country count** toggle: see "12 countries contain 'and'" or play blind for extra challenge
- Time-based stats tracked separately (fastest completions)

### Scoring System
- Points per correct answer, **scaled by percentage** (9/10 is worth more per answer than 9/20)
- **Perfect score bonus** for 100% completion
- Incorrect guesses carry no penalty (encourages trying)
- **Lifetime statistics** tracking: total correct guesses, quizzes completed, categories mastered, etc.
- **Time leaderboards** as a separate category (fastest to complete)

### Country Name Handling
- **Accept all recognized variations:** official names AND common English names
  - "Côte d'Ivoire" and "Ivory Coast" both accepted
  - "Myanmar" and "Burma" both accepted
- **Well-known abbreviations accepted:** UK, USA, UAE - but not arbitrary shortenings (not "Aus" for Australia)
- **Flexible on dashes:** "Guinea Bissau" and "Guinea-Bissau" both accepted
- **Strict spelling** - autocorrect on the user's keyboard handles typos
- Curated alias list per country for consistent validation

#### Input Normalization Rules
All user input is normalized before matching against the alias table:
- **Case-insensitive:** "france" matches "France"
- **Accent/diacritic-insensitive:** "Cote d'Ivoire" matches "Côte d'Ivoire" (but the accented form is also accepted directly)
- **Whitespace-trimmed:** leading/trailing spaces stripped, multiple internal spaces collapsed
- **Hyphens optional:** dashes are stripped before comparison ("Guinea Bissau" = "Guinea-Bissau")
- **Apostrophes flexible:** curly and straight quotes treated as equivalent
- **The canonical comparison flow:** strip accents → lowercase → remove hyphens → collapse whitespace → match against alias table

### Hints (Ad-Gated)
- **"Give me a letter"** - reveals a letter in the country name
- **"Give me the country"** - reveals a full answer
- **"First letter / Last letter"** - reveals the starting or ending letter
- Hints require watching an ad (premium users get free hints)
- Hint availability is context-sensitive based on quiz type

### Daily Question
- Random geography question delivered daily per user (not global)
- Push notification (toggleable in settings)
- Can be turned off entirely in settings

### Achievements
- **In-app badges** for milestones (e.g., "Named all countries in Africa", "Perfect score", "7-day streak")
- **Google Play Games achievements** mirrored for platform-level visibility
- Achievement categories: completion, streaks, speed, perfect scores, category mastery

### Leaderboards
- Powered by **Google Play Games Services**
- Online-only (requires connectivity)
- Separate leaderboards for score and time
- Per-category and overall rankings

### Social
- **Share results** to social media / messaging ("I named 187/195 countries!")
- Shareable result cards (visual, branded)

### Engagement & Retention
- **Streak tracking** with visual progress
- **Progress bars** per category
- **Satisfying animations** on correct answers and completions
- **Unlockable content** as players progress
- **Push notifications** (granular control in settings):
  - Daily reminder
  - Weekly stats summary
  - General notifications (catch-all)

### User Accounts
- **Auto-prompted Google Play Games sign-in**
- Handles identity, cloud saves, achievement sync, and leaderboards
- Progress synced across devices via Google Play

### Offline Support
- **Full offline gameplay** after initial data download
- Country data bundled in the app (static dataset)
- Hints require ad viewing (online only)
- Leaderboards require connectivity
- Achievements sync when back online

---

## Technical Architecture

### Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Local DB | Room (prepopulated from bundled JSON) |
| Preferences | DataStore |
| Async | Kotlin Coroutines + Flow |
| Navigation | Jetpack Navigation |
| Background Work | WorkManager |
| Auth/Social | Google Play Games Services |
| Ads | Google AdMob |
| In-App Purchases | Google Play Billing Library |
| Build | Gradle Kotlin DSL + Version Catalogs |
| Testing | JUnit + Compose UI Tests + MockK |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 (Android 15) |

### Data Sources

| Source | Usage | License |
|--------|-------|---------|
| [mledoze/countries](https://github.com/mledoze/countries) | Primary country data (names, aliases, translations, codes) | ODbL-1.0 |
| [REST Countries API](https://restcountries.com/) | Supplementary data & future updates | MPL 2.0 |
| [UN M49 Standard](https://unstats.un.org/unsd/methodology/m49/) | Official UN member state list & regional classifications | Public Domain |
| [Flagcdn.com](https://flagcdn.com/) | Country flag images via CDN | Free |

### Data Strategy
- **Offline-first:** Country data bundled as JSON in app assets, loaded into Room on first launch
- **Custom alias table:** Curated mapping of country → accepted names/abbreviations/variants
- **Room database** enables fast filtering for all quiz categories (contains letter, string match, length, region, etc.)
- Data rarely changes - periodic updates via app releases or optional background sync

#### Data Authority Hierarchy
When sources disagree on country names or classifications, resolve in this order:
1. **UN M49 list** - authoritative for which countries exist and regional classification
2. **App-curated alias table** - final say on what answers are accepted (manually reviewed)
3. **mledoze/countries** - primary seed data for names, translations, and alternative spellings
4. **REST Countries API** - supplementary/fallback only

The curated alias table is the single source of truth for answer validation. It is seeded from mledoze/countries but manually reviewed and extended with abbreviations, dash variants, and edge cases.

#### Data Update Policy
- **Country data updates** ship with app releases (not dynamic). Country changes are rare (~1-2/year).
- **Alias table changes** require manual review and regression testing against a suite of known-good input/output pairs before release.
- **Regression test suite:** Maintain a JSON fixture of ~500+ input → expected country mappings. Run as unit tests on every build. Any alias table change must pass all existing tests before merging.
- **Owner:** The alias table and regression suite are maintained by the app developer(s). Community contributions welcome via issue reports.

### Project Structure (Proposed)

```
app/
├── data/
│   ├── local/
│   │   ├── db/              # Room database, DAOs, entities
│   │   ├── datastore/       # User preferences, settings
│   │   └── assets/          # Bundled country JSON data
│   ├── remote/              # REST Countries API client (optional sync)
│   └── repository/          # Repository implementations
├── domain/
│   ├── model/               # Domain models (Country, Quiz, Achievement, etc.)
│   ├── usecase/             # Business logic (scoring, validation, filtering)
│   └── repository/          # Repository interfaces
├── ui/
│   ├── theme/               # Material 3 theme, colors, typography, dark mode
│   ├── home/                # Home screen
│   ├── quiz/                # Quiz gameplay screens
│   ├── categories/          # Category selection
│   ├── results/             # Results & sharing
│   ├── achievements/        # Achievement display
│   ├── leaderboard/         # Leaderboard screens
│   ├── stats/               # Player statistics
│   ├── settings/            # App settings & notification preferences
│   └── components/          # Shared composables (timer, input field, etc.)
├── service/
│   ├── notification/        # Push notification management
│   └── daily/               # Daily question scheduling
└── di/                      # Hilt modules
```

### Security
- **R8/ProGuard** enabled for release builds (code shrinking + obfuscation)
- **No secret API keys stored in code** - country data bundled locally. Public app identifiers (AdMob app ID, Play Games app ID) are stored in resource XML as required by their SDKs - these are non-secret and safe to include in the APK.
- **EncryptedSharedPreferences** for any sensitive local data
- **TLS 1.2+** enforced for all network calls (ads, leaderboards, billing)
- **Input sanitization** on all free-text quiz inputs
- **Google Play Billing** verification server-side if applicable

---

## Plan of Action

### MVP Milestone (Playable Build)
Before wiring up Play Games, ads, and social features, hit this checkpoint first:
- Project scaffolded with Compose, Hilt, Room
- Country data loaded into Room with alias table
- One working quiz mode (e.g., "All Countries") with free-text input and validation
- Basic scoring (percentage-scaled)
- Optional timer
- Minimal UI (no polish needed - just functional)

**Gate:** If this isn't fun to play in a bare-bones state, revisit game mechanics before investing in monetization and engagement layers.

### Phase 1 - Foundation
1. **Project setup:** Initialize Android project with Gradle Kotlin DSL, version catalogs, Hilt, Room, Compose
2. **Data layer:** Bundle country JSON (mledoze/countries), create Room schema (countries, aliases, regions), prepopulate DB on first launch
3. **Country alias system:** Build curated alias table (official names, common names, abbreviations like UK/USA, dash variants)
4. **Domain layer:** Core models (Country, Quiz, QuizCategory, QuizResult), answer validation use case with alias matching

### Phase 2 - Core Gameplay
5. **Quiz engine:** Implement quiz session logic - load questions by category, accept free-text input, validate against alias table, track progress
6. **Category system:** Build all quiz category filters (alphabet, starting letter, containing letter/string, repeated letters/vowels/consonants, continent, name length, mixed mode)
7. **Scoring engine:** Percentage-scaled scoring, perfect score bonus, per-quiz results
8. **Timer system:** Optional countdown timer, time tracking per quiz, fastest completion records
9. **Show/hide country count** toggle per quiz

### Phase 3 - UI & UX
10. **Theme & design system:** Material 3 theme, dark mode, clean + inviting aesthetic
11. **Home screen:** Category browser, daily question card, streak display, quick stats
12. **Quiz screen:** Free-text input with autocorrect-friendly keyboard, live progress, timer display, hint buttons
13. **Results screen:** Score breakdown, percentage, time, shareable result card
14. **Animations:** Correct answer feedback, completion celebrations, streak visuals, progress bar fills

### Phase 4 - Engagement Systems
15. **Achievement system:** Define achievements (completion, streaks, speed, perfection), in-app badge UI
16. **Google Play Games integration:** Sign-in, cloud saves, achievements, leaderboards
17. **Statistics tracking:** Lifetime stats - total guesses, quizzes completed, categories mastered, best times
18. **Streak system:** Daily play tracking, streak counter, streak-loss warnings

### Phase 5 - Monetization & Notifications
19. **AdMob integration:** Interstitial ads, rewarded ads for hints
20. **Premium tier:** In-app purchase for ad-free experience + free hints (Google Play Billing)
21. **Hint system:** Ad-gated hints with context-sensitive options per quiz type
22. **Push notifications:** Daily reminder, weekly stats, general - with granular settings controls
23. **Daily question:** Random question scheduling, notification delivery, settings toggle

### Phase 6 - Social & Polish
24. **Share results:** Generate shareable result cards/text for social media and messaging
25. **Leaderboards UI:** Per-category and overall leaderboards via Google Play Games
26. **Settings screen:** Notification preferences, daily question toggle, display preferences, account management
27. **Onboarding:** Brief first-launch tutorial/walkthrough

### Non-Functional Targets
| Metric | Target |
|--------|--------|
| Cold start time | < 1.5s to interactive home screen |
| Quiz screen frame stability | 0 janky frames during typing/validation (60fps) |
| Offline DB size (bundled) | < 2MB for country data + aliases |
| APK size | < 15MB (before ad SDK bloat) |
| Answer validation latency | < 50ms per keystroke/submit |
| Room query time (category filter) | < 100ms for any category load |

### Phase 7 - Release Prep
28. **Testing:** Unit tests for scoring/validation logic, UI tests for quiz flow, integration tests for data layer
29. **Performance:** Profile and optimize Room queries, Compose recompositions, app startup time
30. **Security audit:** R8 config, network security config, billing verification
31. **Store listing:** Screenshots, description, feature graphic, privacy policy
32. **Launch:** Internal testing → closed beta → open production release

---

### V1 Priority (If Scoping Down)

**Must-have for launch:**
- Core quiz engine with free-text input and answer validation
- All letter-based quiz categories + continent + name length + mixed mode
- Country alias system (variations, abbreviations, dashes)
- Scoring with percentage scaling and perfect bonus
- Optional timer
- Show/hide country count toggle
- Google Play Games sign-in, achievements, and leaderboards
- Dark mode + clean UI
- Offline gameplay
- AdMob ads + rewarded hints
- Share results
- Basic statistics

**Can wait for V2:**
- Premium in-app purchase tier
- Daily question with notifications
- Weekly stats notifications
- Onboarding walkthrough
- Advanced engagement animations/unlockables

---

## Data Attribution

```
Geographic data provided by:
- mledoze/countries (github.com/mledoze/countries) - ODbL-1.0
- REST Countries API (restcountries.com) - MPL 2.0
- UN Statistics Division (unstats.un.org) - Public Domain
- Flags by Flagcdn.com
```

### Licensing Compliance Note
**ODbL-1.0 (mledoze/countries):** This license requires attribution and share-alike for the *database* layer. Our curated alias table is a derivative database and must be shared under ODbL if distributed separately. The app binary itself (code, UI, logic) is not affected - only the country data layer. **Action item for release:** Conduct a legal review to confirm ODbL compliance, particularly around how transformed data is packaged in the APK and whether the alias table qualifies as a "produced work" (no share-alike) or "derivative database" (share-alike applies).

---

## Review Comments (Resolved)

1. **Country count wording** - Fixed. Now reads "193 UN member states (+ optionally 2 observer states: Vatican City & Palestine, for 195 total)".
2. **Single source of truth** - Added "Data Authority Hierarchy" section under Data Strategy. App-curated alias table is the final authority for answer validation, seeded from mledoze and manually reviewed.
3. **Data update policy** - Added under Data Strategy. Country data ships with releases, alias changes require regression testing against a 500+ fixture suite.
4. **ODbL licensing** - Added "Licensing Compliance Note" under Data Attribution. Flagged legal review action item for release to determine produced-work vs derivative-database classification.
5. **Normalization rules** - Added "Input Normalization Rules" subsection under Country Name Handling. Documents case, accent, whitespace, hyphen, and apostrophe handling with canonical comparison flow.
6. **Secret vs public identifiers** - Clarified in Security section. Public app identifiers (AdMob app ID, Play Games app ID) are explicitly called out as non-secret and safe in resource XML.
7. **MVP milestone** - Added "MVP Milestone (Playable Build)" checkpoint before Phase 1. Ensures core gameplay is validated before investing in monetization/engagement layers.
8. **Non-functional targets** - Added table between Phase 6 and Phase 7 with concrete targets: cold start <1.5s, 60fps quiz screen, <2MB DB, <15MB APK, <50ms validation, <100ms queries.
