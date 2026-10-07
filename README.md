# GeoQuiz

An Android geography quiz app that challenges players to name countries, capitals and flags across dozens of quiz categories. Built with Kotlin and Jetpack Compose.

**Platform:** Android (Kotlin + Jetpack Compose)
**Current Version:** 2.7.2 (versionCode 17)

---

## Features

### Quiz Modes
- **Countries** - Type country names from memory across 40+ category filters
- **Capitals** - Name the capital city for each country
- **Flags** - Identify countries by their flag

### 197 Countries
193 UN member states plus Vatican City, Palestine, Taiwan and Kosovo.

### Quiz Categories

| Group | Categories |
|-------|-----------|
| All Countries | All 197 countries |
| By Region | Africa, Americas, Asia, Europe, Oceania |
| By Subregion | 18 subregions (Western Europe, Southeast Asia, etc.) |
| Starting Letter | A-Z (26 quizzes) |
| Ending Letter | A-Z |
| Containing Letter | A-Z |
| Name Length | Grouped by character count (4-5, 6-7, etc.) |
| Letter Patterns | Double Letter, Consonant Cluster, Repeated Letter (x3/x4), Starts & Ends Same, All Vowels Present, All Unique Letters, Ending in a Vowel, Single Vowel Type |
| Word Patterns | By Word Count, Ending with Suffix (-land, -stan, etc.), Containing Word, Cardinal Direction |
| Island Countries | Countries with "Island" in the name |
| Capital Matches Country | Capitals that share their country's name (capitals mode) |
| Flag Colours | Single colour, colour combo, colour count (flags mode) |
| Flag Shapes & Objects | Plants & Trees, Animals, Sun, Moon, Stars & Constellations, Union Jack, Coat of Arms, Text & Script (flags mode) |

### Gameplay
- **Free-text input** with alias matching (accepts common names, abbreviations, spelling variants)
- **Optional timer** (count-up, toggleable mid-quiz)
- **Hard mode** - 3 incorrect guesses and you're out
- **Show/hide flags** next to country names
- **Country hint** for capitals mode
- **Pause/resume** with auto-save when backgrounded
- **In-quiz settings** via gear icon (toggle timer, flags, hints, hard mode without leaving)
- **Give up** with confirmation dialog

### Answer Validation
- Case-insensitive, accent-insensitive, whitespace-flexible
- Accepts official names, common names and curated abbreviations (UK, USA, UAE, etc.)
- Hyphen-optional ("Guinea Bissau" = "Guinea-Bissau")
- St/Saint variants accepted

### Results & Review
- Score with percentage scaling and perfect score bonus
- Answer review screen showing all countries (answered/missed)
- Incorrect guesses toggle with contextual hints (e.g. "'I' repeats", "In Europe", "Starts with 'A'")

### Statistics
- Quizzes completed, unique quizzes, perfect scores
- Total correct/incorrect answers, questions faced
- Time played, highest score, average accuracy
- Breakdown by mode (countries, capitals, flags)
- Reset statistics option

### Achievements
- 38 achievements across completion, speed, accuracy and category milestones
- Synced with **Google Play Games Services**

### Challenges
- Challenge friends via shareable deep links
- Compare scores, times and accuracy on a result card
- Track incoming/outgoing challenges

### Ads
- Banner ads on home screen
- Interstitial ads between quizzes
- Ads are non-personalised and child-directed for every user, with consent requested where required by law
- One-off "Remove ads" purchase via Google Play Billing
- Debug builds use Google test ad IDs

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Local DB | Room (v10, prepopulated from bundled JSON) |
| Preferences | DataStore |
| Async | Kotlin Coroutines + Flow |
| Navigation | Jetpack Navigation (Compose) |
| Auth/Social | Google Play Games Services |
| Ads | Google AdMob + User Messaging Platform (consent) |
| Purchases | Google Play Billing |
| Images | Coil 3 (bundled SVG flags) |
| Build | Gradle Kotlin DSL + Version Catalogs |
| Testing | JUnit + MockK |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 36 (Android 16) |

### Project Structure

```
app/src/main/java/com/geoquiz/app/
├── data/
│   ├── local/
│   │   ├── db/           # Room database, DAOs, entities, migrations
│   │   └── preferences/  # DataStore repositories (settings, achievements)
│   ├── repository/       # CountryRepository, QuizHistory, SavedQuiz, Challenge
│   └── service/          # AdManager, PlayGamesAchievementService
├── domain/
│   ├── model/            # Country, Quiz, QuizCategory, QuizState, Achievement
│   └── usecase/          # ValidateAnswer, CalculateScore, GetCountriesForQuiz
├── ui/
│   ├── home/             # Home screen + category groups
│   ├── category/         # Category list + filtering
│   ├── quiz/             # Quiz gameplay (screen, ViewModel, components)
│   ├── results/          # Results screen + answer review
│   ├── stats/            # Statistics screen
│   ├── achievements/     # Achievements screen
│   ├── challenges/       # Challenge screens (accept, list)
│   ├── settings/         # Settings screen
│   ├── ads/              # Banner ad composable
│   └── theme/            # Material 3 theme, colours
├── di/                   # Hilt modules (Database, Repository)
└── MainActivity.kt       # Single activity, navigation graph
```

### Data Sources

| Source | Usage | Licence |
|--------|-------|---------|
| [mledoze/countries](https://github.com/mledoze/countries) | Country names, official names, alternative spellings, capitals, regions and subregions | ODbL 1.0 |
| [flag-icons](https://github.com/lipis/flag-icons) 7.5.0 | Flag images (SVG, bundled) | MIT |

Country data is bundled as JSON in app assets and seeded into Room on first launch. Flag colour and flag element data (`flag_colors.json`, `flag_elements.json`) were curated for GeoQuiz. A curated alias table handles answer validation with about 900 accepted name variants (704 country names and 204 capital names); it is published as [`data/aliases.json`](data/aliases.json) under ODbL 1.0.

---

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release bundle (requires signing.properties)
./gradlew bundleRelease

# Run tests
./gradlew test
```

Release signing requires a `signing.properties` file in the project root (not committed):
```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

---

## Privacy

Privacy policy: https://geoquiz-app.netlify.app/privacy-policy.html

---

## Data and licences

- **In the app:** Settings → Credits and licences shows the data and flag credits (including the ODbL notice for mledoze/countries) and links to the Open-source licences screen for every library.
- [`data/SOURCES.md`](data/SOURCES.md): every bundled data and asset file, with source, version, licence and the script that produces it.
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md): third-party data, assets and libraries with versions and required notices.
- [`data/aliases.json`](data/aliases.json): the answer alias table, a derivative database of mledoze/countries published under ODbL 1.0 (see [`data/README.md`](data/README.md)).
- [`LICENSE`](LICENSE): licence for this repository.

---

## Licence

Copyright (c) 2026 davmos15. All rights reserved. The app code and original assets are proprietary; see [`LICENSE`](LICENSE).

Third-party components keep their own licences (see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)). Country data contains information from [mledoze/countries](https://github.com/mledoze/countries), made available under the Open Database License (ODbL) v1.0. Flag images are from [flag-icons](https://github.com/lipis/flag-icons) (MIT). The derived alias table [`data/aliases.json`](data/aliases.json) is available under ODbL 1.0.
