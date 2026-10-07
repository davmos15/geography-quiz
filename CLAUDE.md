# GeoQuiz – notes for Claude Code

## Start here
- **Always read `docs/UPGRADE_PLAN.md` first.** It is the single source of truth for the upgrade: phase status, codebase facts, decisions and the session log. Update it before finishing a session.
- Work one phase per session on its phase branch (`upgrade/pN-...`), branched from `main`.
- Project sub-agents live in `.claude/agents/` (android-implementer, test-engineer, geodata-engineer, licence-auditor, code-reviewer).

## Build and test
Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`, not committed).

```
./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # JVM unit tests (JUnit 4, MockK, Turbine, coroutines-test)
./gradlew lintDebug                  # Android lint
./gradlew assembleRelease            # R8-minified release (signs only if signing.properties exists)
./gradlew clean assembleDebug testDebugUnitTest lintDebug   # phase verification gate
```
CI (`.github/workflows/ci.yml`) runs build, unit tests and lint on every PR.
Never commit `signing.properties`, `*.jks` or `local.properties`.

## Architecture conventions
- Single `:app` module, package `com.geoquiz.app`: `data` (Room in `data/local/db`, DataStore in `data/local/preferences`, repositories, services such as ads, billing, Play Games), `domain` (models, repository interfaces, use cases), `ui` (one package per screen, Compose + Material 3), `di` (Hilt modules).
- MVVM: `@HiltViewModel` per screen exposing `StateFlow`; composables stay free of business logic. Logic lives in use cases or repositories and gets unit tests.
- Navigation: string routes in `ui/navigation/Screen.kt`, graph in `ui/navigation/AppNavigation.kt`.
- Room schema changes need a migration in `di/DatabaseModule.kt` and an exported schema in `app/schemas/`.
- New modes and features go behind a `FeatureFlag` (`domain/model/FeatureFlag.kt`, read via `FeatureFlagRepository`). Flags can be overridden only in debug builds from the hidden debug menu (tap the Settings title 7 times).
- New UI: user-facing strings in `res/values/strings.xml`; colours from theme tokens only (no hard-coded colours). Correct answers = blue + tick icon, wrong = orange + cross icon; never colour alone.
- Accessibility: content descriptions on meaningful icons and images, 48 dp touch targets, no fixed-height text containers, test at 200% font scale.
- Shared files (nav graph, DI modules, `strings.xml`, DB schema, `gradle/libs.versions.toml`) are edited by one agent at a time.
- Keep the existing letter/word-pattern categories working in every change. Gameplay stays offline.

## Writing
- Australian spelling in all UI copy, docs and comments (colour, licence (noun), practise (verb), organise, centre, favourite).

## Licensing and trade dress (L9)
- Only use data and assets from the "Approved sources" table in `docs/UPGRADE_PLAN.md` section 5, and record each in `data/SOURCES.md`.
- No "-dle"/"-le" style names for modes or features.
- No green/yellow/grey square tile grids (share cards, results or anywhere else).
- Never name other games or apps in the UI, strings, share text or store listing. Describe mechanics generically.
- Never copy names, layouts, artwork or data from other games or apps.
