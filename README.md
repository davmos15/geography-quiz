# GeoQuiz

An Android geography quiz app that challenges players to name countries, capitals and flags across dozens of quiz categories. Built with Kotlin and Jetpack Compose.

**Platform:** Android (Kotlin + Jetpack Compose)
**Current Version:** 2.7.0

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
- 30+ achievements across completion, speed, accuracy and category milestones
- Synced with **Google Play Games Services**

### Challenges
- Challenge friends via shareable deep links
- Compare scores, times and accuracy on a result card
- Track incoming/outgoing challenges

### Ads
- Banner ads on home screen
- Interstitial ads between quizzes
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
| Ads | Google AdMob |
| Build | Gradle Kotlin DSL + Version Catalogs |
| Testing | JUnit + MockK |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 (Android 15) |

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

| Source | Usage |
|--------|-------|
| [mledoze/countries](https://github.com/mledoze/countries) | Primary country data (names, aliases, codes) |
| [UN M49 Standard](https://unstats.un.org/unsd/methodology/m49/) | Regional classifications |

Country data is bundled as JSON in app assets and seeded into Room on first launch. A curated alias table handles answer validation with ~2000+ accepted name variants.

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

## Licence

Country data sourced from mledoze/countries under ODbL-1.0. The curated alias table is a derivative database.
